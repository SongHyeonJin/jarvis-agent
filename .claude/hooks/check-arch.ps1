# PostToolUse hook: Java 파일 작성 후 헥사고날 아키텍처 경계 위반 감지

param()

$raw = [Console]::In.ReadToEnd()
if (-not $raw) { exit 0 }

try {
    $data = $raw | ConvertFrom-Json
    $path = $data.tool_input.file_path

    if (-not $path -or $path -notmatch '\.java$') { exit 0 }
    if (-not (Test-Path $path)) { exit 0 }

    $content = Get-Content $path -Raw -ErrorAction SilentlyContinue
    if (-not $content) { exit 0 }

    $warnings = @()

    # Controller가 UseCase 대신 Service를 직접 주입하는지 검사
    # (패키지 위치가 아닌 의존 방향만 검사 → 새 레이어 추가해도 오탐 없음)
    if ($content -match '@RestController|@Controller') {
        if ($content -match 'private final \w+Service\s') {
            $warnings += "[ARCH] Controller는 Service 대신 UseCase 인터페이스를 주입하세요. (예: *UseCase, *Port)"
        }
    }

    # Service가 Spring AI SDK를 직접 호출하는지 검사 (AiModelPort 우회 감지)
    if ($content -match '@Service') {
        if ($content -match 'ChatClient|OpenAiChatModel|AnthropicChatModel') {
            $warnings += "[ARCH] Service에서 AI SDK 직접 호출 금지 — AiModelPort 인터페이스를 통해 라우팅하세요."
        }
    }

    # Domain model에 Spring Bean 어노테이션 혼용 검사
    # domain/model 경로에 있는 파일만 검사 (경로 기반이지만 이건 절대 바뀌지 않는 규칙)
    if ($path -match 'domain[/\\]model[/\\]') {
        if ($content -match '\b@Service\b|\b@Component\b|\b@Controller\b') {
            $warnings += "[ARCH] Domain model에 Spring Bean 어노테이션 사용 금지."
        }
    }

    # @Autowired 사용 감지 (전체 프로젝트에 해당)
    if ($content -match '\b@Autowired\b') {
        $warnings += "[STYLE] @Autowired 대신 @RequiredArgsConstructor + final 필드를 사용하세요."
    }

    if ($warnings.Count -gt 0) {
        Write-Host ""
        Write-Host "=== Jarvis 아키텍처 경고 ==="
        $warnings | ForEach-Object { Write-Host "  $_" }
        Write-Host "============================="
    }

} catch {
    exit 0
}
