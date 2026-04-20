package com.example.fileapp.generation.service;

import com.example.fileapp.common.ApiException;
import com.example.fileapp.file.domain.StoredFile;
import com.example.fileapp.file.repository.StoredFileRepository;
import com.example.fileapp.file.service.FileService;
import com.example.fileapp.file.service.FileStorageService;
import com.example.fileapp.generation.domain.GenerationJob;
import com.example.fileapp.generation.domain.JobStatus;
import com.example.fileapp.generation.dto.GenerationDtos.GenerateRequest;
import com.example.fileapp.generation.repository.GenerationJobRepository;
import com.example.fileapp.security.AppUserPrincipal;
import com.example.fileapp.user.domain.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationService {

    private final GenerationJobRepository jobRepository;
    private final StoredFileRepository fileRepository;
    private final FileService fileService;
    private final FileStorageService storage;
    private final AsyncJobRunner runner;

    @Transactional
    public GenerationJob submit(AppUserPrincipal user, GenerateRequest req) {
        if (req.inputFileId() != null) {
            StoredFile f = fileRepository.findById(req.inputFileId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "input file not found"));
            if (!user.getId().equals(f.getOwnerId()) && user.getRole() != Role.ADMIN) {
                throw new ApiException(HttpStatus.FORBIDDEN, "cannot use this input file");
            }
        }
        GenerationJob job = GenerationJob.builder()
                .ownerId(user.getId())
                .prompt(req.prompt())
                .inputFileId(req.inputFileId())
                .status(JobStatus.PENDING)
                .build();
        jobRepository.save(job);
        runner.enqueue(job.getId());
        return job;
    }

    @Transactional
    public GenerationJob markRunning(Long jobId) {
        GenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("job gone: " + jobId));
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        return job;
    }

    @Transactional
    public void markSucceeded(Long jobId, String stdoutLog, int savedCount) {
        jobRepository.findById(jobId).ifPresent(j -> {
            j.setStatus(JobStatus.SUCCEEDED);
            j.setStdoutLog(truncate(stdoutLog, 20000));
            if (savedCount == 0) j.setErrorMessage("no output files were produced");
            j.setFinishedAt(LocalDateTime.now());
        });
    }

    @Transactional
    public void markFailed(Long jobId, String message, String stdoutLog) {
        jobRepository.findById(jobId).ifPresent(j -> {
            j.setStatus(JobStatus.FAILED);
            j.setErrorMessage(truncate(message, 2000));
            if (stdoutLog != null) j.setStdoutLog(truncate(stdoutLog, 20000));
            j.setFinishedAt(LocalDateTime.now());
        });
    }

    @Transactional(readOnly = true)
    public GenerationJob loadJob(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("job not found: " + jobId));
    }

    @Transactional
    public Path prepareWorkDir(Path jobRoot, GenerationJob job) throws IOException {
        Files.createDirectories(jobRoot);
        if (job.getInputFileId() != null) {
            StoredFile src = fileRepository.findById(job.getInputFileId()).orElseThrow();
            Path copy = jobRoot.resolve(src.getOriginalName());
            Files.copy(storage.resolve(src.getRelativePath()), copy, StandardCopyOption.REPLACE_EXISTING);
        }
        return jobRoot;
    }

    @Transactional
    public StoredFile saveGeneratedFile(Long jobId, Long ownerId, String originalName, Path path, String description) {
        return fileService.saveFromPath(ownerId, originalName, path, jobId, description);
    }

    @Transactional(readOnly = true)
    public GenerationJob getOwned(AppUserPrincipal user, Long id) {
        GenerationJob j = jobRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job not found"));
        if (!user.getId().equals(j.getOwnerId()) && user.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden");
        }
        return j;
    }

    @Transactional(readOnly = true)
    public List<StoredFile> outputsOf(Long jobId) {
        return fileRepository.findAllByGenerationJobId(jobId);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "...(truncated)" : s;
    }
}
