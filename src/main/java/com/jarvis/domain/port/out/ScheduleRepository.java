package com.jarvis.domain.port.out;

import com.jarvis.domain.model.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일정 저장소 — 날짜 범위 검색을 지원하는 아웃바운드 포트
 */
@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    /**
     * 시작 시간이 주어진 범위에 포함되는 일정을 조회
     */
    List<Schedule> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
}
