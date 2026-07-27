package com.ywh.enums;

/**
 * 用印申请状态：待保管人确认 → 已用印（盖章留档）/ 已驳回。
 */
public enum SealUseStatus {
    pending("待确认"),
    approved("已用印"),
    rejected("已驳回");

    private final String label;
    SealUseStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
