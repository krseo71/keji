package com.example.fileapp.chat.repository;

import com.example.fileapp.chat.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findAllByConversationIdOrderByIdAsc(Long conversationId);
    void deleteAllByConversationId(Long conversationId);
}
