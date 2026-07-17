package com.jarvis.adapter.out.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.domain.model.ChatMessage;
import com.jarvis.domain.model.MessageRole;
import com.jarvis.domain.port.out.AiModelPort;
import com.jarvis.tool.ToolFunction;
import com.jarvis.tool.ToolProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SpringAiAdapter implements AiModelPort {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Map<String, ToolFunction> toolFunctionMap;
    private final List<Map<String, Object>> toolDefinitions;

    @Value("${ai.openai.model:gpt-4o-mini}")
    private String model;

    public SpringAiAdapter(@org.springframework.beans.factory.annotation.Qualifier("openAiWebClient") WebClient openAiWebClient,
                           ObjectMapper objectMapper,
                           List<ToolProvider> toolProviders) {
        this.webClient = openAiWebClient;
        this.objectMapper = objectMapper;
        this.toolFunctionMap = new LinkedHashMap<>();
        this.toolDefinitions = new ArrayList<>();

        for (ToolProvider provider : toolProviders) {
            for (ToolFunction fn : provider.getToolFunctions()) {
                toolFunctionMap.put(fn.name(), fn);
                toolDefinitions.add(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", fn.name(),
                                "description", fn.description(),
                                "parameters", fn.parameters()
                        )
                ));
            }
        }
    }

    @Override
    public String generate(String systemPrompt, List<ChatMessage> history, String userMessage) {
        List<Map<String, Object>> messages = buildMessages(systemPrompt, history, userMessage);
        return runToolLoop(messages);
    }

    @Override
    public Flux<String> generateStream(String systemPrompt, List<ChatMessage> history, String userMessage) {
        return Flux.defer(() -> {
            List<Map<String, Object>> messages = buildMessages(systemPrompt, history, userMessage);
            List<Map<String, Object>> resolved = resolveToolCalls(messages);
            return streamResponse(resolved);
        });
    }

    private String runToolLoop(List<Map<String, Object>> messages) {
        List<Map<String, Object>> mutableMessages = new ArrayList<>(messages);
        for (int round = 0; round < 10; round++) {
            JsonNode response = callApi(mutableMessages, true);
            JsonNode choice = response.path("choices").path(0);
            JsonNode messageNode = choice.path("message");
            String finishReason = choice.path("finish_reason").asText();

            if (!"tool_calls".equals(finishReason)) {
                return messageNode.path("content").asText("");
            }

            mutableMessages.add(jsonNodeToMap(messageNode));
            executeAndAppendToolCalls(messageNode.path("tool_calls"), mutableMessages);
        }
        return "처리 중 오류가 발생했습니다.";
    }

    private List<Map<String, Object>> resolveToolCalls(List<Map<String, Object>> messages) {
        List<Map<String, Object>> mutableMessages = new ArrayList<>(messages);
        for (int round = 0; round < 10; round++) {
            JsonNode response = callApi(mutableMessages, true);
            JsonNode choice = response.path("choices").path(0);
            JsonNode messageNode = choice.path("message");
            String finishReason = choice.path("finish_reason").asText();

            if (!"tool_calls".equals(finishReason)) {
                return mutableMessages;
            }
            mutableMessages.add(jsonNodeToMap(messageNode));
            executeAndAppendToolCalls(messageNode.path("tool_calls"), mutableMessages);
        }
        return mutableMessages;
    }

    private void executeAndAppendToolCalls(JsonNode toolCalls, List<Map<String, Object>> messages) {
        if (toolCalls == null || toolCalls.isMissingNode()) return;
        toolCalls.forEach(toolCall -> {
            String id = toolCall.path("id").asText();
            String name = toolCall.path("function").path("name").asText();
            String argsJson = toolCall.path("function").path("arguments").asText();
            String result = executeToolCall(name, argsJson);
            Map<String, Object> toolResult = new HashMap<>();
            toolResult.put("role", "tool");
            toolResult.put("tool_call_id", id);
            toolResult.put("content", result);
            messages.add(toolResult);
        });
    }

    private Flux<String> streamResponse(List<Map<String, Object>> messages) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", true);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isBlank() && !line.equals("data: [DONE]"))
                .mapNotNull(line -> {
                    try {
                        String json = line.startsWith("data: ") ? line.substring(6) : line;
                        if ("[DONE]".equals(json.trim())) return null;
                        JsonNode node = objectMapper.readTree(json);
                        JsonNode content = node.path("choices").path(0).path("delta").path("content");
                        return (content.isMissingNode() || content.isNull()) ? null : content.asText();
                    } catch (Exception e) {
                        return null;
                    }
                });
    }

    private JsonNode callApi(List<Map<String, Object>> messages, boolean withTools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        if (withTools && !toolDefinitions.isEmpty()) {
            body.put("tools", toolDefinitions);
            body.put("tool_choice", "auto");
        }
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    private List<Map<String, Object>> buildMessages(String systemPrompt, List<ChatMessage> history, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
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

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        try {
            return objectMapper.convertValue(node, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String executeToolCall(String name, String argsJson) {
        try {
            ToolFunction fn = toolFunctionMap.get(name);
            if (fn == null) return "오류: 알 수 없는 도구 '" + name + "'";
            Map<String, Object> args = objectMapper.readValue(argsJson, new TypeReference<>() {});
            return fn.execute(args);
        } catch (Exception e) {
            return "도구 실행 오류: " + e.getMessage();
        }
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}
