package com.example.fileapp.file.service;

import com.example.fileapp.common.ApiException;
import com.example.fileapp.file.domain.FileSource;
import com.example.fileapp.file.domain.StoredFile;
import com.example.fileapp.file.repository.StoredFileRepository;
import com.example.fileapp.security.AppUserPrincipal;
import com.example.fileapp.user.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class FileService {

    private final StoredFileRepository repository;
    private final FileStorageService storage;

    @Transactional
    public StoredFile uploadManual(AppUserPrincipal user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "file is empty");
        }
        try (InputStream in = file.getInputStream()) {
            FileStorageService.Saved saved = storage.save(file.getOriginalFilename(), in);
            StoredFile entity = StoredFile.builder()
                    .originalName(file.getOriginalFilename())
                    .storedName(saved.storedName())
                    .relativePath(saved.relativePath())
                    .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .sizeBytes(saved.sizeBytes())
                    .extension(saved.extension())
                    .ownerId(user.getId())
                    .source(FileSource.MANUAL)
                    .build();
            return repository.save(entity);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "upload failed");
        }
    }

    @Transactional
    public StoredFile saveFromPath(Long ownerId, String originalName, Path path, Long jobId, String description) {
        try (InputStream in = Files.newInputStream(path)) {
            FileStorageService.Saved saved = storage.save(originalName, in);
            StoredFile entity = StoredFile.builder()
                    .originalName(originalName)
                    .storedName(saved.storedName())
                    .relativePath(saved.relativePath())
                    .contentType(guessContentType(saved.extension()))
                    .sizeBytes(saved.sizeBytes())
                    .extension(saved.extension())
                    .ownerId(ownerId)
                    .source(FileSource.GENERATED)
                    .generationJobId(jobId)
                    .description(description)
                    .build();
            return repository.save(entity);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to save generated file");
        }
    }

    @Transactional(readOnly = true)
    public StoredFile getOwned(AppUserPrincipal user, Long id) {
        StoredFile f = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "file not found"));
        if (!user.getId().equals(f.getOwnerId()) && user.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden");
        }
        return f;
    }

    @Transactional
    public void delete(AppUserPrincipal user, Long id) {
        StoredFile f = getOwned(user, id);
        storage.delete(f.getRelativePath());
        repository.delete(f);
    }

    private String guessContentType(String ext) {
        return switch (ext) {
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "pdf" -> "application/pdf";
            case "csv" -> "text/csv";
            case "md", "txt" -> "text/plain; charset=UTF-8";
            case "json" -> "application/json";
            case "hwp", "hwpx" -> "application/x-hwp";
            default -> "application/octet-stream";
        };
    }
}
