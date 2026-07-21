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

    @Column(name = "confirmed", nullable = false)
    private Boolean signedIn;  // DB: confirmed（确认参会）

    @Column(name = "attested", nullable = false)
    private Boolean signed;    // DB: attested（确认签字）

    @Column(name = "declined")
    private Boolean declined;   // 因故缺席（委员主动选择"无法参会"）；null/false=未拒绝

    @Column(name = "attendance_mode", length = 20)
    private String attendanceMode;

    @Builder.Default
    @Column(name = "proxy_sign_authorized", nullable = false)
    private Boolean proxySignAuthorized = false;

    @Column(name = "proxy_sign_authorized_at")
    private LocalDateTime proxySignAuthorizedAt;

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
