package com.jarvis.tool;

import com.jarvis.application.RunningServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RunningServiceTool implements ToolProvider {

    private final RunningServiceService runningServiceService;

    @Tool(description = "AutoDev가 실행한 개발 서비스(npm, Spring Boot 등) 목록을 조회합니다")
    public String listRunningServices() {
        List<RunningServiceService.ServiceInfo> list = runningServiceService.list();
        if (list.isEmpty()) return "현재 실행 중인 서비스가 없습니다.";
        return list.stream()
                .map(s -> String.format("[%d] %s (%s)%s%s",
                        s.id(), s.name(), s.type(),
                        s.port() > 0 ? " :" + s.port() : "",
                        s.alive() ? " 실행중" : " 종료됨"))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "실행 중인 특정 개발 서비스를 ID로 종료합니다")
    public String stopRunningService(@ToolParam(description = "서비스 ID") Long id) {
        return runningServiceService.stop(id)
                .map(s -> "서비스 [" + s.id() + "] " + s.name() + " 종료됨")
                .orElse("서비스 [" + id + "]를 찾을 수 없습니다.");
    }

    @Tool(description = "실행 중인 모든 개발 서비스를 한번에 종료합니다")
    public String stopAllRunningServices() {
        int count = runningServiceService.stopAll();
        return count == 0 ? "종료할 서비스가 없습니다." : count + "개 서비스 모두 종료됨";
    }
}
