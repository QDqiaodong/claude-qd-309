package com.car.wash.service;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Bay;
import com.car.wash.entity.ReworkOrder;
import com.car.wash.enums.BayState;
import com.car.wash.enums.ReworkState;
import com.car.wash.enums.WashState;
import com.car.wash.repository.BayRepository;
import com.car.wash.repository.ReworkOrderRepository;
import com.car.wash.repository.WashOrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BayService {

    private static final List<ReworkState> OPEN_REWORK_STATES = List.of(ReworkState.待回炉, ReworkState.回炉中);

    private final BayRepository bays;
    private final WashOrderRepository orders;
    private final ReworkOrderRepository reworks;
    private final BayOccupancy occupancy;

    public BayService(BayRepository bays, WashOrderRepository orders,
                      ReworkOrderRepository reworks, BayOccupancy occupancy) {
        this.bays = bays;
        this.orders = orders;
        this.reworks = reworks;
        this.occupancy = occupancy;
    }

    public List<Bay> list(BayState state, String keyword) {
        return bays.findAllByOrderByIdAsc().stream()
                .filter(b -> state == null || state == b.bayState)
                .filter(b -> keyword == null || keyword.isBlank()
                        || b.bayCode.contains(keyword) || b.bayName.contains(keyword))
                .peek(b -> {
                    b.washCount = occupancy.washCount(b.id);
                    b.reworkCount = occupancy.reworkCount(b.id);
                })
                .toList();
    }

    @Transactional
    public Bay save(Bay form) {
        Bay existed = null;
        if (form.id != null) {
            existed = bays.findById(form.id).orElseThrow(() -> new BizException("工位不存在"));
            if (form.bayCode == null || form.bayCode.isBlank()) {
                form.bayCode = existed.bayCode;
            }
            if (form.bayName == null || form.bayName.isBlank()) {
                form.bayName = existed.bayName;
            }
        }
        if (form.bayCode == null || form.bayName == null) {
            throw new BizException("工位编号和名称都得填");
        }
        bays.findByBayCode(form.bayCode).ifPresent(o -> {
            if (!o.id.equals(form.id)) {
                throw new BizException("工位编号 " + form.bayCode + " 重复了");
            }
        });
        if (existed == null) {
            if (form.bayState == null) {
                form.bayState = BayState.空闲;
            }
            return bays.save(form);
        }
        if (form.bayState == BayState.停用 && existed.bayState != BayState.停用) {
            if (orders.countByBayIdAndWashStateNot(form.id, WashState.已完成) > 0) {
                throw new BizException("这个工位上还有没洗完的单子，先处理完再停用");
            }
            moveOpenReworksAway(form.id);
        }
        if (form.seatCount != null) {
            if (form.seatCount <= 0) {
                throw new BizException("同时容纳得是正数");
            }
            existed.seatCount = form.seatCount;
        }
        if (form.bayState != null) {
            existed.bayState = form.bayState;
        }
        existed.bayCode = form.bayCode;
        existed.bayName = form.bayName;
        return bays.save(existed);
    }

    /**
     * 停用工位前，把上面未验收的回炉挪走：回炉不能拆，只许改派到另一个空闲工位；
     * 当时选不到空位，整次停用失败，回炉状态原地不动。
     */
    private void moveOpenReworksAway(Long bayId) {
        List<ReworkOrder> open = reworks.findByBayIdAndReworkStateIn(bayId, OPEN_REWORK_STATES);
        for (ReworkOrder rw : open) {
            Bay target = bays.findAllByOrderByIdAsc().stream()
                    .filter(b -> !b.id.equals(bayId))
                    .filter(occupancy::canAccept)
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                throw new BizException("工位上还有" + rw.reworkState + "的回炉单 " + rw.reworkNo
                        + "，现在没有别的空闲工位能改派，停用不了");
            }
            rw.bayId = target.id;
            reworks.save(rw);
        }
    }
}
