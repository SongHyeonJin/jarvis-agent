package com.jarvis.adapter.in.web;

import com.jarvis.domain.model.ClaudePermissionEvent;
import com.jarvis.domain.port.in.ClaudePermissionUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/claude-permissions")
@RequiredArgsConstructor
public class ClaudePermissionController {

    private final ClaudePermissionUseCase claudePermissionUseCase;

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody Map<String, String> body) {
        ClaudePermissionEvent event = ClaudePermissionEvent.builder()
                .toolName(body.getOrDefault("tool_name", "unknown"))
                .toolInput(body.getOrDefault("tool_input", "{}"))
                .sessionId(body.getOrDefault("session_id", ""))
                .build();
        claudePermissionUseCase.publish(event);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream() {
        return claudePermissionUseCase.stream()
                .map(e -> "data: {\"toolName\":\"" + esc(e.getToolName()) +
                          "\",\"toolInput\":" + e.getToolInput() +
                          ",\"sessionId\":\"" + esc(e.getSessionId()) +
                          "\",\"requestedAt\":\"" + e.getRequestedAt() + "\"}\n\n");
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
