package com.jarvis.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Chrome Extension manifest.json 검증 및 자동 보정 컴포넌트.
 *
 * 검증 항목:
 *   - JSON 파싱 가능 여부
 *   - manifest_version, name, version 필드 존재
 *   - content_scripts 참조 파일 존재 (없으면 빈 파일 생성)
 *   - icons 참조 파일 존재 (없으면 PlaceholderPngAssetGenerator 로 생성)
 *
 * 자동 보정 항목:
 *   - manifest.json 자체가 없으면 최소 기본값으로 생성
 *   - 아이콘 파일 누락 시 플레이스홀더 PNG 자동 생성
 *   - 참조 JS/CSS 파일 누락 시 빈 파일 생성
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ManifestValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AssetGenerator assetGenerator;

    /**
     * manifest.json 을 검증하고 필요한 파일을 보정한다.
     *
     * @param projectRoot 프로젝트 루트 경로
     * @param projectName 프로젝트 슬러그 (보정 시 manifest name 에 사용)
     * @param logCb       SSE 로그 콜백
     * @return 검증/보정 통과 여부
     */
    public boolean validateAndFix(Path projectRoot, String projectName, Consumer<String> logCb) {
        logCb.accept("\n━━━ Chrome Extension 필수 파일 검증 ━━━");

        Path manifestPath = projectRoot.resolve("manifest.json");

        // ── manifest.json 존재 확인 ───────────────────────────
        if (!Files.exists(manifestPath)) {
            logCb.accept("⚠ manifest.json 없음 → 기본값으로 자동 생성");
            createDefaultManifest(manifestPath, projectName, logCb);
        }

        // ── JSON 파싱 ─────────────────────────────────────────
        JsonNode manifest;
        try {
            manifest = MAPPER.readTree(manifestPath.toFile());
        } catch (Exception e) {
            logCb.accept("✗ manifest.json JSON 파싱 실패: " + e.getMessage());
            logCb.accept("▶ manifest.json 재생성 중...");
            createDefaultManifest(manifestPath, projectName, logCb);
            try { manifest = MAPPER.readTree(manifestPath.toFile()); }
            catch (Exception e2) { logCb.accept("✗ manifest.json 복구 실패"); return false; }
        }

        // ── 필수 필드 검증 ────────────────────────────────────
        checkField(manifest, "manifest_version", logCb);
        checkField(manifest, "name",             logCb);
        checkField(manifest, "version",          logCb);

        // ── content_scripts 참조 파일 검증 ────────────────────
        JsonNode contentScripts = manifest.path("content_scripts");
        if (contentScripts.isArray()) {
            for (JsonNode cs : contentScripts) {
                fixReferencedFiles(projectRoot, cs.path("js"),  ".js",  logCb);
                fixReferencedFiles(projectRoot, cs.path("css"), ".css", logCb);
            }
        }

        // ── background / service_worker 검증 ──────────────────
        JsonNode bg = manifest.path("background");
        if (!bg.isMissingNode()) {
            String swPath = bg.path("service_worker").asText(bg.path("scripts").path(0).asText(""));
            if (!swPath.isBlank()) {
                Path swFile = projectRoot.resolve(swPath);
                if (!Files.exists(swFile)) {
                    createEmptyFile(swFile, "// service-worker.js\n", logCb);
                }
            }
        }

        // ── icons 참조 파일 검증 ──────────────────────────────
        JsonNode icons = manifest.path("icons");
        if (icons.isObject()) {
            icons.fields().forEachRemaining(entry -> {
                String iconPath = entry.getValue().asText("");
                if (!iconPath.isBlank()) {
                    Path iconFile = projectRoot.resolve(iconPath);
                    if (!Files.exists(iconFile)) {
                        int size = parseSizeFromKey(entry.getKey());
                        logCb.accept("⚠ 아이콘 누락 → 자동 생성: " + iconPath);
                        assetGenerator.generate(projectRoot,
                            new GeneratedAsset(iconPath, "icon", "chrome extension icon", size, size));
                    }
                    if (Files.exists(iconFile)) {
                        logCb.accept("  ✓ " + iconPath);
                    }
                }
            });
        } else {
            // icons 필드가 없으면 기본 아이콘 3종 생성
            logCb.accept("⚠ manifest.json icons 없음 → 기본 아이콘 3종 생성");
            generateDefaultIcons(projectRoot, logCb);
            injectIconsIntoManifest(manifestPath, logCb);
        }

        // ── 최종 판정 ─────────────────────────────────────────
        // manifest.json, README.md, content.js(or background.js) 검증
        boolean hasManifest = Files.exists(projectRoot.resolve("manifest.json"));
        boolean hasScript   = hasAnyScript(projectRoot);
        boolean hasReadme   = Files.exists(projectRoot.resolve("README.md"));

        if (hasManifest) logCb.accept("  ✓ manifest.json");
        if (hasReadme)   logCb.accept("  ✓ README.md");
        if (hasScript)   logCb.accept("  ✓ 스크립트 파일 확인");

        boolean passed = hasManifest && hasScript;
        if (passed) {
            logCb.accept("✓ manifest 참조 파일 검증 통과");
            logCb.accept("✓ 필수 파일 검증 통과");
        } else {
            logCb.accept("⚠ 일부 파일 누락 — 보정 완료 상태로 계속 진행");
        }
        return passed;
    }

    // ─────────────────────────────────────────────────────────
    //  내부 헬퍼
    // ─────────────────────────────────────────────────────────

    private boolean checkField(JsonNode manifest, String field, Consumer<String> log) {
        if (manifest.path(field).isMissingNode() || manifest.path(field).asText("").isBlank()) {
            log.accept("⚠ manifest.json 필드 누락: " + field);
            return false;
        }
        log.accept("  ✓ manifest." + field + " = " + manifest.path(field).asText());
        return true;
    }

    private void fixReferencedFiles(Path root, JsonNode fileArray,
                                     String ext, Consumer<String> log) {
        if (!fileArray.isArray()) return;
        for (JsonNode fn : fileArray) {
            String relPath = fn.asText("");
            if (relPath.isBlank()) continue;
            Path target = root.resolve(relPath);
            if (!Files.exists(target)) {
                log.accept("⚠ 참조 파일 누락 → 빈 파일 생성: " + relPath);
                String stub = ext.equals(".js")
                    ? "// " + relPath + "\nconsole.log('" + relPath + " loaded');\n"
                    : "/* " + relPath + " */\n";
                createEmptyFile(target, stub, log);
            } else {
                log.accept("  ✓ " + relPath);
            }
        }
    }

    private void createEmptyFile(Path target, String content, Consumer<String> log) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            log.accept("  ✓ " + target.getFileName() + " 생성됨");
        } catch (Exception e) {
            log.accept("  ⚠ 파일 생성 실패: " + target.getFileName() + " — " + e.getMessage());
        }
    }

    private void createDefaultManifest(Path manifestPath, String projectName,
                                        Consumer<String> log) {
        try {
            String json = "{\n"
                + "  \"manifest_version\": 3,\n"
                + "  \"name\": \"" + projectName + "\",\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"description\": \"Generated by JARVIS Dev Agent\",\n"
                + "  \"permissions\": [\"activeTab\", \"scripting\"],\n"
                + "  \"content_scripts\": [\n"
                + "    { \"matches\": [\"<all_urls>\"], \"js\": [\"content.js\"], \"css\": [\"styles.css\"] }\n"
                + "  ],\n"
                + "  \"icons\": {\n"
                + "    \"16\": \"icons/icon16.png\",\n"
                + "    \"48\": \"icons/icon48.png\",\n"
                + "    \"128\": \"icons/icon128.png\"\n"
                + "  }\n"
                + "}\n";
            Files.createDirectories(manifestPath.getParent());
            Files.writeString(manifestPath, json, StandardCharsets.UTF_8);
            log.accept("  ✓ manifest.json 기본값으로 생성됨");
        } catch (Exception e) {
            log.accept("  ✗ manifest.json 생성 실패: " + e.getMessage());
        }
    }

    private void generateDefaultIcons(Path projectRoot, Consumer<String> log) {
        int[] sizes = {16, 48, 128};
        for (int size : sizes) {
            String path = "icons/icon" + size + ".png";
            assetGenerator.generate(projectRoot,
                new GeneratedAsset(path, "icon", "chrome extension icon", size, size));
            if (Files.exists(projectRoot.resolve(path))) {
                log.accept("  ✓ " + path + " 생성됨");
            }
        }
    }

    private void injectIconsIntoManifest(Path manifestPath, Consumer<String> log) {
        try {
            ObjectNode manifest = (ObjectNode) MAPPER.readTree(manifestPath.toFile());
            ObjectNode icons = MAPPER.createObjectNode();
            icons.put("16",  "icons/icon16.png");
            icons.put("48",  "icons/icon48.png");
            icons.put("128", "icons/icon128.png");
            manifest.set("icons", icons);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
            log.accept("  ✓ manifest.json icons 필드 추가됨");
        } catch (Exception e) {
            log.accept("  ⚠ manifest icons 주입 실패: " + e.getMessage());
        }
    }

    private boolean hasAnyScript(Path root) {
        try (var stream = Files.walk(root, 3)) {
            return stream.anyMatch(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return (name.equals("content.js") || name.equals("background.js")
                     || name.equals("service-worker.js") || name.equals("popup.js"))
                    && Files.isRegularFile(p);
            });
        } catch (Exception e) { return false; }
    }

    private int parseSizeFromKey(String key) {
        try { return Integer.parseInt(key); } catch (Exception e) { return 48; }
    }
}
