package com.jarvis.domain.port.out;

import com.jarvis.domain.model.DevJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DevJobRepository extends JpaRepository<DevJob, Long> {

    @Query("SELECT j FROM DevJob j ORDER BY j.createdAt DESC LIMIT 20")
    List<DevJob> findRecent();

    /** 가장 최근 완료된 비-JARVIS 잡 (수정 타깃용) */
    @Query("SELECT j FROM DevJob j WHERE j.status = 'DONE' AND j.jobType != 'MODIFY_JARVIS' ORDER BY j.completedAt DESC LIMIT 1")
    Optional<DevJob> findLastExternalCompleted();

    /** 가장 최근 완료된 잡 (JARVIS 포함) */
    @Query("SELECT j FROM DevJob j WHERE j.status = 'DONE' ORDER BY j.completedAt DESC LIMIT 1")
    Optional<DevJob> findLastCompleted();
}
