package com.ywh.repository;

import com.ywh.entity.UserRoleEntity;
import com.ywh.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, Long> {
    List<UserRoleEntity> findByUserId(Long userId);
    List<UserRoleEntity> findByCommunityIdAndRoleNotIn(Long communityId, List<String> excludedRoles);
    List<UserRoleEntity> findByCommunityIdAndRoleIn(Long communityId, List<String> roles);
    List<UserRoleEntity> findByCommunityIdAndRole(Long communityId, UserRole role);
}
