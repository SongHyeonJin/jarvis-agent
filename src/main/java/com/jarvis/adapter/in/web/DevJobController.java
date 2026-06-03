package com.jarvis.adapter.in.web;

import com.jarvis.application.DevJobService;
import com.jarvis.domain.model.DevJob;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dev/jobs")
@RequiredArgsConstructor
public class DevJobController {

    private final DevJobService jobService;

    /** 새 Dev Job 제출 — 즉시 jobId 반환 */
    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@RequestBody Map<String, String> body) {
        String command = body.get("command");
        if (command == null || command.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "command is required"));
        }
        DevJob job = jobService.submitJob(command.trim());
        return ResponseEntity.ok(Map.of(
            "jobId",         job.getId(),
            "status",        job.getStatus().name(),
            "command",       job.getCommand(),
            "jobType",       job.getJobType()        != null ? job.getJobType()        : "MODIFY_JARVIS",
            "projectType",   job.getProjectType()    != null ? job.getProjectType()    : "UNKNOWN",
            "workspacePath", job.getWorkspacePath()  != null ? job.getWorkspacePath()  : ""
        ));
    }

    /** SSE 실시간 로그 스트림 */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@PathVariable Long id) {
        return jobService.streamJob(id);
    }

    /** 잡 취소 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable Long id) {
        jobService.cancelJob(id);
        return ResponseEntity.ok(Map.of("jobId", id, "cancelled", true));
    }

    /** 잡 상세 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<DevJob> getJob(@PathVariable Long id) {
        return jobService.getJob(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 최근 잡 목록 */
    @GetMapping
    public List<DevJob> listJobs() {
        return jobService.listJobs();
    }
}
