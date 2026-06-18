package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "owner_meeting_notifies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerMeetingNotify {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false, unique = true)
    private OwnerMeeting meeting;

    @Column(name = "sent_count", nullable = false)
    private Integer sentCount;

    @Column(nullable = false)
    private Boolean announced;

    @Column(name = "content_complete", nullable = false)
    private Boolean contentComplete;
}
