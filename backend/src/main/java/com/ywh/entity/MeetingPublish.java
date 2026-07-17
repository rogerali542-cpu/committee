package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_publishes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingPublish {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false, unique = true)
    private CommitteeMeeting meeting;

    @Column(nullable = false)
    private Boolean published;

    @Column(name = "publish_date")
    private LocalDate publishDate;

    @Column(name = "public_title", length = 300)
    private String publicTitle;

    @Column(name = "public_content", columnDefinition = "TEXT")
    private String publicContent;

    // ===== 公示留痕（公示=正式动作，见 产品边界定稿.md §5）=====
    @Column(name = "published_by_id")
    private Long publishedById;

    @Column(name = "published_by_name", length = 50)
    private String publishedByName;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // ===== 撤回留痕（不丢历史）=====
    @Column(name = "withdrawn", nullable = false)
    private Boolean withdrawn;

    @Column(name = "withdrawn_by_id")
    private Long withdrawnById;

    @Column(name = "withdrawn_by_name", length = 50)
    private String withdrawnByName;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "withdraw_reason", length = 500)
    private String withdrawReason;
}
