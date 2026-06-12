package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "record_attendances", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"record_id", "user_role_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordAttendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private MeetingRecord record;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_role_id", nullable = false)
    private UserRoleEntity userRole;

    @Column(name = "signed_in", nullable = false)
    private Boolean signedIn;

    @Column(nullable = false)
    private Boolean signed;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "operator_id")
    private UserRoleEntity operator;

    @Builder.Default
    @Column(name = "is_proxy", nullable = false)
    private Boolean isProxy = false;

    @Column(name = "proof_url", length = 500)
    private String proofUrl;

    @Column(name = "operated_at")
    private LocalDateTime operatedAt;
}
