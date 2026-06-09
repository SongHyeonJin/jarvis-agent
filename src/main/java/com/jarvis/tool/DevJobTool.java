package com.jarvis.tool;

import com.jarvis.application.DevJobService;
import com.jarvis.application.GitService;
import com.jarvis.domain.model.DevJob;
import com.jarvis.domain.port.out.DevJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DevJobTool implements ToolProvider {

    private static final int MAX_FILE_BYTES = 12_000;

    private final DevJobService     devJobService;
    private final DevJobRepository  jobRepo;
    private final GitService        gitService;

    @Override
    public List<ToolFunction> getToolFunctions() {
        return List.of(
                getRecentDevJobs(),
                getCurrentGitStatus(),
                modifyRecentProject(),
                readWorkspaceFile()
        );
    }

    // ──────────────────────────────────────────────────────────
    //  Tool 1: 최근 Dev 잡 목록
    // ──────────────────────────────────────────────────────────

    private ToolFunction getRecentDevJobs() {
        return new ToolFunction() {
            @Override public String name() { return "get_recent_dev_jobs"; }
            @Override public String description() {
                return "최근 Dev Agent 작업 목록을 반환합니다. " +
                       "현진님이 방금 만든 프로젝트가 무엇인지 확인하거나, " +
                       "진행 중인 작업 상태를 알고 싶을 때 사용하세요.";
            }
            @Override public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of(), "required", List.of());
            }
            @Override public String execute(Map<String, Object> args) {
                List<DevJob> jobs = devJobService.listJobs();
                if (jobs.isEmpty()) return "최근 Dev 작업이 없습니다.";
                StringBuilder sb = new StringBuilder("최근 Dev Agent 작업:\n");
                for (DevJob job : jobs) {
                    sb.append(String.format("- Job #%d [%s] %s\n", job.getId(), job.getStatus(), job.getCommand()));
                    if (job.getWorkspacePath() != null && !job.getWorkspacePath().isBlank()) {
                        sb.append(String.format("  위치: %s (유형: %s)\n",
                                job.getWorkspacePath(),
                                job.getProjectType() != null ? job.getProjectType() : "UNKNOWN"));
                    }
                    if (job.getSummary() != null && !job.getSummary().isBlank()) {
                        sb.append(String.format("  요약: %s\n", job.getSummary()));
                    }
                    if (job.getChangedFiles() != null && !job.getChangedFiles().isBlank()) {
                        String files = job.getChangedFiles().replace("\n", ", ");
                        if (files.length() > 120) files = files.substring(0, 120) + "...";
                        sb.append(String.format("  파일: %s\n", files));
                    }
                }
                return sb.toString();
            }
        };
    }

    // ──────────────────────────────────────────────────────────
    //  Tool 2: JARVIS 프로젝트 Git 상태
    // ──────────────────────────────────────────────────────────

    private ToolFunction getCurrentGitStatus() {
        return new ToolFunction() {
            @Override public String name() { return "get_current_git_status"; }
            @Override public String description() {
                return "현재 JARVIS 프로젝트의 Git 변경 상태(diff --stat)를 반환합니다. " +
                       "최근 코드가 어떻게 변경됐는지 알고 싶을 때 사용하세요.";
            }
            @Override public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of(), "required", List.of());
            }
            @Override public String execute(Map<String, Object> args) {
                if (!gitService.isGitRepo()) return "Git 저장소가 아닙니다.";
                String stat = gitService.getDiffStat();
                String status = gitService.getStatus();
                return stat.isBlank() && status.isBlank()
                        ? "변경 사항 없음"
                        : "Git 상태:\n" + status + "\n\nDiff Stat:\n" + stat;
            }
        };
    }

    // ──────────────────────────────────────────────────────────
    //  Tool 3: 최근 프로젝트 수정 요청 (핵심 신규 기능)
    // ──────────────────────────────────────────────────────────

    private ToolFunction modifyRecentProject() {
        return new ToolFunction() {
            @Override public String name() { return "modify_recent_project"; }
            @Override public String description() {
                return "최근에 Dev Agent가 생성·수정한 프로젝트를 즉시 수정합니다. " +
                       "현진님이 '이거 바꿔줘', '색 수정해줘', '버튼 추가해줘' 등 " +
                       "방금 만든 프로젝트에 대한 수정을 요청할 때 반드시 이 도구를 사용하세요. " +
                       "job_id를 지정하면 해당 프로젝트, 생략하면 가장 최근 완료 프로젝트를 수정합니다.";
            }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "instruction", Map.of(
                            "type", "string",
                            "description", "수정 내용을 구체적으로 설명하세요. 예: '팝업 배경색을 파란색으로 바꿔줘', '로그인 버튼 크기를 키워줘'"
                        ),
                        "job_id", Map.of(
                            "type", "integer",
                            "description", "(선택) 수정할 특정 Dev Job ID. 생략하면 가장 최근 완료된 외부 프로젝트를 수정합니다."
                        )
                    ),
                    "required", List.of("instruction")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                String instruction = String.valueOf(args.getOrDefault("instruction", "")).trim();
                if (instruction.isBlank()) return "오류: 수정 내용(instruction)을 입력해주세요.";

                // 타깃 잡 결정
                DevJob target = null;
                Object jobIdObj = args.get("job_id");
                if (jobIdObj != null) {
                    try {
                        long jobId = ((Number) jobIdObj).longValue();
                        target = jobRepo.findById(jobId).orElse(null);
                    } catch (Exception ignored) {}
                }
                if (target == null) {
                    target = jobRepo.findLastExternalCompleted().orElse(null);
                }
                if (target == null) {
                    return "수정할 완료된 프로젝트가 없습니다. 먼저 프로젝트를 생성해주세요.";
                }
                if (target.getWorkspacePath() == null || target.getWorkspacePath().isBlank()) {
                    return "오류: 대상 프로젝트의 워크스페이스 경로를 알 수 없습니다.";
                }

                DevJob modJob = devJobService.submitModificationJob(
                        instruction,
                        target.getWorkspacePath(),
                        target.getProjectType()
                );

                return String.format(
                        "수정 작업을 Job #%d으로 제출했습니다.\n" +
                        "대상 프로젝트: %s\n" +
                        "수정 내용: %s\n\n" +
                        "Dev 패널에서 진행 상황을 확인해주세요.",
                        modJob.getId(),
                        target.getWorkspacePath(),
                        instruction
                );
            }
        };
    }

    // ──────────────────────────────────────────────────────────
    //  Tool 4: 워크스페이스 파일 읽기
    // ──────────────────────────────────────────────────────────

    private ToolFunction readWorkspaceFile() {
        return new ToolFunction() {
            @Override public String name() { return "read_workspace_file"; }
            @Override public String description() {
                return "Dev Agent가 생성한 프로젝트의 파일 내용을 읽습니다. " +
                       "현진님이 '어떤 코드야?', '파일 내용 보여줘', '현재 어떻게 구현됐어?' 같이 " +
                       "생성된 코드의 구체적 내용을 물어볼 때 사용하세요. " +
                       "file_path를 생략하면 파일 목록을 반환합니다.";
            }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "workspace_path", Map.of(
                            "type", "string",
                            "description", "프로젝트 절대 경로. get_recent_dev_jobs로 확인한 '위치' 값을 사용하세요."
                        ),
                        "file_path", Map.of(
                            "type", "string",
                            "description", "(선택) 읽을 파일의 상대 경로. 예: 'content.js', 'src/App.js'. 생략 시 파일 목록 반환."
                        )
                    ),
                    "required", List.of("workspace_path")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                String wsPath = String.valueOf(args.getOrDefault("workspace_path", "")).trim();
                String filePath = String.valueOf(args.getOrDefault("file_path", "")).trim();

                if (wsPath.isBlank()) return "오류: workspace_path가 필요합니다.";

                // 보안: 허용된 경로 내에서만 읽기
                Path root = Paths.get(wsPath).normalize().toAbsolutePath();
                if (!isAllowedPath(root)) {
                    return "오류: 허용되지 않은 경로입니다.";
                }

                if (filePath.isBlank()) {
                    return listFiles(root);
                }

                Path target = root.resolve(filePath).normalize();
                if (!target.startsWith(root)) {
                    return "오류: 디렉토리 경계를 벗어나는 경로입니다.";
                }

                return readFile(target);
            }
        };
    }

    private boolean isAllowedPath(Path p) {
        String s = p.toString().replace('\\', '/');
        return s.startsWith("d:/jarvis-workspaces") || s.startsWith("d:/jarvis-agent")
            || s.startsWith("D:/jarvis-workspaces") || s.startsWith("D:/jarvis-agent");
    }

    private String listFiles(Path root) {
        try {
            if (!Files.exists(root)) return "경로가 존재하지 않습니다: " + root;
            String list = Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.toString().replace('\\', '/').contains("/.git/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/node_modules/"))
                    .map(p -> "  " + root.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .collect(java.util.stream.Collectors.joining("\n"));
            return list.isBlank() ? "(파일 없음)" : "파일 목록:\n" + list;
        } catch (IOException e) {
            return "파일 목록 조회 실패: " + e.getMessage();
        }
    }

    private String readFile(Path target) {
        try {
            if (!Files.exists(target)) return "파일이 존재하지 않습니다: " + target.getFileName();
            byte[] bytes = Files.readAllBytes(target);
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (bytes.length > MAX_FILE_BYTES) {
                content = content.substring(0, MAX_FILE_BYTES) + "\n... (이하 생략 — 파일 크기 초과)";
            }
            return String.format("파일: %s\n```\n%s\n```", target.getFileName(), content);
        } catch (IOException e) {
            return "파일 읽기 실패: " + e.getMessage();
        }
    }
}
