package com.jarvis.tool;

import com.jarvis.application.RunningServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RunningServiceTool implements ToolProvider {

    private final RunningServiceService runningServiceService;

    @Override
    public List<ToolFunction> getToolFunctions() {
        return List.of(listServices(), stopService(), stopAllServices());
    }

    private ToolFunction listServices() {
        return new ToolFunction() {
            @Override public String name() { return "listRunningServices"; }
            @Override public String description() {
                return "AutoDev가 실행한 개발 서비스(npm, Spring Boot 등) 목록을 조회합니다";
            }
            @Override public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of());
            }
            @Override public String execute(Map<String, Object> args) {
                List<RunningServiceService.ServiceInfo> list = runningServiceService.list();
                if (list.isEmpty()) return "현재 실행 중인 서비스가 없습니다.";
                return list.stream()
                        .map(s -> String.format("[%d] %s (%s)%s%s",
                                s.id(), s.name(), s.type(),
                                s.port() > 0 ? " :" + s.port() : "",
                                s.alive() ? " ✅" : " ⛔"))
                        .collect(Collectors.joining("\n"));
            }
        };
    }

    private ToolFunction stopService() {
        return new ToolFunction() {
            @Override public String name() { return "stopRunningService"; }
            @Override public String description() {
                return "실행 중인 특정 개발 서비스를 ID로 종료합니다";
            }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                        "type", "object",
                        "properties", Map.of("id", Map.of("type", "integer", "description", "서비스 ID")),
                        "required", List.of("id")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                Long id = ((Number) args.get("id")).longValue();
                return runningServiceService.stop(id)
                        .map(s -> "서비스 [" + s.id() + "] " + s.name() + " 종료됨")
                        .orElse("서비스 [" + id + "]를 찾을 수 없습니다.");
            }
        };
    }

    private ToolFunction stopAllServices() {
        return new ToolFunction() {
            @Override public String name() { return "stopAllRunningServices"; }
            @Override public String description() {
                return "실행 중인 모든 개발 서비스를 한번에 종료합니다";
            }
            @Override public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of());
            }
            @Override public String execute(Map<String, Object> args) {
                int count = runningServiceService.stopAll();
                return count == 0 ? "종료할 서비스가 없습니다." : count + "개 서비스 모두 종료됨";
            }
        };
    }
}
