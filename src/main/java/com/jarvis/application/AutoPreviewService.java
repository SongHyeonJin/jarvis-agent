package com.jarvis.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 새 프로젝트 완성 후 자동으로 미리보기를 실행한다.
 * - Chrome Extension / HTML 앱: 폴더를 탐색기로 열기
 * - React / Next.js: npm start / npm run dev 백그라운드 실행
 * - Web App: Python http.server 또는 npx serve 실행
 * 실패해도 DevJobService 흐름에 영향 없음 (warn 로그만).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoPreviewService {

    private final RunningServiceService runningServiceService;

    public void launchPreview(Long jobId, String workPath, String projectTypeName, String name) {
        try {
            Path root = Paths.get(workPath);
            if (!Files.exists(root)) return;

            switch (projectTypeName) {
                case "REACT_APP", "NEXT_APP" -> launchNpm(jobId, root, name, projectTypeName);
                case "CHROME_EXTENSION"       -> openFolder(root);
                default                       -> launchStaticServer(jobId, root, name);
            }
        } catch (Exception e) {
            log.warn("[AutoPreview] 미리보기 실행 실패 (무시됨): {}", e.getMessage());
        }
    }

    private void launchNpm(Long jobId, Path root, String name, String projectTypeName) throws Exception {
        File pkg = root.resolve("package.json").toFile();
        if (!pkg.exists()) { launchStaticServer(jobId, root, name); return; }

        boolean isNext = "NEXT_APP".equals(projectTypeName);
        String script  = isNext ? "dev" : "start";
        int    port    = freePort();

        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "npm", "run", script,
                "--", "--port", String.valueOf(port));
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(root.resolve("preview.log").toFile());
        pb.start();

        runningServiceService.register(jobId, name, port, projectTypeName,
                "http://localhost:" + port);
        log.info("[AutoPreview] npm {} 시작: {} port={}", script, name, port);
    }

    private void launchStaticServer(Long jobId, Path root, String name) throws Exception {
        int port = freePort();
        ProcessBuilder pb;

        // npx serve 우선, 없으면 Python http.server
        File npx = new File(System.getenv().getOrDefault("APPDATA", ""), "npm/npx.cmd");
        if (npx.exists()) {
            pb = new ProcessBuilder("cmd", "/c", npx.getAbsolutePath(),
                    "serve", "-s", ".", "-l", String.valueOf(port));
        } else {
            pb = new ProcessBuilder("cmd", "/c", "python", "-m", "http.server", String.valueOf(port));
        }
        pb.directory(root.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(root.resolve("preview.log").toFile());
        pb.start();

        runningServiceService.register(jobId, name, port, "WEB_APP",
                "http://localhost:" + port);
        log.info("[AutoPreview] 정적 서버 시작: {} port={}", name, port);
    }

    private void openFolder(Path root) throws Exception {
        new ProcessBuilder("explorer.exe", root.toAbsolutePath().toString()).start();
        log.info("[AutoPreview] 폴더 열기: {}", root);
    }

    private int freePort() {
        return ThreadLocalRandom.current().nextInt(3000, 9000);
    }
}
