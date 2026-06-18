package com.ywh.enums;

public enum UserRole {
    主任, 副主任, 委员, 记录员, 业主, 物业;

    public boolean isChair() {
        return this == 主任 || this == 副主任;
    }

    public boolean isCommitteeMember() {
        return this == 主任 || this == 副主任 || this == 委员;
    }

    public boolean isRecorder() {
        return this == 记录员;
    }

    public boolean isOwner() {
        return this == 业主;
    }

    public boolean isPropertyMgmt() {
        return this == 物业;
    }

    public boolean isExternal() {
        return isOwner() || isPropertyMgmt();
    }
}
