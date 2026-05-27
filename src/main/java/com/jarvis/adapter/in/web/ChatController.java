package com.jarvis.adapter.in.web;

import com.jarvis.domain.model.Conversation;
import com.jarvis.domain.port.in.ChatUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ChatUseCase chatUseCase;

    record ConvSummary(Long id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    @PostMapping
    public ResponseEntity<ConvSummary> create(@RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : null;
        Conversation c = chatUseCase.createConversation(title);
        return ResponseEntity.ok(new ConvSummary(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()));
    }

    @GetMapping
    public ResponseEntity<List<ConvSummary>> listAll() {
        return ResponseEntity.ok(chatUseCase.getAllConversations().stream()
                .map(c -> new ConvSummary(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()))
                .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Conversation> get(@PathVariable Long id) {
        return ResponseEntity.ok(chatUseCase.getConversation(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        chatUseCase.deleteConversation(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<Map<String, String>> sendMessage(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String response = chatUseCase.chat(id, body.get("message"));
        return ResponseEntity.ok(Map.of("response", response));
    }

    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamMessage(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return chatUseCase.streamChat(id, body.get("message"));
    }
}
