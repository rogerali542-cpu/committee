package com.ywh.service;

import com.ywh.entity.UserRoleEntity;
import com.ywh.enums.UserRole;
import com.ywh.repository.UserRoleRepository;
import com.ywh.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoleManagementService {
    private final UserRoleRepository userRoleRepository;

    public List<Map<String, Object>> listSecretaries() {
        Long communityId = SecurityUtils.getCurrentCommunityId();
        return userRoleRepository.findByCommunityIdAndRole(communityId, UserRole.业委会秘书)
                .stream().map(this::toSecretaryView).toList();
    }

    @Transactional
    public Map<String, Object> authorizeSecretary(Long secretaryRoleId) {
        UserRoleEntity chair = requireChair();
        UserRoleEntity secretary = requireSecretaryInSameCommunity(secretaryRoleId, chair);
        secretary.setEnabled(true);
        secretary.setAuthorizedByRoleId(chair.getId());
        secretary.setAuthorizedAt(LocalDateTime.now());
        secretary.setRevokedAt(null);
        return toSecretaryView(userRoleRepository.save(secretary));
    }

    @Transactional
    public Map<String, Object> revokeSecretary(Long secretaryRoleId) {
        UserRoleEntity chair = requireChair();
        UserRoleEntity secretary = requireSecretaryInSameCommunity(secretaryRoleId, chair);
        secretary.setEnabled(false);
        secretary.setRevokedAt(LocalDateTime.now());
        return toSecretaryView(userRoleRepository.save(secretary));
    }

    private UserRoleEntity requireChair() {
        UserRoleEntity current = SecurityUtils.getCurrentUserRole();
        if (current == null || current.getRole() != UserRole.主任) {
            throw new IllegalArgumentException("仅主任可以管理秘书授权");
        }
        return current;
    }

    private UserRoleEntity requireSecretaryInSameCommunity(Long id, UserRoleEntity chair) {
        UserRoleEntity target = userRoleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("秘书身份不存在"));
        if (target.getRole() != UserRole.业委会秘书
                || target.getCommunity() == null
                || chair.getCommunity() == null
                || !target.getCommunity().getId().equals(chair.getCommunity().getId())) {
            throw new IllegalArgumentException("只能管理本小区的业委会秘书");
        }
        return target;
    }

    private Map<String, Object> toSecretaryView(UserRoleEntity role) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", role.getId());
        result.put("realName", role.getRealName());
        result.put("role", role.getRole().name());
        result.put("enabled", role.getEnabled());
        result.put("authorizedByRoleId", role.getAuthorizedByRoleId());
        result.put("authorizedAt", role.getAuthorizedAt());
        result.put("revokedAt", role.getRevokedAt());
        return result;
    }
}
