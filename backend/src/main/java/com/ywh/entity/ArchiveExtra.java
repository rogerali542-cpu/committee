package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "archive_extras")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveExtra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "size_text")
    private String sizeText;

    @Column(name = "reason")
    private String reason;

    @Column(name = "added_by")
    private String addedBy;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }
}
