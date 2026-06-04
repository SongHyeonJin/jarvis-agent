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
 * 실행 흐름:
 *   DevJobService → execute(prompt, workingDir, ...) → buildProcess(claudeExe, prompt)
 *   → ProcessBuilder(cmd /c claude.cmd ... -p "prompt")
 *   → stdout stream-json 실시간 파싱 → SSE Consumer 전달
 *
 * 핵심:
 *   - 팝업 터미널 절대 사용 안 함
 *   - 프롬프트를 -p 인자로 직접 전달 (stdin 방식 제거)
 *   - workingDir 파라미터로 JARVIS 프로젝트 or 새 워크스페이스 선택 가능
 *   - D:/jarvis-workspaces 하위 경로에서는 trust prompt 자동 응답 (stdin "1\n")
 */
@Component
@Slf4j
public class ClaudeCliExecutor {

    private static final ObjectMapper MAPPER     = new ObjectMapper();
    private static final long         TIMEOUT_MS = 300_000; // 5분

    /** 자동 trust 처리를 허용할 베이스 경로 (이 경로 하위 폴더만 허용) */
    private static final String TRUST_BASE_PATH = "d:/jarvis-workspaces";

    @Value("${app.project-root:d:/jarvis-agent}")
    private String defaultProjectRoot;

    public record ExecutionResult(int exitCode, boolean timedOut, boolean cancelled) {
        public boolean success() { return exitCode == 0 && !timedOut && !cancelled; }
    }

    // ─────────────────────────────────────────────────────────
    //  공개 API — 기존 호환 (defaultProjectRoot 사용)
    // ─────────────────────────────────────────────────────────

    public ExecutionResult execute(String prompt,
                                   Consumer<String> logChunk,
                                   Consumer<String> rawLogChunk,
                                   AtomicBoolean cancelled) {
        return executeInternal(prompt, defaultProjectRoot, logChunk, rawLogChunk, cancelled);
    }

    // ─────────────────────────────────────────────────────────
    //  공개 API — workingDir 명시 버전
    // ─────────────────────────────────────────────────────────

    public ExecutionResult execute(String prompt,
                                   String workingDir,
                                   Consumer<String> logChunk,
                                   Consumer<String> rawLogChunk,
                                   AtomicBoolean cancelled) {
        return executeInternal(prompt, workingDir, logChunk, rawLogChunk, cancelled);
    }

    // ─────────────────────────────────────────────────────────
    //  내부 실행
    // ─────────────────────────────────────────────────────────

    private ExecutionResult executeInternal(String prompt,
                                             String workingDir,
                                             Consumer<String> logChunk,
                                             Consumer<String> rawLogChunk,
                                             AtomicBoolean cancelled) {
        Process proc = null;
        try {
            String claudeExe = resolveClaudeExecutable();
            boolean trustable = isTrustableWorkspace(workingDir);

            // ── 1. 실행 전 진단 ──────────────────────────────
            runDiagnostics(claudeExe, workingDir, trustable, logChunk);

            // ── 2. 프로세스 빌드 (-p 인자로 직접 전달) ───────
            List<String> cmd = buildCommand(claudeExe, prompt);
            log.info("[ClaudeCliExecutor] command={}", cmd);
            log.info("[ClaudeCliExecutor] cwd={} trustable={}", workingDir, trustable);
            if (logChunk != null) {
                String cmdBase = String.join(" ", cmd.subList(0, Math.min(cmd.size() - 2, cmd.size())));
                String preview = prompt.replaceAll("[\\r\\n]+", " ").trim();
                if (preview.length() > 100) preview = preview.substring(0, 100) + "...";
                logChunk.accept("▶ 실행 명령: " + cmdBase + " -p <prompt length=" + prompt.length() + ">");
                logChunk.accept("▶ prompt preview: " + preview);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(workingDir));
            pb.redirectErrorStream(false); // stderr 분리 캡처

            // PATH 보강
            String home    = System.getProperty("user.home", "C:\\Users\\Song");
            String appData = nvl(System.getenv("APPDATA"), home + "\\AppData\\Roaming");
            String existing = nvl(pb.environment().get("PATH"), "");
            pb.environment().put("PATH",
                appData + "\\npm;" +
                home + "\\.local\\bin;" +
                existing);
            pb.environment().put("PYTHONUTF8", "1");
            pb.environment().put("NO_COLOR", "1");

            // D:/jarvis-workspaces 하위: stdin 파이프 (trust 자동 응답)
            // 그 외: stdin = NUL (입력 차단)
            if (trustable) {
                // PIPE 모드 — 프로세스 시작 직후 "1\n" 전송
                // (별도 redirect 설정 없으면 기본 PIPE)
            } else {
                pb.redirectInput(ProcessBuilder.Redirect.from(new File("NUL")));
            }

            proc = pb.start();
            if (logChunk != null) logChunk.accept("▶ Claude Code 프로세스 시작됨");

            // trust prompt 자동 응답 (trustable 경로에서만)
            if (trustable) {
                try (OutputStream stdin = proc.getOutputStream()) {
                    // "1\n" = "Yes, I trust this folder" 자동 선택
                    stdin.write("1\n".getBytes(StandardCharsets.UTF_8));
                    stdin.flush();
                    if (logChunk != null) logChunk.accept("▶ Claude trust 자동 응답 완료 ✓");
                } catch (Exception e) {
                    log.warn("[ClaudeCliExecutor] stdin trust 응답 실패 (무시됨): {}", e.getMessage());
                }
            }

            // ── 3. 타임아웃 와치독 ───────────────────────────
            final long    deadline = System.currentTimeMillis() + TIMEOUT_MS;
            final Process fp       = proc;
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

            // ── 4a. stderr 비동기 수집 ───────────────────────
            var stderrBuf = new StringBuilder();
            final Process fpStderr = proc;
            Thread stderrThread = Thread.ofVirtual().start(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(fpStderr.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String clean = stripAnsi(line);
                        stderrBuf.append(clean).append('\n');
                        if (rawLogChunk != null) rawLogChunk.accept("[STDERR] " + clean);
                        // trust prompt 감지 → 이미 stdin에 "1\n" 보냈으므로 추가 처리 불필요
                        if (trustable && (clean.contains("Is this a project you created") || clean.contains("trust"))) {
                            log.info("[ClaudeCliExecutor] trust prompt 감지됨 (이미 응답됨)");
                        }
                    }
                } catch (IOException ignored) {}
            });

            // ── 4b. stdout 실시간 스트리밍 (stream-json 파싱) ─
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelled.get()) {
                        proc.destroyForcibly();
                        watchdog.interrupt();
                        stderrThread.interrupt();
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

            stderrThread.join(3000);
            watchdog.interrupt();

            // ── 4c. stderr 내용이 있으면 rawLogChunk에 전달 ──
            if (!stderrBuf.isEmpty() && rawLogChunk != null) {
                rawLogChunk.accept("[STDERR 전체]\n" + stderrBuf);
            }

            // ── 5. 프로세스 종료 대기 ────────────────────────
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

    // ─────────────────────────────────────────────────────────
    //  명령 목록 구성
    // ─────────────────────────────────────────────────────────

    List<String> buildCommand(String claudeExe, String prompt) {
        List<String> cmd = new ArrayList<>();
        if (claudeExe.toLowerCase().endsWith(".cmd") || claudeExe.toLowerCase().endsWith(".bat")) {
            cmd.add("cmd");
            cmd.add("/c");
            cmd.add(claudeExe);
        } else {
            cmd.add(claudeExe);
        }
        cmd.add("--dangerously-skip-permissions");
        cmd.add("--output-format");
        cmd.add("stream-json");
        cmd.add("--verbose");
        cmd.add("-p");
        cmd.add(prompt);
        return Collections.unmodifiableList(cmd);
    }

    // ─────────────────────────────────────────────────────────
    //  Trust 경로 판별
    // ─────────────────────────────────────────────────────────

    /**
     * 경로가 D:/jarvis-workspaces 하위인지 확인한다.
     * 이 경로에서만 stdin "1\n" 자동 trust 처리를 허용한다.
     */
    boolean isTrustableWorkspace(String workingDir) {
        if (workingDir == null || workingDir.isBlank()) return false;
        String normalized = workingDir.replace('\\', '/').toLowerCase();
        return normalized.startsWith(TRUST_BASE_PATH.replace('\\', '/').toLowerCase());
    }

    // ─────────────────────────────────────────────────────────
    //  실행 전 진단 로그
    // ─────────────────────────────────────────────────────────

    private void runDiagnostics(String claudeExe, String workingDir,
                                 boolean trustable, Consumer<String> logChunk) {
        send(logChunk, "━━━ Claude Code 진단 시작 ━━━");

        File rootDir = new File(workingDir);
        boolean rootExists = rootDir.exists() && rootDir.isDirectory();
        send(logChunk, "▶ 작업 디렉토리: " + workingDir + " → " + (rootExists ? "존재함 ✓" : "존재하지 않음 ✗"));
        log.info("[ClaudeCliExecutor] workingDir={} exists={}", workingDir, rootExists);

        boolean exeExists = new File(claudeExe).exists();
        send(logChunk, "▶ claude 경로: " + claudeExe + " → " + (exeExists ? "존재함 ✓" : "PATH 탐색 필요"));

        send(logChunk, "▶ Claude trust 처리 가능 여부: " + (trustable ? "예 (jarvis-workspaces 경로) ✓" : "아니오"));

        // claude --version
        try {
            List<String> vCmd = new ArrayList<>();
            if (claudeExe.toLowerCase().endsWith(".cmd") || claudeExe.toLowerCase().endsWith(".bat")) {
                vCmd.addAll(List.of("cmd", "/c", claudeExe));
            } else {
                vCmd.add(claudeExe);
            }
            vCmd.add("--version");
            ProcessBuilder vpb = new ProcessBuilder(vCmd);
            vpb.directory(rootDir.exists() ? rootDir : new File(defaultProjectRoot));
            vpb.redirectErrorStream(true);
            addPath(vpb);
            Process vp = vpb.start();
            String ver = new String(vp.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            vp.waitFor(5, TimeUnit.SECONDS);
            send(logChunk, "▶ claude --version: " + (ver.isBlank() ? "(응답 없음)" : ver));
        } catch (Exception e) {
            send(logChunk, "▶ claude --version 실패: " + e.getMessage());
        }

        send(logChunk, "━━━ 진단 완료, 작업 시작 ━━━");
    }

    private void addPath(ProcessBuilder pb) {
        String home    = System.getProperty("user.home", "C:\\Users\\Song");
        String appData = nvl(System.getenv("APPDATA"), home + "\\AppData\\Roaming");
        String existing = nvl(pb.environment().get("PATH"), "");
        pb.environment().put("PATH", home + "\\.local\\bin;" + appData + "\\npm;" + existing);
    }

    // ─────────────────────────────────────────────────────────
    //  Claude 실행 파일 탐색
    // ─────────────────────────────────────────────────────────

    String resolveClaudeExecutable() {
        String home     = System.getProperty("user.home", "C:\\Users\\Song");
        String appData  = nvl(System.getenv("APPDATA"),      home + "\\AppData\\Roaming");
        String localApp = nvl(System.getenv("LOCALAPPDATA"), home + "\\AppData\\Local");
        String[] candidates = {
            appData  + "\\npm\\claude.cmd",
            home     + "\\.local\\bin\\claude.exe",
            localApp + "\\Programs\\claude\\claude.exe",
            "C:\\Program Files\\Claude\\claude.exe",
        };
        for (String c : candidates) {
            if (new File(c).exists()) { log.info("[ClaudeCliExecutor] claude 발견: {}", c); return c; }
        }
        for (String base : new String[]{
                home + "\\.antigravity-ide\\extensions",
                home + "\\.vscode\\extensions"}) {
            String found = scanForExe(base, "claude.exe", "native-binary");
            if (found != null) { log.info("[ClaudeCliExecutor] IDE 번들 발견: {}", found); return found; }
        }
        try {
            ProcessBuilder wb = new ProcessBuilder("cmd", "/c", "where", "claude");
            wb.redirectErrorStream(true);
            Process wp = wb.start();
            if (wp.waitFor(3, TimeUnit.SECONDS) && wp.exitValue() == 0) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(wp.getInputStream()))) {
                    String path = br.readLine();
                    if (path != null && !path.isBlank()) { log.info("[ClaudeCliExecutor] where claude → {}", path.trim()); return path.trim(); }
                }
            }
        } catch (Exception e) { log.warn("[ClaudeCliExecutor] where claude 실패: {}", e.getMessage()); }
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
                        .map(Path::toString).findFirst().orElse(null);
            }
        } catch (Exception e) { return null; }
    }

    // ─────────────────────────────────────────────────────────
    //  stream-json 파싱 (tool_use 아이콘 포함)
    // ─────────────────────────────────────────────────────────

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
                                          item.path("input").path("path").asText(
                                          item.path("input").path("command").asText("")));
                            String icon = switch (name) {
                                case "Write"   -> "📝";
                                case "Edit"    -> "✏️";
                                case "Read"    -> "👁";
                                case "Bash"    -> "⚙️";
                                case "Glob"    -> "🔍";
                                case "Grep"    -> "🔎";
                                case "LS"      -> "📁";
                                default        -> "🔧";
                            };
                            if (sb.length() > 0) sb.append('\n');
                            sb.append(icon).append(' ').append(name)
                              .append(fp.isEmpty() ? "" : " → " + fp);
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
                    String subtype = node.path("subtype").asText("");
                    if ("error".equals(subtype) || "error_during_execution".equals(subtype)) {
                        String errType = node.path("error").path("type").asText("");
                        String errMsg  = node.path("error").path("message").asText("");
                        if (!errMsg.isBlank()) yield "✗ Claude Error [" + errType + "]: " + errMsg;
                        String topErr = node.path("error").asText("");
                        yield topErr.isBlank() ? "✗ Claude Code 오류 (subtype=" + subtype + ")" : "✗ " + topErr;
                    }
                    String r = node.path("result").asText("").trim();
                    if (r.isEmpty()) {
                        int turns = node.path("num_turns").asInt(0);
                        if (turns > 0) yield "✓ 완료 (" + turns + " turns)";
                    }
                    yield r.isEmpty() ? null : r;
                }
                case "system" -> {
                    String sub = node.path("subtype").asText("");
                    if ("init".equals(sub)) {
                        String model = node.path("model").asText("");
                        yield model.isBlank() ? null : "▶ Claude 모델: " + model;
                    }
                    yield null;
                }
                default -> null;
            };
        } catch (Exception e) { return line; }
    }

    // ─────────────────────────────────────────────────────────
    //  유틸
    // ─────────────────────────────────────────────────────────

    private static String stripAnsi(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\u001B\\[[;\\d]*[A-Za-z]", "");
        s = s.replaceAll("\\u001B][^\\u0007]*\\u0007?", "");
        s = s.replaceAll("\\u001B.", "");
        s = s.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");
        return s;
    }

    private void send(Consumer<String> cb, String msg) {
        log.info("[ClaudeCliExecutor] {}", msg);
        if (cb != null) cb.accept(msg);
    }

    private static String nvl(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
