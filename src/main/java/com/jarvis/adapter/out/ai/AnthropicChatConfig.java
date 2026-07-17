package com.jarvis.adapter.out.ai;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

/**
 * Spring Boot 자동설정의 AnthropicChatModel을 대체한다.
 * 자동설정 기본값은 temperature를 항상 채워 넣는데, opus 계열 모델은
 * temperature 파라미터 자체를 거부(HTTP 400 "temperature is deprecated for this model")하므로
 * temperature를 지정하지 않는 기본 옵션으로 재구성한다.
 */
@Configuration
public class AnthropicChatConfig {

    @Bean
    public AnthropicChatModel anthropicChatModel(AnthropicApi anthropicApi,
                                                   RetryTemplate retryTemplate,
                                                   ToolCallingManager toolCallingManager,
                                                   ObjectProvider<ObservationRegistry> observationRegistry) {
        AnthropicChatOptions defaultOptions = AnthropicChatOptions.builder()
                .model("claude-sonnet-4-6")
                .maxTokens(16384)
                .build();

        return AnthropicChatModel.builder()
                .anthropicApi(anthropicApi)
                .defaultOptions(defaultOptions)
                .retryTemplate(retryTemplate)
                .toolCallingManager(toolCallingManager)
                .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
                .build();
    }
}
