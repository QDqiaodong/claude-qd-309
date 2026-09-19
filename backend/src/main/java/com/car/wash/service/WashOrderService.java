package com.car.wash.service;

import com.car.wash.dto.BizException;
import com.car.wash.entity.Bay;
import com.car.wash.entity.MemberCard;
import com.car.wash.entity.WashOrder;
import com.car.wash.enums.BayState;
import com.car.wash.enums.WashState;
import com.car.wash.repository.BayRepository;
import com.car.wash.repository.MemberCardRepository;
import com.car.wash.repository.WashOrderRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 洗车单：状态只能顺着走，工位停用就开不了单；
 * 如果指定用会员卡结账，金额直接从卡里扣。
 */
@Service
public class WashOrderService {

    private final WashOrderRepository orders;
    private final BayRepository bays;
    private final MemberCardRepository cards;

    public WashOrderService(WashOrderRepository orders, BayRepository bays, MemberCardRepository cards) {
        this.orders = orders;
        this.bays = bays;
        this.cards = cards;
    }

    public List<WashOrder> list(WashState state, String keyword) {
        return orders.findAllByOrderByIdAsc().stream()
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
        if (existed != null && existed.washState == WashState.已完成 && form.washState != null
                && form.washState != WashState.已完成) {
            throw new BizException("这单已经洗完了，不能再退回去");
        }
        if (form.washState != null && existed != null && form.washState != existed.washState
                && !existed.washState.canMoveTo(form.washState)) {
            throw new BizException("洗车单得按「待洗 → 清洗中 → 已完成」走，不能跳步");
        }
        if (form.bayId != null) {
            Bay bay = bays.findById(form.bayId).orElseThrow(() -> new BizException("工位不存在"));
            if (bay.bayState == BayState.停用) {
                throw new BizException("工位「" + bay.bayName + "」停用了，排不进去");
            }
            if (existed != null) {
                existed.bayId = form.bayId;
            }
        }
        if (existed != null) {
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
