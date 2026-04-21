package com.example.fileapp.chat.controller;

import com.example.fileapp.chat.domain.Conversation;
import com.example.fileapp.chat.dto.ChatDtos.*;
import com.example.fileapp.chat.service.ChatService;
import com.example.fileapp.chat.service.ChatStreamer;
import com.example.fileapp.security.AppUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService service;
    private final ChatStreamer streamer;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Executor chatExecutor;

    public ChatController(ChatService service, ChatStreamer streamer,
                          @org.springframework.beans.factory.annotation.Qualifier("chatExecutor") Executor chatExecutor) {
        this.service = service;
        this.streamer = streamer;
        this.chatExecutor = chatExecutor;
    }

    // ---- Conversation CRUD ------------------------------------------------

    @GetMapping("/conversations")
    public Page<ConversationResponse> list(@AuthenticationPrincipal AppUserPrincipal user,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "30") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return service.list(user, pageable).map(ConversationResponse::of);
    }

    @PostMapping("/conversations")
    public ConversationResponse create(@AuthenticationPrincipal AppUserPrincipal user,
                                       @Valid @RequestBody(required = false) CreateConversationRequest req) {
        String title = req != null ? req.title() : null;
        return ConversationResponse.of(service.create(user, title));
    }

    @PatchMapping("/conversations/{id}")
    public ConversationResponse rename(@AuthenticationPrincipal AppUserPrincipal user,
                                       @PathVariable Long id,
                                       @Valid @RequestBody UpdateConversationRequest req) {
        return ConversationResponse.of(service.rename(user, id, req.title()));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal user,
                                       @PathVariable Long id) {
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{id}/messages")
    public java.util.List<MessageResponse> messages(@AuthenticationPrincipal AppUserPrincipal user,
                                                    @PathVariable Long id) {
        return service.listMessages(user, id).stream().map(MessageResponse::of).toList();
    }

    // ---- Send + stream ----------------------------------------------------

    @PostMapping(value = "/conversations/{id}/messages",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter send(@AuthenticationPrincipal AppUserPrincipal user,
                           @PathVariable Long id,
                           @Valid @RequestBody SendMessageRequest req) {
        Conversation conv = service.getOwned(user, id);
        SseEmitter emitter = new SseEmitter(15L * 60 * 1000); // 15min cap

        // persist user message before spawning claude
        service.saveUserMessage(id, req.content(), req.attachedFileIds());
        java.util.List<String> stagedNames;
        try {
            stagedNames = service.stageAttachments(user, id, req.attachedFileIds());
        } catch (Exception e) {
            safeSend(emitter, "error", Map.of("message", "attachment failed: " + e.getMessage()));
            emitter.complete();
            return emitter;
        }

        Path workDir;
        try {
            workDir = service.workspaceDir(id);
        } catch (Exception e) {
            safeSend(emitter, "error", Map.of("message", "workspace init failed: " + e.getMessage()));
            emitter.complete();
            return emitter;
        }

        String existingSession = conv.getClaudeSessionId();
        String newSessionId = existingSession == null ? UUID.randomUUID().toString() : null;
        String fullPrompt = buildPrompt(req.content(), stagedNames);

        CompletableFuture.runAsync(() -> {
            ReentrantLock lock = service.lockFor(id);
            lock.lock();
            try {
                java.util.Set<Path> before = service.snapshotFiles(workDir);
                ChatStreamer.Result result = streamer.run(
                        workDir, existingSession, newSessionId, fullPrompt,
                        delta -> safeSend(emitter, "delta", Map.of("text", delta)),
                        event -> safeSend(emitter, "tool", event)
                );
                java.util.List<com.example.fileapp.file.domain.StoredFile> created =
                        service.captureChatOutputs(id, user.getId(), workDir, before, req.content());
                java.util.List<Long> createdIds = created.stream()
                        .map(com.example.fileapp.file.domain.StoredFile::getId)
                        .toList();
                var saved = service.saveAssistantMessage(id, result.getAssistantText(),
                        result.getToolEvents(), result.getSessionId(), createdIds);
                safeSend(emitter, "done", Map.of(
                        "messageId", saved.getId(),
                        "sessionId", result.getSessionId() == null ? "" : result.getSessionId(),
                        "error", result.getError() == null ? "" : result.getError(),
                        "timedOut", result.isTimedOut(),
                        "createdFiles", created.stream().map(f -> Map.of(
                                "id", f.getId(),
                                "originalName", f.getOriginalName()
                        )).toList()
                ));
                emitter.complete();
            } catch (Exception e) {
                log.error("chat stream failed for conversation {}", id, e);
                safeSend(emitter, "error", Map.of("message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                emitter.completeWithError(e);
            } finally {
                lock.unlock();
            }
        }, chatExecutor);

        return emitter;
    }

    private String buildPrompt(String userContent, java.util.List<String> attachedNames) {
        if (attachedNames == null || attachedNames.isEmpty()) return userContent;
        StringBuilder sb = new StringBuilder();
        sb.append("[첨부 파일들 — 현재 작업 디렉토리에 위치]\n");
        for (String n : attachedNames) sb.append("- ").append(n).append('\n');
        sb.append("\n[사용자 메시지]\n");
        sb.append(userContent);
        return sb.toString();
    }

    private void safeSend(SseEmitter emitter, String event, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name(event).data(json, MediaType.APPLICATION_JSON));
        } catch (JsonProcessingException e) {
            log.warn("sse serialize failed", e);
        } catch (IllegalStateException e) {
            // emitter already completed
        } catch (Exception e) {
            log.debug("sse send skipped: {}", e.getMessage());
        }
    }
}
