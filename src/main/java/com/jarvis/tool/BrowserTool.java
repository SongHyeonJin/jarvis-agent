package com.jarvis.tool;

import com.jarvis.domain.port.in.BrowserUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BrowserTool implements ToolProvider {

    private final BrowserUseCase browserUseCase;

    @Override
    public List<ToolFunction> getToolFunctions() {
        return List.of(openUrl(), controlYoutube());
    }

    private ToolFunction controlYoutube() {
        return new ToolFunction() {
            @Override public String name() { return "controlYoutube"; }
            @Override public String description() {
                return "현재 열려있는 YouTube 탭에서 n번째 영상을 재생합니다. " +
                       "'세 번째 영상 틀어줘' → action='play-nth', n=3. " +
                       "YouTube 탭이 없으면 먼저 openUrl로 열어주세요.";
            }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "action", Map.of("type", "string", "description", "수행할 동작 (play-nth)"),
                        "n", Map.of("type", "integer", "description", "재생할 영상 순서, 1부터 시작")
                    ),
                    "required", List.of("action", "n")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                String action = (String) args.get("action");
                int n = ((Number) args.get("n")).intValue();
                log.info("BrowserTool.controlYoutube called: action={}, n={}", action, n);
                return browserUseCase.controlYoutube(action, n);
            }
        };
    }

    private ToolFunction openUrl() {
        return new ToolFunction() {
            @Override public String name() { return "openUrl"; }
            @Override public String description() {
                return "사용자가 요청한 웹사이트나 URL을 기본 브라우저에서 엽니다. " +
                       "'유튜브 켜줘'처럼 사이트 이름만 말해도 되고, 특정 URL을 직접 전달해도 됩니다. " +
                       "유튜브 검색은 https://www.youtube.com/results?search_query=검색어 형태로 URL을 구성하세요. " +
                       "url 파라미터에 열 주소를 전달합니다.";
            }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "url", Map.of("type", "string", "description", "열 URL (https://로 시작해야 함)")
                    ),
                    "required", List.of("url")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                String url = (String) args.get("url");
                log.info("BrowserTool.openUrl called: url={}", url);
                return browserUseCase.openUrl(url);
            }
        };
    }
}
