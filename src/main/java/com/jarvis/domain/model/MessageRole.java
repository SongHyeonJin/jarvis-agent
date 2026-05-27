package com.jarvis.domain.model;

/**
 * AI 대화에서 메시지 발신자의 역할을 정의하는 열거형
 */
public enum MessageRole {
    USER,       // 사용자 메시지
    ASSISTANT,  // AI 어시스턴트 응답
    SYSTEM,     // 시스템 프롬프트
    TOOL        // 도구 호출 결과
}
