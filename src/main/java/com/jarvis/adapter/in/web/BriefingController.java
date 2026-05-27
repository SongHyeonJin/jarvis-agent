package com.jarvis.adapter.in.web;

import com.jarvis.application.BriefingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/briefing")
@RequiredArgsConstructor
public class BriefingController {

    private final BriefingService briefingService;

    @GetMapping("/daily")
    public ResponseEntity<Map<String, Object>> daily() {
        return ResponseEntity.ok(briefingService.getDailyBriefing());
    }
}
