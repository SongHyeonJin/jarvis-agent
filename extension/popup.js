document.getElementById('openBtn').addEventListener('click', () => {
  chrome.tabs.create({ url: 'http://localhost:8081' });
  window.close();
});
document.getElementById('activateBtn').addEventListener('click', () => {
  chrome.windows.create({
    url: 'http://localhost:8081?activate=1',
    focused: true,
    state: 'maximized'
  });
  window.close();
});
