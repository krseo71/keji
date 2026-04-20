package com.example.fileapp.file.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stored_files", indexes = {
    @Index(name = "ix_files_owner", columnList = "ownerId"),
    @Index(name = "ix_files_source", columnList = "source"),
    @Index(name = "ix_files_extension", columnList = "extension"),
    @Index(name = "ix_files_job", columnList = "generationJobId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String originalName;

    @Column(nullable = false, length = 128)
    private String storedName;

    @Column(nullable = false, length = 1024)
    private String relativePath;

    @Column(nullable = false, length = 128)
    private String contentType;

    @Column(nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 16)
    private String extension;

    @Column(nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FileSource source;

    private Long generationJobId;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (source == null) source = FileSource.MANUAL;
    }
}
