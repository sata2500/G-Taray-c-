"use strict";
/*
 * Resim içinde resim yardımcısı.
 * Uygulama küçük pencereye geçerken, o an oynayan en büyük video CSS ile tüm sayfayı kaplayacak
 * şekilde büyütülür ve sayfanın geri kalanı gizlenir. Tam ekran API'sine (kullanıcı dokunuşu ister)
 * ihtiyaç duymaz. Küçük pencereden çıkılınca sayfa eski hâline döner.
 */
(function () {
  const STYLE_ID = "gt-pip-style";
  const CSS = `
    html.gt-pip-mode, html.gt-pip-mode body {
      overflow: hidden !important; background: #000 !important;
    }
    html.gt-pip-mode body * { visibility: hidden !important; }
    html.gt-pip-mode .gt-pip-ancestor {
      transform: none !important; filter: none !important; contain: none !important;
      clip-path: none !important; will-change: auto !important; perspective: none !important;
      overflow: visible !important; visibility: hidden !important;
    }
    html.gt-pip-mode video.gt-pip-video {
      visibility: visible !important; position: fixed !important; inset: 0 !important;
      left: 0 !important; top: 0 !important; width: 100vw !important; height: 100vh !important;
      max-width: none !important; max-height: none !important; margin: 0 !important;
      transform: none !important; object-fit: contain !important; background: #000 !important;
      z-index: 2147483647 !important; opacity: 1 !important;
    }`;

  let active = null;
  let port = null;

  function playingVideo() {
    let best = null;
    let bestArea = 0;
    for (const video of document.querySelectorAll("video")) {
      if (video.paused || video.ended || video.readyState < 2) continue;
      const rect = video.getBoundingClientRect();
      const area = Math.max(rect.width * rect.height, 1);
      if (area >= bestArea) {
        best = video;
        bestArea = area;
      }
    }
    return best;
  }

  function enter() {
    const video = playingVideo();
    if (!video) return false;
    exit();
    if (!document.getElementById(STYLE_ID)) {
      const style = document.createElement("style");
      style.id = STYLE_ID;
      style.textContent = CSS;
      (document.head || document.documentElement).appendChild(style);
    }
    video.classList.add("gt-pip-video");
    for (let node = video.parentElement; node && node !== document.documentElement; node = node.parentElement) {
      node.classList.add("gt-pip-ancestor");
    }
    document.documentElement.classList.add("gt-pip-mode");
    active = video;
    return true;
  }

  function exit() {
    if (!active) return;
    document.documentElement.classList.remove("gt-pip-mode");
    document.querySelectorAll(".gt-pip-ancestor").forEach((n) => n.classList.remove("gt-pip-ancestor"));
    active.classList.remove("gt-pip-video");
    active = null;
  }

  function handle(message) {
    if (!message) return;
    if (message.type === "pip-enter") enter();
    if (message.type === "pip-exit") exit();
  }

  // Video oynamaya başlayınca arka plan betiğine bağlan; komutlar bu bağlantıdan gelir.
  function connect() {
    if (port) return;
    try {
      port = browser.runtime.connect({ name: "gt-media" });
      port.onMessage.addListener(handle);
      port.onDisconnect.addListener(() => (port = null));
    } catch (e) {
      port = null;
    }
  }
  document.addEventListener("play", (e) => { if (e.target instanceof HTMLVideoElement) connect(); }, true);
  if (playingVideo()) connect();

  browser.runtime.onMessage.addListener((message) => handle(message));
})();
