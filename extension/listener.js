const WAKE_PATS = [/자비스야/, /자비스아/, /자\s*비\s*스/, /jarvis/i];

const dot  = document.getElementById('dot');
const lbl  = document.getElementById('lbl');
const vbar = document.getElementById('vbar');

async function init() {
  lbl.textContent = '마이크 권한 요청 중...';
  try {
    await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
    setupSpeech();
    dot.className = 'ok';
    lbl.textContent = '자비스 리스너 대기 중';
  } catch (e) {
    dot.className = 'err';
    lbl.textContent = '마이크 오류: ' + e.message;
  }
}

function setupSpeech() {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SR) return;
  const recog = new SR();
  recog.lang = 'ko-KR';
  recog.continuous = true;
  recog.interimResults = true;
  recog.onresult = (e) => {
    const text = Array.from(e.results).map(r => r[0].transcript).join('');
    if (WAKE_PATS.some(p => p.test(text))) trigger();
  };
  recog.onend = () => { try { recog.start(); } catch {} };
  try { recog.start(); } catch {}
}

function trigger() {
  lbl.textContent = '음성 감지 → 자비스 활성화!';
  dot.style.background = '#ffd040';
  setTimeout(() => { dot.className = 'ok'; lbl.textContent = '자비스 리스너 대기 중'; }, 2000);
  chrome.runtime.sendMessage({ type: 'JARVIS_ACTIVATE', source: 'voice' });
}

init();
