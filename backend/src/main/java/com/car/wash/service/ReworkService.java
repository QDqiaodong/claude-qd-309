package com.car.wash.service;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Bay;
import com.car.wash.entity.ReworkOrder;
import com.car.wash.entity.WashOrder;
import com.car.wash.enums.BayState;
import com.car.wash.enums.ReworkState;
import com.car.wash.enums.WashState;
import com.car.wash.repository.BayRepository;
import com.car.wash.repository.ReworkOrderRepository;
import com.car.wash.repository.WashOrderRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 回炉台：只挂在已完成的洗车单上；同一张原单同时只允许一张未验收的回炉。
 * 工位先认原单那个，原工位停用、还有没洗完的车或者占满了，才允许改派到别的空闲工位。
 */
@Service
public class ReworkService {

    private static final List<ReworkState> OPEN_STATES = List.of(ReworkState.待回炉, ReworkState.回炉中);

    private final ReworkOrderRepository reworks;
    private final WashOrderRepository orders;
    private final BayRepository bays;
    private final BayOccupancy occupancy;

    public ReworkService(ReworkOrderRepository reworks, WashOrderRepository orders,
                         BayRepository bays, BayOccupancy occupancy) {
        this.reworks = reworks;
        this.orders = orders;
        this.bays = bays;
        this.occupancy = occupancy;
    }

    public List<ReworkOrder> list(ReworkState state, String keyword) {
        Map<Long, WashOrder> orderMap = orders.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(o -> o.id, Function.identity()));
        Map<Long, Bay> bayMap = bays.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(b -> b.id, Function.identity()));
        return reworks.findAllByOrderByIdAsc().stream()
                .filter(r -> state == null || state == r.reworkState)
                .filter(r -> {
                    if (keyword == null || keyword.isBlank()) {
                        return true;
                    }
                    WashOrder o = orderMap.get(r.orderId);
                    return r.reworkNo.contains(keyword)
                            || (o != null && (o.orderNo.contains(keyword) || o.plateNo.contains(keyword)));
                })
                .sorted((a, b) -> b.id.compareTo(a.id))
                .peek(r -> fillDisplay(r, orderMap.get(r.orderId), bayMap.get(r.bayId)))
                .toList();
    }

    public ReworkOrder get(Long id) {
        ReworkOrder rw = reworks.findById(id).orElseThrow(() -> new BizException("回炉单不存在"));
        fillDisplay(rw, orders.findById(rw.orderId).orElse(null), bays.findById(rw.bayId).orElse(null));
        return rw;
    }

    /**
     * 挂一张回炉：原单必须已完成、没有未验收的回炉；
     * 工位先认原工位，认不上才允许改派，改派的工位也得是空闲能排的。
     */
    @Transactional
    public ReworkOrder create(ReworkOrder form) {
        if (form.orderId == null) {
            throw new BizException("得指明挂在哪张洗车单上");
        }
        WashOrder order = orders.findById(form.orderId)
                .orElseThrow(() -> new BizException("原洗车单不存在"));
        if (order.washState != WashState.已完成) {
            throw new BizException("只有已完成的洗车单才能挂回炉，这单还在「" + order.washState + "」");
        }
        reworks.findByOrderId(order.id).stream()
                .filter(r -> OPEN_STATES.contains(r.reworkState))
                .findFirst()
                .ifPresent(r -> {
                    throw new BizException("这张单还有未验收的回炉（" + r.reworkNo + "，" + r.reworkState + "），不能再挂一张");
                });

        ReworkOrder rw = new ReworkOrder();
        rw.orderId = order.id;
        rw.bayId = resolveBay(order, form.bayId);
        rw.reason = form.reason;
        // 回炉只能从待回炉起走，不许一上来就变成已验收。
        rw.reworkState = ReworkState.待回炉;
        rw.reworkNo = "RW-" + order.orderNo + "-" + (reworks.findByOrderId(order.id).size() + 1);
        return reworks.save(rw);
    }

    /** 回炉往前推一步：待回炉 → 回炉中 → 已验收，跳步和倒退都拦。 */
    @Transactional
    public ReworkOrder advance(Long id, ReworkState target) {
        ReworkOrder rw = reworks.findById(id).orElseThrow(() -> new BizException("回炉单不存在"));
        if (target == null || target == rw.reworkState) {
            throw new BizException("回炉状态没变，没什么好推的");
        }
        if (!rw.reworkState.canMoveTo(target)) {
            throw new BizException("回炉得按「待回炉 → 回炉中 → 已验收」走，不能跳步也不能倒回去");
        }
        if (target == ReworkState.回炉中) {
            Bay bay = bays.findById(rw.bayId).orElseThrow(() -> new BizException("工位不存在"));
            if (bay.bayState == BayState.停用) {
                throw new BizException("工位「" + bay.bayName + "」停用了，先改派到别的空闲工位再开工");
            }
            if (occupancy.full(bay)) {
                throw new BizException("工位「" + bay.bayName + "」现在占满了，等腾出位置再开工");
            }
        }
        rw.reworkState = target;
        return reworks.save(rw);
    }

    /** 改派到另一个工位：只能改未验收的，目标工位必须空闲能排。 */
    @Transactional
    public ReworkOrder reassign(Long id, Long bayId) {
        ReworkOrder rw = reworks.findById(id).orElseThrow(() -> new BizException("回炉单不存在"));
        if (rw.reworkState == ReworkState.已验收) {
            throw new BizException("这张回炉已经验收了，不用再改派");
        }
        if (bayId == null) {
            throw new BizException("得选一个要改派过去的工位");
        }
        Bay target = bays.findById(bayId).orElseThrow(() -> new BizException("工位不存在"));
        if (target.bayState == BayState.停用) {
            throw new BizException("工位「" + target.bayName + "」停用了，改派不过去");
        }
        if (occupancy.full(target)) {
            throw new BizException("工位「" + target.bayName + "」占满了，改派不过去");
        }
        rw.bayId = target.id;
        return reworks.save(rw);
    }

    /**
     * 定回炉工位：先认原单那个工位；原工位停用、还有没洗完的别的车、或者占满了，
     * 才允许改派到别的空闲工位。认不上又说不出去哪，当场把原因摆出来。
     */
    private Long resolveBay(WashOrder order, Long requestedBayId) {
        Bay orig = order.bayId == null ? null : bays.findById(order.bayId).orElse(null);
        boolean askedForOther = requestedBayId != null && (orig == null || !requestedBayId.equals(orig.id));

        if (!askedForOther) {
            if (orig == null) {
                throw new BizException("原单没排过工位，得指定一个空闲工位再挂回炉");
            }
            if (orig.bayState == BayState.停用) {
                throw new BizException("原工位「" + orig.bayName + "」停用了，挂不回去" + reassignHint());
            }
            if (occupancy.full(orig)) {
                throw new BizException("原工位「" + orig.bayName + "」占满了（还有没洗完的车或回炉中的单），挂不回去"
                        + reassignHint());
            }
            return orig.id;
        }

        // 想改派：原工位真的认不上（停用 / 有没洗完的别的车 / 占满）才放行。
        boolean origUnusable = orig == null
                || orig.bayState == BayState.停用
                || occupancy.washCount(orig.id) > 0
                || occupancy.full(orig);
        if (!origUnusable) {
            throw new BizException("原工位「" + orig.bayName + "」还空着能用，回炉得先认原工位，认不上才能改派");
        }
        Bay target = bays.findById(requestedBayId).orElseThrow(() -> new BizException("工位不存在"));
        if (target.bayState == BayState.停用) {
            throw new BizException("改派的工位「" + target.bayName + "」停用了，换一个有空的");
        }
        if (occupancy.full(target)) {
            throw new BizException("改派的工位「" + target.bayName + "」占满了，换一个有空的");
        }
        return target.id;
    }

    /** 改派提示：店里还有没有能接的工位，一句话说清楚。 */
    private String reassignHint() {
        boolean anyFree = bays.findAllByOrderByIdAsc().stream().anyMatch(occupancy::canAccept);
        return anyFree ? "；请改派到别的空闲工位" : "；店里现在也没有能改派的空闲工位，回炉先挂不上";
    }

    private void fillDisplay(ReworkOrder rw, WashOrder order, Bay bay) {
        if (order != null) {
            rw.orderNo = order.orderNo;
            rw.plateNo = order.plateNo;
        }
        if (bay != null) {
            rw.bayCode = bay.bayCode;
            rw.bayName = bay.bayName;
        }
    }
}
