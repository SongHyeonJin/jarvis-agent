#!/usr/bin/env pwsh
# DAY 19 Jarvis Auto-Commit Routine (2026-06-19 23:00 KST)
# CardEvent SSE, Controller streaming updates

Set-Location "d:\jarvis-agent"
Write-Host "[DAY 19] Starting routine..." -ForegroundColor Cyan

git stash -u 2>$null
git checkout develop
git pull origin develop
git checkout -b feature/day-19

git checkout wip/pending-backup -- `
  src/main/java/com/jarvis/adapter/in/web/CardEventController.java `
  src/main/java/com/jarvis/application/CardEventEmitter.java `
  src/main/java/com/jarvis/adapter/in/web/DevJobController.java `
  src/main/java/com/jarvis/adapter/in/web/MemoController.java `
  src/main/java/com/jarvis/adapter/in/web/TodoController.java

git add src/main/java/com/jarvis/adapter/in/web/CardEventController.java `
        src/main/java/com/jarvis/application/CardEventEmitter.java `
        src/main/java/com/jarvis/adapter/in/web/DevJobController.java `
        src/main/java/com/jarvis/adapter/in/web/MemoController.java `
        src/main/java/com/jarvis/adapter/in/web/TodoController.java

$env:GIT_AUTHOR_DATE = "2026-06-19T23:00:00+09:00"
$env:GIT_COMMITTER_DATE = "2026-06-19T23:00:00+09:00"
git commit -m @'
Feat: CardEvent SSE 실시간 알림 + 컨트롤러 SSE 스트리밍 응답 추가

- CardEventEmitter: Reactor Sinks.Many 기반 SSE 이벤트 발행기 (박수 wake 신호 포함)
- CardEventController: GET /api/card-events/stream (SSE 구독), POST /api/card-events/wake (wake 신호)
- DevJobController·MemoController·TodoController: SSE 스트리밍 응답 엔드포인트 추가

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
'@
$env:GIT_AUTHOR_DATE = ""
$env:GIT_COMMITTER_DATE = ""

git push origin feature/day-19

$prJson = gh pr create --base develop --head feature/day-19 `
  --title "Feat: CardEvent SSE 실시간 알림 + 컨트롤러 SSE 스트리밍 응답 추가" `
  --body @'
## 변경 내용

### CardEventEmitter (신규)
- Reactor `Sinks.Many` 기반 SSE 이벤트 발행기
- 박수 감지 wake 신호 수신 및 SSE 브로드캐스트
- 멀티 클라이언트 동시 스트리밍 지원

### CardEventController (신규)
- `GET /api/card-events/stream` — SSE 스트림 엔드포인트
- `POST /api/card-events/wake` — 박수/외부 트리거 wake 신호 수신

### DevJobController·MemoController·TodoController (개선)
- SSE 스트리밍 응답 엔드포인트 추가
- 실시간 카드 업데이트 지원

## Test plan

- [ ] `GET /api/card-events/stream` SSE 연결 후 wake 신호 수신 확인
- [ ] `POST /api/card-events/wake` 호출 시 구독 클라이언트에 이벤트 전달 확인
- [ ] 박수 2회 → wake → 자비스 화면 활성화 전체 흐름 확인
'@ --json number 2>&1

$prNum = ($prJson | ConvertFrom-Json).number
Write-Host "[DAY 19] PR #$prNum created" -ForegroundColor Green
Start-Sleep -Seconds 3

gh api repos/SongHyeonJin/jarvis-agent/pulls/$prNum/merge -X PUT -f merge_method=merge
gh api repos/SongHyeonJin/jarvis-agent/git/refs/heads/feature/day-19 -X DELETE 2>$null

Write-Host "✅ DAY 19 완료 (2026-06-19 23:00): PR #$prNum merged to develop" -ForegroundColor Green
