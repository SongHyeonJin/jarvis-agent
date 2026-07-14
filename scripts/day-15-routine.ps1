#!/usr/bin/env pwsh
# DAY 15 Jarvis Auto-Commit Routine (2026-06-15 23:00 KST)
# Merge PR #17 (feature/day-15 -> develop)

Set-Location "d:\jarvis-agent"
Write-Host "[DAY 15] Merging PR #17 (Anthropic API)..." -ForegroundColor Cyan

# Merge via GitHub API (no local checkout needed)
gh api repos/SongHyeonJin/jarvis-agent/pulls/17/merge -X PUT -f merge_method=merge `
  -f "commit_title=Merge PR #17: Feat: Anthropic 직통 API 교체 + .gitignore 수정 (adapter/out 패키지 추적)"

# Delete remote branch
gh api repos/SongHyeonJin/jarvis-agent/git/refs/heads/feature/day-15 -X DELETE 2>$null

Write-Host "✅ DAY 15 완료 (2026-06-15 23:00): PR #17 merged to develop" -ForegroundColor Green
