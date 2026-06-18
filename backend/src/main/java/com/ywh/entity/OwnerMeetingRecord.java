package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "owner_meeting_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerMeetingRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false, unique = true)
    private OwnerMeeting meeting;

    @Column(name = "present_owners")
    private Integer presentOwners;

    @Column(name = "present_area")
    private Integer presentArea;

    @Column(name = "supervisor_name", length = 50)
    private String supervisorName;

    @Column(name = "supervisor_signed")
    private Boolean supervisorSigned;

    @Column(nullable = false)
    private Boolean designated;

    @Column(nullable = false)
    private Boolean monitor;

    @Column(nullable = false)
    private Boolean callout;

    @Column(nullable = false)
    private Boolean tally;

    @Column(name = "minutes_text", columnDefinition = "TEXT")
    private String minutesText;
}
