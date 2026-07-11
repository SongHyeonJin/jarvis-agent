package com.jarvis.adapter.in.web;

import com.jarvis.domain.port.in.BrowserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/browser-commands")
@RequiredArgsConstructor
public class BrowserCommandController {

    private final BrowserUseCase browserUseCase;

    @GetMapping("/youtube")
    public ResponseEntity<Map<String, Object>> pollYoutubeCommand() {
        Map<String, Object> cmd = browserUseCase.pollYoutubeCommand();
        if (cmd == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(cmd);
    }
}
