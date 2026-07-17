package com.jarvis.application;

/**
 * Dev Job 작업 유형.
 *
 * MODIFY_JARVIS   — 기존 JARVIS 프로젝트(d:/jarvis-agent) 수정
 * NEW_PROJECT     — D:/jarvis-workspaces/{slug} 에 새 독립 프로젝트 생성
 * MODIFY_EXTERNAL — 이미 생성된 외부 워크스페이스 프로젝트 수정
 */
public enum JobType {
    MODIFY_JARVIS,
    NEW_PROJECT,
    MODIFY_EXTERNAL
}
