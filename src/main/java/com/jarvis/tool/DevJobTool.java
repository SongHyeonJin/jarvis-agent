package com.jarvis.tool;

import com.jarvis.application.DevJobService;
import com.jarvis.application.GitService;
import com.jarvis.domain.model.DevJob;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI가 최근 DevJob 이력과 현재 git 상태를 조회할 수 있는 Tool.
 * '방금 뭐 바뀐 거야?', '최근 작업 보여줘' 같은 질문에 응답한다.
 */
@Component
@RequiredArgsConstructor
public class DevJobTool implements ToolProvider {

    private final DevJobService devJobService;
    private final GitService    gitService;

    @Tool(description = "최근 개발 작업(DevJob) 이력을 조회합니다. " +
            "'방금 뭐 바뀐 거야?', '최근 작업 뭐야?', '마지막으로 뭐 만들었어?' 같은 질문에 사용하세요.")
    public String getRecentDevJobs(@ToolParam(description = "조회할 작업 수 (기본값 5, 최대 20)", required = false) Integer limit) {
        int n = limit != null ? Math.min(limit, 20) : 5;

        List<DevJob> jobs = devJobService.listJobs().stream()
                .limit(n)
                .toList();

        if (jobs.isEmpty()) return "최근 개발 작업 이력이 없습니다.";

        return jobs.stream().map(j -> {
            String time = j.getCompletedAt() != null
                    ? j.getCompletedAt().toString()
                    : (j.getCreatedAt() != null ? j.getCreatedAt().toString() : "-");
            String status = j.getStatus() != null ? j.getStatus().name() : "?";
            String summary = j.getSummary() != null ? j.getSummary() : j.getCommand();
            String files = j.getChangedFiles() != null && !j.getChangedFiles().isBlank()
                    ? "\n  변경 파일: " + j.getChangedFiles().replace("\n", ", ")
                    : "";
            return String.format("[%d] %s | %s\n  %s%s",
                    j.getId(), status, time, summary, files);
        }).collect(Collectors.joining("\n\n"));
    }

    @Tool(description = "현재 git 저장소의 변경 상태(status, diff --stat, 브랜치)를 조회합니다. " +
            "'지금 뭐가 바뀌어 있어?', '현재 git 상태 알려줘' 같은 질문에 사용하세요.")
    public String getCurrentGitStatus() {
        String branch = gitService.getCurrentBranch();
        String status = gitService.getStatus();
        String stat   = gitService.getDiffStat();

        StringBuilder sb = new StringBuilder();
        sb.append("브랜치: ").append(branch).append('\n');

        if (status == null || status.isBlank()) {
            sb.append("변경된 파일 없음 (working tree clean)");
        } else {
            sb.append("변경 파일:\n").append(status);
            if (stat != null && !stat.isBlank()) {
                sb.append("\n\ndiff --stat:\n").append(stat);
            }
        }
        return sb.toString();
    }
}
