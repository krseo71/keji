package com.example.fileapp.file.controller;

import com.example.fileapp.file.domain.FileSource;
import com.example.fileapp.file.domain.StoredFile;
import com.example.fileapp.file.dto.FileDtos.FileResponse;
import com.example.fileapp.file.repository.FileQueryRepository;
import com.example.fileapp.file.service.FileService;
import com.example.fileapp.file.service.FileStorageService;
import com.example.fileapp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final FileQueryRepository fileQueryRepository;
    private final FileStorageService storage;

    @GetMapping
    public Page<FileResponse> list(
            @AuthenticationPrincipal AppUserPrincipal user,
            @RequestParam(required = false) String extension,
            @RequestParam(required = false) FileSource source,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return fileQueryRepository.search(user.getId(), extension, source, keyword, pageable)
                .map(FileResponse::of);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileResponse upload(@AuthenticationPrincipal AppUserPrincipal user,
                               @RequestPart("file") MultipartFile file) {
        return FileResponse.of(fileService.uploadManual(user, file));
    }

    @GetMapping("/{id}")
    public FileResponse metadata(@AuthenticationPrincipal AppUserPrincipal user,
                                 @PathVariable Long id) {
        return FileResponse.of(fileService.getOwned(user, id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal AppUserPrincipal user,
                                                        @PathVariable Long id) throws IOException {
        StoredFile f = fileService.getOwned(user, id);
        Path p = storage.resolve(f.getRelativePath());
        InputStream in = Files.newInputStream(p);
        String filename = URLEncoder.encode(f.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .contentLength(f.getSizeBytes())
                .body(new InputStreamResource(in));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AppUserPrincipal user,
                                       @PathVariable Long id) {
        fileService.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
