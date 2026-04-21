package com.example.fileapp.chat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations", indexes = {
    @Index(name = "ix_conv_owner_updated", columnList = "ownerId,updatedAt")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(length = 64)
    private String claudeSessionId;

    @Column(nullable = false)
    private Integer messageCount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (messageCount == null) messageCount = 0;
        if (title == null) title = "New conversation";
    }
}
