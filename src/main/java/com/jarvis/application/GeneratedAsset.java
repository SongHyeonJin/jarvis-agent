package com.jarvis.application;

/**
 * 생성할 단일 에셋 정보.
 *
 * @param path   프로젝트 루트 기준 상대 경로 (예: "icons/icon16.png")
 * @param type   에셋 유형 ("icon" | "sprite" | "background" | "favicon" 등)
 * @param prompt 이미지 생성용 텍스트 설명 (AI 이미지 API 연동 시 사용)
 * @param width  가로 픽셀 크기
 * @param height 세로 픽셀 크기
 */
public record GeneratedAsset(
        String path,
        String type,
        String prompt,
        int    width,
        int    height
) {}
