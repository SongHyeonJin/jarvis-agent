package com.jarvis.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CodeAssistantTool implements ToolProvider {

    @Override
    public List<ToolFunction> getToolFunctions() {
        return List.of(reviewCode(), generateCode());
    }

    private ToolFunction reviewCode() {
        return new ToolFunction() {
            @Override public String name() { return "reviewCode"; }
            @Override public String description() { return "코드를 리뷰하고 개선 사항을 제안합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "code", Map.of("type", "string", "description", "리뷰할 코드"),
                        "language", Map.of("type", "string", "description", "프로그래밍 언어 (java, python 등)")
                    ),
                    "required", List.of("code", "language")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                String language = (String) args.get("language");
                String code = (String) args.get("code");
                return String.format("다음 %s 코드를 분석하여 버그, 개선점, 최적화 방안을 제안해주세요:\n\n```%s\n%s\n```",
                        language, language, code);
            }
        };
    }

    private ToolFunction generateCode() {
        return new ToolFunction() {
            @Override public String name() { return "generateCode"; }
            @Override public String description() { return "설명을 기반으로 코드를 생성합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "description", Map.of("type", "string", "description", "생성할 코드 설명"),
                        "language", Map.of("type", "string", "description", "프로그래밍 언어")
                    ),
                    "required", List.of("description", "language")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                String language = (String) args.get("language");
                String description = (String) args.get("description");
                return String.format("다음 요구사항에 맞는 %s 코드를 생성해주세요:\n%s", language, description);
            }
        };
    }
}
