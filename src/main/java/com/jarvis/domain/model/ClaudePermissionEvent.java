package com.jarvis.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ClaudePermissionEvent {

    private final String toolName;
    private final String toolInput;
    private final String sessionId;
    @Builder.Default
    private final LocalDateTime requestedAt = LocalDateTime.now();
}
