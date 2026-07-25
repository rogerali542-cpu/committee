package com.ywh.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.ywh.dto.LoginRequest;
import com.ywh.dto.LoginResponse;
import com.ywh.entity.Community;
import com.ywh.entity.User;
import com.ywh.entity.UserRoleEntity;
import com.ywh.enums.UserRole;
import com.ywh.repository.CommunityRepository;
import com.ywh.repository.UserRepository;
import com.ywh.repository.UserRoleRepository;
import com.ywh.util.JwtUtil;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    @Value("${app.dev-auth-enabled:false}")
    private boolean devAuthEnabled;

    private final WxMaService wxMaService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepo;
    private final UserRoleRepository userRoleRepo;
    private final CommunityRepository communityRepo;

    @Transactional
    public LoginResponse login(LoginRequest req) {
        // In dev mode, use the code as user identifier directly
        // In production: exchange code for openid via wxMaService
        String openid;
        try {
            if (req.getCode().startsWith("dev-")) {
                openid = req.getCode();
            } else {
                WxMaJscode2SessionResult session = wxMaService.getUserService().getSessionInfo(req.getCode());
                openid = session.getOpenid();
            }
        } catch (Exception e) {
            log.warn("微信登录失败，使用dev mode: {}", e.getMessage());
            openid = "dev-" + req.getCode().substring(0, Math.min(req.getCode().length(), 20));
        }

        String finalOpenid = openid;
        User user = userRepo.findByOpenid(openid)
                .orElseGet(() -> userRepo.save(User.builder()
                        .openid(finalOpenid)
                        .nickName(req.getNickName())
                        .avatarUrl(req.getAvatarUrl())
                        .build()));

        String token = jwtUtil.generateToken(user.getId(), openid);
        List<UserRoleEntity> roles = userRoleRepo.findByUserId(user.getId());

        List<LoginResponse.RoleDTO> roleDTOs = roles.stream()
                .map(r -> LoginResponse.RoleDTO.builder()
                        .id(r.getId())
                        .role(r.getRole().name())
                        .realName(r.getRealName())
                        .communityId(r.getCommunity().getId())
                        .communityName(r.getCommunity().getName())
                        .enabled(r.getEnabled())
                        .scopeLevel(r.getScopeLevel().name())
                        .scopeRegionCode(r.getScopeRegionCode())
                        .scopeRegionName(r.getScopeRegionName())
                        .build())
                .collect(Collectors.toList());

        UserRoleEntity activeRole = roles.isEmpty() ? null : roles.get(0);
        LoginResponse.RoleDTO activeRoleDTO = activeRole != null ? roleDTOs.get(0) : null;

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .roles(roleDTOs)
                .activeRole(activeRoleDTO)
                .build();
    }

    /** 测试期身份名单：普通业务身份；隐藏技术管理员永不下发到客户端。 */
    public List<java.util.Map<String, Object>> listDevRoles() {
        if (!devAuthEnabled) {
            throw new AccessDeniedException("测试身份登录未启用");
        }
        return userRoleRepo.findAll().stream()
                .filter(r -> {
                    String n = r.getRole() == null ? "" : r.getRole().name();
                    return "主任".equals(n) || "副主任".equals(n) || "委员".equals(n)
                            || "业委会秘书".equals(n) || "街道管理员".equals(n) || "区级管理员".equals(n);
                })
                .sorted(java.util.Comparator.comparing(UserRoleEntity::getId))
                .map(r -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("realName", r.getRealName());
                    m.put("role", r.getRole().name());
                    m.put("communityId", r.getCommunity() != null ? r.getCommunity().getId() : null);
                    m.put("communityName", r.getCommunity() != null ? r.getCommunity().getName() : null);
                    m.put("enabled", r.getEnabled());
                    m.put("scopeLevel", r.getScopeLevel().name());
                    m.put("scopeRegionCode", r.getScopeRegionCode());
                    m.put("scopeRegionName", r.getScopeRegionName());
                    return m;
                })
                .collect(Collectors.toList());
    }

    public LoginResponse refreshMe() {
        UserRoleEntity ur = SecurityUtils.getCurrentUserRole();
        if (ur == null) throw new IllegalArgumentException("未登录");

        List<UserRoleEntity> roles = userRoleRepo.findByUserId(ur.getUser().getId());
        List<LoginResponse.RoleDTO> roleDTOs = roles.stream()
                .map(r -> LoginResponse.RoleDTO.builder()
                        .id(r.getId())
                        .role(r.getRole().name())
                        .realName(r.getRealName())
                        .communityId(r.getCommunity().getId())
                        .communityName(r.getCommunity().getName())
                        .enabled(r.getEnabled())
                        .scopeLevel(r.getScopeLevel().name())
                        .scopeRegionCode(r.getScopeRegionCode())
                        .scopeRegionName(r.getScopeRegionName())
                        .build())
                .collect(Collectors.toList());

        LoginResponse.RoleDTO activeDTO = roleDTOs.stream()
                .filter(r -> r.getId().equals(ur.getId()))
                .findFirst().orElse(roleDTOs.get(0));

        return LoginResponse.builder()
                .userId(ur.getUser().getId())
                .roles(roleDTOs)
                .activeRole(activeDTO)
                .build();
    }
}
