package com.car.wash.service;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Bay;
import com.car.wash.entity.Rework;
import com.car.wash.entity.WashOrder;
import com.car.wash.enums.BayState;
import com.car.wash.enums.ReworkState;
import com.car.wash.enums.WashState;
import com.car.wash.repository.BayRepository;
import com.car.wash.repository.ReworkRepository;
import com.car.wash.repository.WashOrderRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 回炉台规则（按店长口径）：
 * 1. 只能挂在「已完成」的洗车单上；同一张原单只要还有未验收回炉，不得再挂第二张。
 * 2. 回炉自己按「待回炉 → 回炉中 → 已验收」走，不跳步、不倒退。
 * 3. 挂上必须先认原单工位；原工位停用或被别的车占满，才允许改派到别的空闲工位。
 * 4. 回炉中算工位占用；满位的工位接不了新回炉，开新单也一样（见 WashOrderService）。
 * 5. 回炉中工位被停用：单子不拆，只能改派到别的空闲工位；选不到空位，改派失败、状态停在回炉中。
 */
@Service
public class ReworkService {

    private static final List<ReworkState> OPEN_STATES =
            List.of(ReworkState.待回炉, ReworkState.回炉中);

    private final ReworkRepository reworks;
    private final WashOrderRepository orders;
    private final BayRepository bays;
    private final BayOccupancyService occupancy;

    public ReworkService(ReworkRepository reworks, WashOrderRepository orders,
                         BayRepository bays, BayOccupancyService occupancy) {
        this.reworks = reworks;
        this.orders = orders;
        this.bays = bays;
        this.occupancy = occupancy;
    }

    public List<Rework> list(ReworkState state, String keyword) {
        List<Rework> all = enrich(reworks.findAllByOrderByIdAsc());
        return all.stream()
                .filter(r -> state == null || state == r.reworkState)
                .filter(r -> keyword == null || keyword.isBlank()
                        || r.reworkNo.contains(keyword)
                        || (r.orderNo != null && r.orderNo.contains(keyword))
                        || (r.plateNo != null && r.plateNo.contains(keyword)))
                .sorted((a, b) -> b.id.compareTo(a.id))
                .toList();
    }

    /** 洗车单打标用：哪些原单还挂着未验收回炉。 */
    public Map<Long, Rework> openByOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        return reworks.findByReworkStateIn(OPEN_STATES).stream()
                .filter(r -> orderIds.contains(r.orderId))
                .collect(Collectors.toMap(r -> r.orderId, Function.identity(), (a, b) -> a));
    }

    /** 原单上有没有未验收回炉。 */
    public List<Rework> openOfOrder(Long orderId) {
        return reworks.findByOrderIdAndReworkStateIn(orderId, OPEN_STATES);
    }

    /**
     * 挂回炉：原单必须已完成且没有未结回炉；工位先认原单。
     * 原工位认不上（停用/满位）才接受改派，且只能改派到空闲（没停用、还有空位）的工位。
     */
    @Transactional
    public Rework create(Rework form) {
        if (form.orderId == null) {
            throw new BizException("得先说清这是哪张洗车单的回炉");
        }
        WashOrder origin = orders.findById(form.orderId)
                .orElseThrow(() -> new BizException("原洗车单不存在"));
        if (origin.washState != WashState.已完成) {
            throw new BizException("只有走到「已完成」的单子才能挂回炉，单子「" + origin.orderNo + "」现在是「"
                    + origin.washState + "」");
        }
        if (!reworks.findByOrderIdAndReworkStateIn(origin.id, OPEN_STATES).isEmpty()) {
            throw new BizException("原单「" + origin.orderNo + "」还有没验收的回炉，不能再挂第二张");
        }
        if (origin.bayId == null) {
            throw new BizException("原单「" + origin.orderNo + "」没有工位记录，没法先认原工位");
        }
        Bay originBay = occupancy.requireBay(origin.bayId);

        Long bayId;
        if (form.bayId == null || form.bayId.equals(originBay.id)) {
            // 先认原工位：停用或满位都要当场把原因说清楚，不许偷偷塞到别处。
            occupancy.requireCanAccept(originBay, "回炉");
            bayId = originBay.id;
        } else {
            // 原工位还能进车（没停用、没满），就不许嫌麻烦随便找空位。
            if (originBay.bayState != BayState.停用 && !occupancy.isFull(originBay)) {
                throw new BizException("原工位「" + originBay.bayName + "」还空着，回炉得先认原工位，不能随便改派");
            }
            Bay target = occupancy.requireBay(form.bayId);
            occupancy.requireCanAccept(target, "回炉");
            bayId = target.id;
        }

        Rework rw = new Rework();
        rw.reworkNo = nextNo();
        rw.orderId = origin.id;
        rw.bayId = bayId;
        rw.reason = form.reason;
        rw.reworkState = ReworkState.待回炉;
        rw.createdDate = LocalDate.now();
        return enrichOne(reworks.save(rw));
    }

    /** 顺着状态机往前走一步。 */
    @Transactional
    public Rework advance(Long id) {
        Rework rw = mustGet(id);
        if (rw.reworkState == ReworkState.已验收) {
            throw new BizException("这张回炉已经验收过了，流程到头了");
        }
        ReworkState next = rw.reworkState == ReworkState.待回炉
                ? ReworkState.回炉中 : ReworkState.已验收;
        if (!rw.reworkState.canMoveTo(next)) {
            throw new BizException("回炉得按「待回炉 → 回炉中 → 已验收」走，不能跳步");
        }
        Bay bay = occupancy.requireBay(rw.bayId);
        if (next == ReworkState.回炉中) {
            // 车进场要占座：工位停用或满了（被别的车占满）就进不去，状态留在待回炉。
            occupancy.requireCanAccept(bay, "回炉");
        } else {
            // 验收前工位被停用了：单子不能偷偷变已验收，得先改派到空闲工位做完。
            if (bay.bayState == BayState.停用) {
                throw new BizException("工位「" + bay.bayName + "」已经停用，先把这张回炉改派到空闲工位再验收");
            }
        }
        rw.reworkState = next;
        return enrichOne(reworks.save(rw));
    }

    /**
     * 改派工位：待回炉 / 回炉中都可以改派，但目标必须是空闲工位（没停用、还有空位）。
     * 选不到空位就整次失败，回炉状态原样不动。回炉中的车不会被拆掉。
     */
    @Transactional
    public Rework reassign(Long id, Long targetBayId) {
        Rework rw = mustGet(id);
        if (rw.reworkState == ReworkState.已验收) {
            throw new BizException("已经验收的回炉不用再改派了");
        }
        Bay target = occupancy.requireBay(targetBayId);
        occupancy.requireCanAccept(target, "回炉");
        rw.bayId = target.id;
        return enrichOne(reworks.save(rw));
    }

    private Rework mustGet(Long id) {
        return reworks.findById(id).orElseThrow(() -> new BizException("回炉单不存在"));
    }

    private String nextNo() {
        for (long i = reworks.count() + 1; ; i++) {
            String no = String.format("RW-%02d", i);
            if (!reworks.existsByReworkNo(no)) {
                return no;
            }
        }
    }

    private List<Rework> enrich(List<Rework> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Map<Long, WashOrder> orderMap = orders.findAllById(
                rows.stream().map(r -> r.orderId).distinct().toList()).stream()
                .collect(Collectors.toMap(o -> o.id, Function.identity()));
        Map<Long, Bay> bayMap = bays.findAllById(
                rows.stream().map(r -> r.bayId).distinct().toList()).stream()
                .collect(Collectors.toMap(b -> b.id, Function.identity()));
        for (Rework r : rows) {
            WashOrder o = orderMap.get(r.orderId);
            if (o != null) {
                r.orderNo = o.orderNo;
                r.plateNo = o.plateNo;
                r.reassigned = o.bayId != null && !o.bayId.equals(r.bayId);
            }
            Bay b = bayMap.get(r.bayId);
            if (b != null) {
                r.bayCode = b.bayCode;
                r.bayName = b.bayName;
            }
        }
        return rows;
    }

    private Rework enrichOne(Rework rw) {
        enrich(List.of(rw));
        return rw;
    }
}
