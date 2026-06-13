package com.jarvis.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * AutoDev NEW_PROJECT 완료 후 프로젝트 유형에 맞는 미리보기를 자동 실행한다.
 *
 * WEB_APP / GAME        → file:// URL을 기본 브라우저로 열기
 * REACT_APP / NEXT_APP  → npm install 후 npm run dev (포트 3000)
 * SPRING_BOOT           → gradlew bootRun (포트 8080)
 * CHROME_EXTENSION      → 설치 안내 메시지만 반환
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoPreviewService {

    public record PreviewResult(
            boolean launched,
            String  url,
            String  message
    ) {}

    public PreviewResult launch(Path projectRoot, ProjectType projectType) {
        log.info("[AutoPreviewService] 미리보기 시작: {} ({})", projectRoot, projectType);
        return switch (projectType) {
            case WEB_APP, GAME          -> launchFileUrl(projectRoot);
            case REACT_APP, NEXT_APP    -> launchNpmDev(projectRoot);
            case SPRING_BOOT            -> launchGradleBootRun(projectRoot);
            case CHROME_EXTENSION       -> chromeExtensionGuide(projectRoot);
            default -> new PreviewResult(false, null,
                    "미리보기를 지원하지 않는 프로젝트 유형입니다: " + projectType);
        };
    }

    // ── WEB_APP / GAME ────────────────────────────────────────────────

    private PreviewResult launchFileUrl(Path projectRoot) {
        Optional<Path> entryFile = findEntryHtml(projectRoot);
        if (entryFile.isEmpty()) {
            return new PreviewResult(false, null,
                    "index.html 을 찾을 수 없습니다: " + projectRoot);
        }

        String fileUrl = entryFile.get().toUri().toString();
        try {
            openBrowser(fileUrl);
            log.info("[AutoPreviewService] 브라우저 열기: {}", fileUrl);
            return new PreviewResult(true, fileUrl,
                    "브라우저에서 미리보기를 열었습니다: " + fileUrl);
        } catch (IOException e) {
            log.warn("[AutoPreviewService] 브라우저 열기 실패: {}", e.getMessage());
            return new PreviewResult(false, fileUrl,
                    "브라우저 자동 실행 실패. 직접 열어주세요: " + fileUrl);
        }
    }

    // ── REACT_APP / NEXT_APP ──────────────────────────────────────────

    private PreviewResult launchNpmDev(Path projectRoot) {
        String devUrl = "http://localhost:3000";
        try {
            runDetached(projectRoot, "npm", "install");
            runDetachedBackground(projectRoot, "npm", "run", "dev");
            log.info("[AutoPreviewService] npm run dev 실행: {}", projectRoot);
            return new PreviewResult(true, devUrl,
                    "npm run dev 를 시작했습니다. 잠시 후 브라우저에서 확인하세요: " + devUrl);
        } catch (IOException e) {
            log.error("[AutoPreviewService] npm dev 실패: {}", e.getMessage());
            return new PreviewResult(false, devUrl,
                    "npm run dev 실행 실패: " + e.getMessage());
        }
    }

    // ── SPRING_BOOT ───────────────────────────────────────────────────

    private PreviewResult launchGradleBootRun(Path projectRoot) {
        String serverUrl = "http://localhost:8080";
        try {
            String gradlew = isWindows() ? "gradlew.bat" : "./gradlew";
            runDetachedBackground(projectRoot, gradlew, "bootRun");
            log.info("[AutoPreviewService] gradle bootRun 실행: {}", projectRoot);
            return new PreviewResult(true, serverUrl,
                    "Gradle bootRun 을 시작했습니다. 잠시 후 접속하세요: " + serverUrl);
        } catch (IOException e) {
            log.error("[AutoPreviewService] bootRun 실패: {}", e.getMessage());
            return new PreviewResult(false, serverUrl,
                    "gradle bootRun 실행 실패: " + e.getMessage());
        }
    }

    // ── CHROME_EXTENSION ──────────────────────────────────────────────

    private PreviewResult chromeExtensionGuide(Path projectRoot) {
        String guide = """
                Chrome 확장 프로그램 로드 방법:
                1. Chrome 주소창에 chrome://extensions 입력
                2. 오른쪽 상단 '개발자 모드' 활성화
                3. '압축해제된 확장 프로그램을 로드합니다' 클릭
                4. 폴더 선택: %s
                """.formatted(projectRoot.toAbsolutePath());
        log.info("[AutoPreviewService] Chrome 확장 안내 반환: {}", projectRoot);
        return new PreviewResult(false, null, guide);
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────

    private Optional<Path> findEntryHtml(Path root) {
        for (String name : List.of("index.html", "main.html", "game.html")) {
            Path candidate = root.resolve(name);
            if (Files.exists(candidate)) return Optional.of(candidate);
        }
        try (var stream = Files.walk(root, 2)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".html"))
                    .findFirst();
        } catch (IOException e) {
            log.warn("[AutoPreviewService] HTML 탐색 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void openBrowser(String url) throws IOException {
        if (isWindows()) {
            new ProcessBuilder("cmd", "/c", "start", url).start();
        } else if (isMac()) {
            new ProcessBuilder("open", url).start();
        } else {
            new ProcessBuilder("xdg-open", url).start();
        }
    }

    private void runDetached(Path dir, String... command) throws IOException {
        new ProcessBuilder(command)
                .directory(dir.toFile())
                .inheritIO()
                .start()
                .waitFor();
    }

    @SuppressWarnings("UnusedReturnValue")
    private Process runDetachedBackground(Path dir, String... command) throws IOException {
        return new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectOutput(new File(dir.toFile(), "preview.log"))
                .redirectErrorStream(true)
                .start();
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }
}
