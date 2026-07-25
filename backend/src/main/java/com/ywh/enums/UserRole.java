package com.ywh.enums;

public enum UserRole {
    主任, 副主任, 委员, 业委会秘书, 记录员, 业主, 物业, 街道管理员, 区级管理员, 技术管理员;

    public boolean isChair() {
        return this == 主任 || this == 副主任;
    }

    public boolean isCommitteeMember() {
        return this == 主任 || this == 副主任 || this == 委员;
    }

    public boolean isSecretary() {
        return this == 业委会秘书;
    }

    /** 主任、副主任和已授权秘书的日常业务操作界面保持一致。 */
    public boolean isCommitteeOperator() {
        return isChair() || isSecretary();
    }

    public boolean isGovernmentManager() {
        return this == 街道管理员 || this == 区级管理员;
    }

    public boolean isTechnicalAdmin() {
        return this == 技术管理员;
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
