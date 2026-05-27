package com.jarvis.adapter.in.web;

import com.jarvis.application.HelloService;
import com.jarvis.domain.model.Hello;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/hello")
@RequiredArgsConstructor
public class HelloController {

    private final HelloService helloService;

    @GetMapping
    public ResponseEntity<List<Hello>> getAll(
            @RequestParam(required = false) String title) {
        if (title != null && !title.isBlank()) {
            return ResponseEntity.ok(helloService.findByTitle(title));
        }
        return ResponseEntity.ok(helloService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Hello> getById(@PathVariable Long id) {
        return helloService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Hello> create(@RequestBody Hello hello) {
        Hello created = helloService.create(hello);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Hello> update(@PathVariable Long id, @RequestBody Hello hello) {
        return helloService.update(id, hello)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        if (helloService.delete(id)) {
            return ResponseEntity.ok(Map.of("message", "삭제되었습니다."));
        }
        return ResponseEntity.notFound().build();
    }
}
