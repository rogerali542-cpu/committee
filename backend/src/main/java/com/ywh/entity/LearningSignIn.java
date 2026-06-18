package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "learning_sign_ins")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LearningSignIn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "real_name", nullable = false)
    private String realName;

    @Column(name = "signed_in")
    private Boolean signedIn;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;
}
