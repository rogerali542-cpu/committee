package com.ywh.entity;

import com.ywh.enums.VoteChoice;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "topic_votes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"topic_id", "user_role_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicVote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private RecordTopic topic;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_role_id", nullable = false)
    private UserRoleEntity userRole;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private VoteChoice choice;

    @Column(name = "selected_id")
    private Long selectedId;

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
