package com.ywh.entity;

import com.ywh.enums.LearningType;
import com.ywh.enums.MeetingStage;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "learning_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(nullable = false, length = 200)
    private String title;

    private LocalDate date;

    private LocalTime time;

    @Column(length = 100)
    private String location;

    @Column(length = 30)
    private String trainer;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Boolean notified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private LearningType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MeetingStage stage;

    @Column(nullable = false)
    private Integer progress;

    @Column(length = 100)
    private String attendees;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
