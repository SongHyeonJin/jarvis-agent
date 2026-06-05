package com.jarvis.application;

import java.util.List;

/**
 * 프로젝트 유형별로 생성할 에셋 계획.
 *
 * @param required 에셋 생성이 필수인지 여부
 * @param assets   생성할 에셋 목록
 */
public record AssetPlan(
        boolean              required,
        List<GeneratedAsset> assets
) {
    public static AssetPlan empty() {
        return new AssetPlan(false, List.of());
    }
}
