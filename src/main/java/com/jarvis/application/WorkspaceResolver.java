package com.jarvis.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 사용자 명령을 분석해 작업 유형(JobType), 실행 디렉토리, 프로젝트 유형(ProjectType)을 결정한다.
 *
 * NOTE: build.gradle.kts에 options.encoding = "UTF-8" 설정 필수
 *       (Windows 기본 EUC-KR 환경에서 한글 리터럴이 깨지는 문제 방지)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkspaceResolver {

    private final WorkspaceNameResolver nameResolver;
    private final AppWorkspaceProperties props;
    private final ProjectTypeAnalyzer typeAnalyzer;

    // ── JARVIS 수정 패턴 ──────────────────────────────────────────────
    private static final List<Pattern> MODIFY_PATS = List.of(
        Pattern.compile("jarvis|자비스", Pattern.CASE_INSENSITIVE),
        Pattern.compile("spring\\s*boot|springboot|gradle|maven", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:이|현재|기존)\\s*(?:프로젝트|서버|백엔드|코드|소스)"),
        Pattern.compile("(?:ui|화면|레이아웃|스타일|디자인)\\s*(?:수정|변경|바꿔|고쳐|개선)"),
        Pattern.compile("(?:응답|메시지|박스|패널|버튼|헤더|푸터)\\s*(?:수정|변경|바꿔|이동|위치|고쳐)"),
        Pattern.compile("(?:할\\s*일|할일|메모|일정|브리핑|대화)\\s*(?:기능|수정|변경|고쳐|추가)"),
        Pattern.compile("(?:api|엔드포인트|컨트롤러|서비스|레포지토리)\\s*(?:수정|변경|추가|고쳐)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:orb|hud|패널|사이드바|센터)\\s*(?:수정|변경|고쳐|이동)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("기능\\s*(?:추가|수정|개선)")
    );

    // ── 신규 프로젝트 패턴 ────────────────────────────────────────────
    private static final List<Pattern> NEW_PATS = List.of(
        // 크롬 확장 (띄어져 있어도 OK)
        Pattern.compile("(?:크롬|chrome).*(?:확장|extension)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:확장|extension).*(?:크롬|chrome)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("확장\\s*프로그램"),
        Pattern.compile("확장프로그램"),
        Pattern.compile("chrome.*extension|extension.*chrome", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:firefox|파이어포스).*(?:확장|플러그인|extension|plugin)", Pattern.CASE_INSENSITIVE),
        // 게임
        Pattern.compile("게임.*만들"),
        Pattern.compile("게임.*개발"),
        Pattern.compile("만들.*게임"),
        Pattern.compile("공\\s*피하기"),
        Pattern.compile("테트리스|tetris", Pattern.CASE_INSENSITIVE),
        Pattern.compile("뱀\\s*게임|snake.*game", Pattern.CASE_INSENSITIVE),
        Pattern.compile("html.*game|html.*게임", Pattern.CASE_INSENSITIVE),
        Pattern.compile("canvas.*game|game.*canvas", Pattern.CASE_INSENSITIVE),
        // 웹앱
        Pattern.compile("웹앱.*만들"),
        Pattern.compile("웹앱만들"),
        Pattern.compile("웹\\s*앱.*만들"),
        Pattern.compile("만들.*웹앱"),
        Pattern.compile("만들.*웹\\s*앱"),
        Pattern.compile("웹사이트.*만들"),
        Pattern.compile("만들.*웹사이트"),
        Pattern.compile("web\\s*app.*(?:make|build|create)", Pattern.CASE_INSENSITIVE),
        // React / Vue 등 프레임워크 + 만들기
        Pattern.compile("(?:react|vue|angular|svelte|next\\.?js).*(?:앱|웹|만들|개발)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:python|flask|fastapi|node\\.?js|express).*(?:앱|만들|개발)", Pattern.CASE_INSENSITIVE),
        // Spring Boot 독립 프로젝트 (JARVIS 수정이 아닌 새 서버)
        Pattern.compile("spring\\s*boot.*(?:만들|생성|개발|project|프로젝트)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:새|신규|독립).*spring\\s*boot", Pattern.CASE_INSENSITIVE),
        // 투두 독립 앱
        Pattern.compile("투두.*(?:앱|리스트|만들)"),
        Pattern.compile("(?:앱|만들).*투두"),
        Pattern.compile("할\\s*일.*(?:앱|만들)"),
        // 독립 프로젝트 명시
        Pattern.compile("(?:새|신규|독립)\\s*(?:프로젝트|웹앱|앱|사이트|게임)"),
        Pattern.compile("standalone|단독\\s*실행", Pattern.CASE_INSENSITIVE)
    );

    // ── 복합 판단: 만들어 + 주제 키워드 → NEW_PROJECT ──────────────
    private static final List<Pattern> MAKE_SUBJECT_PATS = List.of(
        Pattern.compile("크롬"),
        Pattern.compile("확장"),
        Pattern.compile("게임"),
        Pattern.compile("웹앱"),
        Pattern.compile("웹\\s*앱"),
        Pattern.compile("웹사이트"),
        Pattern.compile("투두"),
        Pattern.compile("달력"),
        Pattern.compile("브라우저"),
        Pattern.compile("북마크"),
        Pattern.compile("메모\\s*앱"),
        Pattern.compile("채팅\\s*앱")
    );

    public record WorkspaceInfo(
        JobType     jobType,
        Path        projectRoot,
        String      workspaceSlug,
        ProjectType projectType
    ) {}

    public WorkspaceInfo resolve(String command) {
        if (command == null || command.isBlank()) {
            return new WorkspaceInfo(JobType.MODIFY_JARVIS, Paths.get(props.getProjectRoot()),
                                     null, ProjectType.UNKNOWN);
        }

        String lower = command.toLowerCase();

        // 1) MODIFY_JARVIS 우선 판단
        for (Pattern p : MODIFY_PATS) {
            if (p.matcher(lower).find()) {
                log.info("[WorkspaceResolver] MODIFY_JARVIS (패턴 매칭): '{}'", abbr(command));
                return new WorkspaceInfo(JobType.MODIFY_JARVIS, Paths.get(props.getProjectRoot()),
                                         null, ProjectType.UNKNOWN);
            }
        }

        // 2) NEW_PROJECT 단독 판단
        for (Pattern p : NEW_PATS) {
            if (p.matcher(lower).find()) {
                log.info("[WorkspaceResolver] NEW_PROJECT (패턴 매칭): '{}'", abbr(command));
                return buildNewProjectInfo(command);
            }
        }

        // 3) 복합 판단: "만들어줘" + 주제 키워드
        boolean hasMakeVerb = lower.contains("만들어") || lower.contains("개발해")
                           || lower.contains("생성해") || lower.contains("만들어줘")
                           || lower.contains("만들어주세요");
        if (hasMakeVerb) {
            for (Pattern p : MAKE_SUBJECT_PATS) {
                if (p.matcher(lower).find()) {
                    log.info("[WorkspaceResolver] NEW_PROJECT (만들어+주제 매칭): '{}'", abbr(command));
                    return buildNewProjectInfo(command);
                }
            }
        }

        // 4) 기본값: MODIFY_JARVIS
        log.info("[WorkspaceResolver] MODIFY_JARVIS (기본값): '{}'", abbr(command));
        return new WorkspaceInfo(JobType.MODIFY_JARVIS, Paths.get(props.getProjectRoot()),
                                 null, ProjectType.UNKNOWN);
    }

    private WorkspaceInfo buildNewProjectInfo(String command) {
        ProjectType projectType = typeAnalyzer.analyze(command);
        String slug   = nameResolver.resolve(command);
        Path   workspace = Paths.get(props.getWorkspaceRoot(), slug);
        try {
            Files.createDirectories(workspace);
            log.info("[WorkspaceResolver] NEW_PROJECT 폴더 생성: {} ({})", workspace, projectType);
        } catch (Exception e) {
            log.error("[WorkspaceResolver] 폴더 생성 실패 {}: {}", workspace, e.getMessage());
        }
        return new WorkspaceInfo(JobType.NEW_PROJECT, workspace, slug, projectType);
    }

    private String abbr(String s) {
        return s != null && s.length() > 50 ? s.substring(0, 50) + "..." : s;
    }
}
