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
}
