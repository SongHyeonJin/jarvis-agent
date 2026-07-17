package com.jarvis.domain.port.out;

import com.jarvis.domain.model.Memo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 메모 저장소 — 태그 검색을 지원하는 아웃바운드 포트
 */
@Repository
public interface MemoRepository extends JpaRepository<Memo, Long> {

    /**
     * 쉼표로 구분된 tags 컬럼에서 특정 태그를 포함하는 메모를 검색
     */
    @Query("SELECT m FROM Memo m WHERE m.tags LIKE %:tag%")
    List<Memo> findByTagsContaining(@Param("tag") String tag);
}
