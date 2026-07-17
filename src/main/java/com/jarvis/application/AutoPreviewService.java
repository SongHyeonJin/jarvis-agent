package com.jarvis.application;

import com.jarvis.domain.model.Memo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 새 프로젝트 완성 후 자동으로 미리보기를 실행하고 브라우저를 연다.
 * - GAME / WEB_APP / REACT_APP / NEXT_APP: 정적/dev 서버 → 브라우저 자동 오픈
 * - CHROME_EXTENSION: 폴더를 탐색기로 열기
 * - SPRING_BOOT / JAVA_APP 등: 미리보기 불가 (Optional.empty 반환)
 * 실패해도 DevJobService 흐름에 영향 없음 (warn 로그만).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoPreviewService {

    private final RunningServiceService runningServiceService;
    private final MemoService           memoService;

    /** 미리보기를 실행하고, 열린 URL을 반환한다 (열 수 없으면 empty). */
    public java.util.Optional<String> launchPreview(Long jobId, String workPath,
                                                     String projectTypeName, String name) {
        try {
            Path root = Paths.get(workPath);
            if (!Files.exists(root)) return java.util.Optional.empty();

            return switch (projectTypeName) {
                case "GAME"                  -> openGameFile(root);
                case "REACT_APP", "NEXT_APP" -> launchNpm(jobId, root, name, projectTypeName);
                case "CHROME_EXTENSION"      -> { openFolder(root); yield java.util.Optional.empty(); }
                case "SPRING_BOOT", "JAVA_APP", "PYTHON_APP" -> java.util.Optional.empty();
                default -> launchStaticServer(jobId, root, name); // WEB_APP, UNKNOWN 등
            };
        } catch (Exception e) {
            log.warn("[AutoPreview] 미리보기 실행 실패 (무시됨): {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<String> launchNpm(Long jobId, Path root,
                                                   String name, String projectTypeName) throws Exception {
        File pkg = root.resolve("package.json").toFile();
        if (!pkg.exists()) return launchStaticServer(jobId, root, name);

        boolean isNext = "NEXT_APP".equals(projectTypeName);
        String script  = isNext ? "dev" : "start";
        int    port    = freePort();
        String url     = "http://localhost:" + port;

        String command = "npm run " + script;
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "npm", "run", script,
                "--", "--port", String.valueOf(port));
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(root.resolve("preview.log").toFile());
        Process process = pb.start();

        runningServiceService.register(name, projectTypeName, command, port, root.toString(), process);
        log.info("[AutoPreview] npm {} 시작: {} port={}", script, name, port);

        openBrowserDelayed(url, 5);
        return java.util.Optional.of(url);
    }

    /** GAME: index.html을 file:// 프로토콜로 직접 브라우저에서 열기 (서버 불필요) */
    private java.util.Optional<String> openGameFile(Path root) throws Exception {
        Path indexHtml = root.resolve("index.html");
        if (!Files.exists(indexHtml)) {
            // index.html 없으면 폴더 열기 fallback
            openFolder(root);
            return java.util.Optional.empty();
        }
        String fileUrl = "file:///" + indexHtml.toAbsolutePath().toString().replace('\\', '/');
        new ProcessBuilder("cmd", "/c", "start", "", fileUrl).start();
        log.info("[AutoPreview] 게임 파일 열기: {}", fileUrl);
        return java.util.Optional.of(fileUrl);
    }

    private java.util.Optional<String> launchStaticServer(Long jobId, Path root, String name) throws Exception {
        int    port = freePort();
        String url  = "http://localhost:" + port;
        ProcessBuilder pb;

        // npx serve 우선, 없으면 Python http.server
        File npx = new File(System.getenv().getOrDefault("APPDATA", ""), "npm/npx.cmd");
        String command;
        if (npx.exists()) {
            pb = new ProcessBuilder("cmd", "/c", npx.getAbsolutePath(),
                    "serve", "-s", ".", "-l", String.valueOf(port));
            command = "npx serve";
        } else {
            pb = new ProcessBuilder("cmd", "/c", "python", "-m", "http.server", String.valueOf(port));
            command = "python -m http.server";
        }
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(root.resolve("preview.log").toFile());
        Process process = pb.start();

        runningServiceService.register(name, "WEB_APP", command, port, root.toString(), process);
        log.info("[AutoPreview] 정적 서버 시작: {} port={}", name, port);

        // 메모에 서버 정보 기록
        saveMemo(name, port, url);

        // 서버 기동 대기 후 브라우저 열기
        openBrowserDelayed(url, 3);
        return java.util.Optional.of(url);
    }

    private void openFolder(Path root) throws Exception {
        new ProcessBuilder("explorer.exe", root.toAbsolutePath().toString()).start();
        log.info("[AutoPreview] 폴더 열기: {}", root);
    }

    /** 실행 중인 웹 서버 정보를 메모로 저장 */
    private void saveMemo(String name, int port, String url) {
        try {
            Memo memo = Memo.builder()
                    .title("[서버] " + name + " — port " + port)
                    .content("자비스가 실행한 웹 서버\nURL: " + url + "\n프로젝트: " + name)
                    .tags("서버,자비스,포트" + port)
                    .build();
            memoService.create(memo);
            log.info("[AutoPreview] 서버 메모 저장: port={}", port);
        } catch (Exception e) {
            log.warn("[AutoPreview] 메모 저장 실패 (무시됨): {}", e.getMessage());
        }
    }

    /** 지정 초 후 기본 브라우저로 URL을 연다 (가상 스레드). */
    private void openBrowserDelayed(String url, int delaySec) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(delaySec * 1_000L);
                new ProcessBuilder("cmd", "/c", "start", "", url).start();
                log.info("[AutoPreview] 브라우저 열기: {}", url);
            } catch (Exception e) {
                log.warn("[AutoPreview] 브라우저 열기 실패: {}", e.getMessage());
            }
        });
    }

    private int freePort() {
        return ThreadLocalRandom.current().nextInt(3000, 9000);
    }
}
