package com.jarvis.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.domain.model.DevJob;
import com.jarvis.domain.port.out.DevJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dev Agent 핵심 오케스트레이터.
 *
 * 흐름:
 * 1. submitJob()으로 DevJob DB 레코드 생성 → 비동기 실행 시작
 * 2. ClaudeCliExecutor가 팝업 터미널에서 claude CLI 실행
 * 3. 로그 청크를 Sinks.Many로 방출 → SSE로 브라우저에 실시간 전달
 * 4. 완료 후 Git diff / Gradle 빌드 수행 → 결과 저장
 * 5. cancelJob()으로 AtomicBoolean 설정 → 프로세스 graceful 중단
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DevJobService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DevJobRepository     jobRepo;
    private final ClaudeCliExecutor    executor;
    private final GitService           gitService;

    @Value("${app.project-root:d:/jarvis-agent}")
    private String projectRoot;

    /** 실행 중인 잡의 SSE 싱크 (replay(500) — 늦게 구독해도 과거 이벤트 수신) */
    private final ConcurrentHashMap<Long, Sinks.Many<String>> sinks   = new ConcurrentHashMap<>();
    /** 취소 신호 */
    private final ConcurrentHashMap<Long, AtomicBoolean>      cancels = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────
    //  공개 API
    // ─────────────────────────────────────────────────────────

    /** 새 Dev Job 제출 — 즉시 jobId 반환, 실행은 비동기 */
    public DevJob submitJob(String command) {
        String prompt = buildPrompt(command);
        DevJob job = DevJob.builder()
                .command(command)
                .prompt(prompt)
                .status(DevJob.JobStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        job = jobRepo.save(job);
        Long jobId = job.getId();

        // replay(500): 최근 500개 이벤트를 늦은 구독자에게 재전송
        Sinks.Many<String> sink = Sinks.many().replay().limit(500);
        sinks.put(jobId, sink);
        AtomicBoolean cancel = new AtomicBoolean(false);
        cancels.put(jobId, cancel);

        final Long id = jobId;
        Schedulers.boundedElastic().schedule(() -> runJob(id, sink, cancel));

        log.info("[DevJobService] Job #{} 제출: {}", jobId, command);
        return job;
    }

    /** SSE Flux 반환 — 실행 중이면 실시간, 완료된 잡이면 저장된 로그 재생 */
    public Flux<String> streamJob(Long jobId) {
        Sinks.Many<String> sink = sinks.get(jobId);
        if (sink != null) return sink.asFlux();

        // 이미 완료된 잡 — 저장된 데이터를 SSE 포맷으로 한 번에 방출
        return jobRepo.findById(jobId)
                .map(job -> Flux.concat(
                    Flux.just(sse("status", job.getStatus().name())),
                    Flux.just(sse("log",    coalesce(job.getLogOutput(), "(로그 없음)"))),
                    Flux.just(sse("done",   buildDonePayload(job))),
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

    private void runJob(Long jobId, Sinks.Many<String> sink, AtomicBoolean cancel) {
        // RUNNING으로 전환
        DevJob job = jobRepo.findById(jobId).orElseThrow();
        job.markRunning();
        jobRepo.save(job);
        emit(sink, "status", "RUNNING");
        emit(sink, "log",
             "━━━ JARVIS Dev Agent 시작 ━━━\n" +
             "명령: " + job.getCommand() + "\n" +
             "프로젝트: " + projectRoot + "\n");

        // Claude CLI 실행
        var fullLog = new StringBuilder();
        ClaudeCliExecutor.ExecutionResult result = executor.execute(
            job.getPrompt(),
            chunk -> {                          // 파싱된 로그
                fullLog.append(chunk).append('\n');
                emit(sink, "log", chunk);
            },
            null,                               // raw 로그 (사용 안 함)
            cancel
        );

        if (cancel.get()) {
            job = jobRepo.findById(jobId).orElseThrow();
            job.markCancelled();
            jobRepo.save(job);
            emit(sink, "status", "CANCELLED");
            emit(sink, "log", "\n⚠ 작업이 취소되었습니다.");
            emit(sink, "done", "{\"cancelled\":true}");
            sink.tryEmitComplete();
            sinks.remove(jobId);
            cancels.remove(jobId);
            return;
        }

        // Git diff
        emit(sink, "log", "\n━━━ Git 변경 사항 확인 중... ━━━");
        String diffStat     = gitService.getDiffStat();
        String diff         = gitService.getDiff();
        List<String> changed = gitService.getChangedFiles();
        if (!diffStat.isBlank()) emit(sink, "log", diffStat);

        // Gradle 빌드
        emit(sink, "log", "\n━━━ Gradle 빌드 검증 중... ━━━");
        boolean buildOk = runGradleBuild(sink);

        // 결과 저장
        String summary    = buildSummary(changed, buildOk, result);
        String changedStr = String.join("\n", changed);

        job = jobRepo.findById(jobId).orElseThrow();
        job.markDone(fullLog.toString(), diff, diffStat, changedStr, summary,
                     buildOk, result.success() ? 0 : 1);
        jobRepo.save(job);

        emit(sink, "status", result.success() ? "DONE" : "FAILED");
        emit(sink, "log", "\n━━━ 완료 ━━━\n" + summary);
        emit(sink, "done", buildDonePayload(job));
        sink.tryEmitComplete();
        sinks.remove(jobId);
        cancels.remove(jobId);
        log.info("[DevJobService] Job #{} 완료 (build={})", jobId, buildOk);
    }

    // ─────────────────────────────────────────────────────────
    //  Gradle 빌드
    // ─────────────────────────────────────────────────────────

    private boolean runGradleBuild(Sinks.Many<String> sink) {
        // Gradle 데몬 락 파일이 있으면 빌드 건너뜀 (서버가 이미 동일 프로젝트 실행 중)
        File lockDir = new File(System.getProperty("user.home"), ".gradle/daemon");
        boolean daemonRunning = lockDir.exists() && lockDir.listFiles() != null
            && java.util.Arrays.stream(lockDir.listFiles(File::isDirectory)).anyMatch(d -> {
                File[] locks = d.listFiles((f, n) -> n.endsWith(".lock"));
                return locks != null && locks.length > 0;
            });
        if (daemonRunning) {
            emit(sink, "log", "ℹ 서버 실행 중 — Gradle 빌드 검증 생략 (재시작으로 확인 가능)");
            return true;
        }

        try {
            boolean isWin  = System.getProperty("os.name", "").toLowerCase().contains("win");
            File    projDir = new File(projectRoot);
            String  gradle  = resolveGradle(projDir, isWin);

            List<String> cmd = isWin
                ? List.of("cmd", "/c", gradle, "compileJava", "--no-daemon", "--quiet")
                : List.of(gradle, "compileJava", "--no-daemon", "--quiet");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(projDir);
            pb.redirectErrorStream(true);

            Process proc = pb.start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.isBlank()) emit(sink, "log", line);
                }
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
    //  SSE 헬퍼
    // ─────────────────────────────────────────────────────────

    /**
     * Sinks에 JSON 이벤트를 방출.
     * Spring WebFlux TEXT_EVENT_STREAM 응답이 자동으로 "data: ...\n\n" 래핑하므로
     * 여기서는 순수 JSON만 방출한다 (이중 data: 방지).
     */
    private void emit(Sinks.Many<String> sink, String type, String payload) {
        try {
            String json = MAPPER.writeValueAsString(Map.of("type", type, "data", payload));
            sink.tryEmitNext(json);
        } catch (Exception e) {
            sink.tryEmitNext("{\"type\":\"" + type + "\",\"data\":\"" + esc(payload) + "\"}");
        }
    }

    /** 완료 이벤트 JSON 페이로드 */
    private String buildDonePayload(DevJob job) {
        try {
            List<String> files = job.getChangedFiles() != null
                ? Arrays.stream(job.getChangedFiles().split("[\r\n]+"))
                        .filter(s -> !s.isBlank()).toList()
                : Collections.emptyList();
            return MAPPER.writeValueAsString(Map.of(
                "jobId",        job.getId(),
                "status",       job.getStatus().name(),
                "summary",      coalesce(job.getSummary(), ""),
                "diffStat",     coalesce(job.getDiffStat(), ""),
                "changedFiles", files,
                "buildSuccess", Boolean.TRUE.equals(job.getBuildSuccess())
            ));
        } catch (Exception e) { return "{}"; }
    }

    // 완료된 잡 재생용 — Spring SSE 래핑 없이 직접 전송되므로 data: 포맷 유지
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

    // ─────────────────────────────────────────────────────────
    //  프롬프트 & 요약 빌더
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

    private String buildSummary(List<String> changed, boolean buildOk,
                                 ClaudeCliExecutor.ExecutionResult result) {
        if (result.cancelled()) return "작업이 취소되었습니다.";
        if (result.timedOut())  return "Claude CLI 타임아웃 (300초 초과)";
        var sb = new StringBuilder("Claude Code 작업 완료. ");
        if (!changed.isEmpty()) sb.append(changed.size()).append("개 파일 변경. ");
        sb.append(buildOk ? "빌드 성공. " : "빌드 실패. ");
        sb.append("현진님, 요청하신 기능 개발이 완료되었습니다.");
        return sb.toString();
    }
}
