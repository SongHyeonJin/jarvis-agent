package com.jarvis.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;

/**
 * 프로젝트 유형별 scaffold(폴더 구조 + 초기 파일)를 생성하는 서비스.
 *
 * Claude Code CLI 실행 전에 반드시 호출해야 한다.
 * 이미 파일이 존재하면 덮어쓰지 않는다.
 */
@Service
@Slf4j
public class ScaffoldService {

    /**
     * 프로젝트 루트에 유형별 기본 scaffold를 생성한다.
     *
     * @param projectRoot 대상 프로젝트 루트 디렉토리
     * @param type        프로젝트 유형
     * @param projectName 프로젝트 이름 (슬러그)
     * @param command     원본 사용자 명령 (README에 포함)
     * @param log         로그 콜백
     */
    public void create(Path projectRoot, ProjectType type, String projectName,
                       String command, Consumer<String> log) {
        log.accept("▶ Scaffold 생성 시작: " + type.name());
        switch (type) {
            case SPRING_BOOT    -> createSpringBoot(projectRoot, projectName, command, log);
            case JAVA_APP       -> createJavaApp(projectRoot, projectName, command, log);
            case CHROME_EXTENSION -> createChromeExtension(projectRoot, projectName, command, log);
            case REACT_APP      -> createReactApp(projectRoot, projectName, command, log);
            case NEXT_APP       -> createNextApp(projectRoot, projectName, command, log);
            case GAME           -> createGame(projectRoot, projectName, command, log);
            case ELECTRON_APP   -> createElectronApp(projectRoot, projectName, command, log);
            case PYTHON_APP     -> createPythonApp(projectRoot, projectName, command, log);
            default             -> createWebApp(projectRoot, projectName, command, log);
        }
        log.accept("▶ Scaffold 생성 완료 ✓");
    }

    // ─────────────────────────────────────────────────────────
    //  Spring Boot
    // ─────────────────────────────────────────────────────────

    private void createSpringBoot(Path root, String name, String command, Consumer<String> log) {
        String pkg  = "com/jarvis/demo";
        String cls  = toPascalCase(name);

        mkdir(root, "src/main/java/" + pkg,           log);
        mkdir(root, "src/main/resources",              log);
        mkdir(root, "src/test/java/" + pkg,            log);

        writeIfAbsent(root, "src/main/java/" + pkg + "/" + cls + "Application.java",
            "package com.jarvis.demo;\n\n"
            + "import org.springframework.boot.SpringApplication;\n"
            + "import org.springframework.boot.autoconfigure.SpringBootApplication;\n\n"
            + "@SpringBootApplication\n"
            + "public class " + cls + "Application {\n"
            + "    public static void main(String[] args) {\n"
            + "        SpringApplication.run(" + cls + "Application.class, args);\n"
            + "    }\n"
            + "}\n", log);

        writeIfAbsent(root, "src/main/resources/application.yml",
            "spring:\n"
            + "  application:\n"
            + "    name: " + name + "\n"
            + "  datasource:\n"
            + "    url: jdbc:h2:mem:testdb\n"
            + "    driver-class-name: org.h2.Driver\n"
            + "  jpa:\n"
            + "    hibernate:\n"
            + "      ddl-auto: create-drop\n"
            + "    show-sql: false\n\n"
            + "server:\n"
            + "  port: 8080\n", log);

        writeIfAbsent(root, "build.gradle",
            "plugins {\n"
            + "    id 'java'\n"
            + "    id 'org.springframework.boot' version '3.4.5'\n"
            + "    id 'io.spring.dependency-management' version '1.1.7'\n"
            + "}\n\n"
            + "group = 'com.jarvis'\nversion = '0.0.1-SNAPSHOT'\n\n"
            + "java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }\n\n"
            + "repositories { mavenCentral() }\n\n"
            + "dependencies {\n"
            + "    implementation 'org.springframework.boot:spring-boot-starter-web'\n"
            + "    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'\n"
            + "    compileOnly 'org.projectlombok:lombok'\n"
            + "    annotationProcessor 'org.projectlombok:lombok'\n"
            + "    runtimeOnly 'com.h2database:h2'\n"
            + "    testImplementation 'org.springframework.boot:spring-boot-starter-test'\n"
            + "}\n", log);

        writeIfAbsent(root, "settings.gradle",
            "rootProject.name = '" + name + "'\n", log);

        writeIfAbsent(root, ".gitignore",
            ".gradle\nbuild/\n*.class\n.idea/\n*.iml\n", log);

        writeIfAbsent(root, "README.md", buildReadme(name, command,
            "## 실행 방법\n```bash\n./gradlew bootRun\n```\n"), log);
    }

    // ─────────────────────────────────────────────────────────
    //  Java App
    // ─────────────────────────────────────────────────────────

    private void createJavaApp(Path root, String name, String command, Consumer<String> log) {
        String pkg = "com/jarvis/app";
        String cls = toPascalCase(name);

        mkdir(root, "src/main/java/" + pkg, log);

        writeIfAbsent(root, "src/main/java/" + pkg + "/Main.java",
            "package com.jarvis.app;\n\n"
            + "/**\n * " + name + " 메인 진입점\n */\n"
            + "public class Main {\n"
            + "    public static void main(String[] args) {\n"
            + "        System.out.println(\"" + cls + " 시작\");\n"
            + "    }\n"
            + "}\n", log);

        writeIfAbsent(root, ".gitignore", "*.class\nbuild/\n.idea/\n*.iml\n", log);
        writeIfAbsent(root, "README.md", buildReadme(name, command,
            "## 실행 방법\n```bash\njavac src/main/java/com/jarvis/app/*.java\njava com.jarvis.app.Main\n```\n"), log);
    }

    // ─────────────────────────────────────────────────────────
    //  Chrome Extension
    // ─────────────────────────────────────────────────────────

    private void createChromeExtension(Path root, String name, String command, Consumer<String> log) {
        mkdir(root, "icons",   log);
        mkdir(root, "assets",  log);

        writeIfAbsent(root, "manifest.json",
            "{\n"
            + "  \"manifest_version\": 3,\n"
            + "  \"name\": \"" + name + "\",\n"
            + "  \"version\": \"1.0.0\",\n"
            + "  \"description\": \"Generated by JARVIS Dev Agent\",\n"
            + "  \"permissions\": [\"activeTab\", \"scripting\"],\n"
            + "  \"content_scripts\": [\n"
            + "    {\n"
            + "      \"matches\": [\"<all_urls>\"],\n"
            + "      \"js\": [\"content.js\"],\n"
            + "      \"css\": [\"styles.css\"]\n"
            + "    }\n"
            + "  ],\n"
            + "  \"icons\": {\n"
            + "    \"16\": \"icons/icon16.png\",\n"
            + "    \"48\": \"icons/icon48.png\",\n"
            + "    \"128\": \"icons/icon128.png\"\n"
            + "  }\n"
            + "}\n", log);

        writeIfAbsent(root, "content.js",
            "// content.js — 생성된 파일입니다. 실제 로직은 Claude가 작성합니다.\n"
            + "console.log('[" + name + "] content script loaded');\n", log);

        writeIfAbsent(root, "styles.css",
            "/* styles.css — 생성된 파일입니다. 실제 스타일은 Claude가 작성합니다. */\n", log);

        writeIfAbsent(root, "README.md", buildReadme(name, command,
            "## 설치 방법\n1. `chrome://extensions` 열기\n2. 개발자 모드 활성화\n3. '압축해제된 확장 프로그램 로드' 클릭\n4. 이 폴더 선택\n"), log);

        writeIfAbsent(root, ".gitignore", "node_modules/\n*.zip\n", log);
    }

    // ─────────────────────────────────────────────────────────
    //  Web App
    // ─────────────────────────────────────────────────────────

    private void createWebApp(Path root, String name, String command, Consumer<String> log) {
        mkdir(root, "assets", log);

        writeIfAbsent(root, "index.html",
            "<!DOCTYPE html>\n<html lang=\"ko\">\n<head>\n"
            + "<meta charset=\"UTF-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
            + "<title>" + name + "</title>\n"
            + "<link rel=\"stylesheet\" href=\"styles.css\">\n"
            + "</head>\n<body>\n"
            + "<h1>" + name + "</h1>\n"
            + "<script src=\"script.js\"></script>\n"
            + "</body>\n</html>\n", log);

        writeIfAbsent(root, "styles.css", "/* styles.css */\nbody { font-family: sans-serif; margin: 0; padding: 20px; }\n", log);
        writeIfAbsent(root, "script.js",  "// script.js\nconsole.log('" + name + " loaded');\n", log);
        writeIfAbsent(root, "README.md",  buildReadme(name, command,
            "## 실행 방법\n브라우저에서 `index.html` 파일을 열거나 Live Server를 사용하세요.\n"), log);
        writeIfAbsent(root, ".gitignore", "node_modules/\n", log);
    }

    // ─────────────────────────────────────────────────────────
    //  Game
    // ─────────────────────────────────────────────────────────

    private void createGame(Path root, String name, String command, Consumer<String> log) {
        mkdir(root, "assets", log);

        writeIfAbsent(root, "index.html",
            "<!DOCTYPE html>\n<html lang=\"ko\">\n<head>\n"
            + "<meta charset=\"UTF-8\">\n<title>" + name + "</title>\n"
            + "<link rel=\"stylesheet\" href=\"styles.css\">\n"
            + "</head>\n<body>\n"
            + "<canvas id=\"gameCanvas\"></canvas>\n"
            + "<script src=\"game.js\"></script>\n"
            + "</body>\n</html>\n", log);

        writeIfAbsent(root, "styles.css",
            "* { margin: 0; padding: 0; box-sizing: border-box; }\n"
            + "body { background: #000; display: flex; justify-content: center; align-items: center; height: 100vh; }\n"
            + "#gameCanvas { border: 2px solid #fff; }\n", log);

        writeIfAbsent(root, "game.js",
            "// game.js — 게임 로직\n"
            + "const canvas = document.getElementById('gameCanvas');\n"
            + "const ctx = canvas.getContext('2d');\n"
            + "canvas.width = 640; canvas.height = 480;\n"
            + "console.log('" + name + " game loaded');\n", log);

        writeIfAbsent(root, "README.md", buildReadme(name, command,
            "## 실행 방법\n브라우저에서 `index.html` 파일을 여세요.\n"), log);
        writeIfAbsent(root, ".gitignore", "node_modules/\n", log);
    }

    // ─────────────────────────────────────────────────────────
    //  React App
    // ─────────────────────────────────────────────────────────

    private void createReactApp(Path root, String name, String command, Consumer<String> log) {
        mkdir(root, "src",    log);
        mkdir(root, "public", log);

        writeIfAbsent(root, "package.json",
            "{\n  \"name\": \"" + name + "\",\n  \"version\": \"0.1.0\",\n"
            + "  \"private\": true,\n  \"scripts\": {\n"
            + "    \"start\": \"react-scripts start\",\n"
            + "    \"build\": \"react-scripts build\"\n  },\n"
            + "  \"dependencies\": {\n"
            + "    \"react\": \"^18.2.0\",\n"
            + "    \"react-dom\": \"^18.2.0\",\n"
            + "    \"react-scripts\": \"5.0.1\"\n  }\n}\n", log);

        writeIfAbsent(root, "src/index.js",
            "import React from 'react';\nimport ReactDOM from 'react-dom/client';\nimport App from './App';\n"
            + "const root = ReactDOM.createRoot(document.getElementById('root'));\n"
            + "root.render(<React.StrictMode><App /></React.StrictMode>);\n", log);

        writeIfAbsent(root, "src/App.js",
            "import React from 'react';\nfunction App() { return <div><h1>" + name + "</h1></div>; }\nexport default App;\n", log);

        writeIfAbsent(root, "public/index.html",
            "<!DOCTYPE html>\n<html lang=\"ko\">\n<head><meta charset=\"UTF-8\">\n"
            + "<title>" + name + "</title></head>\n<body><div id=\"root\"></div></body>\n</html>\n", log);

        writeIfAbsent(root, "README.md", buildReadme(name, command,
            "## 실행 방법\n```bash\nnpm install\nnpm start\n```\n"), log);
        writeIfAbsent(root, ".gitignore", "node_modules/\nbuild/\n.env\n", log);
    }

    // ─────────────────────────────────────────────────────────
    //  Next.js App
    // ─────────────────────────────────────────────────────────

    private void createNextApp(Path root, String name, String command, Consumer<String> log) {
        mkdir(root, "src/app",    log);
        mkdir(root, "public",     log);

        writeIfAbsent(root, "package.json",
            "{\n  \"name\": \"" + name + "\",\n  \"version\": \"0.1.0\",\n"
            + "  \"private\": true,\n  \"scripts\": {\n"
            + "    \"dev\": \"next dev\",\n    \"build\": \"next build\",\n    \"start\": \"next start\"\n  },\n"
            + "  \"dependencies\": {\n"
            + "    \"next\": \"14.0.4\",\n    \"react\": \"^18\",\n    \"react-dom\": \"^18\"\n  },\n"
            + "  \"devDependencies\": { \"typescript\": \"^5\" }\n}\n", log);

        writeIfAbsent(root, "src/app/page.js",
            "export default function Home() { return <main><h1>" + name + "</h1></main>; }\n", log);

        writeIfAbsent(root, "src/app/layout.js",
            "export const metadata = { title: '" + name + "' };\n"
            + "export default function RootLayout({ children }) { return <html lang='ko'><body>{children}</body></html>; }\n", log);

        writeIfAbsent(root, "README.md", buildReadme(name, command,
            "## 실행 방법\n```bash\nnpm install\nnpm run dev\n```\n"), log);
        writeIfAbsent(root, ".gitignore", "node_modules/\n.next/\n.env*\n", log);
    }

    // ─────────────────────────────────────────────────────────
    //  Electron App
    // ─────────────────────────────────────────────────────────

    private void createElectronApp(Path root, String name, String command, Consumer<String> log) {
        mkdir(root, "src", log);

        writeIfAbsent(root, "package.json",
            "{\n  \"name\": \"" + name + "\",\n  \"version\": \"1.0.0\",\n"
            + "  \"main\": \"main.js\",\n  \"scripts\": { \"start\": \"electron .\" },\n"
            + "  \"dependencies\": { \"electron\": \"^28.0.0\" }\n}\n", log);

        writeIfAbsent(root, "main.js",
            "const { app, BrowserWindow } = require('electron');\n"
            + "function createWindow() {\n"
            + "  const win = new BrowserWindow({ width: 800, height: 600 });\n"
            + "  win.loadFile('index.html');\n}\n"
            + "app.whenReady().then(createWindow);\n"
            + "app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });\n", log);

        writeIfAbsent(root, "index.html",
            "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>" + name + "</title></head>\n"
            + "<body><h1>" + name + "</h1></body></html>\n", log);

        writeIfAbsent(root, "README.md", buildReadme(name, command,
            "## 실행 방법\n```bash\nnpm install\nnpm start\n```\n"), log);
        writeIfAbsent(root, ".gitignore", "node_modules/\ndist/\n", log);
    }

    // ─────────────────────────────────────────────────────────
    //  Python App
    // ─────────────────────────────────────────────────────────

    private void createPythonApp(Path root, String name, String command, Consumer<String> log) {
        mkdir(root, "src", log);

        writeIfAbsent(root, "main.py",
            "# main.py — " + name + "\n"
            + "def main():\n    print('" + name + " started')\n\n"
            + "if __name__ == '__main__':\n    main()\n", log);

        writeIfAbsent(root, "requirements.txt", "# requirements.txt\n", log);
        writeIfAbsent(root, "README.md", buildReadme(name, command,
            "## 실행 방법\n```bash\npip install -r requirements.txt\npython main.py\n```\n"), log);
        writeIfAbsent(root, ".gitignore", "__pycache__/\n*.pyc\nvenv/\n.env\n", log);
    }

    // ─────────────────────────────────────────────────────────
    //  내부 유틸
    // ─────────────────────────────────────────────────────────

    private void mkdir(Path root, String relative, Consumer<String> log) {
        try {
            Path dir = root.resolve(relative);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                log.accept("  📁 " + relative + "/");
            }
        } catch (Exception e) {
            log.accept("  ⚠ 폴더 생성 실패: " + relative + " — " + e.getMessage());
        }
    }

    private void writeIfAbsent(Path root, String relative, String content, Consumer<String> log) {
        try {
            Path file = root.resolve(relative);
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, content, StandardCharsets.UTF_8);
                log.accept("  📄 " + relative);
            }
        } catch (Exception e) {
            log.accept("  ⚠ 파일 생성 실패: " + relative + " — " + e.getMessage());
        }
    }

    private String buildReadme(String name, String command, String runSection) {
        return "# " + name + "\n\n"
             + "> Generated by JARVIS Dev Agent\n\n"
             + "## 요청 내용\n\n"
             + command + "\n\n"
             + runSection;
    }

    /** "my-project-name" → "MyProjectName" */
    private String toPascalCase(String slug) {
        if (slug == null || slug.isBlank()) return "App";
        StringBuilder sb = new StringBuilder();
        for (String part : slug.split("[-_\\s]+")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.isEmpty() ? "App" : sb.toString();
    }
}
