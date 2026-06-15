package com.jarvis.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class TtsService {

    private final WebClient ttsClient;

    public TtsService(@Value("${ai.openai.api-key:}") String apiKey) {
        this.ttsClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();
    }

    public byte[] generateSpeech(String text) {
        if (text == null || text.isBlank()) return null;
        String input = text.length() > 4096 ? text.substring(0, 4096) : text;
        try {
            return ttsClient.post()
                    .uri("/audio/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "model", "tts-1",
                            "voice", "onyx",
                            "input", input,
                            "speed", 1.0
                    ))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(8))
                    .block();
        } catch (Exception e) {
            log.error("OpenAI TTS 생성 실패: {}", e.getMessage());
            return null;
        }
    }
}
