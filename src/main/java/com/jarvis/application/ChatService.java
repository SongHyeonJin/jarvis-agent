package com.jarvis.application;

import com.jarvis.domain.model.ChatMessage;
import com.jarvis.domain.model.Conversation;
import com.jarvis.domain.model.MessageRole;
import com.jarvis.domain.port.in.ChatUseCase;
import com.jarvis.domain.port.out.AiModelPort;
import com.jarvis.domain.port.out.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService implements ChatUseCase {

    private String buildSystemPrompt() {
        String today = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", java.util.Locale.KOREAN));
        return """
                당신은 '대현자(大賢者)', 현진님의 전용 AI 비서 J.A.R.V.I.S.입니다.

                규칙:
                - 항상 사용자를 '현진님'이라고 부르세요.
                - 한국어로 간결하고 명확하게 답변하세요.
                - 오늘 날짜: %s
                - 대화 문맥을 정확히 기억하고 활용하세요.
                  예) '방금 말한 거 4시로 미뤄줘' → 직전 대화에서 언급된 일정/할 일의 시간을 수정
                  예) '그거 삭제해줘' → 가장 최근 언급된 항목을 삭제
                - 할 일(Todo), 메모(Memo), 일정(Schedule) 관리 도구를 적극 활용하세요.
                - 코드 관련 질문에는 실용적인 답변을 제공하세요.
                - 답변은 TTS로 읽힐 것을 고려해 마크다운 기호(#, *, -, `)와 이모지 사용을 최소화하세요.
                - 목록은 '첫째', '둘째' 또는 '1번', '2번' 같은 구어체 형식을 사용하세요.
                """.formatted(today);
    }

    private final ConversationRepository conversationRepository;
    private final AiModelPort aiModelPort;

    @Override
    @Transactional
    public String chat(Long conversationId, String userMessage) {
        Conversation conversation = conversationRepository.findByIdWithMessages(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다: " + conversationId));

        String response = aiModelPort.generate(buildSystemPrompt(), conversation.getMessages(), userMessage);

        conversation.addMessage(ChatMessage.builder().role(MessageRole.USER).content(userMessage).build());
        conversation.addMessage(ChatMessage.builder().role(MessageRole.ASSISTANT).content(response).build());
        conversationRepository.save(conversation);

        return response;
    }

    @Override
    public Flux<String> streamChat(Long conversationId, String userMessage) {
        Conversation conversation = conversationRepository.findByIdWithMessages(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다: " + conversationId));
        List<ChatMessage> historySnapshot = new ArrayList<>(conversation.getMessages());

        StringBuilder fullResponse = new StringBuilder();

        return aiModelPort.generateStream(buildSystemPrompt(), historySnapshot, userMessage)
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> saveStreamedMessages(conversationId, userMessage, fullResponse.toString()));
    }

    @Transactional
    public void saveStreamedMessages(Long conversationId, String userMessage, String assistantResponse) {
        Conversation conv = conversationRepository.findByIdWithMessages(conversationId).orElseThrow();
        conv.addMessage(ChatMessage.builder().role(MessageRole.USER).content(userMessage).build());
        conv.addMessage(ChatMessage.builder().role(MessageRole.ASSISTANT).content(assistantResponse).build());
        conversationRepository.save(conv);
    }

    @Override
    @Transactional
    public Conversation createConversation(String title) {
        Conversation conv = Conversation.builder()
                .title(title != null && !title.isBlank() ? title : "새 대화")
                .build();
        return conversationRepository.save(conv);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Conversation> getAllConversations() {
        return conversationRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Conversation getConversation(Long id) {
        return conversationRepository.findByIdWithMessages(id)
                .orElseThrow(() -> new IllegalArgumentException("대화를 찾을 수 없습니다: " + id));
    }

    @Override
    @Transactional
    public void deleteConversation(Long id) {
        conversationRepository.deleteById(id);
    }
}
