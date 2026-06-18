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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

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
