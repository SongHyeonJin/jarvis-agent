# Claude Code Project Rules - Jarvis Agent

## 1. Tech Stack & Environment
- **Backend:** Java 17+ (Spring Boot 3.x)
- **Build Tool:** Gradle (Kotlin DSL - `build.gradle.kts`)
- **Key Libraries:** Spring AI, Spring Web, Thymeleaf, Lombok
- **Architecture:** Layered Architecture (Controller -> Service -> Repository -> Domain)

## 2. Code Style & Patterns
- All Java class names must follow PascalCase, and variables/methods must follow camelCase.
- Use Lombok annotations (`@RequiredArgsConstructor`, `@Getter`, `@Slf4j`) to reduce boilerplate code.
- Ensure all business logic resides in the Service layer, not in Controllers.
- Handle exceptions globally using `@RestControllerAdvice` or `@ControllerAdvice`.
- Write concise, self-documenting code with minimal comments unless the logic is highly complex.

## 3. Core Features Hierarchy (Priority)
1. **Priority 1 (Main):** Developer Assistant (Code review, generation, and parsing logic).
2. **Priority 2 (Essential):** Personal Assistant (Task management, notes, scheduling with db persistence).
3. **Priority 3 (Optional):** Conversational chatbot & Document RAG summaries.

## 4. Multi-Model Routing Strategy
- Use **Claude models** for high-complexity code tasks (Priority 1).
- Use **GPT models (OpenAI)** for general tasks, formatting, or lightweight summaries (Priority 2, 3).
- Abstract AI service calls using Java Interfaces to allow hot-swapping models via `application.yml`.

## 5. UI/UX Rules
- Frontend views should be fully self-contained inside `src/main/resources/templates` or `static` using Thymeleaf and vanilla HTML/JS/TailwindCSS.

## 6. Language Preference
- **Code & Comments:** English for code entities/logs, but user-facing UI and response messages must be in Korean (한국어).
- **Communication:** Always reply to the user in Korean (한국어).