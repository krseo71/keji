package com.example.fileapp.generation.controller;

import com.example.fileapp.file.domain.StoredFile;
import com.example.fileapp.file.dto.FileDtos.FileResponse;
import com.example.fileapp.generation.domain.GenerationJob;
import com.example.fileapp.generation.dto.GenerationDtos.GenerateRequest;
import com.example.fileapp.generation.dto.GenerationDtos.JobResponse;
import com.example.fileapp.generation.service.AsyncJobRunner;
import com.example.fileapp.generation.service.GenerationService;
import com.example.fileapp.security.AppUserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/generate")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService service;
    private final AsyncJobRunner runner;

    @PostMapping
    public ResponseEntity<JobResponse> submit(@AuthenticationPrincipal AppUserPrincipal user,
                                              @Valid @RequestBody GenerateRequest req) {
        GenerationJob job = service.submit(user, req);   // tx commits on return
        runner.enqueue(job.getId());                     // async fired after commit
        return ResponseEntity.accepted().body(JobResponse.of(job, List.of()));
    }

    @GetMapping("/{id}")
    public JobResponse get(@AuthenticationPrincipal AppUserPrincipal user,
                           @PathVariable Long id) {
        GenerationJob job = service.getOwned(user, id);
        List<StoredFile> outputs = service.outputsOf(id);
        return JobResponse.of(job, outputs.stream().map(FileResponse::of).toList());
    }
}
