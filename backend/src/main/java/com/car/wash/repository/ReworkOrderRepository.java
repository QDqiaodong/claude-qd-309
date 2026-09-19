package com.car.wash.repository;

import com.car.wash.entity.ReworkOrder;
import com.car.wash.enums.ReworkState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReworkOrderRepository extends JpaRepository<ReworkOrder, Long> {

    Optional<ReworkOrder> findByReworkNo(String reworkNo);

    List<ReworkOrder> findAllByOrderByIdAsc();

    List<ReworkOrder> findByOrderId(Long orderId);

    List<ReworkOrder> findByReworkStateIn(Collection<ReworkState> states);

    List<ReworkOrder> findByBayIdAndReworkStateIn(Long bayId, Collection<ReworkState> states);

    long countByBayIdAndReworkState(Long bayId, ReworkState state);

    boolean existsByOrderIdAndReworkStateIn(Long orderId, Collection<ReworkState> states);
}
