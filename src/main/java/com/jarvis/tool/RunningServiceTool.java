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

    private final RunningServiceService serviceService;

    @Override
    public List<ToolFunction> getToolFunctions() {
        return List.of(listRunningServices(), stopRunningService(), stopAllRunningServices());
    }

    private ToolFunction listRunningServices() {
        return new ToolFunction() {
            @Override public String name() { return "listRunningServices"; }
            @Override public String description() {
                return "Dev Agent가 실행 중인 미리보기 서비스(React 앱, 정적 서버 등) 목록을 반환합니다.";
            }
            @Override public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of(), "required", List.of());
            }
            @Override public String execute(Map<String, Object> args) {
                List<RunningServiceService.ServiceInfo> list = serviceService.list();
                if (list.isEmpty()) return "현재 실행 중인 서비스가 없습니다.";
                return list.stream()
                        .map(s -> String.format("- %s (port=%d, type=%s, url=%s)",
                                s.name(), s.port(), s.type(), s.url()))
                        .collect(Collectors.joining("\n", "실행 중인 서비스:\n", ""));
            }
        };
    }

    private ToolFunction stopRunningService() {
        return new ToolFunction() {
            @Override public String name() { return "stopRunningService"; }
            @Override public String description() {
                return "지정한 포트의 실행 중인 서비스를 중지합니다.";
            }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "port", Map.of("type", "integer", "description", "중지할 서비스의 포트 번호")
                    ),
                    "required", List.of("port")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                int port = ((Number) args.get("port")).intValue();
                boolean ok = serviceService.stopByPort(port);
                return ok ? "포트 " + port + " 서비스를 중지했습니다." : "포트 " + port + "에서 실행 중인 서비스가 없습니다.";
            }
        };
    }

    private ToolFunction stopAllRunningServices() {
        return new ToolFunction() {
            @Override public String name() { return "stopAllRunningServices"; }
            @Override public String description() {
                return "Dev Agent가 실행 중인 모든 미리보기 서비스를 중지합니다.";
            }
            @Override public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of(), "required", List.of());
            }
            @Override public String execute(Map<String, Object> args) {
                int count = serviceService.list().size();
                serviceService.stopAll();
                return count == 0 ? "실행 중인 서비스가 없습니다." : count + "개의 서비스를 모두 중지했습니다.";
            }
        };
    }
}
