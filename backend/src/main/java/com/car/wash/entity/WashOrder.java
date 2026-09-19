package com.car.wash.entity;

import com.car.wash.enums.WashState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;

/** 洗车单。 */
@Entity
@Table(name = "wash_order")
public class WashOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "order_no", nullable = false, length = 20, unique = true)
    public String orderNo;

    @Column(name = "plate_no", nullable = false, length = 16)
    public String plateNo;

    @Column(name = "bay_id")
    public Long bayId;

    @Column(name = "service_type", length = 24)
    public String serviceType;

    @Column(name = "price")
    public Integer price;

    @Column(name = "order_date")
    public LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "wash_state", nullable = false, length = 12)
    public WashState washState;

    // 以下只是带出给页面看的，不落库：挂着未验收回炉时，列表和详情都要看得出「回炉未结」。
    @Transient
    public Boolean reworkOpen;

    /** 未验收那张回炉单当前的状态（待回炉/回炉中），没有未结回炉就是空。 */
    @Transient
    public String reworkState;
}
