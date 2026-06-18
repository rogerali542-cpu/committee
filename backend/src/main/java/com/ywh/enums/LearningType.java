package com.ywh.enums;

public enum LearningType {
    internal("内部学习"),
    street("街镇组织培训"),
    special("专项业务培训");

    private final String label;
    LearningType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
