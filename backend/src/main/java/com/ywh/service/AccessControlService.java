package com.ywh.service;

import com.ywh.entity.UserRoleEntity;
import com.ywh.enums.SystemPermission;
import com.ywh.enums.UserRole;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class AccessControlService {

    public boolean isRoleActive(UserRoleEntity identity) {
        if (identity == null || !Boolean.TRUE.equals(identity.getEnabled())) return false;
        // 秘书必须处于明确授权状态；撤权后旧 token 也会在下一次请求被拒绝。
        if (identity.getRole() == UserRole.业委会秘书) {
            return identity.getAuthorizedByRoleId() != null && identity.getRevokedAt() == null;
        }
        return true;
    }

    /**
     * 兼容既有 @RequireRole：已授权秘书可执行主任/副主任的日常业务操作。
     * 不能继承的敏感动作必须额外使用 @RequirePermission。
     */
    public boolean satisfiesAnyRole(UserRoleEntity identity, String[] allowedRoles) {
        if (!isRoleActive(identity)) return false;
        String actual = identity.getRole().name();
        if (Arrays.asList(allowedRoles).contains(actual)) return true;
        if (identity.getRole() == UserRole.业委会秘书) {
            return Arrays.asList(allowedRoles).contains("主任")
                    || Arrays.asList(allowedRoles).contains("副主任")
                    || Arrays.asList(allowedRoles).contains("记录员");
        }
        if (identity.getRole() == UserRole.技术管理员) return true;
        return false;
    }

    public boolean hasPermission(UserRoleEntity identity, SystemPermission permission) {
        if (!isRoleActive(identity)) return false;
        UserRole role = identity.getRole();
        if (role == UserRole.技术管理员) return true;
        return switch (permission) {
            case SECRETARY_MANAGE, MEMBER_PERMISSION_MANAGE, FORMAL_ARCHIVE_REVOKE ->
                    role == UserRole.主任;
            case MANAGEMENT_OVERVIEW ->
                    role == UserRole.街道管理员 || role == UserRole.区级管理员;
            case TECHNICAL_ADMIN -> false;
        };
    }

    /** 本地测试登录只允许登录页明确展示的身份，隐藏/外部/历史身份不能靠猜ID进入。 */
    public boolean isDevLoginAllowed(UserRoleEntity identity) {
        if (identity == null || !isRoleActive(identity)) return false;
        UserRole role = identity.getRole();
        return role == UserRole.主任
                || role == UserRole.副主任
                || role == UserRole.委员
                || role == UserRole.业委会秘书
                || role == UserRole.街道管理员
                || role == UserRole.区级管理员;
    }
}
