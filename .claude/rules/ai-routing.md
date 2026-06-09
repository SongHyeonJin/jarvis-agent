---
description: AI model routing rules — when to use Claude vs GPT
globs:
  - src/main/java/**/*Service*.java
  - src/main/java/**/*Tool*.java
  - src/main/java/**/*Executor*.java
  - src/main/java/**/*Adapter*.java
  - src/main/java/**/*Config*.java
  - src/main/resources/**/*.yml
---

# AI Model Routing Rules

## 모델 선택 기준

| 작업 유형 | 모델 | 이유 |
|---|---|---|
| 코드 생성·리뷰·분석 | Claude Sonnet | 복잡한 추론, 긴 컨텍스트 |
| AutoDev 스캐폴딩 | Claude Sonnet | 멀티파일 코드 생성 |
| 일반 대화 (ChatService) | GPT-4o-mini | 빠르고 저렴 |
| 브리핑·요약 (BriefingService) | GPT-4o-mini | 경량 포맷팅 |
| TTS 전처리 | GPT-4o-mini | 텍스트 정리 |

## 구현 규칙

**항상 Port 인터페이스를 통해 호출**
```java
// 올바른 방법
private final AiModelPort aiModelPort;
String result = aiModelPort.generate(prompt);

// 금지: Spring AI ChatClient 직접 주입
private final ChatClient chatClient; // ❌ Service에서 직접 금지
```

**application.yml에서 모델 설정 관리**
```yaml
anthropic:
  model: claude-sonnet-4-6   # 코드 작업용

ai:
  openai:
    model: gpt-4o-mini       # 일반 작업용
```

## ClaudeCliExecutor 사용 시
- Claude CLI를 subprocess로 실행하는 클래스
- 긴 코드 생성 작업, 파일 스캐폴딩에 사용
- 결과는 `DevJob`에 저장 후 비동기 폴링으로 전달
- 직접 HTTP 응답 스트리밍 금지 (타임아웃 위험)

## Spring AI @Tool 추가 시
- `tool/` 패키지에 클래스 생성
- `@Tool(description = "...")` — description은 Claude가 읽으므로 명확하게
- 도구 메서드는 단일 책임 원칙 준수
- 내부에서 Service 레이어 호출 (비즈니스 로직 직접 구현 금지)
