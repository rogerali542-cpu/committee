package com.ywh.util;

import com.ywh.entity.UserRoleEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    private SecurityUtils() {}

    public static UserRoleEntity getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserRoleEntity) {
            return (UserRoleEntity) auth.getPrincipal();
        }
        return null;
    }

    public static boolean isChair() {
        UserRoleEntity ur = getCurrentUserRole();
        return ur != null && ur.getRole().isChair();
    }

    public static boolean isRecorder() {
        UserRoleEntity ur = getCurrentUserRole();
        return ur != null && ur.getRole().isRecorder();
    }

    public static boolean isExternal() {
        UserRoleEntity ur = getCurrentUserRole();
        return ur != null && ur.getRole().isExternal();
    }

    public static boolean isOwner() {
        UserRoleEntity ur = getCurrentUserRole();
        return ur != null && ur.getRole().isOwner();
    }

    public static boolean isPropertyMgmt() {
        UserRoleEntity ur = getCurrentUserRole();
        return ur != null && ur.getRole().isPropertyMgmt();
    }

    public static boolean isSelf(String memberName) {
        UserRoleEntity ur = getCurrentUserRole();
        return ur != null && ur.getRealName().equals(memberName);
    }

    public static String getCurrentRealName() {
        UserRoleEntity ur = getCurrentUserRole();
        return ur != null ? ur.getRealName() : null;
    }

    public static Long getCurrentUserId() {
        UserRoleEntity ur = getCurrentUserRole();
        return ur != null ? ur.getId() : null;
    }

    public static Long getCurrentCommunityId() {
        UserRoleEntity ur = getCurrentUserRole();
        return ur != null ? ur.getCommunity().getId() : null;
    }
}
