const WAKE_PATS = [/자비스야/, /자비스아/, /자\s*비\s*스/, /jarvis/i];
const CLAP_HI = 0.62;
const CLAP_LO = 0.06;
const CLAP_COOLDOWN_MS = 1500;

const dot  = document.getElementById('dot');
const lbl  = document.getElementById('lbl');
const vbar = document.getElementById('vbar');

let audioCtx, analyser, dataArr;
let wasHigh = false, lastClapTime = 0;
let clapCount = 0, clapTimer = null;

async function init() {
  lbl.textContent = '마이크 권한 요청 중...';
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
    setupClap(stream);
    setupSpeech();
    dot.className = 'ok';
    lbl.textContent = '자비스 리스너 대기 중';
  } catch (e) {
    dot.className = 'err';
    lbl.textContent = '마이크 오류: ' + e.message;
  }
}

function setupClap(stream) {
  audioCtx = new AudioContext();
  analyser = audioCtx.createAnalyser();
  analyser.fftSize = 256;
  audioCtx.createMediaStreamSource(stream).connect(analyser);
  dataArr = new Uint8Array(analyser.frequencyBinCount);
  rafLoop();
}

function rafLoop() {
  analyser.getByteFrequencyData(dataArr);
  const avg = dataArr.reduce((s, v) => s + v, 0) / dataArr.length / 255;

  // 볼륨 바 업데이트
  vbar.style.width = Math.min(avg * 300, 100) + '%';

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
  requestAnimationFrame(rafLoop);
}

function onClap() {
  clapCount++;
  lbl.textContent = `박수 ${clapCount}번 감지`;
  clearTimeout(clapTimer);
  clapTimer = setTimeout(() => {
    clapCount = 0;
    lbl.textContent = '자비스 리스너 대기 중';
  }, 3000);
  if (clapCount >= 2) {
    clapCount = 0;
    trigger('clap');
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
    if (WAKE_PATS.some(p => p.test(text))) trigger('voice');
  };
  recog.onend = () => { try { recog.start(); } catch {} };
  try { recog.start(); } catch {}
}

function trigger(source) {
  lbl.textContent = source === 'clap' ? '박수 감지 → 자비스 활성화!' : '음성 감지 → 자비스 활성화!';
  dot.style.background = '#ffd040';
  setTimeout(() => { dot.className = 'ok'; lbl.textContent = '자비스 리스너 대기 중'; }, 2000);
  chrome.runtime.sendMessage({ type: 'JARVIS_ACTIVATE', source });
}

init();
