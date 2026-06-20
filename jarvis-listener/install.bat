@echo off
chcp 65001 > nul
echo ╔════════════════════════════════════════╗
echo ║     자비스 리스너 설치                     ║
echo ╚════════════════════════════════════════╝
echo.

:: Python 확인
python --version > nul 2>&1
if errorlevel 1 (
    echo [!] Python을 찾을 수 없습니다.
    echo     https://www.python.org 에서 Python 3.11+ 설치 후 재실행하세요.
    echo     설치 시 "Add Python to PATH" 체크 필수!
    pause
    exit /b 1
)

echo [1/3] Python 확인 완료
python --version

echo.
echo [2/3] 패키지 설치 중...
pip install sounddevice numpy pystray Pillow SpeechRecognition

:: pyaudio는 별도 처리 (설치 실패해도 계속)
pip install pyaudio 2>nul || echo [INFO] pyaudio 설치 실패 — 음성인식 없이 박수만 동작합니다.

echo.
echo [3/3] Windows 시작 프로그램 등록 중...
set SCRIPT_DIR=%~dp0
set VBS_PATH=%SCRIPT_DIR%run.vbs
set STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup
set LNK_PATH=%STARTUP%\JarvisListener.lnk

powershell -NoProfile -Command ^
  "$ws = New-Object -COM WScript.Shell; ^
   $s = $ws.CreateShortcut('%LNK_PATH%'); ^
   $s.TargetPath = 'wscript.exe'; ^
   $s.Arguments = '\"%VBS_PATH%\"'; ^
   $s.WorkingDirectory = '%SCRIPT_DIR%'; ^
   $s.Description = '자비스 백그라운드 리스너'; ^
   $s.Save()" > nul 2>&1

echo.
echo ╔════════════════════════════════════════╗
echo ║  설치 완료!                               ║
echo ║                                          ║
echo ║  • run.vbs 를 더블클릭하면 바로 실행         ║
echo ║  • 다음 Windows 시작 시 자동 실행됩니다       ║
echo ║  • 트레이 아이콘(우하단)으로 종료 가능          ║
echo ╚════════════════════════════════════════╝
echo.
echo 지금 바로 시작하시겠습니까? (Y/N)
set /p START_NOW=
if /i "%START_NOW%"=="Y" (
    wscript "%VBS_PATH%"
    echo 자비스 리스너가 백그라운드에서 실행 중입니다.
)
pause
