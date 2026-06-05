package com.jarvis.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 프로젝트 유형에 따른 에셋 계획(AssetPlan)을 생성하고
 * {@link AssetGenerator}를 통해 실제 파일로 출력하는 서비스.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetService {

    private final AssetGenerator assetGenerator;

    // ─────────────────────────────────────────────────────────
    //  AssetPlan 생성 (유형별 규칙)
    // ─────────────────────────────────────────────────────────

    /**
     * 프로젝트 유형과 명령에 맞는 AssetPlan을 결정한다.
     *
     * @param type    프로젝트 유형
     * @param command 사용자 원문 명령 (특수 캐릭터 감지에 활용)
     * @return 에셋 계획
     */
    public AssetPlan plan(ProjectType type, String command) {
        return switch (type) {
            case CHROME_EXTENSION -> planChromeExtension(command);
            case GAME             -> planGame(command);
            case WEB_APP          -> planWebApp(command);
            default               -> AssetPlan.empty();
        };
    }

    // ── Chrome Extension ──────────────────────────────────────

    private AssetPlan planChromeExtension(String command) {
        List<GeneratedAsset> assets = new ArrayList<>();
        String iconPrompt = "Chrome extension icon, " + command;

        assets.add(new GeneratedAsset("icons/icon16.png",  "icon", iconPrompt,  16,  16));
        assets.add(new GeneratedAsset("icons/icon48.png",  "icon", iconPrompt,  48,  48));
        assets.add(new GeneratedAsset("icons/icon128.png", "icon", iconPrompt, 128, 128));

        // 특수 캐릭터: 치즈냥이
        if (hasCat(command)) {
            assets.add(new GeneratedAsset("assets/cat.png", "sprite", "cute cheese cat character", 128, 128));
        }
        return new AssetPlan(true, assets);
    }

    // ── Game ──────────────────────────────────────────────────

    private AssetPlan planGame(String command) {
        List<GeneratedAsset> assets = new ArrayList<>();
        assets.add(new GeneratedAsset("assets/player.png",     "player",     "game player sprite", 48, 48));
        assets.add(new GeneratedAsset("assets/enemy.png",      "enemy",      "game enemy sprite",  48, 48));
        assets.add(new GeneratedAsset("assets/background.png", "background", "game background",   640, 480));
        return new AssetPlan(false, assets);
    }

    // ── Web App ───────────────────────────────────────────────

    private AssetPlan planWebApp(String command) {
        List<GeneratedAsset> assets = new ArrayList<>();
        assets.add(new GeneratedAsset("assets/favicon.png", "favicon", "website favicon", 32, 32));
        return new AssetPlan(false, assets);
    }

    // ─────────────────────────────────────────────────────────
    //  에셋 생성 실행
    // ─────────────────────────────────────────────────────────

    /**
     * AssetPlan 에 따라 실제 파일을 생성한다.
     *
     * @param projectRoot 프로젝트 루트 경로
     * @param plan        에셋 계획
     * @param logCb       SSE 로그 콜백
     * @return 성공적으로 생성된 파일 경로 목록
     */
    public List<String> generate(Path projectRoot, AssetPlan plan, Consumer<String> logCb) {
        List<String> created = new ArrayList<>();
        if (plan == null || !plan.required() && plan.assets().isEmpty()) {
            return created;
        }

        logCb.accept("▶ AssetPlan 생성 완료 (" + plan.assets().size() + "개 에셋)");

        for (GeneratedAsset asset : plan.assets()) {
            Path target = projectRoot.resolve(asset.path());
            if (Files.exists(target)) {
                logCb.accept("  ✓ " + asset.path() + " (기존 파일 유지)");
                created.add(asset.path());
                continue;
            }
            boolean ok = assetGenerator.generate(projectRoot, asset);
            if (ok) {
                logCb.accept("  ✓ " + asset.path() + " 생성 완료 (" + asset.width() + "×" + asset.height() + ")");
                created.add(asset.path());
            } else {
                logCb.accept("  ⚠ " + asset.path() + " 생성 실패 (스킵)");
            }
        }
        return created;
    }

    // ─────────────────────────────────────────────────────────
    //  유틸
    // ─────────────────────────────────────────────────────────

    private boolean hasCat(String command) {
        if (command == null) return false;
        String lower = command.toLowerCase();
        return lower.contains("냥이") || lower.contains("고양이") || lower.contains("cat");
    }
}
