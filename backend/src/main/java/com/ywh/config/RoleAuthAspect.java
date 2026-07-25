package com.ywh.config;

import com.ywh.annotation.RequireRole;
import com.ywh.annotation.RequirePermission;
import com.ywh.entity.UserRoleEntity;
import com.ywh.service.AccessControlService;
import com.ywh.util.SecurityUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Aspect
@Component
@lombok.RequiredArgsConstructor
public class RoleAuthAspect {

    private final AccessControlService accessControl;

    @Before("@annotation(requireRole)")
    public void checkRole(JoinPoint jp, RequireRole requireRole) {
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (ur == null) {
            throw new AccessDeniedException("请先登录");
        }
        if (!accessControl.satisfiesAnyRole(ur, requireRole.value())) {
            throw new AccessDeniedException(
                String.format("仅 %s 可操作此功能", String.join("/", requireRole.value())));
        }
    }

    @Before("@annotation(requirePermission)")
    public void checkPermission(JoinPoint jp, RequirePermission requirePermission) {
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (ur == null) throw new AccessDeniedException("请先登录");
        for (var permission : requirePermission.value()) {
            if (!accessControl.hasPermission(ur, permission)) {
                throw new AccessDeniedException("当前身份无权执行此敏感操作");
            }
        }
    }
}
