#!/usr/bin/env pwsh
# DAY 20 Jarvis Auto-Commit Routine (2026-06-20 23:00 KST)
# index.html, jarvis-listener, extension, docs, DB

Set-Location "d:\jarvis-agent"
Write-Host "[DAY 20] Starting routine..." -ForegroundColor Cyan

git stash -u 2>$null
git checkout develop
git pull origin develop
git checkout -b feature/day-20

# Get all Day 20 files from wip/pending-backup
git checkout wip/pending-backup -- `
  src/main/resources/static/index.html `
  extension/ `
  jarvis-listener/ `
  .claude/rules/ai-routing.md `
  .claude/settings.json `
  CLAUDE.MD `
  data/jarvisdb.mv.db

# Delete trace DB if it exists
git rm data/jarvisdb.trace.db 2>$null

# Stage everything
git add src/main/resources/static/index.html `
        extension/ `
        jarvis-listener/ `
        .claude/rules/ai-routing.md `
        .claude/settings.json `
        CLAUDE.MD `
        data/jarvisdb.mv.db

$env:GIT_AUTHOR_DATE = "2026-06-20T23:00:00+09:00"
$env:GIT_COMMITTER_DATE = "2026-06-20T23:00:00+09:00"
git commit -m @'
Feat: UI 자동 시동·박수 인식 리스너·Chrome 확장 + AI 라우팅 전략 문서화

- index.html: DOMContentLoaded에서 boot() 자동 호출, 카드 상세 모달·글래스모피즘, SSE 구독
- jarvis-listener/ v2.0: 박수 2회 peak 감지 (threshold=0.40), 시스템 트레이, Google STT
- extension/: Chrome Manifest V3 (background.js, offscreen 오디오 API)
- CLAUDE.MD·ai-routing.md: 3-tier 모델 전략(Opus/Sonnet/Haiku) 문서화
- .claude/settings.json: 권한 훅 allowlist 추가
- jarvisdb.trace.db 삭제

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
'@
$env:GIT_AUTHOR_DATE = ""
$env:GIT_COMMITTER_DATE = ""

git push origin feature/day-20

$prJson = gh pr create --base develop --head feature/day-20 `
  --title "Feat: UI 자동 시동·박수 인식 리스너·Chrome 확장 + AI 라우팅 전략 문서화" `
  --body @'
## 변경 내용

### index.html (개편)
- `DOMContentLoaded`에서 `boot()` 자동 호출 — 클릭 오버레이 제거
- 카드 상세 모달·글래스모피즘 UI
- SSE 이벤트 구독으로 실시간 wake 처리

### jarvis-listener/ (신규)
- `listener.py` v2.0 — 박수 2회 peak 감지 (threshold=0.40), 시스템 트레이, Google STT
- `install.bat` / `requirements.txt` / `run.vbs`

### extension/ (신규)
- Chrome Manifest V3 확장 — 백그라운드 오디오 (offscreen API)
- `background.js`, `popup.html/js`, `offscreen.html/js`

### 문서
- `CLAUDE.MD`·`ai-routing.md`: 3-tier AI 모델 전략(Opus/Sonnet/Haiku) 최신화
- `.claude/settings.json`: 권한 훅 allowlist 추가

### DB
- `jarvisdb.mv.db` 업데이트, `jarvisdb.trace.db` 삭제

## Test plan

- [ ] 자비스 페이지 열면 자동으로 boot() 실행 및 브리핑 시작 확인
- [ ] 박수 2회 → wake SSE → 화면 활성화 전체 흐름 확인
- [ ] Python 리스너 install.bat으로 설치·트레이 아이콘 표시 확인
- [ ] Chrome 확장 설치 후 오디오 감지 동작 확인
'@ --json number 2>&1

$prNum = ($prJson | ConvertFrom-Json).number
Write-Host "[DAY 20] PR #$prNum created" -ForegroundColor Green
Start-Sleep -Seconds 3

gh api repos/SongHyeonJin/jarvis-agent/pulls/$prNum/merge -X PUT -f merge_method=merge
gh api repos/SongHyeonJin/jarvis-agent/git/refs/heads/feature/day-20 -X DELETE 2>$null

Write-Host "✅ DAY 20 완료 (2026-06-20 23:00): PR #$prNum merged to develop" -ForegroundColor Green
