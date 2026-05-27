package com.jarvis.tool;

import com.jarvis.domain.model.Memo;
import com.jarvis.domain.port.in.MemoUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MemoTool implements ToolProvider {

    private final MemoUseCase memoUseCase;

    @Override
    public List<ToolFunction> getToolFunctions() {
        return List.of(addMemo(), listMemos(), searchMemos(), deleteMemo());
    }

    private ToolFunction addMemo() {
        return new ToolFunction() {
            @Override public String name() { return "addMemo"; }
            @Override public String description() { return "새로운 메모를 추가합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "title", Map.of("type", "string", "description", "메모 제목"),
                        "content", Map.of("type", "string", "description", "메모 내용"),
                        "tags", Map.of("type", "string", "description", "태그 (쉼표로 구분, 선택사항)")
                    ),
                    "required", List.of("title", "content")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                Memo memo = new Memo();
                memo.setTitle((String) args.get("title"));
                memo.setContent((String) args.get("content"));
                if (args.get("tags") != null) memo.setTags((String) args.get("tags"));
                Memo saved = memoUseCase.create(memo);
                return "메모 추가됨: [" + saved.getId() + "] " + saved.getTitle();
            }
        };
    }

    private ToolFunction listMemos() {
        return new ToolFunction() {
            @Override public String name() { return "listMemos"; }
            @Override public String description() { return "모든 메모 목록을 조회합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of());
            }
            @Override public String execute(Map<String, Object> args) {
                List<Memo> memos = memoUseCase.getAll();
                if (memos.isEmpty()) return "등록된 메모가 없습니다.";
                return memos.stream()
                        .map(m -> String.format("[%d] %s%s", m.getId(), m.getTitle(),
                                m.getTags() != null ? " [" + m.getTags() + "]" : ""))
                        .collect(Collectors.joining("\n"));
            }
        };
    }

    private ToolFunction searchMemos() {
        return new ToolFunction() {
            @Override public String name() { return "searchMemos"; }
            @Override public String description() { return "태그로 메모를 검색합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of("tag", Map.of("type", "string", "description", "검색할 태그")),
                    "required", List.of("tag")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                String tag = (String) args.get("tag");
                List<Memo> memos = memoUseCase.searchByTag(tag);
                if (memos.isEmpty()) return "'" + tag + "' 태그 메모가 없습니다.";
                return memos.stream()
                        .map(m -> "[" + m.getId() + "] " + m.getTitle())
                        .collect(Collectors.joining("\n"));
            }
        };
    }

    private ToolFunction deleteMemo() {
        return new ToolFunction() {
            @Override public String name() { return "deleteMemo"; }
            @Override public String description() { return "메모를 삭제합니다"; }
            @Override public Map<String, Object> parameters() {
                return Map.of(
                    "type", "object",
                    "properties", Map.of("id", Map.of("type", "integer", "description", "메모 ID")),
                    "required", List.of("id")
                );
            }
            @Override public String execute(Map<String, Object> args) {
                Long id = ((Number) args.get("id")).longValue();
                memoUseCase.delete(id);
                return "메모 [" + id + "] 삭제됨";
            }
        };
    }
}
