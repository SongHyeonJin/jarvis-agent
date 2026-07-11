package com.jarvis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.jarvis.domain.model.ChatMessage;
import com.jarvis.domain.model.Conversation;
import com.jarvis.domain.model.ConversationSummary;
import com.jarvis.domain.port.in.ConversationSummaryUseCase;
import com.jarvis.domain.port.out.ConversationRepository;
import com.jarvis.domain.port.out.ConversationSummaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
@Slf4j
public class ConversationSummaryService implements ConversationSummaryUseCase {

    private static final String HAIKU_MODEL = "claude-haiku-4-5-20251001";
    private static final int MIN_MESSAGES_TO_SUMMARIZE = 2;

    private final ConversationRepository conversationRepository;
    private final ConversationSummaryRepository summaryRepository;
    private final WebClient anthropicWebClient;

    public ConversationSummaryService(
            ConversationRepository conversationRepository,
            ConversationSummaryRepository summaryRepository,
            @Qualifier("anthropicWebClient") WebClient anthropicWebClient) {
        this.conversationRepository = conversationRepository;
        this.summaryRepository = summaryRepository;
        this.anthropicWebClient = anthropicWebClient;
    }

    @Override
    @Transactional
    public ConversationSummary summarize(Long conversationId) {
        Optional<ConversationSummary> existing = summaryRepository.findByConversationId(conversationId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Conversation conv = conversationRepository.findByIdWithMessages(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다: " + conversationId));

        List<ChatMessage> messages = conv.getMessages();
        if (messages.size() < MIN_MESSAGES_TO_SUMMARIZE) {
            log.info("[Summary] conversationId={} 메시지 {}개 — 요약 불필요", conversationId, messages.size());
            return null;
        }

        String transcript = buildTranscript(messages);
        String aiResponse = callHaiku(transcript);

        String title = extractField(aiResponse, "제목");
        String summary = extractField(aiResponse, "요약");
        String keywords = extractField(aiResponse, "키워드");

        if (summary.isBlank()) {
            summary = aiResponse;
        }

        ConversationSummary cs = ConversationSummary.builder()
                .conversationId(conversationId)
                .title(title.isBlank() ? conv.getTitle() : title)
                .summary(summary)
                .keywords(keywords)
                .messageCount(messages.size())
                .conversationStartedAt(conv.getCreatedAt())
                .conversationEndedAt(conv.getUpdatedAt())
                .build();

        ConversationSummary saved = summaryRepository.save(cs);
        log.info("[Summary] conversationId={} 요약 저장 완료 ({}자)", conversationId, summary.length());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationSummary> search(String query) {
        if (query == null || query.isBlank()) {
            return summaryRepository.findAllRecent();
        }
        return summaryRepository.searchByKeyword(query.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationSummary> getRecent(int limit) {
        List<ConversationSummary> all = summaryRepository.findAllRecent();
        return all.size() <= limit ? all : all.subList(0, limit);
    }

    private String buildTranscript(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        int tokenBudget = 1500;
        for (ChatMessage msg : messages) {
            String role = msg.getRole().name().equals("USER") ? "사용자" : "자비스";
            String content = msg.getContent() != null ? msg.getContent() : "";
            if (content.length() > 300) {
                content = content.substring(0, 300) + "...";
            }
            sb.append(role).append(": ").append(content).append("\n");
            if (sb.length() > tokenBudget * 4) break;
        }
        return sb.toString();
    }

    private String callHaiku(String transcript) {
        String systemPrompt = """
                대화 내용을 분석해서 아래 형식으로 요약하세요. 간결하게.

                제목: (10자 이내 한 줄 제목)
                요약: (2~3문장으로 핵심 내용)
                키워드: (쉼표로 구분, 최대 5개)""";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", HAIKU_MODEL);
        body.put("max_tokens", 300);
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", transcript)));

        try {
            JsonNode response = anthropicWebClient.post()
                    .uri("/v1/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) return "";

            JsonNode content = response.path("content");
            if (content.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : content) {
                    if ("text".equals(block.path("type").asText())) {
                        sb.append(block.path("text").asText(""));
                    }
                }
                return sb.toString();
            }
            return "";
        } catch (Exception e) {
            log.error("[Summary] Haiku 호출 실패: {}", e.getMessage());
            return "";
        }
    }

    private String extractField(String text, String fieldName) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(fieldName + ":") || trimmed.startsWith(fieldName + "：")) {
                return trimmed.substring(trimmed.indexOf(':') + 1).trim();
            }
        }
        return "";
    }
}
