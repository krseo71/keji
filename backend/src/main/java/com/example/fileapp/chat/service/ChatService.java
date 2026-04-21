package com.example.fileapp.chat.service;

import com.example.fileapp.chat.domain.Conversation;
import com.example.fileapp.chat.domain.Message;
import com.example.fileapp.chat.domain.MessageRole;
import com.example.fileapp.chat.repository.ConversationRepository;
import com.example.fileapp.chat.repository.MessageRepository;
import com.example.fileapp.common.ApiException;
import com.example.fileapp.file.domain.StoredFile;
import com.example.fileapp.file.repository.StoredFileRepository;
import com.example.fileapp.file.service.FileService;
import com.example.fileapp.file.service.FileStorageService;
import com.example.fileapp.security.AppUserPrincipal;
import com.example.fileapp.user.domain.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final StoredFileRepository fileRepository;
    private final FileStorageService storage;
    private final FileService fileService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.chat.workspace-root:/data/chat}")
    private String workspaceRoot;

    private final Map<Long, ReentrantLock> convLocks = new ConcurrentHashMap<>();

    // ---- CRUD -------------------------------------------------------------

    @Transactional
    public Conversation create(AppUserPrincipal user, String title) {
        Conversation c = Conversation.builder()
                .ownerId(user.getId())
                .title(title == null || title.isBlank() ? "New conversation" : title.trim())
                .build();
        return conversationRepository.save(c);
    }

    @Transactional(readOnly = true)
    public Page<Conversation> list(AppUserPrincipal user, Pageable pageable) {
        return conversationRepository.findAllByOwnerIdOrderByUpdatedAtDesc(user.getId(), pageable);
    }

    @Transactional(readOnly = true)
    public Conversation getOwned(AppUserPrincipal user, Long id) {
        Conversation c = conversationRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "conversation not found"));
        if (!user.getId().equals(c.getOwnerId()) && user.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden");
        }
        return c;
    }

    @Transactional(readOnly = true)
    public List<Message> listMessages(AppUserPrincipal user, Long conversationId) {
        getOwned(user, conversationId);
        return messageRepository.findAllByConversationIdOrderByIdAsc(conversationId);
    }

    @Transactional
    public Conversation rename(AppUserPrincipal user, Long id, String title) {
        Conversation c = getOwned(user, id);
        c.setTitle(title.trim());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    @Transactional
    public void delete(AppUserPrincipal user, Long id) {
        Conversation c = getOwned(user, id);
        // Cascade: remove stored files that this conversation generated
        Set<Long> fileIds = collectGeneratedFileIds(id);
        for (Long fid : fileIds) {
            fileRepository.findById(fid).ifPresent(sf -> {
                storage.delete(sf.getRelativePath());
                fileRepository.delete(sf);
            });
        }
        // Remove the per-conversation workspace directory entirely
        try {
            Path ws = Paths.get(workspaceRoot, String.valueOf(id));
            if (Files.isDirectory(ws)) {
                Files.walkFileTree(ws, new java.nio.file.SimpleFileVisitor<>() {
                    @Override public java.nio.file.FileVisitResult visitFile(Path p, java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                        Files.deleteIfExists(p); return java.nio.file.FileVisitResult.CONTINUE;
                    }
                    @Override public java.nio.file.FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                        Files.deleteIfExists(d); return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            log.warn("workspace cleanup failed for conv {}", id, e);
        }
        messageRepository.deleteAllByConversationId(id);
        conversationRepository.delete(c);
        convLocks.remove(id);
    }

    private Set<Long> collectGeneratedFileIds(Long conversationId) {
        Set<Long> out = new LinkedHashSet<>();
        for (Message m : messageRepository.findAllByConversationIdOrderByIdAsc(conversationId)) {
            String s = m.getGeneratedFileIds();
            if (s == null || s.isBlank()) continue;
            for (String part : s.split(",")) {
                try { out.add(Long.parseLong(part.trim())); } catch (NumberFormatException ignore) {}
            }
        }
        return out;
    }

    // ---- Message flow (used by streaming controller) ----------------------

    @Transactional
    public Message saveUserMessage(Long conversationId, String content, List<Long> attachedFileIds) {
        String joined = attachedFileIds == null || attachedFileIds.isEmpty()
                ? null
                : attachedFileIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(null);
        Message m = Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.USER)
                .content(content)
                .attachedFileIds(joined)
                .build();
        messageRepository.save(m);
        Conversation c = conversationRepository.findById(conversationId).orElseThrow();
        c.setMessageCount(c.getMessageCount() + 1);
        c.setUpdatedAt(LocalDateTime.now());
        if ("New conversation".equals(c.getTitle())) {
            c.setTitle(deriveTitle(content));
        }
        return m;
    }

    @Transactional
    public Message saveAssistantMessage(Long conversationId, String content,
                                        List<Map<String, Object>> toolEvents,
                                        String updatedSessionId,
                                        List<Long> generatedFileIds) {
        String toolJson = null;
        if (toolEvents != null && !toolEvents.isEmpty()) {
            try {
                toolJson = mapper.writeValueAsString(toolEvents);
            } catch (JsonProcessingException e) {
                log.warn("tool events serialize failed", e);
            }
        }
        String genIds = (generatedFileIds == null || generatedFileIds.isEmpty())
                ? null
                : generatedFileIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(null);
        Message m = Message.builder()
                .conversationId(conversationId)
                .role(MessageRole.ASSISTANT)
                .content(content)
                .toolEventsJson(toolJson)
                .generatedFileIds(genIds)
                .build();
        messageRepository.save(m);

        Conversation c = conversationRepository.findById(conversationId).orElseThrow();
        c.setMessageCount(c.getMessageCount() + 1);
        c.setUpdatedAt(LocalDateTime.now());
        if (updatedSessionId != null && !updatedSessionId.isBlank()
                && !updatedSessionId.equals(c.getClaudeSessionId())) {
            c.setClaudeSessionId(updatedSessionId);
        }
        return m;
    }

    // ---- Workspace helpers ------------------------------------------------

    public Path workspaceDir(Long conversationId) throws IOException {
        Path dir = Paths.get(workspaceRoot, String.valueOf(conversationId));
        Files.createDirectories(dir);
        return dir;
    }

    @Transactional(readOnly = true)
    public List<String> stageAttachments(AppUserPrincipal user, Long conversationId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return List.of();
        Path dir;
        try { dir = workspaceDir(conversationId); } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "workspace init failed");
        }
        List<String> names = new ArrayList<>();
        for (Long id : fileIds) {
            StoredFile sf = fileRepository.findById(id)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "attached file not found: " + id));
            if (!user.getId().equals(sf.getOwnerId()) && user.getRole() != Role.ADMIN) {
                throw new ApiException(HttpStatus.FORBIDDEN, "cannot attach file " + id);
            }
            Path src = storage.resolve(sf.getRelativePath());
            Path dst = dir.resolve(sf.getOriginalName());
            try {
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "copy failed: " + sf.getOriginalName());
            }
            names.add(sf.getOriginalName());
        }
        return names;
    }

    public ReentrantLock lockFor(Long conversationId) {
        return convLocks.computeIfAbsent(conversationId, k -> new ReentrantLock());
    }

    /** Snapshot regular files present in workspace before a chat turn. */
    public Set<Path> snapshotFiles(Path dir) {
        if (!Files.isDirectory(dir)) return Set.of();
        try (var s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).collect(java.util.stream.Collectors.toSet());
        } catch (IOException e) {
            log.warn("snapshot failed: {}", dir, e);
            return Set.of();
        }
    }

    /**
     * After a chat turn, any files newly created by claude in the workspace are
     * moved into the regular file storage (source=GENERATED) so the user can
     * find them in the Files tab. Returns the saved StoredFile entries.
     */
    public List<StoredFile> captureChatOutputs(Long conversationId, Long ownerId,
                                               Path workDir, Set<Path> before, String promptHead) {
        List<StoredFile> saved = new ArrayList<>();
        if (!Files.isDirectory(workDir)) return saved;
        try (var s = Files.list(workDir)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                if (before.contains(p)) continue;
                String name = p.getFileName().toString();
                String ext = FileStorageService.extractExtension(name);
                if (!storage.isExtensionAllowed(ext)) {
                    log.info("chat output skipped (ext): {}", name);
                    continue;
                }
                String desc = "chat#" + conversationId + ": " + head(promptHead, 150);
                try {
                    StoredFile sf = fileService.saveFromPath(ownerId, name, p, null, desc);
                    saved.add(sf);
                    Files.deleteIfExists(p);
                } catch (Exception e) {
                    log.info("chat output skipped {}: {}", name, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("capture outputs failed: {}", workDir, e);
        }
        return saved;
    }

    private String head(String s, int n) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > n ? t.substring(0, n) + "…" : t;
    }

    private String deriveTitle(String content) {
        if (content == null) return "New conversation";
        String s = content.replaceAll("\\s+", " ").trim();
        return s.length() > 50 ? s.substring(0, 50) + "…" : s;
    }
}
