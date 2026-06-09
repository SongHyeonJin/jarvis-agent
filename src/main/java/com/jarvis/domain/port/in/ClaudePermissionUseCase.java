package com.jarvis.domain.port.in;

import com.jarvis.domain.model.ClaudePermissionEvent;

import java.util.List;

public interface ClaudePermissionUseCase {

    ClaudePermissionEvent receiveEvent(String title, String message,
                                       String hookEventName, String sessionId, String toolName);

    List<ClaudePermissionEvent> listRecent();

    List<ClaudePermissionEvent> listUnacknowledged();

    void acknowledge(Long id);

    void acknowledgeAll();
}
