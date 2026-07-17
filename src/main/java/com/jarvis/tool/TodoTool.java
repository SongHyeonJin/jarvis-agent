package com.jarvis.tool;

import com.jarvis.domain.model.Todo;
import com.jarvis.domain.port.in.TodoUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TodoTool implements ToolProvider {

    private final TodoUseCase todoUseCase;

    @Tool(description = "새로운 할 일을 추가합니다")
    public String addTodo(String title,
                           @ToolParam(description = "할 일 설명", required = false) String description,
                           @ToolParam(description = "마감일 (yyyy-MM-dd)", required = false) String dueDate) {
        Todo todo = new Todo();
        todo.setTitle(title);
        todo.setDescription(description);
        if (dueDate != null) {
            todo.setDueDate(LocalDate.parse(dueDate));
        }
        Todo saved = todoUseCase.create(todo);
        return "할 일 추가됨: [" + saved.getId() + "] " + saved.getTitle();
    }

    @Tool(description = "모든 할 일 목록을 조회합니다")
    public String listTodos() {
        List<Todo> todos = todoUseCase.getAll();
        if (todos.isEmpty()) return "등록된 할 일이 없습니다.";
        return todos.stream()
                .map(t -> String.format("[%d] %s %s%s",
                        t.getId(), t.isDone() ? "완료" : "미완료", t.getTitle(),
                        t.getDueDate() != null ? " (~" + t.getDueDate() + ")" : ""))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "할 일 완료 상태를 토글합니다")
    public String toggleTodo(@ToolParam(description = "할 일 ID") Long id) {
        Todo todo = todoUseCase.toggleDone(id);
        return (todo.isDone() ? "완료" : "미완료") + ": " + todo.getTitle();
    }

    @Tool(description = "할 일을 삭제합니다")
    public String deleteTodo(@ToolParam(description = "할 일 ID") Long id) {
        todoUseCase.delete(id);
        return "할 일 [" + id + "] 삭제됨";
    }
}
