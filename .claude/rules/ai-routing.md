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

## 모델 선택 기준 (3-tier)

| 작업 유형 | 모델 | 이유 |
|---|---|---|
| 일반 대화 · 도구 호출 (ChatService) | Claude Sonnet 4.6 | AnthropicChatAdapter — 빠르고 균형잡힌 추론, 1M 컨텍스트 |
| 일반 NEW_PROJECT 스캐폴딩 | Claude Sonnet 4.6 | 멀티파일 코드 생성에 충분한 추론력 |
| **복잡 NEW_PROJECT** (MSA·풀스택·결제·보안 등) | **Claude Opus 4.8** | 대형 아키텍처 설계 추론력 필요, 비용 정당화됨 |
| MODIFY_JARVIS · MODIFY_EXTERNAL | Claude Sonnet 4.6 | 기존 코드 편집은 Sonnet으로 충분 |
| 단순 UI/스타일 수정 | Claude Haiku | 빠르고 저렴, 단순 변경에 충분 |

## DevJobService 모델 자동 선택 (`selectModel`)

`DevJobService.selectModel(command, jobType)` 이 명령 키워드를 분석해 Haiku/Sonnet/Opus를 자동 선택한다.

| 판단 기준 | 선택 모델 |
|---|---|
| NEW_PROJECT + `풀스택`, `MSA`, `마이크로서비스`, `결제`, `OAuth`, `플랫폼`, `헥사고날` 등 | **Opus 4.8** |
| NEW_PROJECT (일반) | Sonnet 4.6 |
| MODIFY_* + `색상`, `폰트`, `여백`, `배경`, `위치` 등 | Haiku |
| 그 외 기본값 | Sonnet 4.6 |

모델 ID:
- Opus: `claude-opus-4-8`
- Sonnet: `claude-sonnet-4-6`
- Haiku: `claude-haiku-4-5-20251001`

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
