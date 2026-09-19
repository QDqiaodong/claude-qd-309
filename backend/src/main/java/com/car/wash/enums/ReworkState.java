package com.car.wash.enums;

/** 回炉单状态机：待回炉 → 回炉中 → 已验收。只许顺着走一步，不许跳步，也不许倒回去。 */
public enum ReworkState {
    待回炉, 回炉中, 已验收;

    /** 能不能从当前状态走到目标状态（只许往前走一步）。 */
    public boolean canMoveTo(ReworkState target) {
        return target != null && target.ordinal() == this.ordinal() + 1;
    }

    /** 还没验收的回炉才算「回炉未结」。 */
    public boolean isOpen() {
        return this != 已验收;
    }
}
