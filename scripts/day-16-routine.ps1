#!/usr/bin/env pwsh
# DAY 16 Jarvis Auto-Commit Routine (2026-06-16 23:00 KST)
# ClaudeCliExecutor: 20min timeout, 3-tier model routing, trust paths

Set-Location "d:\jarvis-agent"

Write-Host "[DAY 16] Starting routine..." -ForegroundColor Cyan

# Stash any local changes
git stash -u 2>$null

# Switch to develop and pull latest
git checkout develop
git pull origin develop

# Create feature branch
git checkout -b feature/day-16

# Checkout Day 16 file from wip/pending-backup
git checkout wip/pending-backup -- src/main/java/com/jarvis/application/ClaudeCliExecutor.java

# Stage and commit
git add src/main/java/com/jarvis/application/ClaudeCliExecutor.java
$env:GIT_AUTHOR_DATE = "2026-06-16T23:00:00+09:00"
$env:GIT_COMMITTER_DATE = "2026-06-16T23:00:00+09:00"
git commit -m @'
Feat: ClaudeCliExecutor 개선 — 20분 타임아웃·3-tier 모델 라우팅·신뢰 경로

- TIMEOUT_MS: 5분 → 20분 (대형 스캐폴딩 작업 대응)
- execute(prompt, workingDir, model) 오버로드 추가 — Opus/Sonnet/Haiku 선택 전달
- TRUST_BASE_PATHS: d:/jarvis-workspaces, d:/jarvis-agent 화이트리스트

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
'@
$env:GIT_AUTHOR_DATE = ""
$env:GIT_COMMITTER_DATE = ""

# Push
git push origin feature/day-16

# Create PR and capture number
$prJson = gh pr create --base develop --head feature/day-16 `
  --title "Feat: ClaudeCliExecutor 개선 — 20분 타임아웃·3-tier 모델 라우팅·신뢰 경로" `
  --body @'
## 변경 내용

### ClaudeCliExecutor (개선)
- `TIMEOUT_MS` 5분 → 20분 — 대형 스캐폴딩 작업(멀티파일 Spring Boot 등) 대응
- `execute(prompt, workingDir, model)` 오버로드 추가 — Opus/Sonnet/Haiku 직접 지정 가능
- `TRUST_BASE_PATHS`: `d:/jarvis-workspaces`, `d:/jarvis-agent` 신뢰 경로 화이트리스트

## Test plan

- [ ] 대형 NEW_PROJECT 요청 시 20분 내 완료 확인
- [ ] `--model claude-opus-4-8` 플래그가 CLI에 정상 전달되는지 확인
- [ ] 신뢰 경로 외 디렉터리 접근 시 경고 동작 확인
'@ --json number 2>&1

$prNum = ($prJson | ConvertFrom-Json).number
Write-Host "[DAY 16] PR #$prNum created" -ForegroundColor Green

Start-Sleep -Seconds 3

# Merge via GitHub API (no local checkout needed)
gh api repos/SongHyeonJin/jarvis-agent/pulls/$prNum/merge -X PUT -f merge_method=merge

# Delete remote branch
gh api repos/SongHyeonJin/jarvis-agent/git/refs/heads/feature/day-16 -X DELETE 2>$null

Write-Host "✅ DAY 16 완료 (2026-06-16 23:00): PR #$prNum merged to develop" -ForegroundColor Green
