package com.jarvis.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/**
 * Java {@link BufferedImage} 기반 플레이스홀더 PNG 에셋 생성기.
 *
 * 외부 이미지 API 없이 동작하는 MVP 구현체.
 * 프로젝트 유형/에셋 타입에 따라 색상·아이콘 문자를 다르게 렌더링한다.
 *
 * 추후 {@link AssetGenerator} 구현체를 교체하면
 * DALL-E / Stable Diffusion 등 AI 이미지로 교체 가능.
 */
@Component
@Slf4j
public class PlaceholderPngAssetGenerator implements AssetGenerator {

    @Override
    public boolean generate(Path projectRoot, GeneratedAsset asset) {
        try {
            Path target = projectRoot.resolve(asset.path());

            // 이미 파일이 존재하면 생성 스킵
            if (Files.exists(target)) {
                log.debug("[AssetGenerator] 이미 존재함, 스킵: {}", target);
                return true;
            }

            Files.createDirectories(target.getParent());

            int w = asset.width()  > 0 ? asset.width()  : 128;
            int h = asset.height() > 0 ? asset.height() : 128;

            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 배경 그라디언트
            Color bgTop = colorForType(asset.type(), true);
            Color bgBot = colorForType(asset.type(), false);
            GradientPaint gp = new GradientPaint(0, 0, bgTop, 0, h, bgBot);
            g.setPaint(gp);
            g.fillRoundRect(0, 0, w, h, w / 4, h / 4);

            // 아이콘 문자
            String icon = iconCharFor(asset);
            int fontSize = (int)(Math.min(w, h) * 0.52);
            Font font = new Font("Segoe UI Emoji", Font.PLAIN, fontSize);
            g.setFont(font);
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            int tx = (w - fm.stringWidth(icon)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(icon, tx, ty);

            // 테두리
            g.setColor(new Color(255, 255, 255, 50));
            g.setStroke(new BasicStroke(Math.max(1f, w * 0.03f)));
            g.drawRoundRect(1, 1, w - 2, h - 2, w / 4, h / 4);

            g.dispose();
            ImageIO.write(img, "PNG", target.toFile());

            log.info("[AssetGenerator] 생성 완료: {} ({}x{})", asset.path(), w, h);
            return true;

        } catch (Exception e) {
            log.warn("[AssetGenerator] 생성 실패: {} — {}", asset.path(), e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────
    //  색상 결정
    // ─────────────────────────────────────────────────────────

    private Color colorForType(String type, boolean top) {
        if (type == null) type = "";
        return switch (type.toLowerCase()) {
            case "icon", "chrome" -> top ? new Color(0x4A90E2)  : new Color(0x1A5CB5);
            case "sprite"         -> top ? new Color(0xFF6B35)  : new Color(0xC84B00);
            case "background"     -> top ? new Color(0x1A1A2E)  : new Color(0x16213E);
            case "player"         -> top ? new Color(0x00D4FF)  : new Color(0x0088AA);
            case "enemy"          -> top ? new Color(0xFF3333)  : new Color(0xAA0000);
            case "favicon"        -> top ? new Color(0x6C63FF)  : new Color(0x4A41CC);
            default               -> top ? new Color(0x5B4FE8)  : new Color(0x3B2FBB);
        };
    }

    // ─────────────────────────────────────────────────────────
    //  아이콘 문자 결정
    // ─────────────────────────────────────────────────────────

    private String iconCharFor(GeneratedAsset asset) {
        String p = asset.path() != null ? asset.path().toLowerCase() : "";
        String t = asset.type() != null ? asset.type().toLowerCase() : "";

        // 경로 기반
        if (p.contains("cat")    || p.contains("냥")) return "🐱";
        if (p.contains("dog")    || p.contains("강아지")) return "🐶";
        if (p.contains("player"))                    return "🚀";
        if (p.contains("enemy")) return "👾";
        if (p.contains("background")) return "🌌";
        if (p.contains("favicon")) return "⭐";
        if (p.contains("icon"))  return "🔷";

        // 타입 기반
        return switch (t) {
            case "icon"       -> "🔷";
            case "sprite"     -> "🎮";
            case "background" -> "🌌";
            case "player"     -> "🚀";
            case "enemy"      -> "👾";
            case "favicon"    -> "⭐";
            default           -> "✨";
        };
    }
}
