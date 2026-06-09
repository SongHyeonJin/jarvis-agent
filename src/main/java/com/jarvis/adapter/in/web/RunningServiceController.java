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

    private final RunningServiceService serviceService;

    @GetMapping
    public List<RunningServiceService.ServiceInfo> list() {
        return serviceService.list();
    }

    @DeleteMapping("/{port}")
    public ResponseEntity<Map<String, Object>> stopByPort(@PathVariable int port) {
        boolean stopped = serviceService.stopByPort(port);
        return ResponseEntity.ok(Map.of("port", port, "stopped", stopped));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> stopAll() {
        serviceService.stopAll();
        return ResponseEntity.ok(Map.of("stopped", true));
    }
}
