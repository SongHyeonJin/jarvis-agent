package com.jarvis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI API를 통해 자연스러운 프로젝트 폴더 슬러그를 생성한다.
 *
 * 실패 시 null 반환 → WorkspaceNameResolver 가 키워드 기반 fallback 처리
 */
@Service
@Slf4j
public class AiSlugService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient openAiClient;

    public AiSlugService(@Qualifier("openAiWebClient") WebClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    /**
     * 사용자 요청에서 자연스러운 영어 슬러그를 생성한다.
     *
     * @param command 사용자 원문 요청
     * @return slug (소문자·숫자·하이픈만, 최대 50자) or null if failed
     */
    public String generateSlug(String command) {
        if (command == null || command.isBlank()) return null;
        try {
            String systemPrompt = """
                    You are a project folder name generator.
                    Given a user's request (often in Korean), output ONE short English slug that captures the project's essence.

                    Rules:
                    - Lowercase letters, numbers, and hyphens ONLY
                    - 2–5 meaningful words joined by hyphens
                    - Maximum 50 characters
                    - No duplicate words (e.g. avoid "web-web", "app-app")
                    - No generic-only names like "project", "app", or "web" alone
                    - Respond with ONLY the slug — no explanation, no quotes

                    Examples:
                    크롬 탭 여백에 치즈냥이가 걸어다니는 확장 프로그램 → cheese-cat-tab-companion
                    날씨 보여주는 웹앱 → weather-dashboard-app
                    공 피하기 게임 → dodge-ball-game
                    간단한 계산기 앱 → simple-calculator-app
                    투두 리스트 앱 → todo-list-app
                    뱀 게임 → snake-game
                    크롬 북마크 관리 확장 → bookmark-manager-extension
                    """;

            Map<String, Object> body = Map.of(
                "model",       "gpt-4o-mini",
                "messages",    List.of(
                    Map.of("role", "system",  "content", systemPrompt),
                    Map.of("role", "user",    "content", command)
                ),
                "max_tokens",  20,
                "temperature", 0.3
            );

            String response = openAiClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(8))
                .block();

            if (response == null || response.isBlank()) return null;

            JsonNode root = MAPPER.readTree(response);
            String slug = root.path("choices").path(0)
                    .path("message").path("content").asText("").trim();

            if (slug.isBlank()) return null;

            // Sanitize: 소문자·숫자·하이픈만, 앞뒤 하이픈 제거, 최대 50자
            slug = slug.toLowerCase()
                    .replaceAll("[^a-z0-9-]", "-")
                    .replaceAll("-{2,}", "-")
                    .replaceAll("^-|-$", "");

            if (slug.length() > 50) slug = slug.substring(0, 50).replaceAll("-$", "");
            if (slug.length() < 2)  return null;

            log.info("[AiSlugService] AI 슬러그 생성 완료: '{}' → '{}'",
                command.length() > 50 ? command.substring(0, 50) + "…" : command, slug);
            return slug;

        } catch (Exception e) {
            log.warn("[AiSlugService] AI 슬러그 생성 실패 → fallback 사용: {}", e.getMessage());
            return null;
        }
    }
}
