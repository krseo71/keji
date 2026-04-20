package com.example.fileapp.generation.service;

import com.example.fileapp.common.ApiException;
import com.example.fileapp.generation.domain.GenerationJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class AsyncJobRunner {

    private final GenerationService service;
    private final ClaudeRunner claudeRunner;

    @Value("${app.claude.job-dir}")
    private String jobDirRoot;

    public AsyncJobRunner(@Lazy GenerationService service, ClaudeRunner claudeRunner) {
        this.service = service;
        this.claudeRunner = claudeRunner;
    }

    @Async("claudeExecutor")
    public void enqueue(Long jobId) {
        Path workDir = Paths.get(jobDirRoot, String.valueOf(jobId));
        try {
            GenerationJob job = service.markRunning(jobId);
            service.prepareWorkDir(workDir, job);

            Set<Path> inputs = new HashSet<>();
            if (job.getInputFileId() != null) {
                try (var stream = Files.list(workDir)) {
                    stream.filter(Files::isRegularFile).forEach(inputs::add);
                }
            }

            String prompt = buildPrompt(job.getPrompt());
            ClaudeRunner.Result result = claudeRunner.run(workDir, prompt);

            if (result.isTimedOut()) {
                service.markFailed(jobId, "claude timed out", result.getStdout());
                return;
            }
            if (result.getExitCode() != 0) {
                service.markFailed(jobId,
                        "claude exited with code " + result.getExitCode() + ": " + head(result.getStderr(), 500),
                        result.getStdout());
                return;
            }

            List<Path> outputs = collectOutputs(workDir, inputs);
            int saved = 0;
            for (Path p : outputs) {
                try {
                    service.saveGeneratedFile(jobId, job.getOwnerId(), p.getFileName().toString(), p, head(job.getPrompt(), 200));
                    saved++;
                } catch (ApiException ex) {
                    log.info("skipped output {}: {}", p.getFileName(), ex.getMessage());
                }
            }
            service.markSucceeded(jobId, result.getStdout(), saved);
        } catch (Exception e) {
            log.error("job {} failed", jobId, e);
            service.markFailed(jobId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), null);
        } finally {
            cleanup(workDir);
        }
    }

    private String buildPrompt(String userPrompt) {
        return userPrompt + """


                [작업 지시]
                - 현재 작업 디렉토리에 결과 파일만 저장할 것.
                - 허용 확장자: .xlsx .pptx .docx .hwp .hwpx .pdf .csv .md .txt .json
                - 부가 설명은 표준 출력으로만 출력하고, 의미 없는 파일을 만들지 말 것.
                - 입력 파일은 덮어쓰지 말 것.
                """;
    }

    private List<Path> collectOutputs(Path work, Set<Path> exclude) throws IOException {
        List<Path> out = new ArrayList<>();
        Files.walkFileTree(work, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && !exclude.contains(file)) out.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return out;
    }

    private void cleanup(Path work) {
        try {
            if (!Files.exists(work)) return;
            Files.walkFileTree(work, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                    Files.deleteIfExists(file); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir); return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("cleanup failed: {}", work, e);
        }
    }

    private String head(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
