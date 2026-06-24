package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_recordings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingRecording {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private CommitteeMeeting meeting;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uploader_role_id")
    private UserRoleEntity uploader;

    @Column(name = "recording_url", length = 500, nullable = false)
    private String recordingUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "asr_status", length = 20, nullable = false)
    private String asrStatus; // none, pending, processing, done, failed

    @Column(name = "asr_task_id", length = 100)
    private String asrTaskId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.asrStatus == null) {
            this.asrStatus = "none";
        }
    }
}
