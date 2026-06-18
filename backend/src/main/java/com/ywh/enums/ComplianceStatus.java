package com.ywh.enums;

public enum ComplianceStatus {
    valid("会议有效"),
    flawed("会议有效，带说明归档"),
    invalid("会议无效");

    private final String label;
    ComplianceStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
