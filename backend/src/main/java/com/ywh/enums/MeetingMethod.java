package com.ywh.enums;

public enum MeetingMethod {
    offline("线下会议"), online("线上会议");
    private final String label;
    MeetingMethod(String label) { this.label = label; }
    public String getLabel() { return label; }
}
