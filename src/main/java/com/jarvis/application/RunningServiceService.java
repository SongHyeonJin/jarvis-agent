package com.jarvis.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class RunningServiceService {

    public record ServiceInfo(
            Long id,
            String name,
            String type,
            String command,
            int port,
            String workspacePath,
            boolean alive
    ) {}

    private record ManagedProcess(Long id, String name, String type, String command,
                                   int port, String workspacePath, Process process) {
        ServiceInfo toInfo() {
            return new ServiceInfo(id, name, type, command, port, workspacePath,
                    process == null || process.isAlive());
        }
    }

    private final Map<Long, ManagedProcess> registry = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    public ServiceInfo register(String name, String type, String command,
                                 int port, String workspacePath, Process process) {
        long id = idSeq.getAndIncrement();
        ManagedProcess mp = new ManagedProcess(id, name, type, command, port, workspacePath, process);
        registry.put(id, mp);
        log.info("[RunningService] 등록: [{}] {} ({})", id, name, type);
        return mp.toInfo();
    }

    public List<ServiceInfo> list() {
        registry.entrySet().removeIf(e -> {
            Process p = e.getValue().process();
            return p != null && !p.isAlive();
        });
        return registry.values().stream()
                .map(ManagedProcess::toInfo)
                .sorted(Comparator.comparing(ServiceInfo::id))
                .toList();
    }

    public Optional<ServiceInfo> stop(Long id) {
        ManagedProcess mp = registry.remove(id);
        if (mp == null) return Optional.empty();
        if (mp.process() != null && mp.process().isAlive()) {
            mp.process().destroyForcibly();
            log.info("[RunningService] 종료: [{}] {}", id, mp.name());
        }
        return Optional.of(new ServiceInfo(id, mp.name(), mp.type(), mp.command(),
                mp.port(), mp.workspacePath(), false));
    }

    public int stopAll() {
        List<Long> ids = new ArrayList<>(registry.keySet());
        ids.forEach(this::stop);
        return ids.size();
    }
}
