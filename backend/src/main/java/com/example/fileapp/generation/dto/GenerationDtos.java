package com.example.fileapp.generation.dto;

import com.example.fileapp.file.dto.FileDtos.FileResponse;
import com.example.fileapp.generation.domain.GenerationJob;
import com.example.fileapp.generation.domain.JobStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class GenerationDtos {

    public record GenerateRequest(
            @NotBlank @Size(max = 8000) String prompt,
            Long inputFileId
    ) {}

    public record JobResponse(
            Long id,
            JobStatus status,
            String prompt,
            Long inputFileId,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            List<FileResponse> outputs
    ) {
        public static JobResponse of(GenerationJob j, List<FileResponse> outputs) {
            return new JobResponse(
                    j.getId(), j.getStatus(), j.getPrompt(), j.getInputFileId(),
                    j.getErrorMessage(), j.getCreatedAt(), j.getStartedAt(), j.getFinishedAt(),
                    outputs
            );
        }
    }
}
