package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String openid;

    @Column(length = 64)
    private String unionid;

    @Column(name = "nick_name", length = 50)
    private String nickName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    // 注：原 phone 字段前后端零使用（不收集/不展示/不查询/不用于登录），按数据最小化原则已移除。
    //     数据库 users.phone 列因 ddl-auto:update 不自动删列会残留，可手动 ALTER TABLE users DROP COLUMN phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
