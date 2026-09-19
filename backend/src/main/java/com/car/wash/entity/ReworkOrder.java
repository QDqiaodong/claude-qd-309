package com.car.wash.entity;

import com.car.wash.enums.ReworkState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/** 回炉单：挂在一张已完成的洗车单上，占着一个工位重新处理。 */
@Entity
@Table(name = "rework_order")
public class ReworkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "rework_no", nullable = false, length = 32, unique = true)
    public String reworkNo;

    /** 原洗车单。 */
    @Column(name = "order_id", nullable = false)
    public Long orderId;

    /** 回炉占用的工位，挂上时必须定下来。 */
    @Column(name = "bay_id", nullable = false)
    public Long bayId;

    @Column(name = "reason", length = 120)
    public String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "rework_state", nullable = false, length = 12)
    public ReworkState reworkState;

    // 以下只是带出给页面看的，不落库。
    @Transient
    public String orderNo;

    @Transient
    public String plateNo;

    @Transient
    public String bayCode;

    @Transient
    public String bayName;
}
