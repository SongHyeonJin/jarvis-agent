package com.jarvis.application;

import com.jarvis.domain.model.ClaudePermissionEvent;
import com.jarvis.domain.port.in.ClaudePermissionUseCase;
import com.jarvis.domain.port.out.ClaudePermissionEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClaudePermissionService implements ClaudePermissionUseCase {

    private final ClaudePermissionEventRepository eventRepository;

    private final Sinks.Many<ClaudePermissionEvent> sink =
            Sinks.many().multicast().onBackpressureBuffer();

    @Override
    @Transactional
    public ClaudePermissionEvent receiveEvent(String title, String message,
                                               String hookEventName, String sessionId,
                                               String toolName) {
        ClaudePermissionEvent event = ClaudePermissionEvent.builder()
                .title(title)
                .message(message)
                .hookEventName(hookEventName)
                .sessionId(sessionId)
                .toolName(toolName)
                .acknowledged(false)
                .createdAt(LocalDateTime.now())
                .build();
        event = eventRepository.save(event);

        sink.tryEmitNext(event);
        sendWindowsToast(title, message);
        log.info("[ClaudePermission] 이벤트 수신: title={} tool={}", title, toolName);
        return event;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaudePermissionEvent> listRecent() {
        return eventRepository.findTop20ByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaudePermissionEvent> listUnacknowledged() {
        return eventRepository.findByAcknowledgedFalseOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public void acknowledge(Long id) {
        eventRepository.findById(id).ifPresent(e -> {
            e.setAcknowledged(true);
            eventRepository.save(e);
        });
    }

    @Override
    @Transactional
    public void acknowledgeAll() {
        List<ClaudePermissionEvent> unread = eventRepository.findByAcknowledgedFalseOrderByCreatedAtDesc();
        unread.forEach(e -> e.setAcknowledged(true));
        eventRepository.saveAll(unread);
    }

    public Flux<ClaudePermissionEvent> stream() {
        return sink.asFlux();
    }

    private void sendWindowsToast(String title, String message) {
        try {
            String safe = message == null ? "" : message.replace("'", "\\'").replace("\n", " ");
            String script = String.format(
                "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType=WindowsRuntime] | Out-Null; " +
                "$xml = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02); " +
                "$xml.GetElementsByTagName('text')[0].AppendChild($xml.CreateTextNode('JARVIS — %s')) | Out-Null; " +
                "$xml.GetElementsByTagName('text')[1].AppendChild($xml.CreateTextNode('%s')) | Out-Null; " +
                "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('JARVIS').Show([Windows.UI.Notifications.ToastNotification]::new($xml))",
                title == null ? "권한 요청" : title.replace("'", "\\'"),
                safe
            );
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NonInteractive", "-NoProfile", "-Command", script);
            pb.redirectErrorStream(true);
            pb.start();
        } catch (Exception e) {
            log.warn("[ClaudePermission] Toast 알림 실패 (무시됨): {}", e.getMessage());
        }
    }
}
