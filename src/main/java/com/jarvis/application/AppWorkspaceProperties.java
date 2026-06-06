package com.jarvis.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * app.* 설정 바인딩.
 *
 * application.yml:
 *   app:
 *     project-root: d:/jarvis-agent
 *     workspace-root: d:/jarvis-workspaces
 */
@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppWorkspaceProperties {

    /** 기존 JARVIS 프로젝트 루트 */
    private String projectRoot = "d:/jarvis-agent";

    /** 새 독립 프로젝트 생성 시 사용할 워크스페이스 루트 */
    private String workspaceRoot = "d:/jarvis-workspaces";
}
