package com.jarvis.adapter.out.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 대화 메시지의 복잡도를 보고 Opus/Sonnet/Haiku(Anthropic) 또는 로컬 Ollama 중
 * 어느 모델로 응답을 생성할지 고른다.
 */
@Component
@Slf4j
public class ModelRouter {

    public enum Tier { OPUS, SONNET, HAIKU, OLLAMA }

    @Value("${jarvis.ai.model.opus}")
    private String opusModel;
    @Value("${jarvis.ai.model.sonnet}")
    private String sonnetModel;
    @Value("${jarvis.ai.model.haiku}")
    private String haikuModel;
    @Value("${jarvis.ai.model.ollama}")
    private String ollamaModel;

    /** 대규모·복잡 아키텍처 관련 요청 → Opus */
    private static final List<String> OPUS_KEYWORDS = List.of(
        "풀스택", "full.?stack", "마이크로서비스", "microservice", "msa",
        "아키텍처", "architecture", "설계", "인증.*서버", "oauth", "결제", "payment",
        "대규모", "플랫폼", "platform", "헥사고날", "hexagonal", "이벤트.*소싱", "event.?sourcing"
    );

    /** 인사말 등 도구·추론이 필요 없는 잡담 → 로컬 Ollama */
    private static final List<String> TRIVIAL_KEYWORDS = List.of(
        "안녕", "hi", "hello", "고마워", "고맙", "땡큐", "굿모닝", "잘 자", "좋은 아침", "수고"
    );

    public Tier route(String userMessage) {
        String lower = userMessage.toLowerCase();

        if (OPUS_KEYWORDS.stream().anyMatch(k -> lower.matches(".*" + k + ".*"))) {
            return Tier.OPUS;
        }
        if (userMessage.length() <= 12 && TRIVIAL_KEYWORDS.stream().anyMatch(lower::contains)) {
            return Tier.OLLAMA;
        }
        if (userMessage.length() <= 20) {
            return Tier.HAIKU;
        }
        return Tier.SONNET;
    }

    public String modelFor(Tier tier) {
        return switch (tier) {
            case OPUS -> opusModel;
            case SONNET -> sonnetModel;
            case HAIKU -> haikuModel;
            case OLLAMA -> ollamaModel;
        };
    }
}
