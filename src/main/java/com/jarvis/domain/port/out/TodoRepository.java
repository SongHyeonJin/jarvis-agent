package com.jarvis.domain.port.out;

import com.jarvis.domain.model.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 할 일 저장소 — Spring Data JPA 기반 아웃바운드 포트
 */
@Repository
public interface TodoRepository extends JpaRepository<Todo, Long> {
}
