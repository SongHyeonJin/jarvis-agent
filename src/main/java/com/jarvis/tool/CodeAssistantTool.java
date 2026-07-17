package com.jarvis.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class CodeAssistantTool implements ToolProvider {

    @Tool(description = "코드를 리뷰하고 개선 사항을 제안합니다")
    public String reviewCode(@ToolParam(description = "리뷰할 코드") String code,
                              @ToolParam(description = "프로그래밍 언어 (java, python 등)") String language) {
        return String.format("다음 %s 코드를 분석하여 버그, 개선점, 최적화 방안을 제안해주세요:\n\n```%s\n%s\n```",
                language, language, code);
    }

    @Tool(description = "설명을 기반으로 코드를 생성합니다")
    public String generateCode(@ToolParam(description = "생성할 코드 설명") String description,
                                @ToolParam(description = "프로그래밍 언어") String language) {
        return String.format("다음 요구사항에 맞는 %s 코드를 생성해주세요:\n%s", language, description);
    }
}
