package com.jarvis.adapter.in.web;

import com.jarvis.application.CardEventEmitter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/card-events")
@RequiredArgsConstructor
public class CardEventController {

    private final CardEventEmitter cardEventEmitter;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return cardEventEmitter.subscribe();
    }

    @PostMapping("/wake")
    public ResponseEntity<java.util.Map<String, Integer>> wake() {
        int clients = cardEventEmitter.broadcastWake();
        return ResponseEntity.ok(java.util.Map.of("clients", clients));
    }
}
