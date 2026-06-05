package com.jarvis.application;

/**
 * Dev Job 작업 유형.
 *
 * MODIFY_JARVIS  — 기존 JARVIS 프로젝트(d:/jarvis-agent) 수정
 * NEW_PROJECT    — D:/jarvis-workspaces/{slug} 에 새 독립 프로젝트 생성
 */
public enum JobType {
    MODIFY_JARVIS,
    NEW_PROJECT
}
