package com.jarvis.application;

/**
 * 신규 프로젝트 생성 시 유형을 나타내는 열거형.
 *
 * WorkspaceResolver → ProjectTypeAnalyzer → ScaffoldService / AssetService 에서 사용.
 */
public enum ProjectType {

    /** Spring Boot Gradle 프로젝트 */
    SPRING_BOOT,

    /** 순수 Java 콘솔/GUI 앱 */
    JAVA_APP,

    /** Chrome/Firefox 브라우저 확장 프로그램 (Manifest V3) */
    CHROME_EXTENSION,

    /** HTML/CSS/JS 단일 페이지 웹앱 */
    WEB_APP,

    /** React (Vite/CRA) 프론트엔드 앱 */
    REACT_APP,

    /** Next.js 풀스택 앱 */
    NEXT_APP,

    /** HTML Canvas / JavaScript 게임 */
    GAME,

    /** Electron 데스크탑 앱 */
    ELECTRON_APP,

    /** Python 스크립트 / Flask / FastAPI */
    PYTHON_APP,

    /** 분류 불가 (기본값) */
    UNKNOWN
}
