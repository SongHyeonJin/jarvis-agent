package com.jarvis.adapter.in.web;

import com.jarvis.domain.model.Memo;
import com.jarvis.domain.port.in.MemoUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/memos")
@RequiredArgsConstructor
public class MemoController {

    private final MemoUseCase memoUseCase;

    @GetMapping
    public ResponseEntity<List<Memo>> getAll(@RequestParam(required = false) String tag) {
        if (tag != null && !tag.isBlank()) {
            return ResponseEntity.ok(memoUseCase.searchByTag(tag));
        }
        return ResponseEntity.ok(memoUseCase.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Memo> getById(@PathVariable Long id) {
        return ResponseEntity.ok(memoUseCase.getById(id));
    }

    @PostMapping
    public ResponseEntity<Memo> create(@RequestBody Memo memo) {
        return ResponseEntity.ok(memoUseCase.create(memo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Memo> update(@PathVariable Long id, @RequestBody Memo memo) {
        return ResponseEntity.ok(memoUseCase.update(id, memo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        memoUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }
}
