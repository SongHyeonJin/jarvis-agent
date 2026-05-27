# 🤖 Jarvis AI 비서

Spring Boot와 Spring AI 기반의 지능형 AI 비서 애플리케이션입니다.  
OpenAI, Ollama 등 다양한 LLM 제공자를 통합하여 대화형 AI 서비스를 제공합니다.

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| **언어** | Java 21 |
| **프레임워크** | Spring Boot 3.4.5 |
| **AI 통합** | Spring AI 1.0.0 (OpenAI, Ollama) |
| **데이터베이스** | H2 (개발), PostgreSQL (운영) |
| **빌드 도구** | Gradle (Kotlin DSL) |
| **아키텍처** | 헥사고날 아키텍처 |

---

## 실행 방법

### 사전 요구 사항

- **Java 21** 이상
- **Gradle 8.x** (Wrapper 사용 권장)
- **Ollama** (로컬 개발 시, [ollama.com](https://ollama.com) 참고)

### 빌드 및 실행

```bash
# 프로젝트 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun
```

### API 키 설정

`application-local.yml` 파일을 생성하여 API 키를 설정합니다 (`.gitignore`에 포함됨):

```yaml
spring:
  ai:
    openai:
      api-key: sk-your-openai-api-key
    ollama:
      base-url: http://localhost:11434
```

또는 환경 변수로 설정할 수 있습니다:

```bash
export SPRING_AI_OPENAI_API_KEY=sk-your-openai-api-key
```

---

## 프로젝트 구조

```
jarvis-agent/
├── build.gradle.kts          # Gradle 빌드 설정
├── settings.gradle.kts       # 프로젝트 설정
├── gradle.properties         # Gradle 속성
└── src/
    ├── main/
    │   ├── java/com/jarvis/
    │   │   ├── adapter/      # 어댑터 (인바운드/아웃바운드)
    │   │   ├── application/  # 유스케이스, 서비스
    │   │   ├── domain/       # 도메인 모델
    │   │   └── config/       # 설정 클래스
    │   └── resources/
    │       └── application.yml
    └── test/
        └── java/com/jarvis/
```

---

## 라이선스

MIT License
