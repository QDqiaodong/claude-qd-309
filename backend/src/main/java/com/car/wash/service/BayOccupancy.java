package com.car.wash.service;

import com.car.wash.entity.Bay;
import com.car.wash.enums.BayState;
import com.car.wash.enums.ReworkState;
import com.car.wash.enums.WashState;
import com.car.wash.repository.ReworkOrderRepository;
import com.car.wash.repository.WashOrderRepository;
import org.springframework.stereotype.Service;

/**
 * 工位占用的统一口径：没洗完的洗车单 + 回炉中的回炉单，都算占着位。
 * 开新单、挂回炉、改派、停用工位都看这一套数，店里才对得上账。
 */
@Service
public class BayOccupancy {

    private final WashOrderRepository orders;
    private final ReworkOrderRepository reworks;

    public BayOccupancy(WashOrderRepository orders, ReworkOrderRepository reworks) {
        this.orders = orders;
        this.reworks = reworks;
    }

    /** 这个工位上还没洗完（待洗/清洗中）的洗车单数。 */
    public long washCount(Long bayId) {
        return orders.countByBayIdAndWashStateNot(bayId, WashState.已完成);
    }

    /** 这个工位上回炉中的回炉单数。 */
    public long reworkCount(Long bayId) {
        return reworks.countByBayIdAndReworkState(bayId, ReworkState.回炉中);
    }

    /** 总占用：在洗的车 + 回炉中的车。 */
    public long occupancy(Long bayId) {
        return washCount(bayId) + reworkCount(bayId);
    }

    /** 同时容纳上限；没配容量就当不限。 */
    public int seatLimit(Bay bay) {
        return bay.seatCount == null || bay.seatCount <= 0 ? Integer.MAX_VALUE : bay.seatCount;
    }

    /** 占满了没有。 */
    public boolean full(Bay bay) {
        return occupancy(bay.id) >= seatLimit(bay);
    }

    /** 能不能再往里排车：没停用、也没占满。 */
    public boolean canAccept(Bay bay) {
        return bay.bayState != BayState.停用 && !full(bay);
    }
}
