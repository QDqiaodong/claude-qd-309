package com.car.wash.repository;

import com.car.wash.entity.WashOrder;
import com.car.wash.enums.WashState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WashOrderRepository extends JpaRepository<WashOrder, Long> {

    Optional<WashOrder> findByOrderNo(String orderNo);

    List<WashOrder> findAllByOrderByIdAsc();

    long countByBayId(Long bayId);

    long countByBayIdAndWashStateNot(Long bayId, WashState state);
}
