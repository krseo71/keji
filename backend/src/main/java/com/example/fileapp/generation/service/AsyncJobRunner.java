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
            String inputFileName = null;
            if (job.getInputFileId() != null) {
                try (var stream = Files.list(workDir)) {
                    stream.filter(Files::isRegularFile).forEach(inputs::add);
                }
                if (!inputs.isEmpty()) {
                    inputFileName = inputs.iterator().next().getFileName().toString();
                }
            }

            String prompt = buildPrompt(job.getPrompt(), inputFileName);
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

    private String buildPrompt(String userPrompt, String inputFileName) {
        StringBuilder sb = new StringBuilder();
        if (inputFileName != null) {
            sb.append("입력 파일: \"").append(inputFileName).append("\"\n");
            sb.append("위 파일을 참조해 아래 요청을 수행해줘.\n\n");
        }
        sb.append("[요청]\n");
        sb.append(userPrompt);
        sb.append("\n\n[규칙]\n");
        sb.append("- 현재 작업 디렉토리에만 결과 파일을 저장할 것.\n");
        sb.append("- 허용 확장자: .xlsx .pptx .docx .hwp .hwpx .pdf .csv .md .txt .json\n");
        sb.append("- 입력 파일은 덮어쓰지 말 것.\n");
        sb.append("- 웹 검색/조회(WebFetch, WebSearch) 도구는 사용하지 말 것.\n");
        sb.append("- 불필요한 탐색 없이 곧바로 결과 파일을 만들 것.\n");
        sb.append("- 완료 후 한 줄 요약만 출력할 것.\n");
        return sb.toString();
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
