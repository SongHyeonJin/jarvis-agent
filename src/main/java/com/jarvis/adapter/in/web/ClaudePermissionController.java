package com.jarvis.adapter.in.web;

import com.jarvis.application.ClaudePermissionService;
import com.jarvis.domain.model.ClaudePermissionEvent;
import com.jarvis.domain.port.in.ClaudePermissionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/claude-permissions")
@RequiredArgsConstructor
public class ClaudePermissionController {

    private final ClaudePermissionUseCase permissionUseCase;
    private final ClaudePermissionService permissionService;

    /** Claude Code 훅에서 권한 이벤트 수신 */
    @PostMapping
    public ResponseEntity<ClaudePermissionEvent> receive(@RequestBody Map<String, String> body) {
        ClaudePermissionEvent event = permissionUseCase.receiveEvent(
                body.getOrDefault("title", "권한 요청"),
                body.getOrDefault("message", ""),
                body.getOrDefault("hookEventName", ""),
                body.getOrDefault("sessionId", ""),
                body.getOrDefault("toolName", "")
        );
        return ResponseEntity.ok(event);
    }

    /** 최근 이벤트 목록 */
    @GetMapping
    public List<ClaudePermissionEvent> listRecent() {
        return permissionUseCase.listRecent();
    }

    /** 미확인 이벤트 목록 */
    @GetMapping("/unread")
    public List<ClaudePermissionEvent> listUnread() {
        return permissionUseCase.listUnacknowledged();
    }

    /** 개별 확인 */
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable Long id) {
        permissionUseCase.acknowledge(id);
        return ResponseEntity.ok().build();
    }

    /** 전체 확인 */
    @PostMapping("/acknowledge-all")
    public ResponseEntity<Void> acknowledgeAll() {
        permissionUseCase.acknowledgeAll();
        return ResponseEntity.ok().build();
    }

    /** SSE 실시간 스트림 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ClaudePermissionEvent> stream() {
        return permissionService.stream();
    }
}
