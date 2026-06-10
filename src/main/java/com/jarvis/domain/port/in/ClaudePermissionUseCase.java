package com.jarvis.domain.port.in;

import com.jarvis.domain.model.ClaudePermissionEvent;
import reactor.core.publisher.Flux;

public interface ClaudePermissionUseCase {
    void publish(ClaudePermissionEvent event);
    Flux<ClaudePermissionEvent> stream();
}
