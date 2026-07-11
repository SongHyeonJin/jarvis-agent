package com.jarvis.application;

import com.jarvis.domain.port.in.BrowserUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class BrowserService implements BrowserUseCase {

    private final AtomicReference<Map<String, Object>> pendingYoutubeCommand = new AtomicReference<>();

    @Override
    public String openUrl(String url) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "", url);
            pb.inheritIO();
            pb.start();
            log.info("[Browser] Opened: {}", url);
            return "브라우저에서 열었습니다: " + url;
        } catch (Exception e) {
            log.error("[Browser] Failed to open URL: {} — {}", url, e.getMessage(), e);
            return "URL을 열지 못했습니다: " + e.getMessage();
        }
    }

    @Override
    public String controlYoutube(String action, int n) {
        pendingYoutubeCommand.set(Map.of("action", action, "n", n));
        log.info("[Browser] YouTube command queued: action={}, n={}", action, n);
        return "YouTube " + n + "번째 영상 재생 명령을 전송했습니다.";
    }

    @Override
    public Map<String, Object> pollYoutubeCommand() {
        return pendingYoutubeCommand.getAndSet(null);
    }
}
