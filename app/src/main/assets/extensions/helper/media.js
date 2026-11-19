"use strict";
/*
 * Video yardımcısı (tüm siteler):
 * - Resim içinde resim: yedek yöntem (CSS ile yalnızca videoyu göster) ve PiP durumu.
 * - Tanılama kaydı: video olayları ve pause() çağrılarının kaynağı (çağrı yığını) zaman çizelgesiyle
 *   tutulur; uygulama "Ayarlar > PiP tanılama kaydı" ile ister. Yalnızca bellekte, en fazla 300 satır.
 */
(function () {
  // Aynı çerçevedeki diğer yardımcı betiklerle (youtube.js) paylaşılan PiP durumu.
  const shared = (globalThis.gtPip = globalThis.gtPip || {
    active: false,
    guardUntil: 0,
    userPaused: false,
    log: () => {},
  });

  // --- Tanılama kaydı -----------------------------------------------------------------------
  const diag = [];
  function log(message) {
    const time = new Date().toISOString().slice(11, 23);
    diag.push(`${time} ${message}`);
    if (diag.length > 300) diag.shift();
  }
  shared.log = log;

  function describe(video) {
    if (!video) return "video yok";
    let buffered = "-";
    try {
      const b = video.buffered;
      if (b.length) buffered = `${b.end(b.length - 1).toFixed(1)}`;
    } catch (e) {}
    return (
      `paused=${video.paused} t=${video.currentTime.toFixed(1)} buf=${buffered} ` +
      `ready=${video.readyState} net=${video.networkState} ` +
      `${video.videoWidth}x${video.videoHeight}`
    );
  }

  function mainVideo() {
    let best = null;
    let bestArea = -1;
    for (const video of document.querySelectorAll("video")) {
      const r = video.getBoundingClientRect();
      const area = r.width * r.height + (video.paused ? 0 : 1e9);
      if (area > bestArea) {
        best = video;
        bestArea = area;
      }
    }
    return best;
  }

  // pause()/play() çağrılarını kimin yaptığını kaydet (sayfa bağlamında, şeffaf sarmalayıcı).
  // Yalnızca YouTube'da: diğer sitelerin oynatıcılarına dokunulmaz.
  if (/(^|\.)youtube(-nocookie)?\.com$/.test(location.hostname)) try {
    const proto = window.wrappedJSObject.HTMLMediaElement.prototype;
    const originalPause = proto.pause;
    const originalPlay = proto.play;
    const stack = () => {
      try {
        return String(new window.wrappedJSObject.Error().stack || "")
          .split("\n")
          .slice(1, 6)
          .map((l) => l.replace(/https?:\/\/[^/]+/, "").slice(0, 90))
          .join(" < ");
      } catch (e) {
        return "?";
      }
    };
    proto.pause = exportFunction(function () {
      log(`pause() çağrıldı ← ${stack()}`);
      return originalPause.call(this);
    }, window);
    proto.play = exportFunction(function () {
      log(`play() çağrıldı ← ${stack()}`);
      return originalPlay.call(this);
    }, window);
    // Kendi kendine test: sarmalayıcı sayfanın pause() çağrısını bozuyorsa hemen geri alınır.
    try {
      window.wrappedJSObject.document.createElement("video").pause();
    } catch (e) {
      proto.pause = originalPause;
      proto.play = originalPlay;
      log(`sarmalayıcı geri alındı: ${e}`);
    }
  } catch (e) {
    log(`sarmalayıcı kurulamadı: ${e}`);
  }

  const EVENTS = ["play", "playing", "pause", "waiting", "stalled", "suspend", "ended", "emptied", "error", "seeking", "seeked", "ratechange"];
  for (const type of EVENTS) {
    document.addEventListener(
      type,
      (e) => {
        if (e.target instanceof HTMLVideoElement && (type !== "suspend" || shared.active)) {
          log(`olay ${type}: ${describe(e.target)}`);
        }
      },
      true
    );
  }
  window.addEventListener("resize", () => log(`pencere ${window.innerWidth}x${window.innerHeight}`), true);
  window.addEventListener("visibilitychange", () => log(`gerçek görünürlük: ${document.visibilityState}`), true);
  window.addEventListener("blur", (e) => { if (e.target === window) log("pencere odağı kayboldu"); }, true);
  window.addEventListener("focus", (e) => { if (e.target === window) log("pencere odaklandı"); }, true);
  setInterval(() => {
    if (shared.active) log(`örnek: ${describe(mainVideo())} odak=${document.hasFocus()}`);
  }, 2000);

  // --- Resim içinde resim: yedek CSS yöntemi -------------------------------------------------
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

  let cssVideo = null;
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

  function enterCss() {
    const video = playingVideo();
    if (!video) return;
    exitCss();
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
    cssVideo = video;
  }

  function exitCss() {
    if (!cssVideo) return;
    document.documentElement.classList.remove("gt-pip-mode");
    document.querySelectorAll(".gt-pip-ancestor").forEach((n) => n.classList.remove("gt-pip-ancestor"));
    cssVideo.classList.remove("gt-pip-video");
    cssVideo = null;
  }

  // --- Uygulamadan gelen komutlar -------------------------------------------------------------
  const GUARD_MS = 10000;
  function handle(message) {
    if (!message) return;
    switch (message.type) {
      case "pip-enter": // yedek yöntem
        enterCss();
        break;
      case "pip-exit":
        exitCss();
        break;
      case "pip-state":
        shared.active = !!message.active;
        shared.userPaused = false;
        shared.guardUntil = message.active ? Date.now() + GUARD_MS : 0;
        log(`PiP ${message.active ? "başladı" : "bitti"} (${message.mode || "-"}): ${describe(mainVideo())}`);
        break;
      case "pip-user":
        // PiP penceresindeki düğmeler: kullanıcının isteği korunur.
        shared.userPaused = message.action === "pause";
        shared.guardUntil = message.action === "play" ? Date.now() + GUARD_MS : 0;
        log(`PiP düğmesi: ${message.action}`);
        break;
      case "diag-dump":
        if (diag.length && port) {
          port.postMessage({ type: "diag", url: location.href.slice(0, 120), top: window.top === window, lines: diag.slice() });
        }
        break;
    }
  }

  // Video oynayan sayfalar arka plan betiğine bağlanır; komutlar bu bağlantıdan gelir.
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
