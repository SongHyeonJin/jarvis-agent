package com.jarvis.tool;

import com.jarvis.domain.model.Todo;
import com.jarvis.domain.port.in.TodoUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TodoTool implements ToolProvider {

    private final TodoUseCase todoUseCase;

    @Override
    public List<ToolFunction> getToolFunctions() {
        return List.of(addTodo(), listTodos(), toggleTodo(), deleteTodo());
    }

    private ToolFunction addTodo() {
        return new ToolFunction() {
            @Override public String name() { return "addTodo"; }
            @Override public String description() { return "새로운 할 일을 추가합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "title", Map.of("type", "string", "description", "할 일 제목"),
                        "description", Map.of("type", "string", "description", "할 일 설명 (선택)"),
                        "dueDate", Map.of("type", "string", "description", "마감일 (yyyy-MM-dd, 선택)")
                    ),
                    "required", List.of("title")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                Todo todo = new Todo();
                todo.setTitle((String) args.get("title"));
                todo.setDescription((String) args.get("description"));
                if (args.get("dueDate") != null) {
                    todo.setDueDate(LocalDate.parse((String) args.get("dueDate")));
                }
                Todo saved = todoUseCase.create(todo);
                return "할 일 추가됨: [" + saved.getId() + "] " + saved.getTitle();
            }
        };
    }

    private ToolFunction listTodos() {
        return new ToolFunction() {
            @Override public String name() { return "listTodos"; }
            @Override public String description() { return "모든 할 일 목록을 조회합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of());
            }
            @Override public String execute(Map<String, Object> args) {
                List<Todo> todos = todoUseCase.getAll();
                if (todos.isEmpty()) return "등록된 할 일이 없습니다.";
                return todos.stream()
                        .map(t -> String.format("[%d] %s %s%s",
                                t.getId(), t.isDone() ? "✅" : "⬜", t.getTitle(),
                                t.getDueDate() != null ? " (~" + t.getDueDate() + ")" : ""))
                        .collect(Collectors.joining("\n"));
            }
        };
    }

    private ToolFunction toggleTodo() {
        return new ToolFunction() {
            @Override public String name() { return "toggleTodo"; }
            @Override public String description() { return "할 일 완료 상태를 토글합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of("id", Map.of("type", "integer", "description", "할 일 ID")),
                    "required", List.of("id")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                Long id = ((Number) args.get("id")).longValue();
                Todo todo = todoUseCase.toggleDone(id);
                return (todo.isDone() ? "✅ 완료" : "⬜ 미완료") + ": " + todo.getTitle();
            }
        };
    }

    private ToolFunction deleteTodo() {
        return new ToolFunction() {
            @Override public String name() { return "deleteTodo"; }
            @Override public String description() { return "할 일을 삭제합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of("id", Map.of("type", "integer", "description", "할 일 ID")),
                    "required", List.of("id")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                Long id = ((Number) args.get("id")).longValue();
                todoUseCase.delete(id);
                return "할 일 [" + id + "] 삭제됨";
            }
        };
    }
}
