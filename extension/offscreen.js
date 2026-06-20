// 자비스 wake word + 박수 감지 (offscreen document)

const WAKE_PATS = [/자비스야/, /자비스아/, /자\s*비\s*스/, /jarvis/i];
const CLAP_HI = 0.70;
const CLAP_LO = 0.06;
const CLAP_COOLDOWN_MS = 1800;

let audioCtx, analyser, dataArr;
let wasHigh = false, lastClapTime = 0;
let clapCount = 0, clapTimer = null;
let recog = null;

async function init() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
    setupClap(stream);
    setupSpeech();
    console.log('[Jarvis Offscreen] 마이크 초기화 완료');
  } catch (e) {
    console.error('[Jarvis Offscreen] 마이크 접근 실패:', e.message);
  }
}

function setupClap(stream) {
  audioCtx = new AudioContext();
  analyser = audioCtx.createAnalyser();
  analyser.fftSize = 256;
  const src = audioCtx.createMediaStreamSource(stream);
  src.connect(analyser);
  dataArr = new Uint8Array(analyser.frequencyBinCount);
  detectClap();
}

function detectClap() {
  analyser.getByteFrequencyData(dataArr);
  const avg = dataArr.reduce((s, v) => s + v, 0) / dataArr.length / 255;
  const now = Date.now();
  if (!wasHigh && avg > CLAP_HI) {
    wasHigh = true;
  } else if (wasHigh && avg < CLAP_LO) {
    wasHigh = false;
    if (now - lastClapTime > CLAP_COOLDOWN_MS) {
      lastClapTime = now;
      onClap();
    }
  } else if (avg < CLAP_LO / 2) {
    wasHigh = false;
  }
  requestAnimationFrame(detectClap);
}

function onClap() {
  clapCount++;
  clearTimeout(clapTimer);
  clapTimer = setTimeout(() => { clapCount = 0; }, 3000);
  if (clapCount >= 2) {
    clapCount = 0;
    trigger('clap');
  }
}

function setupSpeech() {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SR) return;
  recog = new SR();
  recog.lang = 'ko-KR';
  recog.continuous = true;
  recog.interimResults = true;
  recog.onresult = (e) => {
    const text = Array.from(e.results).map(r => r[0].transcript).join('');
    if (WAKE_PATS.some(p => p.test(text))) trigger('voice');
  };
  recog.onend = () => {
    try { recog.start(); } catch {}
  };
  try { recog.start(); } catch {}
}

function trigger(source) {
  console.log('[Jarvis Offscreen] 활성화 트리거:', source);
  chrome.runtime.sendMessage({ type: 'JARVIS_ACTIVATE', source });
}

init();
