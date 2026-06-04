package com.jarvis.tool;

import com.jarvis.application.DevJobService;
import lombok.RequiredArgsConstructor;
// @Component 제거 — GPT tool 목록에서 제외
// 이유: AutoDevService는 Windows Terminal 팝업을 열어 실제 파일 수정이 안 됨.
//       개발 요청은 반드시 ClaudeCliExecutor 경로(DevJobService)를 통해야 함.
//       JS CODE_GEN_PATS → handleCodeGen() → /api/dev/jobs → DevJobService → ClaudeCliExecutor

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class AutoDevTool implements ToolProvider {

    private final DevJobService devJobService;

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
                // 이 코드는 @Component가 없어 Spring에 등록되지 않음 → 절대 호출 안 됨
                // 개발 요청은 UI의 CODE_GEN_PATS → handleCodeGen() → /api/dev/jobs → DevJobService 경로를 사용
                String command = String.valueOf(args.getOrDefault("command", ""));
                var job = devJobService.submitJob(command);
                return "코드 개발 작업이 백그라운드에서 시작되었습니다. (Job #" + job.getId() + ")";
            }
        });
    }
}
