package com.jarvis.domain.port.in;

import com.jarvis.domain.model.Schedule;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일정 관리 인바운드 포트 — CRUD 및 날짜 범위 검색
 */
public interface ScheduleUseCase {
    Schedule create(Schedule schedule);
    List<Schedule> getAll();
    Schedule getById(Long id);
    Schedule update(Long id, Schedule schedule);
    void delete(Long id);
    List<Schedule> findByDateRange(LocalDateTime start, LocalDateTime end);
}
