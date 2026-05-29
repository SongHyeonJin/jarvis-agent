---
description: Add a new Spring AI @Tool function to the Jarvis tool layer
---

# /add-tool $ARGUMENTS

`$ARGUMENTS`를 도구 이름으로 사용하여 Spring AI @Tool 클래스를 생성합니다.

## 생성 위치
`src/main/java/com/jarvis/tool/<Name>Tool.java`

## 템플릿

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class <Name>Tool {

    private final <Name>UseCase <name>UseCase; // Service가 아닌 UseCase 인터페이스 주입

    @Tool(description = "<도구가 하는 일을 Claude가 이해할 수 있도록 명확하게 한국어로 설명>")
    public String <toolMethodName>(<ToolInput> input) {
        log.info("<Name>Tool called: input={}", input);
        try {
            // UseCase 호출
            var result = <name>UseCase.process(input);
            return result.toString();
        } catch (Exception e) {
            log.error("<Name>Tool failed: {}", e.getMessage(), e);
            return "오류가 발생했습니다: " + e.getMessage();
        }
    }
}
```

## ToolProvider 등록
생성 후 `tool/ToolProvider.java`에 새 Tool을 등록해야 합니다:
```java
// ToolProvider.java의 tools() 메서드에 추가
tools.add(<name>Tool);
```

## @Tool description 작성 원칙
- Claude가 언제 이 도구를 호출해야 하는지 명확히 설명
- 입력 파라미터와 반환값 형식 기술
- 예시: `"사용자의 Todo 항목을 생성합니다. title(제목)과 optional한 dueDate(마감일)를 받습니다."`

## 주의사항
- Tool 클래스 내부에 비즈니스 로직 직접 구현 금지 → Service/UseCase 위임
- 반환 타입은 String (Claude가 파싱하기 쉽게)
- 예외는 반드시 catch하여 한국어 에러 메시지 반환
