package com.jarvis.domain.port.out;

import com.jarvis.domain.model.ClaudePermissionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaudePermissionEventRepository extends JpaRepository<ClaudePermissionEvent, Long> {

    List<ClaudePermissionEvent> findTop20ByOrderByCreatedAtDesc();

    List<ClaudePermissionEvent> findByAcknowledgedFalseOrderByCreatedAtDesc();
}
