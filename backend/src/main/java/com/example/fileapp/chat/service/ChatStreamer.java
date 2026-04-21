package com.example.fileapp.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatStreamer {

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.claude.binary}")
    private String binary;

    @Value("${app.claude.timeout-seconds}")
    private long timeoutSeconds;

    @Value("${app.claude.api-key:}")
    private String apiKey;

    @Value("${app.claude.model:}")
    private String model;

    @Value("${app.claude.allowed-tools:}")
    private String allowedTools;

    @Getter
    public static class Result {
        public final String assistantText;
        public final String sessionId;
        public final List<Map<String, Object>> toolEvents;
        public final boolean timedOut;
        public final int exitCode;
        public final String error;

        public Result(String assistantText, String sessionId, List<Map<String, Object>> toolEvents,
                      boolean timedOut, int exitCode, String error) {
            this.assistantText = assistantText;
            this.sessionId = sessionId;
            this.toolEvents = toolEvents;
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.error = error;
        }
    }

    /**
     * Run claude with streaming output. Calls onDelta for each text chunk,
     * onToolEvent for tool_use / tool_result. Returns aggregated final result.
     *
     * @param workDir             working directory (conversation-specific, persistent)
     * @param sessionId           existing claude session UUID to resume; null for new session
     * @param newSessionId        UUID to use for new session if sessionId is null
     * @param prompt              user message
     * @param onDelta             callback for incremental assistant text (may be called many times)
     * @param onToolEvent         callback for tool_use / tool_result events (JSON object)
     */
    public Result run(Path workDir, String sessionId, String newSessionId, String prompt,
                      Consumer<String> onDelta, Consumer<Map<String, Object>> onToolEvent)
            throws IOException, InterruptedException {

        List<String> cmd = new ArrayList<>();
        cmd.add(binary);
        cmd.add("-p"); cmd.add(prompt);
        cmd.add("--permission-mode"); cmd.add("acceptEdits");
        cmd.add("--output-format"); cmd.add("stream-json");
        cmd.add("--include-partial-messages");
        cmd.add("--verbose");
        if (sessionId != null && !sessionId.isBlank()) {
            cmd.add("-r"); cmd.add(sessionId);
        } else if (newSessionId != null && !newSessionId.isBlank()) {
            cmd.add("--session-id"); cmd.add(newSessionId);
        }
        if (model != null && !model.isBlank()) {
            cmd.add("--model"); cmd.add(model);
        }
        if (allowedTools != null && !allowedTools.isBlank()) {
            cmd.add("--allowed-tools"); cmd.add(allowedTools);
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        if (apiKey != null && !apiKey.isBlank()) {
            pb.environment().put("ANTHROPIC_API_KEY", apiKey);
        }
        pb.redirectErrorStream(false);

        log.info("chat claude: cwd={}, resume={}, newSession={}, tools={}",
                workDir, sessionId, newSessionId, allowedTools);
        long startMs = System.currentTimeMillis();
        Process proc = pb.start();

        StringBuilder assistantText = new StringBuilder();
        List<Map<String, Object>> toolEvents = new ArrayList<>();
        String[] returnedSessionId = {null};
        String[] errorText = {null};

        Thread outThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processLine(line, assistantText, toolEvents, returnedSessionId, errorText,
                            onDelta, onToolEvent);
                }
            } catch (IOException e) {
                log.warn("stdout reader error: {}", e.getMessage());
            }
        }, "chat-stdout");
        outThread.setDaemon(true);
        outThread.start();

        StringBuilder err = new StringBuilder();
        Thread errThread = new Thread(() -> drain(proc.getErrorStream(), err), "chat-stderr");
        errThread.setDaemon(true);
        errThread.start();

        boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroy();
            if (!proc.waitFor(10, TimeUnit.SECONDS)) proc.destroyForcibly();
            outThread.join(2000);
            errThread.join(2000);
            log.warn("chat claude timed out after {}ms", System.currentTimeMillis() - startMs);
            return new Result(assistantText.toString(), returnedSessionId[0], toolEvents, true, -1,
                    errorText[0] != null ? errorText[0] : "timed out");
        }
        outThread.join();
        errThread.join();
        long elapsed = System.currentTimeMillis() - startMs;
        int exit = proc.exitValue();
        log.info("chat claude done in {}ms exit={} textLen={} events={}",
                elapsed, exit, assistantText.length(), toolEvents.size());

        String errorMsg = errorText[0];
        if (exit != 0 && errorMsg == null) {
            errorMsg = "claude exited " + exit + ": " + head(err.toString(), 400);
        }
        return new Result(assistantText.toString(), returnedSessionId[0], toolEvents, false, exit, errorMsg);
    }

    private void processLine(String line,
                             StringBuilder assistantText,
                             List<Map<String, Object>> toolEvents,
                             String[] returnedSessionId,
                             String[] errorText,
                             Consumer<String> onDelta,
                             Consumer<Map<String, Object>> onToolEvent) {
        if (line.isBlank()) return;
        JsonNode node;
        try {
            node = mapper.readTree(line);
        } catch (IOException e) {
            log.debug("non-JSON line: {}", head(line, 120));
            return;
        }
        String type = node.path("type").asText("");

        if ("system".equals(type) && "init".equals(node.path("subtype").asText(""))) {
            String sid = node.path("session_id").asText(null);
            if (sid != null && !sid.isBlank()) returnedSessionId[0] = sid;
            return;
        }

        if ("stream_event".equals(type)) {
            JsonNode event = node.path("event");
            String eventType = event.path("type").asText("");
            if ("content_block_delta".equals(eventType)) {
                JsonNode delta = event.path("delta");
                if ("text_delta".equals(delta.path("type").asText(""))) {
                    String text = delta.path("text").asText("");
                    if (!text.isEmpty()) {
                        assistantText.append(text);
                        try { onDelta.accept(text); } catch (Exception e) { log.warn("onDelta error", e); }
                    }
                } else if ("input_json_delta".equals(delta.path("type").asText(""))) {
                    // incremental tool input — not forwarded directly
                }
            } else if ("content_block_start".equals(eventType)) {
                JsonNode block = event.path("content_block");
                if ("tool_use".equals(block.path("type").asText(""))) {
                    ObjectNode evt = mapper.createObjectNode();
                    evt.put("kind", "tool_use_start");
                    evt.put("tool", block.path("name").asText(""));
                    evt.put("tool_use_id", block.path("id").asText(""));
                    if (block.has("input")) evt.set("input", block.get("input"));
                    dispatchToolEvent(evt, toolEvents, onToolEvent);
                }
            }
            return;
        }

        if ("user".equals(type)) {
            // contains tool_result blocks
            JsonNode message = node.path("message");
            JsonNode content = message.path("content");
            if (content.isArray()) {
                for (JsonNode block : content) {
                    if ("tool_result".equals(block.path("type").asText(""))) {
                        ObjectNode evt = mapper.createObjectNode();
                        evt.put("kind", "tool_result");
                        evt.put("tool_use_id", block.path("tool_use_id").asText(""));
                        String summary = summarizeToolResult(block.path("content"));
                        evt.put("summary", summary);
                        dispatchToolEvent(evt, toolEvents, onToolEvent);
                    }
                }
            }
            return;
        }

        if ("result".equals(type)) {
            String sid = node.path("session_id").asText(null);
            if (sid != null && !sid.isBlank()) returnedSessionId[0] = sid;
            if (node.path("is_error").asBoolean(false)) {
                errorText[0] = node.path("result").asText("claude reported error");
            }
            // final result text is already accumulated via deltas; ignore
        }
    }

    private String summarizeToolResult(JsonNode content) {
        if (content.isTextual()) return head(content.asText(""), 300);
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode b : content) {
                if ("text".equals(b.path("type").asText(""))) {
                    sb.append(b.path("text").asText("")).append("\n");
                }
            }
            return head(sb.toString(), 300);
        }
        return "";
    }

    private void dispatchToolEvent(ObjectNode evt, List<Map<String, Object>> store,
                                   Consumer<Map<String, Object>> onToolEvent) {
        Map<String, Object> map = mapper.convertValue(evt, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        store.add(map);
        try { onToolEvent.accept(map); } catch (Exception e) { log.warn("onToolEvent error", e); }
    }

    private void drain(InputStream is, StringBuilder sink) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sink.append(line).append('\n');
        } catch (IOException e) {
            log.debug("stderr drain ended: {}", e.getMessage());
        }
    }

    private String head(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }
}
