package com.jarvis.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 로컬 Git 저장소 조회 서비스.
 * git repo가 아닌 경우 빈 결과를 반환하고 오류를 스팸하지 않음.
 */
@Service
@Slf4j
public class GitService {

    @Value("${app.project-root:d:/jarvis-agent}")
    private String projectRoot;

    public String getDiffStat()          { return isGitRepo() ? run("git","diff","--stat","HEAD") : ""; }
    public String getDiff()              { return isGitRepo() ? run("git","diff","HEAD")           : ""; }
    public String getStatus()            { return isGitRepo() ? run("git","status","--short")      : ""; }
    public String getCurrentBranch()     { return isGitRepo() ? run("git","rev-parse","--abbrev-ref","HEAD").trim() : "none"; }

    public List<String> getChangedFiles() {
        if (!isGitRepo()) return Collections.emptyList();
        String out = run("git", "diff", "--name-only", "HEAD");
        if (out.isBlank()) {
            // 스테이징된 파일도 확인
            out = run("git", "diff", "--cached", "--name-only");
        }
        if (out.isBlank()) return Collections.emptyList();
        return Arrays.stream(out.split("[\r\n]+"))
                     .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public boolean isGitRepo() {
        return new File(projectRoot, ".git").exists();
    }

    // ── 내부 실행 ─────────────────────────────────────────────
    private String run(String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(new File(projectRoot));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
            }
            boolean done = p.waitFor(15, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); log.warn("git 타임아웃: {}", Arrays.toString(args)); }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("git 실패 {}: {}", Arrays.toString(args), e.getMessage());
            return "";
        }
    }
}
