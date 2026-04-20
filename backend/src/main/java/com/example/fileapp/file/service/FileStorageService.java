package com.example.fileapp.file.service;

import com.example.fileapp.common.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class FileStorageService {

    private final Path rootPath;
    private final Set<String> allowedExtensions;

    public FileStorageService(
            @Value("${app.storage.path}") String root,
            @Value("${app.storage.allowed-extensions}") String allowedExtensionsCsv
    ) {
        this.rootPath = Paths.get(root).toAbsolutePath().normalize();
        this.allowedExtensions = new HashSet<>();
        for (String e : allowedExtensionsCsv.split(",")) {
            if (!e.isBlank()) allowedExtensions.add(e.trim().toLowerCase());
        }
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(rootPath);
        log.info("File storage root: {}", rootPath);
    }

    public record Saved(String storedName, String relativePath, String extension, long sizeBytes) {}

    public Saved save(String originalName, InputStream in) {
        String ext = extractExtension(originalName);
        if (!allowedExtensions.contains(ext)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "extension not allowed: " + ext);
        }
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String storedName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        String relative = datePart + "/" + storedName;
        Path target = rootPath.resolve(relative).normalize();
        if (!target.startsWith(rootPath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid path");
        }
        try {
            Files.createDirectories(target.getParent());
            long size = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return new Saved(storedName, relative, ext, size);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to save file");
        }
    }

    public Path resolve(String relativePath) {
        Path p = rootPath.resolve(relativePath).normalize();
        if (!p.startsWith(rootPath)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid path");
        }
        return p;
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            log.warn("failed to delete file: {}", relativePath, e);
        }
    }

    public boolean isExtensionAllowed(String ext) {
        return allowedExtensions.contains(ext == null ? "" : ext.toLowerCase());
    }

    public static String extractExtension(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        if (i < 0 || i == filename.length() - 1) return "";
        return filename.substring(i + 1).toLowerCase();
    }
}
