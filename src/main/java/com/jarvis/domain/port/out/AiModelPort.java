package com.jarvis.domain.port.out;

import com.jarvis.domain.model.ChatMessage;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 모델 아웃바운드 포트 — LLM 호출을 추상화하는 인터페이스
 */
public interface AiModelPort {
    String generate(String systemPrompt, List<ChatMessage> conversationHistory, String userMessage);
    Flux<String> generateStream(String systemPrompt, List<ChatMessage> conversationHistory, String userMessage);
}
