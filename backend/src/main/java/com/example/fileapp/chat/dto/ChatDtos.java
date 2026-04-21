package com.example.fileapp.chat.dto;

import com.example.fileapp.chat.domain.Conversation;
import com.example.fileapp.chat.domain.Message;
import com.example.fileapp.chat.domain.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class ChatDtos {

    public record ConversationResponse(
            Long id,
            String title,
            Integer messageCount,
            String claudeSessionId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static ConversationResponse of(Conversation c) {
            return new ConversationResponse(
                    c.getId(), c.getTitle(), c.getMessageCount(),
                    c.getClaudeSessionId(), c.getCreatedAt(), c.getUpdatedAt()
            );
        }
    }

    public record CreateConversationRequest(@Size(max = 200) String title) {}

    public record UpdateConversationRequest(@NotBlank @Size(max = 200) String title) {}

    public record MessageResponse(
            Long id,
            MessageRole role,
            String content,
            String toolEventsJson,
            String attachedFileIds,
            String generatedFileIds,
            LocalDateTime createdAt
    ) {
        public static MessageResponse of(Message m) {
            return new MessageResponse(
                    m.getId(), m.getRole(), m.getContent(),
                    m.getToolEventsJson(), m.getAttachedFileIds(),
                    m.getGeneratedFileIds(), m.getCreatedAt()
            );
        }
    }

    public record SendMessageRequest(
            @NotBlank @Size(max = 20000) String content,
            List<Long> attachedFileIds
    ) {}
}
