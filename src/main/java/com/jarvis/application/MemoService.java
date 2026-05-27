package com.jarvis.application;

import com.jarvis.domain.model.Memo;
import com.jarvis.domain.port.in.MemoUseCase;
import com.jarvis.domain.port.out.MemoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class MemoService implements MemoUseCase {

    private final MemoRepository memoRepository;

    @Override
    public Memo create(Memo memo) {
        return memoRepository.save(memo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Memo> getAll() {
        return memoRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Memo getById(Long id) {
        return memoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메모를 찾을 수 없습니다: " + id));
    }

    @Override
    public Memo update(Long id, Memo updated) {
        Memo memo = getById(id);
        memo.setTitle(updated.getTitle());
        memo.setContent(updated.getContent());
        memo.setTags(updated.getTags());
        return memoRepository.save(memo);
    }

    @Override
    public void delete(Long id) {
        memoRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Memo> searchByTag(String tag) {
        return memoRepository.findByTagsContaining(tag);
    }
}
