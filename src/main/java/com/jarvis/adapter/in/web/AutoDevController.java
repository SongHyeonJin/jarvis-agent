package com.jarvis.adapter.in.web;

import com.jarvis.application.AutoDevService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class AutoDevController {

    private final AutoDevService autoDevService;

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generate(@RequestBody Map<String, String> body) {
        String command = body.getOrDefault("command", "").trim();
        if (command.isBlank()) {
            return Flux.just("data: " + errJson("명령어가 없습니다") + "\n\n");
        }

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        Schedulers.boundedElastic().schedule(() -> {
            try {
                sink.tryEmitNext("data: " + progressJson("start", "코드 생성을 시작합니다.") + "\n\n");

                AutoDevService.DevResult result = autoDevService.develop(command,
                        msg -> sink.tryEmitNext("data: " + progressJson("progress", msg) + "\n\n"));

                for (String f : result.createdFiles()) {
                    sink.tryEmitNext("data: " + progressJson("file", f) + "\n\n");
                }

                sink.tryEmitNext("data: " + doneJson(result) + "\n\n");
                sink.tryEmitNext("data: [DONE]\n\n");
            } catch (Exception e) {
                sink.tryEmitNext("data: " + errJson(e.getMessage()) + "\n\n");
            } finally {
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux();
    }

    private String progressJson(String type, String msg) {
        return "{\"type\":\"" + type + "\",\"message\":\"" + esc(msg) + "\"}";
    }

    private String doneJson(AutoDevService.DevResult r) {
        StringBuilder filesArr = new StringBuilder("[");
        for (int i = 0; i < r.createdFiles().size(); i++) {
            if (i > 0) filesArr.append(',');
            filesArr.append('"').append(esc(r.createdFiles().get(i))).append('"');
        }
        filesArr.append(']');

        return "{\"type\":\"done\",\"summary\":\"" + esc(r.summary()) +
               "\",\"files\":" + filesArr +
               ",\"fileCount\":" + r.createdFiles().size() +
               ",\"buildSuccess\":" + r.buildResult().success() +
               ",\"buildOutput\":\"" + esc(r.buildResult().output()) + "\"}";
    }

    private String errJson(String msg) {
        return "{\"type\":\"error\",\"message\":\"" + esc(msg) + "\"}";
    }

    private String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if      (c == '"')  sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') { /* skip */ }
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20)  { /* skip other control chars */ }
            else                sb.append(c);
        }
        return sb.toString();
    }
}
