package com.jarvis.tool;

import com.jarvis.application.AutoDevService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AutoDevTool implements ToolProvider {

    private final AutoDevService autoDevService;

    @Override
    public List<ToolFunction> getToolFunctions() {
        return List.of(new ToolFunction() {
            @Override
            public String name() {
                return "auto_develop";
            }

            @Override
            public String description() {
                return "사용자의 요청에 따라 Spring Boot 소스코드 파일을 자동으로 설계·생성하고 Gradle 빌드를 실행합니다. " +
                       "현진님이 '게시판 만들어줘', '로그인 서비스 구현해줘' 등 실제 개발 요청을 할 때 사용하세요.";
            }

            @Override
            public Map<String, Object> parameters() {
                return Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "command", Map.of(
                                        "type", "string",
                                        "description", "개발할 기능이나 서비스에 대한 구체적인 설명"
                                )
                        ),
                        "required", List.of("command")
                );
            }

            @Override
            public String execute(Map<String, Object> args) {
                String command = String.valueOf(args.getOrDefault("command", ""));
                AutoDevService.DevResult result = autoDevService.develop(command);
                StringBuilder sb = new StringBuilder(result.summary());
                if (!result.createdFiles().isEmpty()) {
                    sb.append(" 생성된 파일 목록: ");
                    sb.append(String.join(", ", result.createdFiles()));
                }
                if (!result.errors().isEmpty()) {
                    sb.append(" 오류: ").append(String.join(", ", result.errors()));
                }
                return sb.toString();
            }
        });
    }
}
