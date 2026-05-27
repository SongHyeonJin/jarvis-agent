package com.jarvis.adapter.in.web;

import com.jarvis.application.TestService;
import com.jarvis.domain.model.Test;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestController {

    private final TestService testService;

    @GetMapping
    public ResponseEntity<List<Test>> getAll(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status) {
        if (title != null && !title.isBlank()) {
            return ResponseEntity.ok(testService.findByTitle(title));
        }
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(testService.findByStatus(status));
        }
        return ResponseEntity.ok(testService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Test> getById(@PathVariable Long id) {
        return testService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Test> create(@RequestBody Test test) {
        Test created = testService.create(test);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Test> update(@PathVariable Long id, @RequestBody Test test) {
        return testService.update(id, test)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        if (testService.delete(id)) {
            return ResponseEntity.ok(Map.of("message", "Test deleted successfully"));
        }
        return ResponseEntity.notFound().build();
    }
}