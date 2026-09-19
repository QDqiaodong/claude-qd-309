package com.car.wash.service;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Bay;
import com.car.wash.enums.BayState;
import com.car.wash.repository.BayRepository;
import com.car.wash.repository.WashOrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BayService {

    private final BayRepository bays;
    private final WashOrderRepository orders;

    public BayService(BayRepository bays, WashOrderRepository orders) {
        this.bays = bays;
        this.orders = orders;
    }

    public List<Bay> list(BayState state, String keyword) {
        return bays.findAllByOrderByIdAsc().stream()
                .filter(b -> state == null || state == b.bayState)
                .filter(b -> keyword == null || keyword.isBlank()
                        || b.bayCode.contains(keyword) || b.bayName.contains(keyword))
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
        if (form.bayState == BayState.停用 && existed.bayState != BayState.停用
                && orders.countByBayId(form.id) > 0
                && orders.countByBayIdAndWashStateNot(form.id, com.car.wash.enums.WashState.已完成) > 0) {
            throw new BizException("这个工位上还有没洗完的单子，先处理完再停用");
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
}
