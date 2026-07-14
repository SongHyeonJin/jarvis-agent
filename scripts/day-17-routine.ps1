#!/usr/bin/env pwsh
# DAY 17 Jarvis Auto-Commit Routine (2026-06-17 23:00 KST)
# ChatService, WorkspaceResolver, ProjectTypeAnalyzer

Set-Location "d:\jarvis-agent"
Write-Host "[DAY 17] Starting routine..." -ForegroundColor Cyan

git stash -u 2>$null
git checkout develop
git pull origin develop
git checkout -b feature/day-17

git checkout wip/pending-backup -- `
  src/main/java/com/jarvis/application/ChatService.java `
  src/main/java/com/jarvis/application/WorkspaceResolver.java `
  src/main/java/com/jarvis/application/ProjectTypeAnalyzer.java

git add src/main/java/com/jarvis/application/ChatService.java `
        src/main/java/com/jarvis/application/WorkspaceResolver.java `
        src/main/java/com/jarvis/application/ProjectTypeAnalyzer.java

$env:GIT_AUTHOR_DATE = "2026-06-17T23:00:00+09:00"
$env:GIT_COMMITTER_DATE = "2026-06-17T23:00:00+09:00"
git commit -m @'
Feat: 프로젝트 구분 시스템 프롬프트 + WorkspaceResolver MODIFY_RECENT + SPRING_BOOT 기본값

- ChatService: 자비스 프로젝트(d:/jarvis-agent)와 워크스페이스(d:/jarvis-workspaces/) 구분 시스템 프롬프트
- WorkspaceResolver: MODIFY_RECENT 패턴 추가 ('방금 만든', '그 게임', '거기에 추가' 등 감지)
- ProjectTypeAnalyzer: 백엔드·REST API·CRUD 패턴 추가, 기본값 WEB_APP → SPRING_BOOT 변경

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
'@
$env:GIT_AUTHOR_DATE = ""
$env:GIT_COMMITTER_DATE = ""

git push origin feature/day-17

$prJson = gh pr create --base develop --head feature/day-17 `
  --title "Feat: 프로젝트 구분 시스템 프롬프트 + WorkspaceResolver MODIFY_RECENT + SPRING_BOOT 기본값" `
  --body @'
## 변경 내용

### ChatService (개선)
- 자비스 프로젝트(`d:/jarvis-agent`)와 워크스페이스(`d:/jarvis-workspaces/`) 명확히 구분하는 시스템 프롬프트
- '방금 만든 프로젝트' vs '자비스 자체' 혼동 방지

### WorkspaceResolver (개선)
- `MODIFY_RECENT` 패턴 추가: '방금 만든', '그 게임', '거기에 추가' 등 최근 프로젝트 지칭 감지
- 패턴 우선순위: NEW_PROJECT → MODIFY_RECENT → MODIFY_EXTERNAL

### ProjectTypeAnalyzer (개선)
- SPRING_BOOT 패턴 추가: 백엔드·REST API·CRUD·관리 시스템 키워드 감지
- 기본값: `WEB_APP` → `SPRING_BOOT` (Java/Spring Boot 우선 원칙)

## Test plan

- [ ] '방금 만든 게임에 이것 추가해줘' → 최근 프로젝트 경로 자동 탐지 확인
- [ ] 'REST API 서버 만들어줘' → ProjectType.SPRING_BOOT 확인
- [ ] 자비스 자체 수정 요청과 워크스페이스 요청이 올바르게 구분되는지 확인
'@ --json number 2>&1

$prNum = ($prJson | ConvertFrom-Json).number
Write-Host "[DAY 17] PR #$prNum created" -ForegroundColor Green
Start-Sleep -Seconds 3

gh api repos/SongHyeonJin/jarvis-agent/pulls/$prNum/merge -X PUT -f merge_method=merge
gh api repos/SongHyeonJin/jarvis-agent/git/refs/heads/feature/day-17 -X DELETE 2>$null

Write-Host "✅ DAY 17 완료 (2026-06-17 23:00): PR #$prNum merged to develop" -ForegroundColor Green
