const JARVIS_URL = 'http://localhost:8081';
let activating = false;


chrome.runtime.onMessage.addListener((msg) => {
  if (msg.type === 'JARVIS_ACTIVATE') openJarvis(msg.source || 'unknown');
});

async function openJarvis(source) {
  if (activating) return;
  activating = true;

  console.log('[Jarvis BG] 활성화:', source);
  try {
    const allTabs = await chrome.tabs.query({});
    const jarvisTab = allTabs.find(t =>
      t.url && t.url.startsWith(JARVIS_URL) && !t.url.includes('listener.html')
    );

    if (jarvisTab) {
      await chrome.windows.update(jarvisTab.windowId, { focused: true, state: 'maximized' });
      await chrome.tabs.update(jarvisTab.id, { active: true });
      try {
        await chrome.scripting.executeScript({
          target: { tabId: jarvisTab.id },
          func: () => { if (typeof activate === 'function') activate(); }
        });
      } catch {}
    } else {
      await chrome.windows.create({
        url: JARVIS_URL + '?activate=1',
        focused: true,
        state: 'maximized'
      });
    }
  } finally {
    setTimeout(() => { activating = false; }, 3000);
  }
}
