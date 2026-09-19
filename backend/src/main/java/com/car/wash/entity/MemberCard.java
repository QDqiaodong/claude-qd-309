package com.car.wash.entity;

import com.car.wash.dto.BizException;
import com.car.wash.enums.CardState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 储值会员卡。 */
@Entity
@Table(name = "member_card")
public class MemberCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "card_no", nullable = false, length = 20, unique = true)
    public String cardNo;

    @Column(name = "holder_name", nullable = false, length = 40)
    public String holderName;

    @Column(name = "phone", length = 11)
    public String phone;

    @Column(name = "balance", nullable = false)
    public Integer balance;

    @Column(name = "card_level", length = 16)
    public String cardLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_state", nullable = false, length = 12)
    public CardState cardState;

    /** 扣费：停卡、余额不够都不行。 */
    public void pay(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new BizException("扣费金额得大于 0");
        }
        if (cardState != CardState.正常) {
            throw new BizException("这张卡已经停用了，扣不了款");
        }
        int bal = balance == null ? 0 : balance;
        if (bal < amount) {
            throw new BizException("卡里只剩 " + bal + " 元，扣不动 " + amount + " 元");
        }
        balance = bal - amount;
    }
}
