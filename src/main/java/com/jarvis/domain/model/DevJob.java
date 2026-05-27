package com.jarvis.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dev_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자 자연어 명령 */
    @Column(nullable = false, length = 3000)
    private String command;

    /** Claude Code에 전달할 전체 프롬프트 */
    @Lob
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    /** Claude CLI 전체 출력 로그 */
    @Lob
    private String logOutput;

    /** git diff 결과 */
    @Lob
    private String gitDiff;

    /** git diff --stat 결과 */
    @Column(length = 5000)
    private String diffStat;

    /** 완료 후 요약 메시지 */
    @Column(length = 3000)
    private String summary;

    /** 처리된 파일 목록 (줄바꿈 구분) */
    @Column(length = 5000)
    private String changedFiles;

    /** Gradle 빌드 성공 여부 */
    private Boolean buildSuccess;

    /** Claude CLI 종료 코드 */
    private Integer exitCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    public enum JobStatus {
        PENDING, RUNNING, DONE, FAILED, CANCELLED
    }

    // ── 상태 전이 메서드 ──────────────────────────────────────

    public void markRunning() {
        this.status = JobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void markDone(String log, String diff, String stat,
                         String changed, String summary, Boolean buildOk, int exitCode) {
        this.status = (exitCode == 0) ? JobStatus.DONE : JobStatus.FAILED;
        this.logOutput    = log;
        this.gitDiff      = diff;
        this.diffStat     = stat;
        this.changedFiles = changed;
        this.summary      = summary;
        this.buildSuccess = buildOk;
        this.exitCode     = exitCode;
        this.completedAt  = LocalDateTime.now();
    }

    public void markFailed(String log, String error) {
        this.status       = JobStatus.FAILED;
        this.logOutput    = log;
        this.summary      = error;
        this.completedAt  = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status      = JobStatus.CANCELLED;
        this.summary     = "사용자가 작업을 취소하였습니다.";
        this.completedAt = LocalDateTime.now();
    }

    public boolean isTerminal() {
        return status == JobStatus.DONE
            || status == JobStatus.FAILED
            || status == JobStatus.CANCELLED;
    }
}
