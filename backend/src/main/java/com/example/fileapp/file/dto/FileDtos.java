package com.example.fileapp.file.dto;

import com.example.fileapp.file.domain.FileSource;
import com.example.fileapp.file.domain.StoredFile;

import java.time.LocalDateTime;

public class FileDtos {

    public record FileResponse(
            Long id,
            String originalName,
            String contentType,
            Long sizeBytes,
            String extension,
            FileSource source,
            Long generationJobId,
            String description,
            LocalDateTime createdAt
    ) {
        public static FileResponse of(StoredFile f) {
            return new FileResponse(
                    f.getId(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(),
                    f.getExtension(), f.getSource(), f.getGenerationJobId(),
                    f.getDescription(), f.getCreatedAt()
            );
        }
    }
}
