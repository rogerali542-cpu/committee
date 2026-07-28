package com.ywh.enums;

/**
 * 用印申请状态：待保管人确认 → 已用印（盖章留档）/ 已驳回。
 */
public enum SealUseStatus {
    pending("处理中"),      // 0728 用户定：申请卡状态语从「待确认」改「处理中」（申请人视角）
    approved("已用印"),
    rejected("已驳回");

    private final String label;
    SealUseStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
