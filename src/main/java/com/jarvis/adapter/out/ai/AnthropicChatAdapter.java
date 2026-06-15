package com.jarvis.adapter.out.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.domain.model.ChatMessage;
import com.jarvis.domain.model.MessageRole;
import com.jarvis.domain.port.out.AiModelPort;
import com.jarvis.tool.ToolFunction;
import com.jarvis.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * Claude Fable 5 기반 채팅 어댑터 (Anthropic Messages API).
 *
 * 기존 SpringAiAdapter(GPT-4o-mini)를 대체하며 @Primary로 등록.
 * - 도구 호출: Anthropic 형식 (tool_use / tool_result content blocks)
 * - 스트리밍: content_block_delta 이벤트 파싱
 * - Fable 5 기본: thinking=adaptive, no assistant prefill
 */
@Component
@Primary
@Slf4j
public class AnthropicChatAdapter implements AiModelPort {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Map<String, ToolFunction> toolFunctionMap;
    private final List<Map<String, Object>> toolDefinitions;

    @Value("${anthropic.model:claude-sonnet-4-6}")
    private String model;

    public AnthropicChatAdapter(
            @Qualifier("anthropicWebClient") WebClient anthropicWebClient,
            ObjectMapper objectMapper,
            List<ToolProvider> toolProviders) {
        this.webClient = anthropicWebClient;
        this.objectMapper = objectMapper;
        this.toolFunctionMap = new LinkedHashMap<>();
        this.toolDefinitions = new ArrayList<>();

        for (ToolProvider provider : toolProviders) {
            for (ToolFunction fn : provider.getToolFunctions()) {
                toolFunctionMap.put(fn.name(), fn);
                toolDefinitions.add(Map.of(
                    "name",         fn.name(),
                    "description",  fn.description(),
                    "input_schema", fn.parameters()
                ));
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  AiModelPort 구현
    // ─────────────────────────────────────────────────────────

    @Override
    public String generate(String systemPrompt, List<ChatMessage> history, String userMessage) {
        List<Map<String, Object>> messages = buildMessages(history, userMessage);
        return runToolLoop(systemPrompt, messages);
    }

    @Override
    public Flux<String> generateStream(String systemPrompt, List<ChatMessage> history, String userMessage) {
        return Flux.defer(() -> {
            // tool loop 포함 전체 응답을 단일 청크로 스트리밍
            // (SSE 스트리밍 파싱 버그 회피 — 응답을 단일 data 이벤트로 전달)
            String response = generate(systemPrompt, history, userMessage);
            log.info("[AnthropicChatAdapter] streamChat 응답 len={}", response.length());
            return Flux.just(response);
        });
    }

    // ─────────────────────────────────────────────────────────
    //  도구 루프
    // ─────────────────────────────────────────────────────────

    private String runToolLoop(String systemPrompt, List<Map<String, Object>> messages) {
        List<Map<String, Object>> history = new ArrayList<>(messages);
        for (int round = 0; round < 10; round++) {
            JsonNode response = callApi(systemPrompt, history);
            if (response == null) return "AI 응답을 받지 못했습니다.";

            String stopReason = response.path("stop_reason").asText();

            if ("refusal".equals(stopReason)) {
                log.warn("[AnthropicChatAdapter] Fable 5 refusal: {}", response.path("stop_details"));
                return "요청을 처리할 수 없습니다.";
            }

            if (!"tool_use".equals(stopReason)) {
                return extractText(response.path("content"));
            }

            // 어시스턴트 메시지 (tool_use 포함) 추가
            history.add(Map.of("role", "assistant",
                               "content", toList(response.path("content"))));

            // 도구 실행 후 tool_result를 user 메시지로 추가
            List<Map<String, Object>> toolResults = executeToolBlocks(response.path("content"));
            if (!toolResults.isEmpty()) {
                history.add(Map.of("role", "user", "content", toolResults));
            }
        }
        return "처리 중 오류가 발생했습니다.";
    }

    // ─────────────────────────────────────────────────────────
    //  API 호출
    // ─────────────────────────────────────────────────────────

    private JsonNode callApi(String systemPrompt, List<Map<String, Object>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 16384);
        body.put("system", systemPrompt);
        body.put("messages", messages);
        if (!toolDefinitions.isEmpty()) body.put("tools", toolDefinitions);
        try {
            return webClient.post()
                    .uri("/v1/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (Exception e) {
            log.error("[AnthropicChatAdapter] API 호출 실패: {}", e.getMessage(), e);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────
    //  메시지 빌더
    // ─────────────────────────────────────────────────────────

    private List<Map<String, Object>> buildMessages(List<ChatMessage> history, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            if (msg.getRole() == MessageRole.USER) {
                messages.add(Map.of("role", "user", "content", nvl(msg.getContent())));
            } else if (msg.getRole() == MessageRole.ASSISTANT) {
                messages.add(Map.of("role", "assistant", "content", nvl(msg.getContent())));
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    // ─────────────────────────────────────────────────────────
    //  도구 실행
    // ─────────────────────────────────────────────────────────

    private List<Map<String, Object>> executeToolBlocks(JsonNode contentArray) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (!contentArray.isArray()) return results;
        for (JsonNode block : contentArray) {
            if ("tool_use".equals(block.path("type").asText())) {
                String id   = block.path("id").asText();
                String name = block.path("name").asText();
                String result = executeToolCall(name, block.path("input"));
                log.info("[AnthropicChatAdapter] tool_use: name={} result_len={}", name, result.length());
                results.add(Map.of(
                    "type",        "tool_result",
                    "tool_use_id", id,
                    "content",     result
                ));
            }
        }
        return results;
    }

    private String executeToolCall(String name, JsonNode inputNode) {
        try {
            ToolFunction fn = toolFunctionMap.get(name);
            if (fn == null) return "오류: 알 수 없는 도구 '" + name + "'";
            Map<String, Object> args = objectMapper.convertValue(inputNode, new TypeReference<>() {});
            return fn.execute(args);
        } catch (Exception e) {
            return "도구 실행 오류: " + e.getMessage();
        }
    }

    // ─────────────────────────────────────────────────────────
    //  유틸
    // ─────────────────────────────────────────────────────────

    private String extractText(JsonNode contentArray) {
        if (!contentArray.isArray()) return contentArray.asText("");
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : contentArray) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText(""));
            }
        }
        return sb.toString();
    }

    private List<Object> toList(JsonNode node) {
        try {
            return objectMapper.convertValue(node, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
