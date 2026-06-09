---
description: Scaffold a complete new feature across all hexagonal architecture layers
---

# /new-feature $ARGUMENTS

`$ARGUMENTS`를 기능 이름으로 사용하여 헥사고날 아키텍처 전 계층에 스캐폴딩을 생성합니다.

## 생성 순서 (반드시 이 순서대로)

### 1. Domain Model — `domain/model/<Name>.java`
```java
@Entity
@Table(name = "<name>s")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class <Name> {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 핵심 필드만
    private LocalDateTime createdAt;
}
```

### 2. UseCase Interface — `domain/port/in/<Name>UseCase.java`
```java
public interface <Name>UseCase {
    <Name> create(<Name>Command command);
    <Name> findById(Long id);
    List<<Name>> findAll();
    void delete(Long id);
}
```

### 3. Repository Interface — `domain/port/out/<Name>Repository.java`
```java
public interface <Name>Repository {
    <Name> save(<Name> entity);
    Optional<<Name>> findById(Long id);
    List<<Name>> findAll();
    void deleteById(Long id);
}
```

### 4. Service — `application/<Name>Service.java`
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class <Name>Service implements <Name>UseCase {
    private final <Name>Repository <name>Repository;
    // UseCase 메서드 구현
}
```

### 5. Controller — `adapter/in/web/<Name>Controller.java`
```java
@RestController
@RequestMapping("/api/<names>")
@RequiredArgsConstructor
@Slf4j
public class <Name>Controller {
    private final <Name>UseCase <name>UseCase;
    // CRUD 엔드포인트: GET, POST, DELETE
}
```

### 6. JPA Adapter (필요 시) — `adapter/out/persistence/<Name>JpaRepository.java`
Spring Data JPA interface 생성.

## 주의사항
- 응답 메시지(log, API response body의 message 필드)는 한국어
- Controller는 UseCase 인터페이스만 의존 (Service 직접 주입 금지)
- 생성 후 컴파일 체크: `gradlew.bat compileJava --no-daemon --quiet`
