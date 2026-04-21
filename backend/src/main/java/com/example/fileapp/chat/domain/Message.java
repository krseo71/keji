package com.example.fileapp.chat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages", indexes = {
    @Index(name = "ix_msg_conv", columnList = "conversationId,id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageRole role;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String toolEventsJson;

    @Column(length = 500)
    private String attachedFileIds;

    @Column(length = 500)
    private String generatedFileIds;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
