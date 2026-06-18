package com.ywh.enums;

public enum MeetingStage {
    preparing("准备阶段"),
    ongoing("进行中"),
    ended("会议结束");

    private final String label;

    MeetingStage(String label) { this.label = label; }
    public String getLabel() { return label; }
}
