#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Jarvis Background Listener v2.1
- "jarvis"/"자비스" voice -> open Chrome
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
JARVIS_URL  = "http://localhost:8081?activate=1"
JARVIS_WAKE = "http://localhost:8081/api/card-events/wake"
# ────────────────────────────────────────────────────────────

activating = False
act_lock   = threading.Lock()


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
        _focus_chrome_window()


def _focus_chrome_window():
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
        except Exception:
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
    print("=== Jarvis Listener v2.1 (voice only) ===", flush=True)

    if SR_OK:
        threading.Thread(target=speech_loop, daemon=True).start()
        print("[STT] Google STT active", flush=True)
    else:
        print("[ERROR] speech_recognition not installed. Run install.bat first.", flush=True)
        input("Press Enter to exit...")
        return

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
        icon.run()
    else:
        print("[Tray] No tray support - Ctrl+C to exit", flush=True)
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
