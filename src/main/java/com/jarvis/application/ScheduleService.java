package com.jarvis.application;

import com.jarvis.domain.model.Schedule;
import com.jarvis.domain.port.in.ScheduleUseCase;
import com.jarvis.domain.port.out.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ScheduleService implements ScheduleUseCase {

    private final ScheduleRepository scheduleRepository;

    @Override
    public Schedule create(Schedule schedule) {
        return scheduleRepository.save(schedule);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Schedule> getAll() {
        return scheduleRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Schedule getById(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다: " + id));
    }

    @Override
    public Schedule update(Long id, Schedule updated) {
        Schedule schedule = getById(id);
        schedule.setTitle(updated.getTitle());
        schedule.setDescription(updated.getDescription());
        schedule.setStartTime(updated.getStartTime());
        schedule.setEndTime(updated.getEndTime());
        return scheduleRepository.save(schedule);
    }

    @Override
    public void delete(Long id) {
        scheduleRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Schedule> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return scheduleRepository.findByStartTimeBetween(start, end);
    }
}
