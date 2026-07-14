---
description: Check Jarvis server status and start if not running
---

# /run-server

Jarvis 서버 상태를 확인하고 필요 시 기동합니다.

## 실행 순서

### 1. application.yml에서 포트 읽기 (하드코딩 금지)
```powershell
$yml = Get-Content "d:\jarvis-agent\src\main\resources\application.yml" -Raw
$port = if ($yml -match 'port:\s*(\d+)') { $Matches[1] } else { "8080" }
```

### 2. 서버 상태 확인
```powershell
try {
    $r = Invoke-WebRequest -Uri "http://localhost:$port/actuator/health" -UseBasicParsing -TimeoutSec 3
    Write-Host "서버 실행 중 (HTTP $($r.StatusCode)) — port $port"
} catch {
    Write-Host "서버 미실행 — port $port"
}
```

### 3. 미실행 시 → 포트 충돌 정리 후 기동
```powershell
# 포트 점유 프로세스 종료
Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }

# gradlew.bat 자동 탐색 (Gradle 버전 무관)
$gradleWrapperPath = "d:\jarvis-agent\gradlew.bat"

# 서버 기동 (local 프로파일로 application-local.yml 적용)
Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/c set SPRING_PROFILES_ACTIVE=local && d:\jarvis-agent\gradlew.bat -p d:\jarvis-agent bootRun > d:\jarvis-agent\server.log 2> d:\jarvis-agent\server.log.err" `
    -WindowStyle Hidden

# 기동 대기
$i = 0
while ($i -lt 20) {
    Start-Sleep -Seconds 4; $i++
    $tail = Get-Content "d:\jarvis-agent\server.log" -Tail 2 -ErrorAction SilentlyContinue
    Write-Host "${i}x4s: $($tail -join ' | ')"
    if ($tail -match "Started \w+Application") { Write-Host "=== 서버 기동 완료 ==="; break }
}
```

## 참고
- 포트 변경은 `src/main/resources/application.yml`의 `server.port`에서
- Gradle은 프로젝트 루트의 `gradlew.bat` wrapper 사용 (버전 자동 대응)
- 로그: `d:\jarvis-agent\server.log`
