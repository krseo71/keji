package com.example.fileapp.generation.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeRunner {

    @Value("${app.claude.binary}")
    private String binary;

    @Value("${app.claude.timeout-seconds}")
    private long timeoutSeconds;

    @Value("${app.claude.api-key:}")
    private String apiKey;

    @Value("${app.claude.model:}")
    private String model;

    @Value("${app.claude.max-turns:0}")
    private int maxTurns;

    @Value("${app.claude.allowed-tools:}")
    private String allowedTools;

    @Getter
    public static class Result {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean timedOut;

        public Result(int exitCode, String stdout, String stderr, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
        }
    }

    public Result run(Path workDir, String finalPrompt) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(binary);
        cmd.add("-p"); cmd.add(finalPrompt);
        cmd.add("--permission-mode"); cmd.add("acceptEdits");
        cmd.add("--output-format"); cmd.add("json");
        if (model != null && !model.isBlank()) {
            cmd.add("--model"); cmd.add(model);
        }
        if (maxTurns > 0) {
            cmd.add("--max-turns"); cmd.add(Integer.toString(maxTurns));
        }
        if (allowedTools != null && !allowedTools.isBlank()) {
            cmd.add("--allowed-tools"); cmd.add(allowedTools);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        Map<String, String> env = pb.environment();
        if (apiKey != null && !apiKey.isBlank()) {
            env.put("ANTHROPIC_API_KEY", apiKey);
        }
        pb.redirectErrorStream(false);

        log.info("Running claude in {} (timeout {}s, model='{}', maxTurns={}, allowedTools='{}')",
                workDir, timeoutSeconds, model, maxTurns, allowedTools);
        long startMs = System.currentTimeMillis();
        Process proc = pb.start();

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        Thread outReader = drain(proc.getInputStream(), out);
        Thread errReader = drain(proc.getErrorStream(), err);

        boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroy();
            if (!proc.waitFor(10, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
            }
            outReader.join(2000);
            errReader.join(2000);
            log.warn("claude timed out after {}ms in {}", System.currentTimeMillis() - startMs, workDir);
            return new Result(-1, out.toString(), err.toString(), true);
        }
        outReader.join();
        errReader.join();
        long elapsed = System.currentTimeMillis() - startMs;
        log.info("claude finished in {}ms exit={}", elapsed, proc.exitValue());
        return new Result(proc.exitValue(), out.toString(), err.toString(), false);
    }

    private Thread drain(java.io.InputStream is, StringBuilder sink) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sink.append(line).append('\n');
                }
            } catch (IOException e) {
                log.debug("stream drain ended: {}", e.getMessage());
            }
        }, "claude-drain");
        t.setDaemon(true);
        t.start();
        return t;
    }
}
