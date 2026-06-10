# PreToolUse hook: Claude Code 권한 요청을 서버에 POST

param()

$raw = [Console]::In.ReadToEnd()
if (-not $raw) { exit 0 }

try {
    $data = $raw | ConvertFrom-Json

    $body = @{
        tool_name  = if ($data.tool_name) { $data.tool_name } else { "unknown" }
        tool_input = if ($data.tool_input) { ($data.tool_input | ConvertTo-Json -Compress) } else { "{}" }
        session_id = if ($data.session_id) { $data.session_id } else { "" }
    } | ConvertTo-Json -Compress

    $null = Invoke-RestMethod `
        -Uri "http://localhost:8080/api/claude-permissions" `
        -Method POST `
        -ContentType "application/json" `
        -Body $body `
        -TimeoutSec 2 `
        -ErrorAction SilentlyContinue

} catch {
    # 서버 미실행 시 조용히 무시
}

exit 0
