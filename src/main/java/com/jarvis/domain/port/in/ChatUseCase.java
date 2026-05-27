package com.jarvis.domain.port.in;

import com.jarvis.domain.model.Conversation;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 채팅 기능 인바운드 포트 — 대화 생성, 메시지 전송, 스트리밍 응답
 */
public interface ChatUseCase {
    String chat(Long conversationId, String userMessage);
    Flux<String> streamChat(Long conversationId, String userMessage);
    Conversation createConversation(String title);
    List<Conversation> getAllConversations();
    Conversation getConversation(Long id);
    void deleteConversation(Long id);
}
