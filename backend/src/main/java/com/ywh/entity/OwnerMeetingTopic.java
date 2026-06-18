package com.ywh.entity;

import com.ywh.enums.TopicType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "owner_meeting_topics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerMeetingTopic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private OwnerMeetingRecord record;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TopicType type;

    @Column(name = "for_owners")
    private Integer forOwners;

    @Column(name = "ag_owners")
    private Integer agOwners;

    @Column(name = "ab_owners")
    private Integer abOwners;

    @Column(name = "for_area")
    private Integer forArea;

    @Column(name = "ag_area")
    private Integer agArea;

    @Column(name = "ab_area")
    private Integer abArea;
}
