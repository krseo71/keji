package com.example.fileapp.chat.repository;

import com.example.fileapp.chat.domain.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Page<Conversation> findAllByOwnerIdOrderByUpdatedAtDesc(Long ownerId, Pageable pageable);
}
