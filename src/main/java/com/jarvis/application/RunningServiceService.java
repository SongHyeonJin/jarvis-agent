package com.jarvis.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 현재 실행 중인 외부 서비스(미리보기 서버 등)를 인메모리로 추적한다.
 * 서버 재시작 시 초기화됨.
 */
@Service
@Slf4j
public class RunningServiceService {

    public record ServiceInfo(Long jobId, String name, int port, String type, String url, long startedAt) {}

    /** port → ServiceInfo */
    private final ConcurrentHashMap<Integer, ServiceInfo> registry = new ConcurrentHashMap<>();

    public void register(Long jobId, String name, int port, String type, String url) {
        registry.put(port, new ServiceInfo(jobId, name, port, type, url, System.currentTimeMillis()));
        log.info("[RunningService] 등록: name={} port={} type={}", name, port, type);
    }

    public List<ServiceInfo> list() {
        return new ArrayList<>(registry.values());
    }

    public boolean stopByPort(int port) {
        ServiceInfo info = registry.remove(port);
        if (info == null) return false;
        killPort(port);
        log.info("[RunningService] 중지: port={} name={}", port, info.name());
        return true;
    }

    public void stopAll() {
        List<Integer> ports = new ArrayList<>(registry.keySet());
        ports.forEach(this::stopByPort);
    }

    private void killPort(int port) {
        try {
            boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
            List<String> cmd = isWin
                ? List.of("cmd", "/c", "for /f \"tokens=5\" %a in ('netstat -ano ^| findstr :" + port + "') do taskkill /PID %a /F")
                : List.of("bash", "-c", "lsof -ti:" + port + " | xargs kill -9");
            new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (Exception e) {
            log.warn("[RunningService] 포트 종료 실패 port={}: {}", port, e.getMessage());
        }
    }
}
