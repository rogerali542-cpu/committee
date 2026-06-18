package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "owner_meeting_evidences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerMeetingEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
    private OwnerMeetingRecord record;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "file_type", length = 20)
    private String fileType;

    @Column(name = "file_url", length = 500)
    private String fileUrl;
}
