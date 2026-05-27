package com.jarvis.application;

import com.jarvis.domain.model.Todo;
import com.jarvis.domain.port.in.TodoUseCase;
import com.jarvis.domain.port.out.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class TodoService implements TodoUseCase {

    private final TodoRepository todoRepository;

    @Override
    public Todo create(Todo todo) {
        return todoRepository.save(todo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Todo> getAll() {
        return todoRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Todo getById(Long id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("할 일을 찾을 수 없습니다: " + id));
    }

    @Override
    public Todo update(Long id, Todo updated) {
        Todo todo = getById(id);
        todo.setTitle(updated.getTitle());
        todo.setDescription(updated.getDescription());
        todo.setDueDate(updated.getDueDate());
        todo.setPriority(updated.getPriority());
        return todoRepository.save(todo);
    }

    @Override
    public void delete(Long id) {
        todoRepository.deleteById(id);
    }

    @Override
    public Todo toggleDone(Long id) {
        Todo todo = getById(id);
        todo.setDone(!todo.isDone());
        return todoRepository.save(todo);
    }
}
