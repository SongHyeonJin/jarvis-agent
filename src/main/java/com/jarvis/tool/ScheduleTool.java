package com.jarvis.tool;

import com.jarvis.domain.model.Schedule;
import com.jarvis.domain.port.in.ScheduleUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScheduleTool implements ToolProvider {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ScheduleUseCase scheduleUseCase;

    @Tool(description = "새로운 일정을 추가합니다")
    public String addSchedule(String title,
                               @ToolParam(description = "일정 설명", required = false) String description,
                               @ToolParam(description = "시작 시간 (yyyy-MM-dd HH:mm)") String startTime,
                               @ToolParam(description = "종료 시간 (yyyy-MM-dd HH:mm)", required = false) String endTime) {
        Schedule s = new Schedule();
        s.setTitle(title);
        s.setDescription(description);
        s.setStartTime(LocalDateTime.parse(startTime, FMT));
        if (endTime != null) s.setEndTime(LocalDateTime.parse(endTime, FMT));
        Schedule saved = scheduleUseCase.create(s);
        return "일정 추가됨: [" + saved.getId() + "] " + saved.getTitle() + " (" + saved.getStartTime().format(FMT) + ")";
    }

    @Tool(description = "모든 일정 목록을 조회합니다")
    public String listSchedules() {
        List<Schedule> schedules = scheduleUseCase.getAll();
        if (schedules.isEmpty()) return "등록된 일정이 없습니다.";
        return schedules.stream()
                .map(s -> String.format("[%d] %s (%s%s)", s.getId(), s.getTitle(),
                        s.getStartTime().format(FMT),
                        s.getEndTime() != null ? " ~ " + s.getEndTime().format(FMT) : ""))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "특정 기간의 일정을 조회합니다")
    public String listSchedulesByDateRange(@ToolParam(description = "시작일 (yyyy-MM-dd HH:mm)") String from,
                                            @ToolParam(description = "종료일 (yyyy-MM-dd HH:mm)") String to) {
        LocalDateTime start = LocalDateTime.parse(from, FMT);
        LocalDateTime end = LocalDateTime.parse(to, FMT);
        List<Schedule> schedules = scheduleUseCase.findByDateRange(start, end);
        if (schedules.isEmpty()) return "해당 기간 일정이 없습니다.";
        return schedules.stream()
                .map(s -> "[" + s.getId() + "] " + s.getTitle() + " (" + s.getStartTime().format(FMT) + ")")
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "일정을 삭제합니다")
    public String deleteSchedule(@ToolParam(description = "일정 ID") Long id) {
        scheduleUseCase.delete(id);
        return "일정 [" + id + "] 삭제됨";
    }
}
