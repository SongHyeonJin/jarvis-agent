package com.jarvis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Claude Code CLI 직접 실행 컴포넌트.
 *
 * ProcessBuilder로 claude.exe를 직접 호출 — 팝업 터미널 없음.
 *   stdin  : 프롬프트 주입 (virtual thread로 비동기 write 후 close)
 *   stdout : stream-json 라인 단위 실시간 파싱 → SSE Consumer 전달
 *   stderr : redirectErrorStream(true)로 stdout에 합산
 *
 * 취소: cancelled AtomicBoolean → proc.destroyForcibly()
 * 타임아웃: 와치독 virtual thread 5분 후 강제 종료
 */
@Component
@Slf4j
public class ClaudeCliExecutor {

    private static final ObjectMapper MAPPER  = new ObjectMapper();
    private static final long TIMEOUT_MS      = 300_000; // 5분

    @Value("${app.project-root:d:/jarvis-agent}")
    private String projectRoot;

    public record ExecutionResult(int exitCode, boolean timedOut, boolean cancelled) {
        public boolean success() { return exitCode == 0 && !timedOut && !cancelled; }
    }

    // ── 메인 실행 ─────────────────────────────────────────────

    public ExecutionResult execute(String prompt,
                                   Consumer<String> logChunk,
                                   Consumer<String> rawLogChunk,
                                   AtomicBoolean cancelled) {
        Process proc = null;
        try {
            String claudeExe = resolveClaudeExecutable();
            log.info("[ClaudeCliExecutor] claude={} dir={}", claudeExe, projectRoot);
            if (logChunk != null) logChunk.accept("▶ Claude Code 실행 중... [" + new File(claudeExe).getName() + "]");

            ProcessBuilder pb = buildProcess(claudeExe);
            proc = pb.start();

            // stdin: virtual thread로 프롬프트 주입 (동기 write 시 출력 버퍼 가득 차면 데드락 발생)
            final byte[] promptBytes = prompt.getBytes(StandardCharsets.UTF_8);
            final Process fp = proc;
            Thread.ofVirtual().start(() -> {
                try (OutputStream out = fp.getOutputStream()) {
                    out.write(promptBytes);
                } catch (IOException e) {
                    log.warn("[ClaudeCliExecutor] stdin 쓰기 오류: {}", e.getMessage());
                }
            });

            // 타임아웃 와치독
            final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            Thread watchdog = Thread.ofVirtual().start(() -> {
                try {
                    while (System.currentTimeMillis() < deadline) {
                        if (!fp.isAlive()) return;
                        Thread.sleep(2000);
                    }
                    log.warn("[ClaudeCliExecutor] 타임아웃 → 강제 종료");
                    fp.destroyForcibly();
                } catch (InterruptedException ignored) {}
            });

            // stdout 라인 단위 스트리밍
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelled.get()) {
                        proc.destroyForcibly();
                        watchdog.interrupt();
                        if (logChunk != null) logChunk.accept("⚠ 작업이 취소되었습니다.");
                        return new ExecutionResult(-1, false, true);
                    }
                    String raw = stripAnsi(line);
                    if (rawLogChunk != null) rawLogChunk.accept(raw);
                    String parsed = parseStreamJson(raw);
                    if (parsed != null && !parsed.isBlank() && logChunk != null) {
                        logChunk.accept(parsed);
                    }
                }
            }

            watchdog.interrupt();
            boolean exited = proc.waitFor(10, TimeUnit.SECONDS);
            if (!exited) {
                proc.destroyForcibly();
                boolean timedOut = System.currentTimeMillis() >= deadline;
                if (logChunk != null) logChunk.accept(timedOut ? "⚠ 타임아웃 (5분)" : "⚠ 프로세스 종료 대기 초과");
                return new ExecutionResult(-1, timedOut, false);
            }

            int exit = proc.exitValue();
            log.info("[ClaudeCliExecutor] 완료 exitCode={}", exit);
            if (logChunk != null)
                logChunk.accept(exit == 0 ? "✓ Claude Code 완료" : "✗ Claude Code 오류 (exit=" + exit + ")");
            return new ExecutionResult(exit, false, false);

        } catch (Exception e) {
            log.error("[ClaudeCliExecutor] 오류: {}", e.getMessage(), e);
            if (logChunk != null) logChunk.accept("ERROR: " + e.getMessage());
            return new ExecutionResult(-1, false, false);
        } finally {
            if (proc != null && proc.isAlive()) proc.destroyForcibly();
        }
    }

    // ── ProcessBuilder 구성 ───────────────────────────────────

    private ProcessBuilder buildProcess(String claudeExe) {
        List<String> cmd = new ArrayList<>();

        // .cmd 파일은 cmd /c를 통해 실행해야 함
        if (claudeExe.toLowerCase().endsWith(".cmd") || claudeExe.toLowerCase().endsWith(".bat")) {
            cmd.addAll(List.of("cmd", "/c", claudeExe));
        } else {
            cmd.add(claudeExe);
        }
        cmd.addAll(List.of(
            "--dangerously-skip-permissions",
            "--output-format", "stream-json",
            "--verbose",
            "-p"
        ));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(projectRoot));
        pb.redirectErrorStream(true);

        // PATH에 .local\bin 추가 (Gradle 서버 프로세스 PATH에 없을 수 있음)
        String home    = System.getProperty("user.home", "C:\\Users\\Song");
        String appData = nvl(System.getenv("APPDATA"), home + "\\AppData\\Roaming");
        String existing = nvl(pb.environment().get("PATH"), "");
        pb.environment().put("PATH",
            home + "\\.local\\bin;" +
            appData + "\\npm;" +
            existing);

        return pb;
    }

    // ── Claude 실행 파일 탐색 ─────────────────────────────────

    String resolveClaudeExecutable() {
        String home     = System.getProperty("user.home", "C:\\Users\\Song");
        String appData  = nvl(System.getenv("APPDATA"),     home + "\\AppData\\Roaming");
        String localApp = nvl(System.getenv("LOCALAPPDATA"), home + "\\AppData\\Local");

        String[] candidates = {
            home     + "\\.local\\bin\\claude.exe",
            appData  + "\\npm\\claude.cmd",
            localApp + "\\Programs\\claude\\claude.exe",
            "C:\\Program Files\\Claude\\claude.exe",
        };
        for (String c : candidates) {
            if (new File(c).exists()) {
                log.info("[ClaudeCliExecutor] claude 발견: {}", c);
                return c;
            }
        }
        // IDE 확장 번들 동적 탐색
        for (String base : new String[]{
                home + "\\.antigravity-ide\\extensions",
                home + "\\.vscode\\extensions"}) {
            String found = scanForExe(base, "claude.exe", "native-binary");
            if (found != null) {
                log.info("[ClaudeCliExecutor] IDE 번들 발견: {}", found);
                return found;
            }
        }
        log.warn("[ClaudeCliExecutor] claude를 찾지 못함 → fallback 'claude'");
        return "claude";
    }

    private String scanForExe(String base, String target, String mustContain) {
        try {
            Path root = Paths.get(base);
            if (!Files.exists(root)) return null;
            try (var s = Files.walk(root, 6)) {
                return s.filter(p -> p.getFileName().toString().equals(target))
                        .filter(p -> mustContain == null || p.toString().contains(mustContain))
                        .map(Path::toString)
                        .findFirst().orElse(null);
            }
        } catch (Exception e) { return null; }
    }

    // ── stream-json 파싱 ──────────────────────────────────────

    private String parseStreamJson(String line) {
        if (line == null || line.isBlank()) return null;
        if (!line.startsWith("{")) return line;
        try {
            JsonNode node = MAPPER.readTree(line);
            return switch (node.path("type").asText("")) {
                case "assistant" -> {
                    JsonNode content = node.path("message").path("content");
                    if (!content.isArray()) yield null;
                    var sb = new StringBuilder();
                    for (JsonNode item : content) {
                        String t = item.path("type").asText("");
                        if ("text".equals(t)) {
                            String txt = item.path("text").asText("").trim();
                            if (!txt.isEmpty()) { if (sb.length() > 0) sb.append(' '); sb.append(txt); }
                        } else if ("tool_use".equals(t)) {
                            String name = item.path("name").asText("");
                            String fp   = item.path("input").path("file_path").asText(
                                          item.path("input").path("path").asText(""));
                            if (sb.length() > 0) sb.append('\n');
                            sb.append(name).append(fp.isEmpty() ? "" : " → " + fp);
                        }
                    }
                    yield sb.isEmpty() ? null : sb.toString().trim();
                }
                case "tool_result" -> {
                    JsonNode c = node.path("content");
                    String txt = c.isArray() && !c.isEmpty()
                        ? c.get(0).path("text").asText("").trim()
                        : c.asText("").trim();
                    yield txt.isEmpty() ? null : "✓ " + txt;
                }
                case "result" -> {
                    String r = node.path("result").asText("").trim();
                    yield r.isEmpty() ? null : r;
                }
                default -> null;
            };
        } catch (Exception e) { return line; }
    }

    // ── 유틸 ──────────────────────────────────────────────────

    private static String stripAnsi(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\u001B\\[[;\\d]*[A-Za-z]", "");
        s = s.replaceAll("\\u001B][^\\u0007]*\\u0007?", "");
        s = s.replaceAll("\\u001B.", "");
        s = s.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");
        return s;
    }

    private static String nvl(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
