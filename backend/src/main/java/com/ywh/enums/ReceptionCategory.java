package com.ywh.enums;

public enum ReceptionCategory {
    property("物业类"),
    public_affairs("公共事务"),
    neighbor("邻里纠纷"),
    other("其他");

    private final String label;
    ReceptionCategory(String label) { this.label = label; }
    public String getLabel() { return label; }
}
