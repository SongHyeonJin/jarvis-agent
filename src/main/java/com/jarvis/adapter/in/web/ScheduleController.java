package com.jarvis.adapter.in.web;

import com.jarvis.domain.model.Schedule;
import com.jarvis.domain.port.in.ScheduleUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ScheduleUseCase scheduleUseCase;

    @GetMapping
    public ResponseEntity<List<Schedule>> getAll(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        if (from != null && to != null) {
            LocalDateTime start = LocalDateTime.parse(from, FMT);
            LocalDateTime end = LocalDateTime.parse(to, FMT);
            return ResponseEntity.ok(scheduleUseCase.findByDateRange(start, end));
        }
        return ResponseEntity.ok(scheduleUseCase.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Schedule> getById(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleUseCase.getById(id));
    }

    @PostMapping
    public ResponseEntity<Schedule> create(@RequestBody Schedule schedule) {
        return ResponseEntity.ok(scheduleUseCase.create(schedule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Schedule> update(@PathVariable Long id, @RequestBody Schedule schedule) {
        return ResponseEntity.ok(scheduleUseCase.update(id, schedule));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }
}
