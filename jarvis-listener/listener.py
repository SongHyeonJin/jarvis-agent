#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Jarvis Background Listener v2.0
- 2 claps (peak detection) -> open Chrome maximized
- "jarvis"/"자비스" voice -> open Chrome (if speech_recognition installed)
- System tray icon, auto-start on Windows login
"""

import sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

import threading
import time
import subprocess
import os
import urllib.request
import json

try:
    import sounddevice as sd
    import numpy as np
    AUDIO_OK = True
except ImportError:
    print("[ERROR] sounddevice/numpy not found. Run install.bat first.", flush=True)
    AUDIO_OK = False

try:
    import pystray
    from PIL import Image, ImageDraw
    TRAY_OK = True
except ImportError:
    TRAY_OK = False

try:
    import speech_recognition as sr
    SR_OK = True
except ImportError:
    SR_OK = False

# ── Config ──────────────────────────────────────────────────
JARVIS_URL     = "http://localhost:8081?activate=1"
JARVIS_WAKE    = "http://localhost:8081/api/card-events/wake"
CLAP_THRESHOLD = 0.40    # peak amplitude — filters out reverb/echo (user-calibrated)
CLAP_COOLDOWN  = 0.5     # min seconds between clap events
CLAP_WINDOW    = 4.0     # time window for 2-clap sequence
SAMPLE_RATE    = 44100
BLOCK_SIZE     = 512     # smaller block = faster response
# ────────────────────────────────────────────────────────────

clap_times = []
clap_lock  = threading.Lock()
last_clap  = 0.0
activating = False
act_lock   = threading.Lock()


def audio_callback(indata, frames, time_info, status):
    """Peak-based clap detection — fires on any buffer with high peak."""
    global last_clap
    peak = float(np.max(np.abs(indata)))
    now  = time.time()

    if peak > CLAP_THRESHOLD and (now - last_clap) > CLAP_COOLDOWN:
        last_clap = now
        with clap_lock:
            clap_times[:] = [t for t in clap_times if now - t < CLAP_WINDOW]
            clap_times.append(now)
            cnt = len(clap_times)
        print(f"[Clap] {cnt}x  peak={peak:.3f}", flush=True)
        if cnt >= 2:
            with clap_lock:
                clap_times.clear()
            threading.Thread(target=activate, args=("clap",), daemon=True).start()


def activate(source="unknown"):
    global activating
    with act_lock:
        if activating:
            return
        activating = True
    print(f"[Jarvis] Activating! source={source}", flush=True)
    try:
        open_jarvis()
    finally:
        time.sleep(4)
        with act_lock:
            activating = False


def open_jarvis():
    # 1) SSE wake 신호 전송 → 이미 열린 탭은 즉시 activate()
    clients = 0
    try:
        req = urllib.request.Request(
            JARVIS_WAKE, method="POST",
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=1) as r:
            data = json.loads(r.read().decode())
            clients = data.get("clients", 0)
            print(f"[Wake] SSE wake sent → clients={clients}", flush=True)
    except Exception as e:
        print(f"[Wake] server not reachable: {e}", flush=True)

    # 2) 열린 탭이 없거나 서버 미응답이면 Chrome 새 창으로 열기
    if clients == 0:
        chrome_paths = [
            r"C:\Program Files\Google\Chrome\Application\chrome.exe",
            r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
            os.path.expandvars(r"%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"),
        ]
        chrome_exe = next((p for p in chrome_paths if os.path.exists(p)), None)
        if chrome_exe:
            subprocess.Popen([chrome_exe, "--new-window", "--start-maximized", JARVIS_URL])
            print(f"[Chrome] No open tab — opened new window: {JARVIS_URL}", flush=True)
        else:
            subprocess.Popen(f'start "" "{JARVIS_URL}"', shell=True)
    else:
        # 이미 열린 탭에 wake 전송됨 — Chrome 창을 앞으로 가져오기
        _focus_chrome_window()


def _focus_chrome_window():
    """PowerShell로 Chrome 창을 전경으로 가져오기."""
    try:
        ps = (
            "$w=(Get-Process chrome -ErrorAction SilentlyContinue|"
            "Where-Object{$_.MainWindowTitle -match 'Jarvis|localhost:8081'}|"
            "Select-Object -First 1);"
            "if($w){"
            "[void][System.Reflection.Assembly]::LoadWithPartialName('Microsoft.VisualBasic');"
            "[Microsoft.VisualBasic.Interaction]::AppActivate($w.Id)"
            "}"
        )
        subprocess.Popen(["powershell", "-NonInteractive", "-Command", ps],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        print("[Focus] Chrome focus requested", flush=True)
    except Exception as e:
        print(f"[Focus] failed: {e}", flush=True)


def speech_loop():
    """Google STT wake word detection loop."""
    recognizer = sr.Recognizer()
    recognizer.energy_threshold = 200
    recognizer.dynamic_energy_threshold = True
    WAKE_WORDS = ["자비스", "jarvis", "재비스", "자비", "저비스"]

    while True:
        try:
            with sr.Microphone() as src:
                recognizer.adjust_for_ambient_noise(src, duration=0.3)
                audio = recognizer.listen(src, timeout=10, phrase_time_limit=3)
            try:
                text = recognizer.recognize_google(audio, language="ko-KR").lower()
                print(f"[STT] '{text}'", flush=True)
                if any(w in text for w in WAKE_WORDS):
                    threading.Thread(target=activate, args=("voice",), daemon=True).start()
            except sr.UnknownValueError:
                pass
            except sr.RequestError as e:
                print(f"[STT] API error: {e}", flush=True)
                time.sleep(5)
        except Exception as e:
            time.sleep(2)


def make_tray_icon():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([2, 2, 62, 62],   fill=(0, 212, 255, 210))
    d.ellipse([12, 12, 52, 52], fill=(0, 8, 24, 230))
    d.ellipse([22, 22, 42, 42], fill=(0, 180, 220, 180))
    d.ellipse([28, 28, 36, 36], fill=(0, 212, 255, 255))
    return img


def main():
    print("=== Jarvis Listener v2.0 ===", flush=True)

    if not AUDIO_OK:
        print("[ERROR] Audio library not available.", flush=True)
        input("Press Enter to exit...")
        return

    # Speech recognition thread
    if SR_OK:
        threading.Thread(target=speech_loop, daemon=True).start()
        print("[STT] Google STT active", flush=True)
    else:
        print("[STT] speech_recognition not installed - clap-only mode", flush=True)

    # Audio stream for clap detection
    try:
        stream = sd.InputStream(
            samplerate=SAMPLE_RATE,
            blocksize=BLOCK_SIZE,
            channels=1,
            dtype="float32",
            callback=audio_callback,
        )
        stream.start()
        print(f"[Audio] Stream started (threshold={CLAP_THRESHOLD}, block={BLOCK_SIZE})", flush=True)
    except Exception as e:
        print(f"[ERROR] Microphone init failed: {e}", flush=True)
        stream = None

    # System tray
    if TRAY_OK:
        icon_img = make_tray_icon()

        def on_open(_icon, _item):
            open_jarvis()

        def on_exit(_icon, _item):
            _icon_ref[0].stop()
            os._exit(0)

        _icon_ref = [None]
        menu = pystray.Menu(
            pystray.MenuItem("Open Jarvis", on_open),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem("Exit", on_exit),
        )
        icon = pystray.Icon("Jarvis", icon_img, "Jarvis Listener", menu)
        _icon_ref[0] = icon
        print("[Tray] System tray icon shown", flush=True)
        icon.run()  # blocking
    else:
        print("[Tray] No tray support - Ctrl+C to exit", flush=True)
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            pass

    if stream:
        stream.stop()


if __name__ == "__main__":
    main()
