package com.jarvis.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class AutoDevService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${app.project-root:d:/jarvis-agent}")
    private String projectRoot;

    public record BuildResult(boolean success, String output, int exitCode) {}

    public record DevResult(
            String command,
            List<String> createdFiles,
            List<String> errors,
            BuildResult buildResult,
            String summary
    ) {}

    public DevResult develop(String command, Consumer<String> progressCallback) {
        log.info("자동 개발 시작 (Claude Code CLI): {}", command);
        notify(progressCallback, "Claude Code CLI를 통해 개발을 시작합니다...");

        List<String> errors = new ArrayList<>();
        StringBuilder cliOutput = new StringBuilder();

        String prompt = buildPrompt(command);
        boolean cliOk = runClaudeCli(prompt, progressCallback, cliOutput, errors);

        if (!cliOk) {
            notify(progressCallback, "⚠ Claude CLI 실행에 문제가 있었습니다. 빌드를 확인합니다...");
        }

        notify(progressCallback, "Gradle 빌드를 실행합니다...");
        BuildResult buildResult = runGradleBuild();

        List<String> createdFiles = extractCreatedFiles(cliOutput.toString());
        String summary = buildSummary(createdFiles, errors, buildResult, cliOk);
        return new DevResult(command, createdFiles, errors, buildResult, summary);
    }

    public DevResult develop(String command) {
        return develop(command, null);
    }

    private void notify(Consumer<String> cb, String msg) {
        if (cb != null) cb.accept(msg);
    }

    // ──────────────────────────────────────────────────────
    //  Claude Code CLI — 실제 터미널 창 오픈 + 파일 tail로 SSE 스트리밍
    // ──────────────────────────────────────────────────────
    private boolean runClaudeCli(String prompt, Consumer<String> progressCallback,
                                  StringBuilder output, List<String> errors) {
        try {
            boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
            String claudeExe = resolveClaudeExecutable(isWin);

            // Prompt → temp file (stdin 파이프로 전달)
            Path promptFile = Files.createTempFile("jarvis_prompt_", ".txt");
            Files.writeString(promptFile, prompt, StandardCharsets.UTF_8);

            // Log file: PS script writes here, Java tails it for SSE streaming
            Path outputLog = Files.createTempFile("jarvis_output_", ".log");

            String promptPath = promptFile.toString().replace("\\", "/");
            String logPath    = outputLog.toString().replace("\\", "/");
            String projRoot   = projectRoot.replace("\\", "/");

            String claudeInvoke;
            String exeLower = claudeExe.toLowerCase();
            if (exeLower.endsWith(".cmd") || exeLower.endsWith(".bat")) {
                claudeInvoke = "cmd /c \"" + claudeExe + "\"";
            } else {
                claudeInvoke = "& '" + claudeExe.replace("\\", "/") + "'";
            }

            String scriptContent = String.format("""
                    $ErrorActionPreference = 'Continue'
                    Set-Location '%s'
                    $env:PATH = "$env:PATH;$env:APPDATA\\npm;$env:LOCALAPPDATA\\Programs\\nodejs"
                    $OutputEncoding = [System.Text.Encoding]::UTF8
                    $enc = [System.Text.UTF8Encoding]::new($false)
                    $fs = [System.IO.FileStream]::new('%s', [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::Read)
                    $writer = [System.IO.StreamWriter]::new($fs, $enc)
                    $writer.AutoFlush = $true
                    try {
                        Get-Content '%s' -Raw | %s --dangerously-skip-permissions --output-format stream-json --verbose -p 2>&1 | ForEach-Object {
                            $line = "$_"
                            Write-Host $line
                            $writer.WriteLine($line)
                        }
                    } catch {
                        $writer.WriteLine("ERROR: $_")
                    } finally {
                        $writer.WriteLine('__JARVIS_DONE__')
                        $writer.Close()
                    }
                    Write-Host ''
                    Write-Host '[JARVIS] 작업 완료. Enter 를 눌러 창을 닫으세요.'
                    Read-Host
                    """, projRoot, logPath, promptPath, claudeInvoke);

            // Write PS1 with UTF-8 BOM
            Path launchScript = Files.createTempFile("jarvis_launch_", ".ps1");
            byte[] bom     = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] content = scriptContent.getBytes(StandardCharsets.UTF_8);
            byte[] withBom = new byte[bom.length + content.length];
            System.arraycopy(bom, 0, withBom, 0, bom.length);
            System.arraycopy(content, 0, withBom, bom.length, content.length);
            Files.write(launchScript, withBom);

            log.info("터미널 실행 스크립트: {} (Claude: {})", launchScript, claudeExe);
            notify(progressCallback, "터미널을 열고 Claude Code CLI를 실행합니다...");

            // 실제 터미널 창 오픈 (Windows Terminal → cmd fallback)
            ProcessBuilder termPb;
            if (isWin && wtExists()) {
                termPb = new ProcessBuilder("wt.exe", "-w", "0", "new-tab",
                        "--title", "JARVIS Claude Code", "--",
                        "powershell.exe", "-ExecutionPolicy", "Bypass", "-File", launchScript.toString());
            } else if (isWin) {
                termPb = new ProcessBuilder("cmd", "/c", "start", "JARVIS Claude Code",
                        "powershell.exe", "-ExecutionPolicy", "Bypass", "-File", launchScript.toString());
            } else {
                termPb = new ProcessBuilder("bash", "-c",
                        "xterm -T 'JARVIS Claude Code' -e 'powershell -File " + launchScript + "' &");
            }
            termPb.directory(new File(projectRoot));
            termPb.start(); // non-blocking: terminal runs independently

            // Log file tail → SSE sink
            tailOutputLog(outputLog, progressCallback, output, errors);

            return errors.stream().noneMatch(e -> e.startsWith("Claude CLI 타임아웃"));

        } catch (Exception e) {
            log.error("Claude CLI 터미널 실행 오류: {}", e.getMessage(), e);
            errors.add("Claude CLI 오류: " + e.getMessage());
            return false;
        }
    }

    private boolean wtExists() {
        String wtAppPath = System.getenv("LOCALAPPDATA") + "\\Microsoft\\WindowsApps\\wt.exe";
        if (new File(wtAppPath).exists()) return true;
        return commandExists("wt", true);
    }

    private void tailOutputLog(Path logFile, Consumer<String> progressCallback,
                                StringBuilder output, List<String> errors) throws Exception {
        long timeout = System.currentTimeMillis() + 300_000;
        long filePosition = 0;
        boolean done = false;
        StringBuilder lineBuffer = new StringBuilder();

        long startWait = System.currentTimeMillis();
        while (Files.size(logFile) == 0 && System.currentTimeMillis() - startWait < 30_000) {
            Thread.sleep(300);
        }

        while (!done && System.currentTimeMillis() < timeout) {
            long fileSize = Files.exists(logFile) ? Files.size(logFile) : 0;
            if (fileSize > filePosition) {
                try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                    raf.seek(filePosition);
                    byte[] buf = new byte[(int) (fileSize - filePosition)];
                    int read = raf.read(buf);
                    if (read > 0) {
                        filePosition += read;
                        lineBuffer.append(new String(buf, 0, read, StandardCharsets.UTF_8));
                        int nlIdx;
                        while ((nlIdx = lineBuffer.indexOf("\n")) != -1) {
                            String line = lineBuffer.substring(0, nlIdx).replaceAll("\r$", "").trim();
                            lineBuffer.delete(0, nlIdx + 1);
                            if ("__JARVIS_DONE__".equals(line)) {
                                done = true;
                                break;
                            }
                            if (line.isEmpty()) continue;
                            String msg = parseStreamJsonLine(line);
                            if (msg != null && !msg.isBlank()) {
                                output.append(msg).append('\n');
                                log.info("[claude] {}", msg);
                                notify(progressCallback, msg);
                            }
                        }
                    }
                }
            }
            if (!done) Thread.sleep(200);
        }

        if (!done) {
            errors.add("Claude CLI 타임아웃 (300초 초과)");
            log.warn("Claude CLI 타임아웃 (파일 테일링)");
        }
    }

    private static String stripAnsi(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\u001B\\[[;\\d]*[A-Za-z]", "");
        s = s.replaceAll("\\u001B][^\\u0007]*\\u0007?", "");
        s = s.replaceAll("\\u001B.", "");
        s = s.replace("\\u001B", "");
        s = s.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "");
        return s;
    }

    private String parseStreamJsonLine(String line) {
        String stripped = stripAnsi(line).trim();
        if (stripped.isEmpty()) return null;

        if (!stripped.startsWith("{")) {
            return stripped;
        }
        try {
            JsonNode node = MAPPER.readTree(stripped);
            String type = node.path("type").asText("");
            return switch (type) {
                case "assistant" -> {
                    JsonNode content = node.path("message").path("content");
                    if (content.isArray()) {
                        StringBuilder sb = new StringBuilder();
                        for (JsonNode item : content) {
                            String itemType = item.path("type").asText("");
                            if ("text".equals(itemType)) {
                                String text = item.path("text").asText("").trim();
                                if (!text.isEmpty()) {
                                    if (sb.length() > 0) sb.append(' ');
                                    sb.append(text);
                                }
                            } else if ("tool_use".equals(itemType)) {
                                String name = item.path("name").asText("");
                                String filePath = item.path("input").path("file_path").asText(
                                        item.path("input").path("path").asText(""));
                                String desc = name + (filePath.isEmpty() ? "" : " → " + filePath);
                                if (sb.length() > 0) sb.append('\n');
                                sb.append(desc);
                            }
                        }
                        String result = sb.toString().trim();
                        yield result.isEmpty() ? null : result;
                    }
                    yield null;
                }
                case "tool_result" -> {
                    JsonNode contentNode = node.path("content");
                    String content;
                    if (contentNode.isArray() && !contentNode.isEmpty()) {
                        content = contentNode.get(0).path("text").asText("").trim();
                    } else {
                        content = contentNode.asText("").trim();
                    }
                    yield content.isEmpty() ? null : "✓ " + content;
                }
                case "result" -> {
                    String result = node.path("result").asText("").trim();
                    yield result.isEmpty() ? null : result;
                }
                default -> null;
            };
        } catch (Exception e) {
            return stripped.isEmpty() ? null : stripped;
        }
    }

    private String resolveClaudeExecutable(boolean isWin) {
        try {
            ProcessBuilder pb = isWin
                    ? new ProcessBuilder("cmd", "/c", "where", "claude")
                    : new ProcessBuilder("which", "claude");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String path = br.readLine();
                    if (path != null && !path.isBlank()) {
                        log.info("Claude 실행 파일 경로: {}", path.trim());
                        return path.trim();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Claude 경로 탐색 실패: {}", e.getMessage());
        }
        if (isWin) {
            String home     = System.getProperty("user.home");
            String appData  = System.getenv("APPDATA");
            String localApp = System.getenv("LOCALAPPDATA");
            String[] candidates = {
                (appData  != null ? appData  : home + "\\AppData\\Roaming") + "\\npm\\claude.cmd",
                (localApp != null ? localApp : home + "\\AppData\\Local")   + "\\Programs\\claude\\claude.exe",
                "C:\\Program Files\\Claude\\claude.exe"
            };
            for (String c : candidates) {
                if (new File(c).exists()) {
                    log.info("Claude 실행 파일 발견: {}", c);
                    return c;
                }
            }
        }
        return "claude";
    }

    private boolean commandExists(String cmd, boolean isWin) {
        try {
            ProcessBuilder pb = isWin
                    ? new ProcessBuilder("cmd", "/c", "where", cmd)
                    : new ProcessBuilder("which", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildPrompt(String command) {
        return String.format("""
                다음 기능을 이 Spring Boot 프로젝트에 즉시 구현해 주세요.

                요청사항: %s

                프로젝트 환경:
                - 루트 패키지: com.jarvis
                - 아키텍처: 헥사고날 (adapter/in/web → application → domain/model, domain/port/out)
                - DB: H2 인메모리, Spring Data JPA (ddl-auto=create-drop)
                - 의존성: Spring Web, Spring Data JPA, Lombok, H2, WebFlux, Thymeleaf
                - Java 21, Spring Boot 3.4.5, Gradle Kotlin DSL

                기존 엔티티: Todo(id,title,description,done,priority,dueDate), Memo(id,title,content,tags), Schedule(id,title,description,startTime,endTime)

                구현 규칙:
                1. @Entity: @Id + @GeneratedValue(strategy=GenerationType.IDENTITY) 필수
                2. Repository: JpaRepository<Entity, Long>, 패키지 domain/port/out
                3. @RestController: 완전한 CRUD 엔드포인트 구현
                4. @Service: @Transactional, Lombok (@Slf4j, @RequiredArgsConstructor, @Builder)
                5. HTML: Thymeleaf + TailwindCSS CDN, fetch API로 REST 호출, CRUD UI
                6. 새 기능에 고유한 API 경로 사용 (/api/[기능명])
                7. 각 파일: public 클래스 하나만 포함

                파일을 직접 생성/수정해 주세요.
                """, command);
    }

    private List<String> extractCreatedFiles(String output) {
        List<String> files = new ArrayList<>();
        for (String line : output.split("\n")) {
            String trim = line.trim();
            if (!trim.isEmpty() &&
                (trim.contains("Created") || trim.contains("Edited") || trim.contains("✓") ||
                 trim.contains("Write →") || trim.contains("Edit →") || trim.contains("wrote")) &&
                (trim.contains(".java") || trim.contains(".html") || trim.contains(".kt") ||
                 trim.contains(".yaml") || trim.contains(".yml"))) {
                files.add(trim);
            }
        }
        return files;
    }

    private BuildResult runGradleBuild() {
        try {
            boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
            File projectDir = new File(projectRoot);
            String gradleExec = resolveGradleExecutable(projectDir, isWin);

            List<String> cmd = isWin
                    ? List.of("cmd", "/c", gradleExec, "compileJava", "--no-daemon", "--quiet")
                    : List.of("/bin/sh", "-c", gradleExec + " compileJava --no-daemon --quiet");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(projectDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append('\n');
            }
            int exitCode = process.waitFor();
            boolean ok = exitCode == 0;
            log.info("Gradle 빌드 {}. 종료 코드: {}", ok ? "성공" : "실패", exitCode);
            return new BuildResult(ok, out.toString(), exitCode);
        } catch (Exception e) {
            log.error("빌드 실행 오류: {}", e.getMessage());
            return new BuildResult(false, "빌드 실행 오류: " + e.getMessage(), -1);
        }
    }

    private String resolveGradleExecutable(File projectDir, boolean isWin) {
        String wrapperName = isWin ? "gradlew.bat" : "gradlew";
        File wrapper = new File(projectDir, wrapperName);
        if (wrapper.exists()) return wrapper.getAbsolutePath();
        try {
            Path distsDir = Paths.get(System.getProperty("user.home"), ".gradle", "wrapper", "dists");
            if (Files.exists(distsDir)) {
                String target = isWin ? "gradle.bat" : "gradle";
                try (var stream = Files.walk(distsDir, 5)) {
                    return stream
                            .filter(p -> p.getFileName().toString().equals(target))
                            .map(p -> p.toAbsolutePath().toString())
                            .findFirst()
                            .orElse(target);
                }
            }
        } catch (Exception e) {
            log.warn("Gradle 실행 파일 탐색 실패: {}", e.getMessage());
        }
        return isWin ? "gradle.bat" : "gradle";
    }

    private String buildSummary(List<String> created, List<String> errors,
                                 BuildResult build, boolean cliOk) {
        StringBuilder sb = new StringBuilder();
        if (cliOk) {
            sb.append("Claude Code가 코드를 성공적으로 구현하였습니다. ");
        } else {
            sb.append("코드 생성 중 문제가 발생하였습니다. ");
        }
        if (!created.isEmpty()) {
            sb.append(created.size()).append("개 파일이 처리되었습니다. ");
        }
        if (!errors.isEmpty()) {
            sb.append(errors.size()).append("건의 오류가 발생하였습니다. ");
        }
        if (build.success()) {
            sb.append("현진님, 요청하신 소스코드 수정 및 빌드 검증을 성공적으로 완료했습니다.");
        } else {
            sb.append("빌드 중 오류가 발생하였습니다. 로그를 확인해 주십시오.");
        }
        return sb.toString();
    }
}
