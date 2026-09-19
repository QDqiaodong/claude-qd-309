package com.car.wash.enums;

/** 洗车单的状态机：待洗 → 清洗中 → 已完成。 */
public enum WashState {
    待洗, 清洗中, 已完成;

    /** 能不能从当前状态走到目标状态（只许往前走一步）。 */
    public boolean canMoveTo(WashState target) {
        return target != null && target.ordinal() == this.ordinal() + 1;
    }
}
