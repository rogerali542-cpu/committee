package com.ywh.config;

import com.ywh.entity.Community;
import com.ywh.entity.User;
import com.ywh.entity.UserRoleEntity;
import com.ywh.enums.ManagementScopeLevel;
import com.ywh.enums.UserRole;
import com.ywh.repository.CommunityRepository;
import com.ywh.repository.UserRepository;
import com.ywh.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * 权限底座的幂等初始化。项目当前关闭 Flyway，生产和本地均由 JPA 建列，
 * 因此这里负责补齐演示秘书与隐藏技术管理员；已有真实配置不会被覆盖。
 */
@Component
@RequiredArgsConstructor
public class RoleFoundationSeeder implements ApplicationRunner {
    private final UserRoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 旧库由 Hibernate 把 Java 枚举建成了 MySQL ENUM，新增角色会报 Data truncated。
        // 改成可扩展文本字段，后续新增业务身份不再需要改数据库枚举定义。
        jdbcTemplate.execute("ALTER TABLE user_roles MODIFY COLUMN role VARCHAR(20) NOT NULL");

        Community fallbackCommunity = communityRepository.findAll().stream()
                .min(Comparator.comparing(Community::getId))
                .orElse(null);
        if (fallbackCommunity == null) return;

        // JPA 给旧表新增 NOT NULL enabled 时，历史行在部分 MySQL 版本会得到 0。
        // 普通既有身份应保持启用；秘书例外，其 false 代表主任已经明确撤权，必须保留。
        roleRepository.findAll().stream()
                .filter(r -> r.getRole() != UserRole.业委会秘书 && !Boolean.TRUE.equals(r.getEnabled()))
                .forEach(r -> {
                    r.setEnabled(true);
                    roleRepository.save(r);
                });

        // 将原演示“记录员·秘书小李”升级为正式秘书，仅执行一次。
        roleRepository.findAll().stream()
                .filter(r -> r.getRole() == UserRole.记录员 && "秘书小李".equals(r.getRealName()))
                .forEach(secretary -> {
                    UserRoleEntity chair = roleRepository
                            .findByCommunityIdAndRole(secretary.getCommunity().getId(), UserRole.主任)
                            .stream().findFirst().orElse(null);
                    secretary.setRole(UserRole.业委会秘书);
                    secretary.setEnabled(true);
                    secretary.setScopeLevel(ManagementScopeLevel.COMMUNITY);
                    if (chair != null) {
                        secretary.setAuthorizedByRoleId(chair.getId());
                        secretary.setAuthorizedAt(LocalDateTime.now());
                        secretary.setRevokedAt(null);
                    }
                    roleRepository.save(secretary);
                });

        // 演示秘书姓名规范化：与其他委员一致用真实姓名，不再用「职位+小名」。仅在仍叫「秘书小李」时改一次。
        // （秘书小李只出现在角色定义里，不出现在任何会议材料正文，改名不影响材料一致性。）
        roleRepository.findAll().stream()
                .filter(r -> (r.getRole() == UserRole.业委会秘书 || r.getRole() == UserRole.记录员)
                        && "秘书小李".equals(r.getRealName()))
                .forEach(secretary -> {
                    secretary.setRealName("周敏");
                    roleRepository.save(secretary);
                });

        // 演示管理员账号：街道/区级先不细分，统一一个「管理员账号」（区级范围看得全）。
        // 非具体某人，故用账号名而非姓名。只补不存在的账号，不覆盖已有真实配置。
        User adminUser = userRepository.findByOpenid("demo-region-admin")
                .orElseGet(() -> userRepository.save(User.builder()
                        .openid("demo-region-admin")
                        .nickName("管理员账号")
                        .build()));
        boolean adminRoleExists = roleRepository.findByUserId(adminUser.getId()).stream()
                .anyMatch(r -> r.getRole() == UserRole.区级管理员);
        if (!adminRoleExists) {
            roleRepository.save(UserRoleEntity.builder()
                    .user(adminUser)
                    .community(fallbackCommunity)
                    .role(UserRole.区级管理员)
                    .realName("管理员账号")
                    .enabled(true)
                    .scopeLevel(ManagementScopeLevel.DISTRICT)
                    .scopeRegionCode("310106")
                    .scopeRegionName("上海市静安区")
                    .build());
        }

        // 隐藏技术管理员：只补不存在的账号，不进入 dev-roles。
        User technicalUser = userRepository.findByOpenid("internal-technical-admin")
                .orElseGet(() -> userRepository.save(User.builder()
                        .openid("internal-technical-admin")
                        .nickName("技术管理员")
                        .build()));
        boolean technicalRoleExists = roleRepository.findByUserId(technicalUser.getId()).stream()
                .anyMatch(r -> r.getRole() == UserRole.技术管理员);
        if (!technicalRoleExists) {
            roleRepository.save(UserRoleEntity.builder()
                    .user(technicalUser)
                    .community(fallbackCommunity)
                    .role(UserRole.技术管理员)
                    .realName("技术管理员")
                    .enabled(true)
                    .scopeLevel(ManagementScopeLevel.TECHNICAL)
                    .scopeRegionCode("310106")
                    .scopeRegionName("上海市静安区")
                    .build());
        }
    }
}
