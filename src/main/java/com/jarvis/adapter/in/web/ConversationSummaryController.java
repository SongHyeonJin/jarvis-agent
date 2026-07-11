package com.jarvis.adapter.in.web;

import com.jarvis.domain.model.ConversationSummary;
import com.jarvis.domain.port.in.ConversationSummaryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversation-summaries")
@RequiredArgsConstructor
public class ConversationSummaryController {

    private final ConversationSummaryUseCase summaryUseCase;

    @PostMapping("/{conversationId}")
    public ResponseEntity<ConversationSummary> summarize(@PathVariable Long conversationId) {
        ConversationSummary result = summaryUseCase.summarize(conversationId);
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ConversationSummary>> search(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(summaryUseCase.search(q));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<ConversationSummary>> recent(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(summaryUseCase.getRecent(limit));
    }
}
