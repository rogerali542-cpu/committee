package com.ywh.enums;

// 学习记录显式分类：内部学习 / 外部培训（不由 type 推断，由数据显式标注）
public enum LearningCategory {
    internal("内部学习"),
    external("外部培训");

    private final String label;
    LearningCategory(String label) { this.label = label; }
    public String getLabel() { return label; }
}
