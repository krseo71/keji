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
        ProcessBuilder pb = new ProcessBuilder(
                binary,
                "-p", finalPrompt,
                "--permission-mode", "acceptEdits",
                "--output-format", "json"
        );
        pb.directory(workDir.toFile());
        Map<String, String> env = pb.environment();
        if (apiKey != null && !apiKey.isBlank()) {
            env.put("ANTHROPIC_API_KEY", apiKey);
        }
        pb.redirectErrorStream(false);

        log.info("Running claude in {} (timeout {}s)", workDir, timeoutSeconds);
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
            return new Result(-1, out.toString(), err.toString(), true);
        }
        outReader.join();
        errReader.join();
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
