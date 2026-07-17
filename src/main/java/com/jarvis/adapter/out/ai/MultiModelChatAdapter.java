package com.jarvis.adapter.out.ai;

import com.jarvis.domain.model.ChatMessage;
import com.jarvis.domain.model.MessageRole;
import com.jarvis.domain.port.out.AiModelPort;
import com.jarvis.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI ChatClient 기반 멀티모델 어댑터.
 * ModelRouter가 판단한 복잡도에 따라 Claude Opus/Sonnet/Haiku(Anthropic) 또는
 * 로컬 Ollama 모델로 라우팅한다.
 */
@Component
@Primary
@Slf4j
public class MultiModelChatAdapter implements AiModelPort {

    private final ChatClient anthropicClient;
    private final ChatClient ollamaClient;
    private final ModelRouter router;

    public MultiModelChatAdapter(AnthropicChatModel anthropicChatModel,
                                  OllamaChatModel ollamaChatModel,
                                  List<ToolProvider> toolProviders,
                                  ModelRouter router) {
        Object[] tools = toolProviders.toArray();
        this.anthropicClient = ChatClient.builder(anthropicChatModel).defaultTools(tools).build();
        this.ollamaClient = ChatClient.builder(ollamaChatModel).defaultTools(tools).build();
        this.router = router;
    }

    @Override
    public String generate(String systemPrompt, List<ChatMessage> history, String userMessage) {
        ModelRouter.Tier tier = router.route(userMessage);
        String model = router.modelFor(tier);
        log.info("[MultiModelChatAdapter] tier={} model={}", tier, model);

        Prompt prompt = buildPrompt(systemPrompt, history, userMessage, tier, model);
        ChatClient client = (tier == ModelRouter.Tier.OLLAMA) ? ollamaClient : anthropicClient;

        return client.prompt(prompt).call().content();
    }

    @Override
    public Flux<String> generateStream(String systemPrompt, List<ChatMessage> history, String userMessage) {
        // tool 루프 포함 전체 응답을 단일 청크로 스트리밍 (SSE 파싱 이슈 회피)
        String response = generate(systemPrompt, history, userMessage);
        return Flux.just(response);
    }

    private Prompt buildPrompt(String systemPrompt, List<ChatMessage> history, String userMessage,
                                ModelRouter.Tier tier, String model) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        for (ChatMessage m : history) {
            messages.add(m.getRole() == MessageRole.USER
                    ? new UserMessage(nvl(m.getContent()))
                    : new AssistantMessage(nvl(m.getContent())));
        }
        messages.add(new UserMessage(userMessage));

        ChatOptions options = (tier == ModelRouter.Tier.OLLAMA)
                ? OllamaOptions.builder().model(model).build()
                : AnthropicChatOptions.builder().model(model).build();

        return new Prompt(messages, options);
    }

    private String nvl(String s) { return s != null ? s : ""; }
}
