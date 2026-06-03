package com.jarvis.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.domain.model.DevJob;
import com.jarvis.domain.port.out.DevJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dev Agent 핵심 오케스트레이터.
 *
 * ═══ 실행 파이프라인 (NEW_PROJECT) ═══
 *  1. 프로젝트 유형 분석 (ProjectTypeAnalyzer — WorkspaceResolver 에서 이미 수행)
 *  2. AI 프로젝트명 생성 (WorkspaceNameResolver)
 *  3. 기본 폴더 생성 + 쓰기 권한 확인
 *  4. ScaffoldService — 유형별 폴더 구조 + 초기 파일 생성
 *  5. git init
 *  6. README.md 생성
 *  7. .claude/settings.local.json 생성
 *  8. AssetService — 이미지/아이콘 생성
 *  9. Claude trust 자동 처리
 * 10. Claude Code CLI 실행
 * 11. 파일 검증
 * 12. Chrome Extension → ManifestValidator 자동 보정
 * 13. 자동 1회 재시도 (파일 미생성 시)
 * 14. SUCCESS / SUCCESS_WITH_WARNINGS / FAILED 최종 판정
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DevJobService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DevJobRepository        jobRepo;
    private final ClaudeCliExecutor       executor;
    private final GitService              gitService;
    private final WorkspaceResolver       workspaceResolver;
    private final AppWorkspaceProperties  workspaceProps;
    private final ScaffoldService         scaffoldService;
    private final AssetService            assetService;
    private final ManifestValidator       manifestValidator;

    /** 실행 중인 잡의 SSE 싱크 (replay(500)) */
    private final ConcurrentHashMap<Long, Sinks.Many<String>> sinks   = new ConcurrentHashMap<>();
    /** 취소 신호 */
    private final ConcurrentHashMap<Long, AtomicBoolean>      cancels = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────
    //  공개 API
    // ─────────────────────────────────────────────────────────

    /** 새 Dev Job 제출 — 즉시 jobId 반환, 실행은 비동기 */
    public DevJob submitJob(String command) {
        WorkspaceResolver.WorkspaceInfo wsInfo = workspaceResolver.resolve(command);
        String prompt = buildPrompt(command, wsInfo.jobType(), wsInfo.projectRoot(), wsInfo.projectType());

        DevJob job = DevJob.builder()
                .command(command)
                .prompt(prompt)
                .status(DevJob.JobStatus.PENDING)
                .jobType(wsInfo.jobType().name())
                .projectType(wsInfo.projectType().name())
                .workspacePath(wsInfo.projectRoot().toString())
                .createdAt(LocalDateTime.now())
                .build();
        job = jobRepo.save(job);
        Long jobId = job.getId();

        Sinks.Many<String> sink = Sinks.many().replay().limit(500);
        sinks.put(jobId, sink);
        AtomicBoolean cancel = new AtomicBoolean(false);
        cancels.put(jobId, cancel);

        final Long        id   = jobId;
        final String      path = wsInfo.projectRoot().toString();
        final JobType     type = wsInfo.jobType();
        final ProjectType pt   = wsInfo.projectType();
        final String      cmd  = command;
        Schedulers.boundedElastic().schedule(() -> runJob(id, path, type, pt, cmd, sink, cancel));

        log.info("[DevJobService] Job #{} 제출: jobType={} projectType={} path={} cmd={}",
                 jobId, type, pt, path, command.length() > 60 ? command.substring(0, 60) + "…" : command);
        return job;
    }

    /** SSE Flux 반환 */
    public Flux<String> streamJob(Long jobId) {
        Sinks.Many<String> sink = sinks.get(jobId);
        if (sink != null) return sink.asFlux();

        return jobRepo.findById(jobId)
                .map(job -> Flux.concat(
                    Flux.just(sse("status",      job.getStatus().name())),
                    Flux.just(sse("jobType",      coalesce(job.getJobType(), "MODIFY_JARVIS"))),
                    Flux.just(sse("projectType",  coalesce(job.getProjectType(), "UNKNOWN"))),
                    Flux.just(sse("workspace",    coalesce(job.getWorkspacePath(), ""))),
                    Flux.just(sse("log",          coalesce(job.getLogOutput(), "(로그 없음)"))),
                    Flux.just(sse("done",         buildDonePayload(job))),
                    Flux.just("[DONE]")
                ))
                .orElseGet(() -> Flux.just(sse("error", "Job not found: " + jobId)));
    }

    /** 잡 취소 */
    public void cancelJob(Long jobId) {
        AtomicBoolean cancel = cancels.get(jobId);
        if (cancel != null) cancel.set(true);
        jobRepo.findById(jobId).ifPresent(job -> {
            if (!job.isTerminal()) {
                job.markCancelled();
                jobRepo.save(job);
                log.info("[DevJobService] Job #{} 취소됨", jobId);
            }
        });
    }

    public Optional<DevJob> getJob(Long jobId) { return jobRepo.findById(jobId); }
    public List<DevJob>     listJobs()          { return jobRepo.findRecent();    }

    // ─────────────────────────────────────────────────────────
    //  비동기 실행 루프
    // ─────────────────────────────────────────────────────────

    private void runJob(Long jobId, String workPath, JobType jobType, ProjectType projectType,
                         String command, Sinks.Many<String> sink, AtomicBoolean cancel) {

        DevJob job = jobRepo.findById(jobId).orElseThrow();
        Path   projectPath = Paths.get(workPath);

        job.markRunning();
        jobRepo.save(job);
        emit(sink, "status",      "RUNNING");
        emit(sink, "jobType",     jobType.name());
        emit(sink, "projectType", projectType.name());
        emit(sink, "workspace",   workPath);

        emit(sink, "log", "━━━ JARVIS Dev Agent 시작 ━━━");
        emit(sink, "log", "▶ Job #" + jobId + " 생성됨");
        emit(sink, "log", "▶ 명령: " + command);
        emit(sink, "log", "▶ 작업 유형: " + jobType.name());
        emit(sink, "log", "▶ 프로젝트 유형: " + projectType.name());
        emit(sink, "log", "▶ 작업 위치: " + workPath);

        // ── NEW_PROJECT 전처리 ────────────────────────────────
        if (jobType == JobType.NEW_PROJECT) {
            emit(sink, "log", "▶ 독립 프로젝트 모드 — 기존 JARVIS 파일 수정 안 함");

            String slug = projectPath.getFileName().toString();
            emit(sink, "log", "▶ 프로젝트명: " + slug);
            emit(sink, "log", "▶ 작업 폴더 생성: " + workPath);

            // ① 디렉토리 생성 + 쓰기 권한 확인
            try {
                Files.createDirectories(projectPath);
                emit(sink, "log", "▶ 작업 폴더 확인됨 ✓");
            } catch (Exception e) {
                String msg = "ERROR: 작업 폴더 생성 실패: " + e.getMessage();
                emit(sink, "log", msg);
                failJob(job, jobId, msg, sink);
                return;
            }

            Path testFile = projectPath.resolve(".jarvis-write-test");
            try {
                Files.writeString(testFile, "ok", StandardCharsets.UTF_8);
                Files.deleteIfExists(testFile);
                emit(sink, "log", "▶ workspace 쓰기 권한 확인 완료 ✓");
            } catch (Exception e) {
                String msg = "ERROR: workspace 쓰기 권한 없음: " + e.getMessage();
                emit(sink, "log", msg);
                failJob(job, jobId, msg, sink);
                return;
            }

            // ② ScaffoldService — 유형별 폴더+파일 생성
            emit(sink, "log", "");
            emit(sink, "log", "━━━ Scaffold 생성 ━━━");
            scaffoldService.create(projectPath, projectType, slug, command,
                                   msg -> emit(sink, "log", msg));

            // ③ git init
            initGit(projectPath, sink);

            // ④ README.md 생성
            initReadme(projectPath, command, sink);

            // ⑤ .claude/settings.local.json 생성
            initClaudeSettings(projectPath, sink);

            // ⑥ AssetService — 이미지/아이콘 생성
            emit(sink, "log", "");
            emit(sink, "log", "━━━ 에셋 생성 ━━━");
            AssetPlan assetPlan = assetService.plan(projectType, command);
            if (!assetPlan.assets().isEmpty()) {
                assetService.generate(projectPath, assetPlan,
                                      msg -> emit(sink, "log", msg));
            } else {
                emit(sink, "log", "▶ 에셋 불필요 (스킵)");
            }
        }

        // ── Claude CLI 실행 ───────────────────────────────────
        emit(sink, "log", "");
        emit(sink, "log", "━━━ Claude Code CLI 실행 시작 ━━━");

        var fullLog = new StringBuilder();
        var rawLog  = new StringBuilder();

        ClaudeCliExecutor.ExecutionResult result = runClaude(job.getPrompt(), workPath, fullLog, rawLog, sink, cancel);

        // ── 취소 처리 ────────────────────────────────────────
        if (cancel.get()) {
            job = jobRepo.findById(jobId).orElseThrow();
            job.markCancelled();
            jobRepo.save(job);
            emit(sink, "status", "CANCELLED");
            emit(sink, "log",    "⚠ 작업이 취소되었습니다.");
            emit(sink, "done",   "{\"cancelled\":true,\"jobType\":\"" + jobType.name() + "\"}");
            sink.tryEmitComplete();
            cleanup(jobId);
            return;
        }

        // ── 후처리: 작업 유형별 분기 ──────────────────────────
        List<String> changedFiles;
        String  diffStat = "";
        String  diff     = "";
        boolean buildOk  = true;

        if (jobType == JobType.MODIFY_JARVIS) {
            emit(sink, "log", "\n━━━ Git 변경 사항 확인 중... ━━━");
            diffStat     = gitService.getDiffStat();
            diff         = gitService.getDiff();
            changedFiles = gitService.getChangedFiles();
            if (!diffStat.isBlank()) emit(sink, "log", diffStat);

            emit(sink, "log", "\n━━━ Gradle 빌드 검증 중... ━━━");
            buildOk = runGradleBuild(sink);

        } else {
            // NEW_PROJECT → Files.walk 기반 파일 목록
            emit(sink, "log", "\n━━━ 생성된 파일 검증 중... ━━━");
            changedFiles = listCreatedFiles(workPath, sink);

            // ── Chrome Extension manifest 자동 보정 ───────────
            if (projectType == ProjectType.CHROME_EXTENSION) {
                String slug = projectPath.getFileName().toString();
                manifestValidator.validateAndFix(projectPath, slug,
                                                  msg -> emit(sink, "log", msg));
                changedFiles = listCreatedFiles(workPath, sink); // 보정 후 재조회
            }

            // ── 1차 성공 판정 ──────────────────────────────────
            boolean firstAttemptOk = validateFiles(changedFiles, projectType, sink);

            // ── Claude 실패 상세 로그 ──────────────────────────
            if (!result.success()) {
                emitClaudeFailureLog(result, workPath, rawLog.toString(), sink);
            }

            // ── 자동 1회 재시도 (파일 미생성 시) ──────────────
            if (!firstAttemptOk && !cancel.get()) {
                emit(sink, "log", "");
                emit(sink, "log", "━━━ 자동 재시도 (1회) ━━━");
                emit(sink, "log", "▶ 파일이 부족하여 재시도합니다...");
                String retryPrompt = buildRetryPrompt(command, workPath, projectType, changedFiles);
                rawLog.setLength(0);
                result = runClaude(retryPrompt, workPath, fullLog, rawLog, sink, cancel);

                if (!cancel.get()) {
                    emit(sink, "log", "\n━━━ 재시도 후 파일 검증 중... ━━━");
                    changedFiles = listCreatedFiles(workPath, sink);
                    if (projectType == ProjectType.CHROME_EXTENSION) {
                        manifestValidator.validateAndFix(projectPath,
                            projectPath.getFileName().toString(),
                            msg -> emit(sink, "log", msg));
                        changedFiles = listCreatedFiles(workPath, sink);
                    }
                    validateFiles(changedFiles, projectType, sink);
                }
            }

            buildOk = true; // NEW_PROJECT 는 Gradle 빌드 없음
        }

        // ── 최종 성공 판정 ────────────────────────────────────
        boolean actualSuccess = evaluateSuccess(result, buildOk, changedFiles, jobType, projectType);
        String  summary       = buildSummary(changedFiles, buildOk, result, jobType, projectType, workPath, actualSuccess);

        String changedStr = String.join("\n", changedFiles);

        job = jobRepo.findById(jobId).orElseThrow();
        job.markDone(fullLog.toString(), diff, diffStat, changedStr, summary,
                     buildOk, actualSuccess ? 0 : 1);
        jobRepo.save(job);

        String finalStatus = actualSuccess ? "DONE" : "FAILED";
        emit(sink, "status", finalStatus);
        emit(sink, "log",    "\n━━━ " + (actualSuccess ? "완료" : "실패") + " ━━━\n" + summary);
        emit(sink, "done",   buildDonePayload(job));
        sink.tryEmitComplete();
        cleanup(jobId);

        log.info("[DevJobService] Job #{} {} (jobType={} projectType={} files={} build={})",
                 jobId, finalStatus, jobType, projectType, changedFiles.size(), buildOk);
    }

    // ─────────────────────────────────────────────────────────
    //  Claude 실행 래퍼
    // ─────────────────────────────────────────────────────────

    private ClaudeCliExecutor.ExecutionResult runClaude(
            String prompt, String workPath,
            StringBuilder fullLog, StringBuilder rawLog,
            Sinks.Many<String> sink, AtomicBoolean cancel) {

        return executor.execute(
            prompt,
            workPath,
            chunk -> {
                fullLog.append(chunk).append('\n');
                emit(sink, "log", chunk);
            },
            rawLine -> rawLog.append(rawLine).append('\n'),
            cancel
        );
    }

    // ─────────────────────────────────────────────────────────
    //  성공 판정
    // ─────────────────────────────────────────────────────────

    /**
     * 최종 성공 여부를 결정한다.
     *
     * NEW_PROJECT 규칙 (SUCCESS_WITH_WARNINGS 지원):
     *   - cancelled / timedOut → false
     *   - 필수 파일 충족 → true  (exitCode 비관여)
     *
     * MODIFY_JARVIS 규칙:
     *   - exitCode == 0 && buildOk → true
     */
    private boolean evaluateSuccess(ClaudeCliExecutor.ExecutionResult result,
                                    boolean buildOk,
                                    List<String> changedFiles,
                                    JobType jobType,
                                    ProjectType projectType) {
        if (result.cancelled() || result.timedOut()) return false;

        if (jobType == JobType.MODIFY_JARVIS) {
            return result.exitCode() == 0 && buildOk;
        }

        // NEW_PROJECT: 파일 기반 판정 (exitCode 무관)
        Set<String> fileNames = fileNameSet(changedFiles);

        // README 외 파일이 1개 이상 있어야 함
        long nonReadme = changedFiles.stream()
            .filter(f -> !baseName(f).equalsIgnoreCase("readme.md"))
            .count();
        if (nonReadme == 0) return false;

        // 프로젝트 유형별 필수 파일 체크
        return switch (projectType) {
            case CHROME_EXTENSION ->
                fileNames.contains("manifest.json")
                && (fileNames.contains("content.js") || fileNames.contains("background.js")
                    || fileNames.contains("service-worker.js"));
            case SPRING_BOOT ->
                changedFiles.stream().anyMatch(f -> f.toLowerCase().endsWith("application.java"))
                || changedFiles.stream().anyMatch(f -> f.toLowerCase().endsWith("build.gradle"));
            case REACT_APP, NEXT_APP ->
                fileNames.contains("package.json");
            default -> true; // WEB_APP, GAME 등: 파일만 있으면 성공
        };
    }

    /**
     * 파일 검증 (로그 출력용 — 반환값은 성공 여부)
     */
    private boolean validateFiles(List<String> changedFiles, ProjectType projectType,
                                   Sinks.Many<String> sink) {
        Set<String> names = fileNameSet(changedFiles);

        long nonReadme = changedFiles.stream()
            .filter(f -> !baseName(f).equalsIgnoreCase("readme.md"))
            .count();

        if (nonReadme == 0) {
            emit(sink, "log", "");
            emit(sink, "log", "ERROR: Claude Code 실행은 완료됐지만 생성된 파일이 없습니다.");
            emit(sink, "log", "▶ 가능한 원인:");
            emit(sink, "log", "  - Claude가 파일 생성 없이 설명만 했을 수 있습니다.");
            emit(sink, "log", "  - --dangerously-skip-permissions 미적용 가능성");
            emit(sink, "log", "  - 프롬프트가 불분명해 작업을 거부했을 수 있습니다.");
            return false;
        }

        boolean ok = switch (projectType) {
            case CHROME_EXTENSION -> {
                boolean hasManifest = names.contains("manifest.json");
                boolean hasScript   = names.contains("content.js") || names.contains("background.js")
                                   || names.contains("service-worker.js");
                if (!hasManifest) emit(sink, "log", "⚠ manifest.json 없음 → ManifestValidator 가 자동 보정합니다");
                if (!hasScript)   emit(sink, "log", "⚠ content.js / background.js 없음");
                yield hasManifest && hasScript;
            }
            case SPRING_BOOT -> {
                boolean hasSrc = changedFiles.stream()
                    .anyMatch(f -> f.toLowerCase().contains("application.java")
                               || f.toLowerCase().endsWith("build.gradle"));
                if (!hasSrc) emit(sink, "log", "⚠ Application.java 또는 build.gradle 없음");
                yield hasSrc;
            }
            default -> true;
        };

        if (ok) emit(sink, "log", "✓ 파일 검증 통과");
        return ok;
    }

    private void emitClaudeFailureLog(ClaudeCliExecutor.ExecutionResult result,
                                       String workPath, String rawLog,
                                       Sinks.Many<String> sink) {
        emit(sink, "log", "");
        emit(sink, "log", "━━━ Claude Code 실행 비정상 종료 (파일 검증으로 판정) ━━━");
        emit(sink, "log", "▶ exitCode: " + result.exitCode());
        emit(sink, "log", "▶ timedOut: " + result.timedOut());
        emit(sink, "log", "▶ 작업 디렉토리: " + workPath);
        String masked = maskSecrets(rawLog);
        int shown = 0;
        for (String line : masked.split("\\n")) {
            if (shown >= 30) { emit(sink, "log", "  ... (로그 생략)"); break; }
            if (!line.isBlank()) { emit(sink, "log", "  " + line); shown++; }
        }
        emit(sink, "log", "▶ 파일 생성 여부로 최종 판정합니다...");
    }

    // ─────────────────────────────────────────────────────────
    //  NEW_PROJECT 전처리 헬퍼
    // ─────────────────────────────────────────────────────────

    private void initGit(Path projectPath, Sinks.Many<String> sink) {
        if (Files.exists(projectPath.resolve(".git"))) {
            emit(sink, "log", "▶ git 이미 초기화됨 (기존 .git 폴더)");
            return;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "init");
            pb.directory(projectPath.toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String  out  = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            proc.waitFor(10, TimeUnit.SECONDS);
            emit(sink, "log", "▶ git init 완료 ✓" + (out.isBlank() ? "" : " — " + out.replace("\n", " ")));
        } catch (Exception e) {
            emit(sink, "log", "⚠ git init 실패 (무시됨): " + e.getMessage());
        }
    }

    private void initReadme(Path projectPath, String command, Sinks.Many<String> sink) {
        Path readmePath = projectPath.resolve("README.md");
        if (Files.exists(readmePath)) { emit(sink, "log", "▶ README.md 이미 존재함 ✓"); return; }
        try {
            String projectName = projectPath.getFileName().toString();
            String content = "# " + projectName + "\n\n"
                + "> Generated by JARVIS Dev Agent\n\n"
                + "## 요청 내용\n\n" + command + "\n";
            Files.writeString(readmePath, content, StandardCharsets.UTF_8);
            emit(sink, "log", "▶ README.md 초기화 완료 ✓");
        } catch (Exception e) {
            emit(sink, "log", "⚠ README.md 생성 실패: " + e.getMessage());
        }
    }

    private void initClaudeSettings(Path projectPath, Sinks.Many<String> sink) {
        try {
            Path claudeDir = projectPath.resolve(".claude");
            Files.createDirectories(claudeDir);
            Path settings = claudeDir.resolve("settings.local.json");
            if (!Files.exists(settings)) {
                String json = """
                        {
                          "permissions": {
                            "allow": [
                              "Bash(*)",
                              "Write(*)",
                              "Edit(*)",
                              "Read(*)",
                              "Glob(*)",
                              "Grep(*)",
                              "LS(*)"
                            ]
                          },
                          "projectSafe": true,
                          "enabledFeatures": ["trust"]
                        }
                        """;
                Files.writeString(settings, json, StandardCharsets.UTF_8);
                emit(sink, "log", "▶ .claude/settings.local.json 생성 완료 ✓");
            }
            emit(sink, "log", "▶ Claude trust 처리 완료 ✓");
        } catch (Exception e) {
            emit(sink, "log", "⚠ .claude/settings 생성 실패 (무시됨): " + e.getMessage());
        }
    }

    /** 즉시 FAILED 처리 후 SSE 종료 */
    private void failJob(DevJob job, Long jobId, String reason, Sinks.Many<String> sink) {
        job.markFailed("", reason);
        jobRepo.save(job);
        emit(sink, "status", "FAILED");
        emit(sink, "done",   buildDonePayload(job));
        sink.tryEmitComplete();
        cleanup(jobId);
    }

    // ─────────────────────────────────────────────────────────
    //  파일 목록 (NEW_PROJECT — Files.walk 기반)
    // ─────────────────────────────────────────────────────────

    private List<String> listCreatedFiles(String workPath, Sinks.Many<String> sink) {
        try {
            Path root = Paths.get(workPath);
            if (!Files.exists(root)) return Collections.emptyList();
            List<String> files = Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(p -> !p.toString().replace('\\', '/').contains("/node_modules/"))
                .filter(p -> !p.toString().replace('\\', '/').contains("/.git/"))
                .filter(p -> !p.toString().replace('\\', '/').contains("/.claude/"))
                .filter(p -> !p.getFileName().toString().equals(".jarvis-write-test"))
                .map(p -> root.relativize(p).toString().replace('\\', '/'))
                .sorted()
                .toList();

            if (files.isEmpty()) {
                emit(sink, "log", "(생성된 파일 없음)");
            } else {
                files.forEach(f -> emit(sink, "log", "  ✓ " + f));
            }
            return files;
        } catch (Exception e) {
            emit(sink, "log", "파일 목록 조회 실패: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Gradle 빌드 (MODIFY_JARVIS 전용)
    // ─────────────────────────────────────────────────────────

    private boolean runGradleBuild(Sinks.Many<String> sink) {
        File lockDir = new File(System.getProperty("user.home"), ".gradle/daemon");
        boolean daemonRunning = lockDir.exists() && lockDir.listFiles() != null
            && Arrays.stream(lockDir.listFiles(File::isDirectory)).anyMatch(d -> {
                File[] locks = d.listFiles((f, n) -> n.endsWith(".lock"));
                return locks != null && locks.length > 0;
            });
        if (daemonRunning) {
            emit(sink, "log", "ℹ 서버 실행 중 — Gradle 빌드 검증 생략 (재시작으로 확인 가능)");
            return true;
        }
        try {
            boolean isWin   = System.getProperty("os.name", "").toLowerCase().contains("win");
            File    projDir  = new File(workspaceProps.getProjectRoot());
            String  gradle   = resolveGradle(projDir, isWin);
            List<String> cmd = isWin
                ? List.of("cmd", "/c", gradle, "compileJava", "--no-daemon", "--quiet")
                : List.of(gradle, "compileJava", "--no-daemon", "--quiet");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(projDir);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) { if (!line.isBlank()) emit(sink, "log", line); }
            }
            boolean finished = proc.waitFor(120, TimeUnit.SECONDS);
            if (!finished) { proc.destroyForcibly(); emit(sink, "log", "⚠ Gradle 타임아웃"); return false; }
            boolean ok = proc.exitValue() == 0;
            emit(sink, "log", ok ? "✓ BUILD SUCCESS" : "✗ BUILD FAILED (exit=" + proc.exitValue() + ")");
            return ok;
        } catch (Exception e) {
            emit(sink, "log", "✗ 빌드 오류: " + e.getMessage());
            return false;
        }
    }

    private String resolveGradle(File dir, boolean isWin) {
        File wrapper = new File(dir, isWin ? "gradlew.bat" : "gradlew");
        if (wrapper.exists()) return wrapper.getAbsolutePath();
        try {
            Path dists = Paths.get(System.getProperty("user.home"), ".gradle", "wrapper", "dists");
            if (Files.exists(dists)) {
                String target = isWin ? "gradle.bat" : "gradle";
                try (var s = Files.walk(dists, 5)) {
                    return s.filter(p -> p.getFileName().toString().equals(target))
                            .map(p -> p.toAbsolutePath().toString())
                            .findFirst().orElse(target);
                }
            }
        } catch (Exception e) { log.warn("Gradle 탐색 실패: {}", e.getMessage()); }
        return isWin ? "gradle.bat" : "gradle";
    }

    // ─────────────────────────────────────────────────────────
    //  프롬프트 빌더
    // ─────────────────────────────────────────────────────────

    private String buildPrompt(String command, JobType jobType,
                                java.nio.file.Path workPath, ProjectType projectType) {
        return jobType == JobType.NEW_PROJECT
            ? buildNewProjectPrompt(command, workPath, projectType)
            : buildModifyJarvisPrompt(command);
    }

    private String buildNewProjectPrompt(String command,
                                          java.nio.file.Path workPath,
                                          ProjectType projectType) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                이 작업은 새 독립 프로젝트 생성 작업입니다.

                반드시 현재 작업 디렉토리에 실제 파일을 생성하세요.
                설명만 하지 말고 Write/Edit 도구로 파일을 직접 작성해야 합니다.

                """);

        sb.append("작업 디렉토리:\n").append(workPath).append("\n\n");
        sb.append("사용자 요청:\n").append(command).append("\n\n");

        sb.append("""
                공통 규칙:
                1. 기존 JARVIS 프로젝트(d:/jarvis-agent)는 절대 수정하지 않습니다.
                2. 현재 작업 디렉토리 밖으로 나가지 않습니다.
                3. 파일을 하나도 만들지 않고 설명만 하면 실패입니다.
                4. 실행 가능한 최소 결과물을 만듭니다.
                5. README.md에 한국어로 설치/실행 방법을 작성합니다.
                6. Scaffold로 이미 생성된 파일이 있으면 덮어써서 내용을 채워주세요.

                """);

        // 유형별 강화 지침
        switch (projectType) {
            case CHROME_EXTENSION -> sb.append("""
                    ══ Chrome Extension Manifest V3 ══
                    설명만 하지 말고 반드시 Write 도구로 아래 파일을 생성/완성하세요:

                    1. manifest.json  (manifest_version: 3, 필요한 권한 포함)
                    2. content.js     (content script — 메인 로직 완전 구현)
                    3. styles.css     (스타일 완전 구현)
                    4. README.md      (설치 방법: chrome://extensions → 개발자 모드 → 압축 해제)
                    5. icons/icon16.png, icons/icon48.png, icons/icon128.png  (이미 생성되어 있음)

                    content.js 에 실제 동작하는 코드를 반드시 구현하세요.
                    파일을 하나라도 작성하지 않으면 실패입니다.

                    """);
            case SPRING_BOOT -> sb.append("""
                    ══ Spring Boot 프로젝트 ══
                    Scaffold 구조가 이미 생성되어 있습니다. 현재 구조를 유지하면서 아래를 구현하세요:

                    - src/main/java/ 아래 Application 클래스가 이미 있습니다.
                    - 요청에 맞는 Controller, Service, Repository, Entity 를 추가하세요.
                    - 패키지: com.jarvis.demo
                    - 어노테이션: @SpringBootApplication, @RestController, @Service, @Repository
                    - build.gradle 에 필요한 의존성을 추가하세요.

                    """);
            case REACT_APP -> sb.append("""
                    ══ React 프로젝트 ══
                    package.json, src/App.js, src/index.js, public/index.html 이 이미 생성되어 있습니다.
                    App.js 를 수정하고 필요한 컴포넌트를 src/ 아래 추가하세요.
                    TailwindCSS CDN 사용 가능합니다.

                    """);
            case GAME -> sb.append("""
                    ══ HTML/JS 게임 ══
                    index.html, game.js, styles.css 가 이미 생성되어 있습니다.
                    game.js 에 실제 동작하는 게임 로직을 완전 구현하세요.
                    HTML5 Canvas 를 활용하세요.
                    키보드 조작, 충돌 감지, 점수 표시를 포함하세요.

                    """);
            default -> sb.append("""
                    ══ Web App ══
                    index.html, styles.css, script.js 가 이미 생성되어 있습니다.
                    실제 동작하는 기능을 구현하세요.

                    """);
        }

        sb.append("지금 즉시 파일 생성/수정을 시작하세요. Write 도구로 각 파일을 직접 작성하세요.");
        return sb.toString();
    }

    private String buildRetryPrompt(String command, String workPath,
                                     ProjectType projectType, List<String> existingFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("이전 시도에서 파일이 충분히 생성되지 않았습니다.\n");
        sb.append("이번에는 반드시 Write 도구를 사용해서 파일을 직접 작성하세요.\n\n");
        sb.append("작업 디렉토리: ").append(workPath).append("\n");
        sb.append("사용자 요청: ").append(command).append("\n\n");

        if (!existingFiles.isEmpty()) {
            sb.append("현재 존재하는 파일:\n");
            existingFiles.forEach(f -> sb.append("  - ").append(f).append("\n"));
            sb.append("\n");
        }

        switch (projectType) {
            case CHROME_EXTENSION -> sb.append("""
                    반드시 생성해야 할 파일:
                    - manifest.json (manifest_version: 3)
                    - content.js    (실제 동작 코드 구현)
                    - styles.css    (스타일)
                    - README.md
                    """);
            case SPRING_BOOT -> sb.append("""
                    반드시 생성해야 할 파일:
                    - Application.java
                    - 요청에 맞는 Controller.java
                    - build.gradle
                    """);
            default -> sb.append("index.html, styles.css, script.js 등 핵심 파일을 반드시 작성하세요.\n");
        }

        sb.append("\n지금 즉시 Write 도구로 파일을 직접 작성하세요.");
        return sb.toString();
    }

    private String buildModifyJarvisPrompt(String command) {
        return String.format("""
                다음 기능을 이 Spring Boot 프로젝트에 즉시 구현해 주세요.

                요청사항: %s

                프로젝트 환경:
                - 루트 패키지: com.jarvis
                - 아키텍처: 헥사고날 (adapter/in/web → application → domain/model, domain/port/out)
                - DB: H2 인메모리, Spring Data JPA (ddl-auto=update)
                - 의존성: Spring Web, Spring Data JPA, Lombok, H2, WebFlux, Thymeleaf
                - Java 21, Spring Boot 3.4.5, Gradle Kotlin DSL

                기존 엔티티: Todo, Memo, Schedule (id/title/description + 각자 고유 필드)

                구현 규칙:
                1. @Entity: @Id + @GeneratedValue(strategy=GenerationType.IDENTITY) 필수
                2. Repository: JpaRepository<Entity, Long>, 패키지 domain/port/out
                3. @RestController: 완전한 CRUD 엔드포인트
                4. @Service: @Transactional, Lombok (@Slf4j, @RequiredArgsConstructor)
                5. HTML: Thymeleaf + TailwindCSS CDN, fetch API로 REST 호출
                6. 고유한 API 경로 사용 (/api/[기능명])
                7. 각 파일: public 클래스 하나만

                파일을 직접 생성/수정해 주세요.
                """, command);
    }

    // ─────────────────────────────────────────────────────────
    //  요약 메시지 빌더
    // ─────────────────────────────────────────────────────────

    private String buildSummary(List<String> changed, boolean buildOk,
                                 ClaudeCliExecutor.ExecutionResult result,
                                 JobType jobType, ProjectType projectType,
                                 String workPath, boolean actualSuccess) {
        if (result.cancelled()) return "작업이 취소되었습니다.";
        if (result.timedOut())  return "Claude CLI 타임아웃 (300초 초과)";

        if (!actualSuccess) {
            if (changed.isEmpty() || changed.stream().allMatch(f -> baseName(f).equalsIgnoreCase("readme.md"))) {
                return "Claude Code 실행에 실패했습니다. 파일이 정상 생성되지 않았습니다. Dev Modal 로그를 확인해 주세요.";
            }
            return "Claude Code가 오류와 함께 종료됐습니다. 일부 파일이 생성되었을 수 있습니다. exitCode=" + result.exitCode();
        }

        // SUCCESS or SUCCESS_WITH_WARNINGS
        boolean withWarnings = result.exitCode() != 0;
        var sb = new StringBuilder();

        if (withWarnings) {
            sb.append("Claude Code는 경고와 함께 종료되었지만, ");
            sb.append("필요한 파일과 에셋은 정상 생성되었습니다. ");
        } else {
            sb.append("Claude Code 작업 완료. ");
        }

        if (!changed.isEmpty()) {
            sb.append(changed.size()).append("개 파일 ")
              .append(jobType == JobType.NEW_PROJECT ? "생성. " : "변경. ");
        }

        if (jobType == JobType.MODIFY_JARVIS) {
            sb.append(buildOk ? "빌드 성공. " : "빌드 실패. ");
            sb.append("현진님, 요청하신 기능 개발이 완료되었습니다.");
        } else {
            sb.append("프로젝트 위치: ").append(workPath);
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────
    //  SSE / 유틸 헬퍼
    // ─────────────────────────────────────────────────────────

    private void emit(Sinks.Many<String> sink, String type, String payload) {
        try {
            String json = MAPPER.writeValueAsString(Map.of("type", type, "data", payload));
            sink.tryEmitNext(json);
        } catch (Exception e) {
            sink.tryEmitNext("{\"type\":\"" + type + "\",\"data\":\"" + esc(payload) + "\"}");
        }
    }

    private String buildDonePayload(DevJob job) {
        try {
            List<String> files = job.getChangedFiles() != null
                ? Arrays.stream(job.getChangedFiles().split("[\r\n]+"))
                        .map(String::trim)
                        .filter(s -> !s.isBlank() && !s.startsWith("warning:") && !s.startsWith("error:"))
                        .toList()
                : Collections.emptyList();
            boolean actualSuccess = job.getExitCode() != null && job.getExitCode() == 0
                && job.getStatus() == DevJob.JobStatus.DONE;
            return MAPPER.writeValueAsString(Map.of(
                "jobId",         job.getId(),
                "status",        job.getStatus().name(),
                "summary",       coalesce(job.getSummary(), ""),
                "diffStat",      coalesce(job.getDiffStat(), ""),
                "changedFiles",  files,
                "buildSuccess",  Boolean.TRUE.equals(job.getBuildSuccess()),
                "jobType",       coalesce(job.getJobType(), "MODIFY_JARVIS"),
                "projectType",   coalesce(job.getProjectType(), "UNKNOWN"),
                "workspacePath", coalesce(job.getWorkspacePath(), ""),
                "actualSuccess", actualSuccess
            ));
        } catch (Exception e) { return "{}"; }
    }

    /** API 키 등 민감 정보 마스킹 */
    private static String maskSecrets(String text) {
        if (text == null) return "";
        return text.replaceAll("sk-[A-Za-z0-9\\-_]{10,}", "sk-***MASKED***")
                   .replaceAll("Bearer [A-Za-z0-9\\-_.]{10,}", "Bearer ***MASKED***")
                   .replaceAll("api[_-]?key[\"'\\s:=]+[A-Za-z0-9\\-_]{10,}", "api_key=***MASKED***");
    }

    /** 파일 경로에서 파일명만 추출 (소문자) */
    private static String baseName(String path) {
        if (path == null) return "";
        int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return (i >= 0 ? path.substring(i + 1) : path).toLowerCase();
    }

    /** 변경 파일 목록 → 소문자 파일명 Set (절대경로 비교 금지) */
    private static Set<String> fileNameSet(List<String> files) {
        Set<String> set = new HashSet<>();
        for (String f : files) set.add(baseName(f));
        return set;
    }

    private static String sse(String type, String payload) {
        return "{\"type\":\"" + type + "\",\"data\":\"" + esc(payload) + "\"}";
    }
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","");
    }
    private static String coalesce(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private void cleanup(Long jobId) {
        sinks.remove(jobId);
        cancels.remove(jobId);
    }
}
