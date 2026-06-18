package com.ywh.enums;

/**
 * 会议模式：普通（逐题表决）/ 快速（录音生成纪要，体验版）。
 * 取值为小写以与小程序前端（detail.meetingMode === 'quick'）保持一致。
 */
public enum MeetingMode {
    normal("普通模式"),
    quick("快速会议");

    private final String label;

    MeetingMode(String label) { this.label = label; }
    public String getLabel() { return label; }
}
