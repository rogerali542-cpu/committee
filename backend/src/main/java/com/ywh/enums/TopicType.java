package com.ywh.enums;

public enum TopicType {
    notice("通报事项"),
    discussion("讨论事项"),
    decision("决定事项"),
    major("重大事项"),
    ordinary("普通决议");

    private final String label;
    TopicType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
