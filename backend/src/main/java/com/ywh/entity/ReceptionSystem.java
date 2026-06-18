package com.ywh.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reception_systems")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceptionSystem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(nullable = false)
    private Boolean published;

    @Column(name = "time_desc", length = 100)
    private String timeDesc;

    @Column(length = 200)
    private String place;

    @Column(length = 100)
    private String person;
}
