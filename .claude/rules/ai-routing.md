---
description: AI model routing rules — when to use Claude vs GPT
globs:
  - src/main/java/**/*Service*.java
  - src/main/java/**/*Tool*.java
  - src/main/java/**/*Executor*.java
  - src/main/java/**/*Adapter*.java
  - src/main/java/**/*Router*.java
  - src/main/java/**/*Config*.java
  - src/main/resources/**/*.yml
---

# AI Model Routing Rules

## 모델 선택 기준

| 작업 유형 | 모델 | 이유 |
|---|---|---|
| 대화 · 도구 호출 — 복잡 아키텍처 질문 (ChatService) | Claude Opus 4.8 | `MultiModelChatAdapter` + `ModelRouter` — 대형 아키텍처 추론력 필요 |
| 대화 · 도구 호출 — 일반 (ChatService) | Claude Sonnet 4.6 | 빠르고 균형잡힌 추론, 기본값 |
| 대화 · 도구 호출 — 짧은 요청 (ChatService) | Claude Haiku | 빠르고 저렴 |
| 대화 · 도구 호출 — 인사말 등 잡담 (ChatService) | **로컬 Ollama** (`llama3.2:1b`) | 추론·도구 호출이 불필요한 요청은 로컬에서 무비용 처리 |
| 일반 NEW_PROJECT 스캐폴딩 | Claude Sonnet 4.6 | 멀티파일 코드 생성에 충분한 추론력 |
| **복잡 NEW_PROJECT** (MSA·풀스택·결제·보안 등) | **Claude Opus 4.8** | 대형 아키텍처 설계 추론력 필요, 비용 정당화됨 |
| MODIFY_JARVIS · MODIFY_EXTERNAL | Claude Sonnet 4.6 | 기존 코드 편집은 Sonnet으로 충분 |
| 단순 UI/스타일 수정 | Claude Haiku | 빠르고 저렴, 단순 변경에 충분 |

## ChatService 모델 자동 선택 (`ModelRouter`)

`ModelRouter.route(userMessage)`가 메시지 키워드·길이를 분석해 Opus/Sonnet/Haiku/Ollama 중 하나를 고르고,
`MultiModelChatAdapter`(`AiModelPort` 구현체, `adapter/out/ai/`)가 Spring AI `ChatClient`로 해당 모델을 호출한다.
Opus·Sonnet·Haiku는 `AnthropicChatModel` 한 인스턴스를 재사용하며 `AnthropicChatOptions.model(...)`로 모델만 바꾼다.
Ollama는 별도 `OllamaChatModel` 기반 `ChatClient`를 사용한다.

| 판단 기준 | 선택 모델 |
|---|---|
| `풀스택`, `MSA`, `마이크로서비스`, `결제`, `OAuth`, `플랫폼`, `헥사고날` 등 | Opus |
| 12자 이하 + 인사말/잡담 키워드 (`안녕`, `hi`, `고마워` 등) | Ollama |
| 20자 이하 | Haiku |
| 그 외 기본값 | Sonnet |

## DevJobService 모델 자동 선택 (`selectModel`)

`DevJobService.selectModel(command, jobType)` 이 명령 키워드를 분석해 Haiku/Sonnet/Opus를 자동 선택한다 (CLI 경로 — ChatService의 `ModelRouter`와는 별개).

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
- Ollama(로컬): `llama3.2:1b` — `jarvis.ai.model.*` (`application.yml`)에서 4개 모델 ID 모두 관리

## AnthropicChatModel 커스텀 빈 — temperature 주의

`AnthropicChatConfig`(`adapter/out/ai/`)가 Spring Boot 자동설정의 `AnthropicChatModel` 빈을 오버라이드한다.
자동설정 기본값은 `temperature`를 항상 채워 넣는데, `claude-opus-4-8`처럼 일부 모델은 `temperature` 파라미터
자체를 거부(`HTTP 400 temperature is deprecated for this model`)하므로 기본 옵션에 `temperature`를 지정하지 않는다.
새 Anthropic 모델을 추가할 때 이 제약을 우선 확인할 것.

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
# ChatService 라우팅용 (MultiModelChatAdapter / ModelRouter)
jarvis:
  ai:
    model:
      opus: claude-opus-4-8
      sonnet: claude-sonnet-4-6
      haiku: claude-haiku-4-5-20251001
      ollama: llama3.2:1b

spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
    ollama:
      base-url: http://localhost:11434

# 레거시 — ConversationSummaryService·TtsService 등 AiModelPort 밖에서 직접 WebClient 호출하는 곳
anthropic:
  model: claude-sonnet-4-6

ai:
  openai:
    model: gpt-4o-mini
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
