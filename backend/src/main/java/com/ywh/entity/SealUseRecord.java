package com.ywh.entity;

import com.ywh.enums.SealType;
import com.ywh.enums.SealUseStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 用印记录（用印台账）。依据《印章管理制度》与《工作记录制度》「印章使用情况记录」：
 * 每次用印登记印章、用途、关联文件、申请人与时间，并由保管人（主任/副主任）确认。
 */
@Entity
@Table(name = "seal_use_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SealUseRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    /** 用印的印章。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "seal_type", nullable = false, length = 20)
    private SealType sealType;

    /** 用印事由 / 用途。 */
    @Column(columnDefinition = "TEXT")
    private String purpose;

    /** 关联文件名称（选填）。 */
    @Column(name = "document_name", length = 200)
    private String documentName;

    /** 申请人姓名与角色（登记时的当前用户，作为台账留痕）。 */
    @Column(name = "applicant_name", length = 30)
    private String applicantName;

    @Column(name = "applicant_role", length = 20)
    private String applicantRole;

    /** 状态：待确认 / 已用印 / 已驳回。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SealUseStatus status;

    /** 保管人确认（主任 / 副主任）：谁、何时。已用印即为盖章留档时间。 */
    @Column(name = "custodian_name", length = 30)
    private String custodianName;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** 驳回理由（选填）。 */
    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = SealUseStatus.pending;
    }
}
