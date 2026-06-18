package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "owner_meeting_ballots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerMeetingBallot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false, unique = true)
    private OwnerMeeting meeting;

    @Column(name = "delivered_count", nullable = false)
    private Integer deliveredCount;

    @Column(name = "records_complete", nullable = false)
    private Boolean recordsComplete;

    @Column(name = "non_face_announce", nullable = false)
    private Boolean nonFaceAnnounce;
}
