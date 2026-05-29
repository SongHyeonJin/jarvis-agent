package com.jarvis.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 사용자 명령어에서 안전한 워크스페이스 폴더 슬러그를 생성한다.
 *
 * 우선순위:
 *   1. AiSlugService (OpenAI) — 자연스러운 영어 슬러그
 *   2. 키워드 기반 fallback — 오프라인·실패 시 사용
 *
 * 설계 원칙:
 *   1. 콘텐츠 키워드(무엇을)를 먼저, 플랫폼/유형 키워드(어디서/어떻게)를 나중에 배치.
 *   2. 복합 슬러그(cheese-cat, chrome-extension)를 파트 단위로 분리해 중복을 제거한다.
 *   3. 최종 슬러그는 소문자·영숫자·하이픈만 허용하며 최대 60자로 제한한다.
 *
 * 예:
 *   "크롬 탭에 치즈냥이가 걸어다니는 확장 프로그램 만들어줘"  → AI: "cheese-cat-tab-companion"
 *   "날씨 보여주는 웹앱 만들어줘"                            → AI: "weather-dashboard-app"
 *   "간단한 공 피하기 게임 만들어줘"                          → AI: "dodge-ball-game"
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkspaceNameResolver {

    private final AiSlugService aiSlugService;

    private record KeywordEntry(String slug, String[] patterns) {}

    private static KeywordEntry kw(String slug, String... patterns) {
        return new KeywordEntry(slug, patterns);
    }

    // ── 콘텐츠 키워드 (주제/소재) ─────────────────────────────────────────
    private static final List<KeywordEntry> CONTENT_KW = List.of(
        kw("cheese-cat",  "치즈\\s*냥이", "치즈냥이"),
        kw("cat",         "냥이", "고양이"),
        kw("cheese",      "치즈"),
        kw("dog",         "강아지"),
        kw("panda",       "판다"),
        kw("rabbit",      "토끼"),
        kw("bear",        "곰"),
        kw("pizza",       "피자"),
        kw("music",       "음악", "뮤직"),
        kw("video",       "동영상", "비디오"),
        kw("photo",       "사진", "포토"),
        kw("map",         "지도"),
        kw("food",        "음식"),
        kw("book",        "책", "도서"),
        kw("dodge-ball",  "공\\s*피하기", "공\\s*피하"),
        kw("ball",        "공"),
        kw("snake",       "뱀"),
        kw("tetris",      "테트리스"),
        kw("weather",     "날씨"),
        kw("todo-list",   "투두", "할\\s*일", "할일"),
        kw("memo",        "메모", "노트"),
        kw("chat",        "채팅"),
        kw("news",        "뉴스"),
        kw("calendar",    "달력", "캘린더"),
        kw("calculator",  "계산기"),
        kw("timer",       "타이머"),
        kw("clock",       "시계"),
        kw("shop",        "쇼핑몰", "쇼핑"),
        kw("blog",        "블로그"),
        kw("portfolio",   "포트폴리오"),
        kw("dashboard",   "대시보드"),
        kw("quiz",        "퀴즈")
    );

    // ── 플랫폼/유형 키워드 (기술/종류) ────────────────────────────────────
    private static final List<KeywordEntry> PLATFORM_KW = List.of(
        kw("chrome-extension", "크롬.*확장", "chrome.*ext(?:ension)?", "크롬확장", "크롬\\s*확장"),
        kw("chrome",           "크롬"),
        kw("firefox",          "파이어폭스"),
        kw("extension",        "확장\\s*(?:프로그램)?", "extension"),
        kw("react-app",        "react.*(?:앱|app|웹|web)", "리액트.*(?:앱|app)"),
        kw("react",            "react", "리액트"),
        kw("vue",              "vue", "뷰"),
        kw("angular",          "angular", "앵귤러"),
        kw("nextjs",           "next\\.?js", "넥스트"),
        kw("svelte",           "svelte", "스벨트"),
        kw("python",           "python", "파이썬"),
        kw("node",             "node\\.?js", "노드"),
        kw("flask",            "flask"),
        kw("fastapi",          "fastapi"),
        kw("express",          "express"),
        kw("web-app",          "웹\\s*앱", "웹앱", "web\\s*app", "웹\\s*사이트", "website"),
        kw("web",              "웹"),
        kw("html",             "html"),
        kw("game",             "게임", "game"),
        kw("plugin",           "플러그인", "plugin"),
        kw("app",              "앱")
    );

    // ── 동의어 그룹 (같은 그룹 내 파트가 2개 이상이면 첫 번째만 유지) ──────
    private static final List<Set<String>> SYNONYM_GROUPS = List.of(
        new HashSet<>(Set.of("chrome", "extension")),
        new HashSet<>(Set.of("app", "web", "site")),
        new HashSet<>(Set.of("todo", "list"))
    );

    // ── "만들어" + 주제 복합 판단용 ──────────────────────────────────────
    private static final List<java.util.regex.Pattern> MAKE_SUBJECT_PATS = List.of(
        java.util.regex.Pattern.compile("크롬"),
        java.util.regex.Pattern.compile("확장"),
        java.util.regex.Pattern.compile("게임"),
        java.util.regex.Pattern.compile("웹앱"),
        java.util.regex.Pattern.compile("웹\\s*앱"),
        java.util.regex.Pattern.compile("웹사이트"),
        java.util.regex.Pattern.compile("투두"),
        java.util.regex.Pattern.compile("달력"),
        java.util.regex.Pattern.compile("브라우저"),
        java.util.regex.Pattern.compile("북마크"),
        java.util.regex.Pattern.compile("메모\\s*앱"),
        java.util.regex.Pattern.compile("채팅\\s*앱")
    );

    /**
     * 명령어에서 슬러그를 생성한다.
     *
     * 1) AiSlugService 호출 → 성공 시 반환
     * 2) 키워드 기반 fallback
     * 3) 최종 fallback: "project-{yyMMdd-HHmm}"
     *
     * @param command 사용자 원문 명령어
     * @return URL-safe 슬러그
     */
    public String resolve(String command) {
        if (command == null || command.isBlank()) return fallback();

        // ① AI 슬러그 시도
        String aiSlug = aiSlugService.generateSlug(command);
        if (aiSlug != null && !aiSlug.isBlank()) {
            return aiSlug;
        }

        // ② 키워드 기반 fallback
        log.info("[WorkspaceNameResolver] AI 실패 → 키워드 fallback: '{}'",
                 command.length() > 50 ? command.substring(0, 50) + "…" : command);
        return resolveByKeyword(command);
    }

    // ─────────────────────────────────────────────────────────────────
    //  키워드 기반 슬러그 (fallback)
    // ─────────────────────────────────────────────────────────────────

    private String resolveByKeyword(String command) {
        String lower = normalize(command);

        List<String> contentParts  = collectParts(lower, CONTENT_KW);
        List<String> platformParts = collectParts(lower, PLATFORM_KW);

        Set<String>  seen = new LinkedHashSet<>();
        List<String> all  = new ArrayList<>();
        for (String p : contentParts)  { if (seen.add(p)) all.add(p); }
        for (String p : platformParts) { if (seen.add(p)) all.add(p); }

        if (all.isEmpty()) {
            log.debug("[WorkspaceNameResolver] 키워드 매핑 실패 → fallback: {}",
                      command.length() > 40 ? command.substring(0, 40) + "…" : command);
            return fallback();
        }

        all = removeSynonymDupes(all);

        String slug = all.stream().limit(5).collect(Collectors.joining("-"));
        slug = slug.toLowerCase()
                   .replaceAll("[^a-z0-9-]", "-")
                   .replaceAll("-{2,}", "-")
                   .replaceAll("^-|-$", "");

        if (slug.isBlank() || slug.length() < 2) return fallback();
        if (slug.length() > 60) slug = slug.substring(0, 60).replaceAll("-$", "");

        log.info("[WorkspaceNameResolver] 키워드 슬러그: '{}' → '{}'",
                 command.length() > 50 ? command.substring(0, 50) + "…" : command, slug);
        return slug;
    }

    // ─────────────────────────────────────────────────────────────────
    //  내부 헬퍼
    // ─────────────────────────────────────────────────────────────────

    private List<String> collectParts(String lower, List<KeywordEntry> keywords) {
        Set<String> seenParts = new LinkedHashSet<>();
        List<String> result   = new ArrayList<>();
        for (KeywordEntry entry : keywords) {
            for (String pat : entry.patterns()) {
                if (lower.matches(".*(?:" + pat + ").*")) {
                    for (String part : entry.slug().split("-")) {
                        if (!part.isBlank() && seenParts.add(part)) result.add(part);
                    }
                    break;
                }
            }
        }
        return result;
    }

    private List<String> removeSynonymDupes(List<String> parts) {
        Map<Set<String>, Boolean> groupUsed = new HashMap<>();
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            Set<String> matchedGroup = null;
            for (Set<String> group : SYNONYM_GROUPS) {
                if (group.contains(part)) { matchedGroup = group; break; }
            }
            if (matchedGroup != null && groupUsed.containsKey(matchedGroup)) {
                if (result.contains(part)) continue;
            }
            if (!result.contains(part)) {
                result.add(part);
                if (matchedGroup != null) groupUsed.put(matchedGroup, true);
            }
        }
        return result;
    }

    private String normalize(String command) {
        return command.toLowerCase().trim();
    }

    private String fallback() {
        return "project-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd-HHmm"));
    }
}
