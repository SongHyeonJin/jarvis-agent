package com.jarvis.domain.port.in;

import com.jarvis.domain.model.Todo;

import java.util.List;

/**
 * 할 일 관리 인바운드 포트 — CRUD 및 완료 토글
 */
public interface TodoUseCase {
    Todo create(Todo todo);
    List<Todo> getAll();
    Todo getById(Long id);
    Todo update(Long id, Todo todo);
    void delete(Long id);
    Todo toggleDone(Long id);
}
