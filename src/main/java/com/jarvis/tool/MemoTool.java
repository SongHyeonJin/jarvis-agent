package com.jarvis.tool;

import com.jarvis.domain.model.Memo;
import com.jarvis.domain.port.in.MemoUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MemoTool implements ToolProvider {

    private final MemoUseCase memoUseCase;

    @Tool(description = "새로운 메모를 추가합니다")
    public String addMemo(String title, String content,
                           @ToolParam(description = "태그 (쉼표로 구분)", required = false) String tags) {
        Memo memo = new Memo();
        memo.setTitle(title);
        memo.setContent(content);
        if (tags != null) memo.setTags(tags);
        Memo saved = memoUseCase.create(memo);
        return "메모 추가됨: [" + saved.getId() + "] " + saved.getTitle();
    }

    @Tool(description = "모든 메모 목록을 조회합니다")
    public String listMemos() {
        List<Memo> memos = memoUseCase.getAll();
        if (memos.isEmpty()) return "등록된 메모가 없습니다.";
        return memos.stream()
                .map(m -> String.format("[%d] %s%s", m.getId(), m.getTitle(),
                        m.getTags() != null ? " [" + m.getTags() + "]" : ""))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "태그로 메모를 검색합니다")
    public String searchMemos(@ToolParam(description = "검색할 태그") String tag) {
        List<Memo> memos = memoUseCase.searchByTag(tag);
        if (memos.isEmpty()) return "'" + tag + "' 태그 메모가 없습니다.";
        return memos.stream()
                .map(m -> "[" + m.getId() + "] " + m.getTitle())
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "메모를 삭제합니다")
    public String deleteMemo(@ToolParam(description = "메모 ID") Long id) {
        memoUseCase.delete(id);
        return "메모 [" + id + "] 삭제됨";
    }
}
