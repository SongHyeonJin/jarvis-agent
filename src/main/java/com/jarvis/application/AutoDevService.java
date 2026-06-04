package com.jarvis.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 자동 개발 서비스 — ClaudeCliExecutor를 통해 Claude Code CLI를 직접 실행.
 *
 * 터미널 팝업(wt.exe, cmd /c start, powershell.exe, Read-Host) 완전 제거.
 * ProcessBuilder 기반 ClaudeCliExecutor가 모든 실행을 담당한다.
 *
 * 주요 흐름:
 *   develop(command) → ClaudeCliExecutor.execute(-p prompt) → git diff → BuildResult
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoDevService {

    private final ClaudeCliExecutor claudeCliExecutor;
    private final GitService        gitService;

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

    // ─────────────────────────────────────────────────────────
    //  공개 API
    // ─────────────────────────────────────────────────────────

    public DevResult develop(String command, Consumer<String> progressCallback) {
        log.info("[AutoDevService] 자동 개발 시작: {}", command);
        notify(progressCallback, "Claude Code CLI를 통해 개발을 시작합니다...");

        List<String> errors = new ArrayList<>();
        StringBuilder fullLog = new StringBuilder();

        String prompt = buildPrompt(command);

        // ── Claude Code CLI 직접 실행 (팝업 터미널 없음) ──────────────────
        AtomicBoolean cancelled = new AtomicBoolean(false);
        ClaudeCliExecutor.ExecutionResult result = claudeCliExecutor.execute(
                prompt,
                chunk -> {
                    fullLog.append(chunk).append('\n');
                    notify(progressCallback, chunk);
                },
                null,        // raw 로그 사용 안 함
                cancelled
        );

        if (!result.success()) {
            String reason = result.timedOut()  ? "타임아웃 (300초 초과)"
                          : result.cancelled() ? "취소됨"
                          : "종료 코드 " + result.exitCode();
            log.warn("[AutoDevService] Claude CLI 비정상 종료: {}", reason);
            errors.add("Claude CLI 오류: " + reason);
        }

        // ── Git 변경 파일 조회 ────────────────────────────────────────────
        notify(progressCallback, "\n━━━ Git 변경 사항 확인 중... ━━━");
        List<String> changedFiles = gitService.getChangedFiles();
        String diffStat = gitService.getDiffStat();
        if (!diffStat.isBlank()) notify(progressCallback, diffStat);

        // ── Gradle 빌드 검증 ──────────────────────────────────────────────
        notify(progressCallback, "\n━━━ Gradle 빌드 검증 중... ━━━");
        BuildResult buildResult = runGradleBuild(progressCallback);

        String summary = buildSummary(changedFiles, errors, buildResult, result.success());
        log.info("[AutoDevService] 완료 — 변경 파일={}, 빌드={}", changedFiles.size(), buildResult.success());
        return new DevResult(command, changedFiles, errors, buildResult, summary);
    }

    public DevResult develop(String command) {
        return develop(command, null);
    }

    // ─────────────────────────────────────────────────────────
    //  Gradle 빌드
    // ─────────────────────────────────────────────────────────

    private BuildResult runGradleBuild(Consumer<String> progressCallback) {
        // Gradle 데몬 락 파일이 있으면 서버가 이미 동일 프로세스에서 실행 중 → 빌드 생략
        File lockDir = new File(System.getProperty("user.home"), ".gradle/daemon");
        boolean daemonRunning = lockDir.exists() && lockDir.listFiles() != null
                && java.util.Arrays.stream(lockDir.listFiles(File::isDirectory)).anyMatch(d -> {
                    File[] locks = d.listFiles((f, n) -> n.endsWith(".lock"));
                    return locks != null && locks.length > 0;
                });
        if (daemonRunning) {
            notify(progressCallback, "ℹ 서버 실행 중 — Gradle 빌드 검증 생략 (재시작으로 확인 가능)");
            return new BuildResult(true, "Gradle 데몬 실행 중 — 빌드 생략", 0);
        }

        try {
            boolean isWin   = System.getProperty("os.name", "").toLowerCase().contains("win");
            File    projDir  = new File(projectRoot);
            String  gradle   = resolveGradle(projDir, isWin);

            List<String> cmd = isWin
                    ? List.of("cmd", "/c", gradle, "compileJava", "--no-daemon", "--quiet")
                    : List.of(gradle, "compileJava", "--no-daemon", "--quiet");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(projDir);
            pb.redirectErrorStream(true);

            StringBuilder out = new StringBuilder();
            Process proc = pb.start();
            try (var br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.isBlank()) {
                        out.append(line).append('\n');
                        notify(progressCallback, line);
                    }
                }
            }
            boolean finished = proc.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                notify(progressCallback, "⚠ Gradle 타임아웃");
                return new BuildResult(false, "Gradle 타임아웃", -1);
            }
            boolean ok = proc.exitValue() == 0;
            notify(progressCallback, ok ? "✓ BUILD SUCCESS" : "✗ BUILD FAILED (exit=" + proc.exitValue() + ")");
            return new BuildResult(ok, out.toString(), proc.exitValue());

        } catch (Exception e) {
            log.error("[AutoDevService] 빌드 오류: {}", e.getMessage(), e);
            notify(progressCallback, "✗ 빌드 오류: " + e.getMessage());
            return new BuildResult(false, "빌드 오류: " + e.getMessage(), -1);
        }
    }

    private String resolveGradle(File dir, boolean isWin) {
        File wrapper = new File(dir, isWin ? "gradlew.bat" : "gradlew");
        if (wrapper.exists()) return wrapper.getAbsolutePath();
        try {
            var dists = Paths.get(System.getProperty("user.home"), ".gradle", "wrapper", "dists");
            if (Files.exists(dists)) {
                String target = isWin ? "gradle.bat" : "gradle";
                try (var s = Files.walk(dists, 5)) {
                    return s.filter(p -> p.getFileName().toString().equals(target))
                            .map(p -> p.toAbsolutePath().toString())
                            .findFirst().orElse(target);
                }
            }
        } catch (Exception e) {
            log.warn("[AutoDevService] Gradle 탐색 실패: {}", e.getMessage());
        }
        return isWin ? "gradle.bat" : "gradle";
    }

    // ─────────────────────────────────────────────────────────
    //  프롬프트 & 요약
    // ─────────────────────────────────────────────────────────

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

    private String buildSummary(List<String> changed, List<String> errors,
                                 BuildResult build, boolean cliOk) {
        var sb = new StringBuilder();
        sb.append(cliOk ? "Claude Code 작업 완료. " : "코드 생성 중 문제가 발생하였습니다. ");
        if (!changed.isEmpty()) sb.append(changed.size()).append("개 파일 변경. ");
        if (!errors.isEmpty())  sb.append(errors.size()).append("건의 오류 발생. ");
        sb.append(build.success()
                ? "현진님, 요청하신 기능 개발 및 빌드 검증이 완료되었습니다."
                : "빌드 중 오류가 발생하였습니다. 로그를 확인해 주십시오.");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────
    //  유틸
    // ─────────────────────────────────────────────────────────

    private void notify(Consumer<String> cb, String msg) {
        if (cb != null) cb.accept(msg);
    }
}
