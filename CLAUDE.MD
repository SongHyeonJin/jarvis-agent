# Jarvis Agent

**Stack:** Spring Boot 3.x · Java 21 · Gradle Kotlin DSL · Spring AI · H2  
**Server:** `localhost:8081` · `gradlew.bat bootRun`

## 절대 규칙 (항상 적용)
- UI/응답 메시지는 한국어. 코드·로그는 영어.
- 비즈니스 로직은 Service 계층에만. Controller는 위임만.
- Lombok 필수 (`@RequiredArgsConstructor`, `@Slf4j`, `@Getter`).
- 예외 처리는 `@RestControllerAdvice`에서 전역 처리.
- **개발 요청 시 Java/Spring Boot 우선 사용.** 구현이 간단하거나 다른 언어가 필수적인 경우에만 다른 언어 허용.

## 패키지 구조
```
adapter/in/web/     → Controllers
application/        → Services (business logic)
domain/port/in/     → UseCase interfaces
domain/port/out/    → Repository / AI port interfaces
domain/model/       → Domain entities
adapter/out/        → DB · AI implementations
tool/               → Spring AI @Tool classes
```

## AI 라우팅
- **ChatService (대화·도구호출)** → `MultiModelChatAdapter`(Spring AI `ChatClient`)가 `ModelRouter`의 판단에 따라 Claude Opus/Sonnet/Haiku 또는 로컬 **Ollama**(`llama3.2:1b`) 중 하나로 자동 라우팅
  - Opus: 대규모·복잡 아키텍처 키워드 (MSA·풀스택·결제·플랫폼 등)
  - Haiku: 짧은 일반 요청
  - Ollama: 인사말 등 도구·추론이 불필요한 잡담 (로컬 무비용 처리)
  - Sonnet: 기본값
- **ClaudeCliExecutor (NEW_PROJECT·MODIFY)** → `DevJobService.selectModel()`이 Opus(복잡 아키텍처)/Sonnet(일반)/Haiku(단순 UI 수정)를 CLI `--model` 플래그로 전달 — ChatService 라우팅과는 별개 경로
- AI 직접 호출 금지 — 반드시 `AiModelPort` 인터페이스를 통해 라우팅.

## 커스텀 커맨드
- `/new-feature <Name>` — 전 계층 스캐폴딩 (Domain → Port → Service → Controller)
- `/add-tool <Name>` — Spring AI @Tool 추가
- `/run-server` — 서버 상태 확인 및 기동

## 상세 규칙
- 아키텍처 경계 → `.claude/rules/architecture.md`
- 코딩 스타일 → `.claude/rules/code-style.md`
- AI 모델 라우팅 → `.claude/rules/ai-routing.md`

## .md 자기 유지 규칙 (구조 변경 시 반드시 같이 수정)

| 변경 내용 | 업데이트할 파일 |
|---|---|
| 새 패키지 추가 | `CLAUDE.md` 패키지맵 + `rules/architecture.md` 레이어 표 |
| 새 AI 모델 추가 | `CLAUDE.md` AI 라우팅 + `rules/ai-routing.md` 모델 표 |
| 새 파일 명명 패턴 (`*Handler*` 등) | `rules/ai-routing.md` globs |
| 새 커맨드 추가 | `CLAUDE.md` 커맨드 목록 |
| 아키텍처 레이어 추가/변경 | `rules/architecture.md` 레이어 경계 표 + `commands/new-feature.md` 생성 순서 |
