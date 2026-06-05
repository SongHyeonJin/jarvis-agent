package com.jarvis.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 사용자 자연어 명령을 분석해 {@link ProjectType}을 결정한다.
 *
 * 우선순위 (높음 → 낮음):
 *   Chrome Extension → Spring Boot → React → Next.js → Electron
 *   → Python → Java App → Game → Web App → UNKNOWN
 */
@Component
@Slf4j
public class ProjectTypeAnalyzer {

    // ── Chrome Extension ──────────────────────────────────────────
    private static final List<Pattern> CHROME_EXT_PATS = List.of(
        Pattern.compile("(?:크롬|chrome).*(?:확장|extension)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:확장|extension).*(?:크롬|chrome)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("확장\\s*프로그램", Pattern.CASE_INSENSITIVE),
        Pattern.compile("chrome\\s*ext", Pattern.CASE_INSENSITIVE),
        Pattern.compile("manifest\\.json", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:파이어폭스|firefox).*(?:확장|extension|plugin)", Pattern.CASE_INSENSITIVE)
    );

    // ── Spring Boot ───────────────────────────────────────────────
    private static final List<Pattern> SPRING_BOOT_PATS = List.of(
        Pattern.compile("spring\\s*boot", Pattern.CASE_INSENSITIVE),
        Pattern.compile("springboot", Pattern.CASE_INSENSITIVE),
        Pattern.compile("spring.*(?:api|서버|server|rest)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("gradle.*(?:java|spring)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("java.*(?:api|서버|server|spring)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:api|rest).*(?:서버|server).*java", Pattern.CASE_INSENSITIVE),
        Pattern.compile("spring.*(?:jpa|mvc|webflux|security|data)", Pattern.CASE_INSENSITIVE)
    );

    // ── React ─────────────────────────────────────────────────────
    private static final List<Pattern> REACT_PATS = List.of(
        Pattern.compile("react", Pattern.CASE_INSENSITIVE),
        Pattern.compile("리액트", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vite.*(?:react|app)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("cra\\b", Pattern.CASE_INSENSITIVE)
    );

    // ── Next.js ───────────────────────────────────────────────────
    private static final List<Pattern> NEXT_PATS = List.of(
        Pattern.compile("next\\.js", Pattern.CASE_INSENSITIVE),
        Pattern.compile("nextjs", Pattern.CASE_INSENSITIVE),
        Pattern.compile("넥스트\\s*(?:js|앱|app)", Pattern.CASE_INSENSITIVE)
    );

    // ── Electron ──────────────────────────────────────────────────
    private static final List<Pattern> ELECTRON_PATS = List.of(
        Pattern.compile("electron", Pattern.CASE_INSENSITIVE),
        Pattern.compile("데스크탑\\s*(?:앱|app)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("desktop\\s*app", Pattern.CASE_INSENSITIVE)
    );

    // ── Python ────────────────────────────────────────────────────
    private static final List<Pattern> PYTHON_PATS = List.of(
        Pattern.compile("python", Pattern.CASE_INSENSITIVE),
        Pattern.compile("파이썬", Pattern.CASE_INSENSITIVE),
        Pattern.compile("flask", Pattern.CASE_INSENSITIVE),
        Pattern.compile("fastapi", Pattern.CASE_INSENSITIVE),
        Pattern.compile("django", Pattern.CASE_INSENSITIVE),
        Pattern.compile("streamlit", Pattern.CASE_INSENSITIVE)
    );

    // ── Java App ──────────────────────────────────────────────────
    private static final List<Pattern> JAVA_APP_PATS = List.of(
        Pattern.compile("java\\s*(?:콘솔|앱|application|app)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:콘솔|console)\\s*(?:게임|앱|app).*java", Pattern.CASE_INSENSITIVE),
        Pattern.compile("java.*(?:콘솔|console|swing|javafx|gui)", Pattern.CASE_INSENSITIVE)
    );

    // ── Game ──────────────────────────────────────────────────────
    private static final List<Pattern> GAME_PATS = List.of(
        Pattern.compile("게임.*만들", Pattern.CASE_INSENSITIVE),
        Pattern.compile("만들.*게임", Pattern.CASE_INSENSITIVE),
        Pattern.compile("게임.*개발", Pattern.CASE_INSENSITIVE),
        Pattern.compile("html.*game|game.*html", Pattern.CASE_INSENSITIVE),
        Pattern.compile("canvas.*game|game.*canvas", Pattern.CASE_INSENSITIVE),
        Pattern.compile("테트리스|tetris", Pattern.CASE_INSENSITIVE),
        Pattern.compile("뱀\\s*게임|snake.*game", Pattern.CASE_INSENSITIVE),
        Pattern.compile("공\\s*피하기", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:플랫폼|platformer|슈팅|shooting|퍼즐|puzzle)\\s*게임", Pattern.CASE_INSENSITIVE)
    );

    // ── Web App (fallback) ────────────────────────────────────────
    private static final List<Pattern> WEB_APP_PATS = List.of(
        Pattern.compile("웹\\s*(?:앱|app|사이트|site)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("web\\s*(?:app|site)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("html.*(?:만들|개발|생성)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:랜딩|landing)\\s*(?:페이지|page)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:대시보드|dashboard).*(?:만들|개발)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:포트폴리오|portfolio).*(?:만들|개발|사이트)", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 명령어에서 프로젝트 유형을 결정한다.
     *
     * @param command 사용자 원문 명령어
     * @return 결정된 {@link ProjectType}
     */
    public ProjectType analyze(String command) {
        if (command == null || command.isBlank()) return ProjectType.UNKNOWN;
        String lower = command.toLowerCase();

        if (matches(lower, CHROME_EXT_PATS))  { log.info("[ProjectTypeAnalyzer] CHROME_EXTENSION: '{}'", abbr(command)); return ProjectType.CHROME_EXTENSION; }
        if (matches(lower, SPRING_BOOT_PATS)) { log.info("[ProjectTypeAnalyzer] SPRING_BOOT: '{}'", abbr(command));      return ProjectType.SPRING_BOOT; }
        if (matches(lower, NEXT_PATS))        { log.info("[ProjectTypeAnalyzer] NEXT_APP: '{}'", abbr(command));          return ProjectType.NEXT_APP; }
        if (matches(lower, REACT_PATS))       { log.info("[ProjectTypeAnalyzer] REACT_APP: '{}'", abbr(command));         return ProjectType.REACT_APP; }
        if (matches(lower, ELECTRON_PATS))    { log.info("[ProjectTypeAnalyzer] ELECTRON_APP: '{}'", abbr(command));      return ProjectType.ELECTRON_APP; }
        if (matches(lower, PYTHON_PATS))      { log.info("[ProjectTypeAnalyzer] PYTHON_APP: '{}'", abbr(command));        return ProjectType.PYTHON_APP; }
        if (matches(lower, JAVA_APP_PATS))    { log.info("[ProjectTypeAnalyzer] JAVA_APP: '{}'", abbr(command));          return ProjectType.JAVA_APP; }
        if (matches(lower, GAME_PATS))        { log.info("[ProjectTypeAnalyzer] GAME: '{}'", abbr(command));              return ProjectType.GAME; }
        if (matches(lower, WEB_APP_PATS))     { log.info("[ProjectTypeAnalyzer] WEB_APP: '{}'", abbr(command));           return ProjectType.WEB_APP; }

        log.info("[ProjectTypeAnalyzer] UNKNOWN (기본 WEB_APP 처리): '{}'", abbr(command));
        return ProjectType.WEB_APP; // 신규 프로젝트는 기본적으로 WEB_APP
    }

    private boolean matches(String lower, List<Pattern> patterns) {
        return patterns.stream().anyMatch(p -> p.matcher(lower).find());
    }

    private String abbr(String s) {
        return (s != null && s.length() > 60) ? s.substring(0, 60) + "…" : s;
    }
}
