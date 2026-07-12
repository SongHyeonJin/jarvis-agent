const JARVIS_URL = 'http://localhost:8081';

async function pollAndExecute() {
  try {
    const res = await fetch(`${JARVIS_URL}/api/browser-commands/youtube`);
    if (res.status === 200) {
      const cmd = await res.json();
      if (cmd.action === 'play-nth') {
        const videos = document.querySelectorAll(
          'ytd-video-renderer a#thumbnail, ytd-rich-item-renderer a#thumbnail'
        );
        if (videos[cmd.n - 1]) videos[cmd.n - 1].click();
      }
    }
  } catch {}
  setTimeout(pollAndExecute, 1000);
}

pollAndExecute();
