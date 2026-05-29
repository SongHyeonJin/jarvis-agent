package com.jarvis.application;

import java.nio.file.Path;

/**
 * 에셋 파일 생성기 인터페이스.
 *
 * 기본 구현: {@link PlaceholderPngAssetGenerator} (BufferedImage 기반)
 * 추후 구현: OpenAiImageAssetGenerator (DALL-E 3 API 연동)
 */
public interface AssetGenerator {

    /**
     * 에셋을 생성하고 파일로 저장한다.
     *
     * @param projectRoot 프로젝트 루트 경로
     * @param asset       생성할 에셋 정보
     * @return 성공 여부 (true = 파일 생성됨, false = 실패/스킵)
     */
    boolean generate(Path projectRoot, GeneratedAsset asset);
}
