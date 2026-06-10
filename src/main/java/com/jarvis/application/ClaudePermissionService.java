package com.jarvis.application;

import com.jarvis.domain.model.ClaudePermissionEvent;
import com.jarvis.domain.port.in.ClaudePermissionUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Service
public class ClaudePermissionService implements ClaudePermissionUseCase {

    private final Sinks.Many<ClaudePermissionEvent> sink =
            Sinks.many().multicast().onBackpressureBuffer();

    @Override
    public void publish(ClaudePermissionEvent event) {
        log.info("Permission request: tool={}, session={}", event.getToolName(), event.getSessionId());
        sink.tryEmitNext(event);
    }

    @Override
    public Flux<ClaudePermissionEvent> stream() {
        return sink.asFlux();
    }
}
