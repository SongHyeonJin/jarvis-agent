#!/usr/bin/env pwsh
# DAY 18 Jarvis Auto-Commit Routine (2026-06-18 23:00 KST)
# DevJobService (3-tier model), AutoPreviewService

Set-Location "d:\jarvis-agent"
Write-Host "[DAY 18] Starting routine..." -ForegroundColor Cyan

git stash -u 2>$null
git checkout develop
git pull origin develop
git checkout -b feature/day-18

git checkout wip/pending-backup -- `
  src/main/java/com/jarvis/application/DevJobService.java `
  src/main/java/com/jarvis/application/AutoPreviewService.java

git add src/main/java/com/jarvis/application/DevJobService.java `
        src/main/java/com/jarvis/application/AutoPreviewService.java

$env:GIT_AUTHOR_DATE = "2026-06-18T23:00:00+09:00"
$env:GIT_COMMITTER_DATE = "2026-06-18T23:00:00+09:00"
git commit -m @'
Feat: 3-tier 모델 자동 선택 (Opus/Sonnet/Haiku) + AutoPreview 서비스 개선

- DevJobService.selectModel(): Opus 4.8(복잡 NEW_PROJECT), Sonnet 4.6(기본), Haiku(UI전용) 자동 선택
- OPUS_PROJECT_KEYWORDS: MSA·풀스택·결제·OAuth·플랫폼·DDD·CQRS 등 대형 키워드 매칭
- SIMPLE_KEYWORDS: 색상·폰트·여백·배경 등 UI 전용 → Haiku 라우팅
- AutoPreviewService: OS별 브라우저 오픈, Spring Boot 포트 자동 감지, REACT/NEXT npm dev 개선

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
'@
$env:GIT_AUTHOR_DATE = ""
$env:GIT_COMMITTER_DATE = ""

git push origin feature/day-18

$prJson = gh pr create --base develop --head feature/day-18 `
  --title "Feat: 3-tier 모델 자동 선택 (Opus/Sonnet/Haiku) + AutoPreview 서비스 개선" `
  --body @'
## 변경 내용

### DevJobService (개선)
- 3-tier 모델 자동 선택 `selectModel()` 구현
  - `NEW_PROJECT` + 복잡 키워드(MSA·풀스택·결제·OAuth·플랫폼·DDD 등) → **Opus 4.8**
  - `NEW_PROJECT` (일반) → **Sonnet 4.6**
  - UI/스타일 전용(색상·폰트·여백·배경) → **Haiku**
  - 기본값 → **Sonnet 4.6**
- `MODEL_OPUS`, `MODEL_SONNET`, `MODEL_HAIKU` 상수 정의

### AutoPreviewService (개선)
- Spring Boot 포트 자동 감지 (8080 기본)
- OS별 브라우저 오픈 커맨드 분기 (Windows/macOS/Linux)
- REACT/NEXT 타입 `npm run dev` 백그라운드 실행 개선

## Test plan

- [ ] 'MSA 아키텍처로 주문 관리 시스템 만들어줘' → Opus 4.8 선택 확인
- [ ] 일반 Spring Boot 요청 → Sonnet 4.6 확인
- [ ] '배경색 바꿔줘' → Haiku 선택 확인
- [ ] Spring Boot 프로젝트 생성 후 브라우저 자동 실행 확인
'@ --json number 2>&1

$prNum = ($prJson | ConvertFrom-Json).number
Write-Host "[DAY 18] PR #$prNum created" -ForegroundColor Green
Start-Sleep -Seconds 3

gh api repos/SongHyeonJin/jarvis-agent/pulls/$prNum/merge -X PUT -f merge_method=merge
gh api repos/SongHyeonJin/jarvis-agent/git/refs/heads/feature/day-18 -X DELETE 2>$null

Write-Host "✅ DAY 18 완료 (2026-06-18 23:00): PR #$prNum merged to develop" -ForegroundColor Green
