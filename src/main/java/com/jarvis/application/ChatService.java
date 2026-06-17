package com.jarvis.application;

import com.jarvis.domain.model.ChatMessage;
import com.jarvis.domain.model.Conversation;
import com.jarvis.domain.model.DevJob;
import com.jarvis.domain.model.MessageRole;
import com.jarvis.domain.port.in.ChatUseCase;
import com.jarvis.domain.port.out.AiModelPort;
import com.jarvis.domain.port.out.ConversationRepository;
import com.jarvis.domain.port.out.DevJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService implements ChatUseCase {

    private final ConversationRepository conversationRepository;
    private final AiModelPort            aiModelPort;
    private final DevJobRepository       devJobRepository;

    private String buildSystemPrompt() {
        String today = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", java.util.Locale.KOREAN));

        String base = """
                당신은 자비스(J.A.R.V.I.S.), 현진님의 전용 AI 비서입니다.

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

                ## Dev Agent 작업 연동 규칙

                ### ⚠️ 프로젝트 구분 (매우 중요)
                - "자비스", "자비스 프로젝트", "이 자비스", "이 시스템", "자비스 에이전트", "이 AI", "이 프로젝트", "현재 프로젝트"는
                  지금 실행 중인 자비스 AI 비서 자체(d:/jarvis-agent)를 의미합니다.
                  → 이 경우 modify_recent_project 도구를 절대 사용하지 마세요.
                  → 자비스 자체 수정은 현진님에게 "자비스야, [기능] 개발해줘" 형식의 음성 명령을 사용하도록 안내하세요.

                - "방금 만든 [게임/앱/웹사이트]", "그 [프로젝트/게임/앱]", "해당 프로젝트", "거기에", "그거"처럼
                  Dev Agent가 최근에 생성한 외부 프로젝트(d:/jarvis-workspaces/...)를 가리킬 때만
                  modify_recent_project 도구를 사용하세요.

                ### 도구 사용 기준
                - 외부 프로젝트 수정 요청 → modify_recent_project 즉시 호출 (설명만 하지 말 것)
                - 생성된 코드 내용이 궁금하면 → read_workspace_file
                - Dev 작업 상태 확인 → get_recent_dev_jobs
                """.formatted(today);

        String recentCtx = buildRecentJobContext();
        return recentCtx.isBlank() ? base : base + recentCtx;
    }

    private String buildRecentJobContext() {
        try {
            List<DevJob> recent = devJobRepository.findRecent().stream()
                    .filter(j -> j.getStatus() == DevJob.JobStatus.DONE
                              || j.getStatus() == DevJob.JobStatus.RUNNING)
                    .limit(3)
                    .toList();
            if (recent.isEmpty()) return "";

            StringBuilder sb = new StringBuilder("\n## 최근 Dev Agent 작업 현황\n");
            for (DevJob job : recent) {
                sb.append(String.format("- Job #%d [%s] \"%s\"\n",
                        job.getId(), job.getStatus(), job.getCommand()));
                if (job.getWorkspacePath() != null && !job.getWorkspacePath().isBlank()) {
                    sb.append(String.format("  위치: %s (유형: %s)\n",
                            job.getWorkspacePath(),
                            job.getProjectType() != null ? job.getProjectType() : "UNKNOWN"));
                }
                if (job.getChangedFiles() != null && !job.getChangedFiles().isBlank()) {
                    String files = job.getChangedFiles().replace("\n", ", ");
                    if (files.length() > 150) files = files.substring(0, 150) + "...";
                    sb.append(String.format("  파일: %s\n", files));
                }
                if (job.getSummary() != null && !job.getSummary().isBlank()) {
                    sb.append(String.format("  요약: %s\n", job.getSummary()));
                }
            }
            sb.append("현진님이 위 프로젝트 수정을 요청하면 modify_recent_project 도구를 즉시 호출하세요.\n");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

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
