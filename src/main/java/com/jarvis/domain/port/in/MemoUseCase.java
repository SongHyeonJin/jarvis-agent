package com.jarvis.domain.port.in;

import com.jarvis.domain.model.Memo;

import java.util.List;

/**
 * 메모 관리 인바운드 포트 — CRUD 및 태그 검색
 */
public interface MemoUseCase {
    Memo create(Memo memo);
    List<Memo> getAll();
    Memo getById(Long id);
    Memo update(Long id, Memo memo);
    void delete(Long id);
    List<Memo> searchByTag(String tag);
}
