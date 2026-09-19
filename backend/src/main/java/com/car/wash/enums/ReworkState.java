package com.car.wash.enums;

/** 回炉单的状态机：待回炉 → 回炉中 → 已验收。 */
public enum ReworkState {
    待回炉, 回炉中, 已验收;

    /** 能不能从当前状态走到目标状态（只许往前走一步，不许跳步也不许倒回去）。 */
    public boolean canMoveTo(ReworkState target) {
        return target != null && target.ordinal() == this.ordinal() + 1;
    }
}
