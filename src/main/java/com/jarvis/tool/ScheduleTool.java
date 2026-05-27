package com.jarvis.tool;

import com.jarvis.domain.model.Schedule;
import com.jarvis.domain.port.in.ScheduleUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScheduleTool implements ToolProvider {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ScheduleUseCase scheduleUseCase;

    @Override
    public List<ToolFunction> getToolFunctions() {
        return List.of(addSchedule(), listSchedules(), listSchedulesByDateRange(), deleteSchedule());
    }

    private ToolFunction addSchedule() {
        return new ToolFunction() {
            @Override public String name() { return "addSchedule"; }
            @Override public String description() { return "새로운 일정을 추가합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "title", Map.of("type", "string", "description", "일정 제목"),
                        "description", Map.of("type", "string", "description", "일정 설명 (선택)"),
                        "startTime", Map.of("type", "string", "description", "시작 시간 (yyyy-MM-dd HH:mm)"),
                        "endTime", Map.of("type", "string", "description", "종료 시간 (yyyy-MM-dd HH:mm, 선택)")
                    ),
                    "required", List.of("title", "startTime")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                Schedule s = new Schedule();
                s.setTitle((String) args.get("title"));
                s.setDescription((String) args.get("description"));
                s.setStartTime(LocalDateTime.parse((String) args.get("startTime"), FMT));
                if (args.get("endTime") != null) s.setEndTime(LocalDateTime.parse((String) args.get("endTime"), FMT));
                Schedule saved = scheduleUseCase.create(s);
                return "일정 추가됨: [" + saved.getId() + "] " + saved.getTitle() + " (" + saved.getStartTime().format(FMT) + ")";
            }
        };
    }

    private ToolFunction listSchedules() {
        return new ToolFunction() {
            @Override public String name() { return "listSchedules"; }
            @Override public String description() { return "모든 일정 목록을 조회합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of());
            }
            @Override public String execute(Map<String, Object> args) {
                List<Schedule> schedules = scheduleUseCase.getAll();
                if (schedules.isEmpty()) return "등록된 일정이 없습니다.";
                return schedules.stream()
                        .map(s -> String.format("[%d] %s (%s%s)", s.getId(), s.getTitle(),
                                s.getStartTime().format(FMT),
                                s.getEndTime() != null ? " ~ " + s.getEndTime().format(FMT) : ""))
                        .collect(Collectors.joining("\n"));
            }
        };
    }

    private ToolFunction listSchedulesByDateRange() {
        return new ToolFunction() {
            @Override public String name() { return "listSchedulesByDateRange"; }
            @Override public String description() { return "특정 기간의 일정을 조회합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "from", Map.of("type", "string", "description", "시작일 (yyyy-MM-dd HH:mm)"),
                        "to", Map.of("type", "string", "description", "종료일 (yyyy-MM-dd HH:mm)")
                    ),
                    "required", List.of("from", "to")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                LocalDateTime start = LocalDateTime.parse((String) args.get("from"), FMT);
                LocalDateTime end = LocalDateTime.parse((String) args.get("to"), FMT);
                List<Schedule> schedules = scheduleUseCase.findByDateRange(start, end);
                if (schedules.isEmpty()) return "해당 기간 일정이 없습니다.";
                return schedules.stream()
                        .map(s -> "[" + s.getId() + "] " + s.getTitle() + " (" + s.getStartTime().format(FMT) + ")")
                        .collect(Collectors.joining("\n"));
            }
        };
    }

    private ToolFunction deleteSchedule() {
        return new ToolFunction() {
            @Override public String name() { return "deleteSchedule"; }
            @Override public String description() { return "일정을 삭제합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of("id", Map.of("type", "integer", "description", "일정 ID")),
                    "required", List.of("id")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                Long id = ((Number) args.get("id")).longValue();
                scheduleUseCase.delete(id);
                return "일정 [" + id + "] 삭제됨";
            }
        };
    }
}
