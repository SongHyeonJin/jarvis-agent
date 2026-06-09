---
description: Hexagonal architecture boundary rules for Java source files
globs:
  - src/main/java/**/*.java
---

# Hexagonal Architecture Rules

## Layer Boundaries — 이 규칙을 절대 위반하지 말 것

| 클래스 타입 | 위치 | 의존 허용 | 의존 금지 |
|---|---|---|---|
| Controller | `adapter/in/web/` | UseCase(port/in) | Service 직접 참조 |
| Service | `application/` | Port interfaces | Controller, JPA Repository 직접 |
| UseCase | `domain/port/in/` | Domain model | Service, Adapter |
| Repository port | `domain/port/out/` | Domain model | JPA, Spring |
| Domain model | `domain/model/` | 없음 (pure POJO) | 모든 외부 의존 금지 |
| AI Adapter | `adapter/out/ai/` | Spring AI SDK | Domain 직접 수정 금지 |
| DB Adapter | `adapter/out/persistence/` | JPA, Spring Data | Business logic 금지 |
| Tool | `tool/` | Service, Port | Controller |

## 신규 기능 추가 순서
1. `domain/model/` — 도메인 엔티티
2. `domain/port/in/` — UseCase 인터페이스 (`*UseCase.java`)
3. `domain/port/out/` — Repository 인터페이스 (`*Repository.java`)
4. `application/` — Service 구현 (`*Service.java`)
5. `adapter/in/web/` — Controller (`*Controller.java`)
6. `adapter/out/` — JPA/AI 어댑터 (필요 시)

## 금지 패턴
- Service에서 `@Autowired` 필드 주입 → `@RequiredArgsConstructor` 생성자 주입 사용
- Controller에서 JPA Repository 직접 주입 금지
- Domain model에 Spring/JPA 어노테이션 혼용 금지 (`@Entity`는 허용, Spring Bean 어노테이션 금지)
- Service에서 `HttpServletRequest` 등 웹 레이어 객체 직접 사용 금지
