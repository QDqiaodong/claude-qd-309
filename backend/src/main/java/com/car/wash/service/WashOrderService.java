package com.car.wash.service;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Bay;
import com.car.wash.entity.MemberCard;
import com.car.wash.entity.Rework;
import com.car.wash.entity.WashOrder;
import com.car.wash.enums.WashState;
import com.car.wash.repository.BayRepository;
import com.car.wash.repository.MemberCardRepository;
import com.car.wash.repository.WashOrderRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 洗车单：状态只能顺着走，工位停用 / 满位就开不了单；
 * 已完成的单子只要还挂着未验收回炉，就不能退回待洗或清洗中，列表和详情带「回炉未结」标。
 * 如果指定用会员卡结账，金额直接从卡里扣。
 */
@Service
public class WashOrderService {

    private final WashOrderRepository orders;
    private final BayRepository bays;
    private final MemberCardRepository cards;
    private final BayOccupancyService occupancy;
    private final ReworkService reworks;

    public WashOrderService(WashOrderRepository orders, BayRepository bays, MemberCardRepository cards,
                            BayOccupancyService occupancy, ReworkService reworks) {
        this.orders = orders;
        this.bays = bays;
        this.cards = cards;
        this.occupancy = occupancy;
        this.reworks = reworks;
    }

    public List<WashOrder> list(WashState state, String keyword) {
        List<WashOrder> rows = orders.findAllByOrderByIdAsc();
        Map<Long, Rework> openMap = reworks.openByOrderIds(rows.stream().map(o -> o.id).toList());
        rows.forEach(o -> {
            Rework rw = openMap.get(o.id);
            o.reworkOpen = rw != null;
            o.openReworkNo = rw == null ? null : rw.reworkNo;
        });
        return rows.stream()
                .filter(o -> state == null || state == o.washState)
                .filter(o -> keyword == null || keyword.isBlank()
                        || o.orderNo.contains(keyword) || o.plateNo.contains(keyword))
                .sorted((a, b) -> b.id.compareTo(a.id))
                .toList();
    }

    @Transactional
    public WashOrder save(WashOrder form) {
        WashOrder existed = null;
        if (form.id != null) {
            existed = orders.findById(form.id).orElseThrow(() -> new BizException("洗车单不存在"));
            if (form.orderNo == null || form.orderNo.isBlank()) {
                form.orderNo = existed.orderNo;
            }
            if (form.plateNo == null || form.plateNo.isBlank()) {
                form.plateNo = existed.plateNo;
            }
        }
        if (form.orderNo == null || form.plateNo == null) {
            throw new BizException("单号和车牌都得填");
        }
        orders.findByOrderNo(form.orderNo).ifPresent(o -> {
            if (!o.id.equals(form.id)) {
                throw new BizException("单号 " + form.orderNo + " 重复了");
            }
        });
        if (form.price != null && form.price < 0) {
            throw new BizException("金额不能是负数");
        }

        if (existed != null) {
            // 已完成且还挂着未验收回炉：原单必须钉在已完成，退回待洗/清洗中一律拦。
            boolean movingBack = form.washState != null && form.washState != existed.washState
                    && form.washState.ordinal() < existed.washState.ordinal();
            if (existed.washState == WashState.已完成 && movingBack
                    && !reworks.openOfOrder(existed.id).isEmpty()) {
                throw new BizException("原单「" + existed.orderNo + "」还挂着未验收的回炉（回炉未结），不能退回"
                        + form.washState);
            }
            if (form.washState != null && form.washState != existed.washState
                    && !existed.washState.canMoveTo(form.washState)) {
                throw new BizException("洗车单得按「待洗 → 清洗中 → 已完成」走，不能跳步也不能倒回去");
            }
            // 换工位（且单子还没洗完）才需要重新看停用/满位；留在原工位不重复计数。
            if (form.bayId != null && !form.bayId.equals(existed.bayId)
                    && (form.washState == null ? existed.washState : form.washState) != WashState.已完成) {
                Bay bay = occupancy.requireBay(form.bayId);
                occupancy.requireCanAccept(bay, "这单");
                existed.bayId = form.bayId;
            } else if (form.bayId != null) {
                existed.bayId = form.bayId;
            }
            if (form.serviceType != null && !form.serviceType.isBlank()) {
                existed.serviceType = form.serviceType;
            }
            if (form.price != null) {
                existed.price = form.price;
            }
            if (form.washState != null) {
                existed.washState = form.washState;
            }
            return orders.save(existed);
        }

        // 新单：必须排进一个没停用、还没满的工位（停用和满位都当场说清原因）。
        if (form.bayId == null) {
            throw new BizException("开单得先排个工位");
        }
        Bay bay = occupancy.requireBay(form.bayId);
        occupancy.requireCanAccept(bay, "新单");
        form.orderDate = form.orderDate == null ? LocalDate.now() : form.orderDate;
        form.washState = form.washState == null ? WashState.待洗 : form.washState;
        return orders.save(form);
    }

    /** 会员卡结账：从卡里扣这笔钱，余额不够或者卡停用都会被拦。 */
    @Transactional
    public MemberCard payByCard(Long cardId, Integer amount) {
        MemberCard card = cards.findById(cardId).orElseThrow(() -> new BizException("会员卡不存在"));
        card.pay(amount);
        return cards.save(card);
    }
}
