---
description: Java code style and Lombok usage rules
globs:
  - src/main/java/**/*.java
---

# Code Style Rules

## Lombok — 항상 사용
```java
@Slf4j                          // 로깅: log.info(), log.error()
@RequiredArgsConstructor        // 생성자 주입 (final 필드 자동 생성)
@Getter                         // DTO, 도메인 모델에 사용
@Builder                        // 복잡한 객체 생성 시
```
- `@Data` 사용 금지 — equals/hashCode 무한루프 위험
- 수동 getter/setter/constructor 작성 금지

## 네이밍
- 클래스: `PascalCase` (예: `DevJobService`, `AutoDevTool`)
- 메서드/변수: `camelCase` (예: `processJob`, `jobStatus`)
- 상수: `UPPER_SNAKE_CASE`
- REST endpoint: `kebab-case` (예: `/api/dev-jobs`)

## 응답 패턴
```java
// 성공 응답 — ResponseEntity 사용
return ResponseEntity.ok(result);
return ResponseEntity.created(uri).body(result);

// 에러 응답 — GlobalExceptionHandler에서 처리
// Controller에서 직접 에러 응답 구성 금지
```

## 로깅
```java
log.info("Processing job: jobId={}, type={}", jobId, type);   // 구조적 로깅
log.error("Failed to process: {}", e.getMessage(), e);        // 예외는 마지막 인자
// System.out.println 절대 금지
```

## 금지 패턴
- `public` 필드 (Lombok 어노테이션으로 대체)
- 빈 catch 블록 (`catch(Exception e) {}`)
- Magic number (상수로 추출)
- `@Transactional`을 Controller에 붙이는 것
