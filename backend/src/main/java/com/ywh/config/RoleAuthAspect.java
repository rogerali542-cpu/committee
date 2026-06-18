package com.ywh.config;

import com.ywh.annotation.RequireRole;
import com.ywh.entity.UserRoleEntity;
import com.ywh.util.SecurityUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Aspect
@Component
public class RoleAuthAspect {

    @Before("@annotation(requireRole)")
    public void checkRole(JoinPoint jp, RequireRole requireRole) {
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (ur == null) {
            throw new AccessDeniedException("请先登录");
        }
        List<String> allowed = Arrays.asList(requireRole.value());
        if (!allowed.contains(ur.getRole().name())) {
            throw new AccessDeniedException(
                String.format("仅 %s 可操作此功能", String.join("/", allowed)));
        }
    }
}
