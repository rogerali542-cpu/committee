package com.ywh.entity;

import com.ywh.enums.UserRole;
import com.ywh.enums.ManagementScopeLevel;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 该实体会被放进 SecurityContext 作登录主体；Lombok 的 toString/equals 不能触碰
    // 懒加载关联，否则在 Session 关闭后被日志/异常处理调用时会抛 LazyInitializationException。
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private UserRole role;

    @Column(name = "real_name", nullable = false, length = 30)
    private String realName;

    @Column(name = "room_number", length = 50)
    private String roomNumber;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /** 秘书的授权主任身份 ID。使用普通字段避免 SecurityContext 序列化时触发关联加载。 */
    @Column(name = "authorized_by_role_id")
    private Long authorizedByRoleId;

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_level", nullable = false, length = 20)
    @Builder.Default
    private ManagementScopeLevel scopeLevel = ManagementScopeLevel.COMMUNITY;

    @Column(name = "scope_region_code", length = 30)
    private String scopeRegionCode;

    @Column(name = "scope_region_name", length = 80)
    private String scopeRegionName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.enabled == null) this.enabled = true;
        if (this.scopeLevel == null) this.scopeLevel = ManagementScopeLevel.COMMUNITY;
        this.createdAt = LocalDateTime.now();
    }
}
