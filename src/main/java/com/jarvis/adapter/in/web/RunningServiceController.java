package com.jarvis.adapter.in.web;

import com.jarvis.application.RunningServiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class RunningServiceController {

    private final RunningServiceService runningServiceService;

    @GetMapping
    public List<RunningServiceService.ServiceInfo> list() {
        return runningServiceService.list();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<RunningServiceService.ServiceInfo> stop(@PathVariable Long id) {
        return runningServiceService.stop(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> stopAll() {
        int count = runningServiceService.stopAll();
        return ResponseEntity.ok(Map.of("stopped", count));
    }
}
