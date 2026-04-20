package com.example.fileapp.generation.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "generation_jobs", indexes = {
    @Index(name = "ix_jobs_owner", columnList = "ownerId"),
    @Index(name = "ix_jobs_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    private Long inputFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    @Column(length = 2000)
    private String errorMessage;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String stdoutLog;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = JobStatus.PENDING;
    }
}
