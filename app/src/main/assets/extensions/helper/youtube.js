"use strict";
/*
 * G-Tarayıcı YouTube yardımcısı
 * 1) Arka planda oynatma: sayfaya her zaman "görünür" olduğunu söyler; böylece uygulama arka plana
 *    alındığında veya ekran kapandığında YouTube videoyu durdurmaz.
 * 2) SponsorBlock: topluluk tarafından işaretlenmiş sponsor bölümlerini otomatik atlar.
 *    Gizlilik için video kimliğinin yalnızca SHA-256 önekinin ilk 4 karakteri sunucuya gönderilir.
 */
(function () {
  let backgroundPlay = true;
  let sponsorBlock = true;

  // --- 1) Görünürlük API'sini sayfa bağlamında geçersiz kıl ---------------------------------
  try {
    const pageDocument = document.wrappedJSObject;
    const define = (name, value) => {
      const realGetter = () => (name === "hidden" || name === "webkitHidden" ? document.hidden : document.visibilityState);
      Object.defineProperty(pageDocument, name, {
        configurable: true,
        get: exportFunction(function () {
          return backgroundPlay ? value : realGetter();
        }, window),
      });
    };
    define("hidden", false);
    define("webkitHidden", false);
    define("visibilityState", "visible");
    define("webkitVisibilityState", "visible");

    const swallow = (event) => {
      if (backgroundPlay) event.stopImmediatePropagation();
    };
    document.addEventListener("visibilitychange", swallow, true);
    document.addEventListener("webkitvisibilitychange", swallow, true);
    // Yalnızca pencerenin kendi odak kaybı yutulur; form alanlarının olayları etkilenmez.
    window.addEventListener("blur", (event) => { if (event.target === window) swallow(event); }, true);
  } catch (e) {
    // Sayfa erişilemiyorsa sessizce devam et.
  }

  browser.runtime
    .sendMessage({ type: "settings" })
    .then((s) => {
      if (!s) return;
      backgroundPlay = s.backgroundPlay !== false;
      sponsorBlock = s.sponsorBlock !== false;
    })
    .catch(() => {});

  // "Video duraklatıldı. İzlemeye devam edilsin mi?" penceresini otomatik geçer.
  setInterval(() => {
    if (!backgroundPlay) return;
    const button = document.querySelector(
      "yt-confirm-dialog-renderer #confirm-button, ytm-confirm-dialog-renderer button, .ytp-pause-overlay-container button"
    );
    if (button && /devam|continue|yes|evet/i.test(button.textContent || "")) button.click();
  }, 3000);

  // --- 2) SponsorBlock ---------------------------------------------------------------------
  if (window.top !== window) return; // Yalnızca ana çerçevede.

  // --- 3) Medya kontrolleri ve oynatma listesinde otomatik geçiş --------------------------
  // YouTube mobil sitesi "önceki/sonraki" işleyicilerini kaydetmez; oynatıcının kendi
  // işlevlerine bağlarız. Böylece bildirimde, kilit ekranında ve resim içinde resimde görünürler.
  function player() {
    const node = document.getElementById("movie_player");
    return node ? node.wrappedJSObject : null;
  }
  function inPlaylist() {
    try {
      return new URL(location.href).searchParams.has("list");
    } catch (e) {
      return false;
    }
  }
  function next() {
    const p = player();
    if (p && typeof p.nextVideo === "function") p.nextVideo();
  }
  function previous() {
    const p = player();
    const video = document.querySelector("video");
    if (video && video.currentTime > 5) {
      video.currentTime = 0;
    } else if (p && typeof p.previousVideo === "function") {
      p.previousVideo();
    }
  }
  function registerHandlers() {
    try {
      const session = navigator.wrappedJSObject.mediaSession;
      if (!session) return;
      session.setActionHandler("nexttrack", exportFunction(next, window));
      session.setActionHandler("previoustrack", exportFunction(previous, window));
    } catch (e) {}
  }
  // YouTube işleyicileri sıfırlayabildiği için düzenli olarak yeniden kaydedilir.
  setInterval(() => {
    const video = document.querySelector("video");
    if (video && !video.paused) registerHandlers();
  }, 2000);

  // Resim içinde resim koruması: PiP'e geçişte (veya PiP'teki "Oynat"tan hemen sonra) YouTube videoyu
  // kendiliğinden durdurursa oynatıcının kendi işleviyle devam ettirilir. Sınırlı deneme yapılır;
  // kullanıcının PiP/bildirim düğmesiyle verdiği duraklatma komutuna karışılmaz.
  let guardRetries = 0;
  document.addEventListener(
    "pause",
    (event) => {
      const pip = globalThis.gtPip;
      const video = event.target;
      if (!pip || !(video instanceof HTMLVideoElement) || video.ended) return;
      if (!pip.active || pip.userPaused || Date.now() > pip.guardUntil) {
        guardRetries = 0;
        return;
      }
      if (guardRetries >= 4) return;
      guardRetries++;
      pip.log(`koruma: PiP sırasında beklenmeyen duraklama, devam ettiriliyor (${guardRetries}/4)`);
      setTimeout(() => {
        const p = player();
        if (p && typeof p.playVideo === "function") p.playVideo();
        else video.play().catch((e) => pip.log(`koruma: play() reddedildi: ${e}`));
      }, 400);
    },
    true
  );

  // Ekran kapalıyken YouTube'un kendi geçişi gecikebilir; video bitince sıradakine geçilir.
  document.addEventListener(
    "ended",
    (event) => {
      if (!(event.target instanceof HTMLVideoElement) || !backgroundPlay) return;
      if (!inPlaylist()) return;
      const endedUrl = location.href;
      setTimeout(() => {
        if (location.href === endedUrl) next();
      }, 1500);
    },
    true
  );

  const CATEGORIES = ["sponsor", "selfpromo", "interaction"];
  let currentVideoId = null;
  let segments = [];
  let attachedVideo = null;

  function videoIdFrom(href) {
    try {
      const url = new URL(href);
      if (url.pathname.startsWith("/shorts/")) return url.pathname.split("/")[2] || null;
      if (url.pathname.startsWith("/embed/")) return url.pathname.split("/")[2] || null;
      return url.searchParams.get("v");
    } catch (e) {
      return null;
    }
  }

  async function sha256Hex(text) {
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
    return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
  }

  async function loadSegments(videoId) {
    try {
      const prefix = (await sha256Hex(videoId)).slice(0, 4);
      const query = encodeURIComponent(JSON.stringify(CATEGORIES));
      const response = await fetch(`https://sponsor.ajay.app/api/skipSegments/${prefix}?categories=${query}`);
      if (!response.ok) return [];
      const list = await response.json();
      const entry = list.find((item) => item.videoID === videoId);
      if (!entry) return [];
      return entry.segments
        .filter((s) => !s.actionType || s.actionType === "skip")
        .map((s) => s.segment)
        .filter((seg) => seg[1] - seg[0] > 1);
    } catch (e) {
      return [];
    }
  }

  function toast(text) {
    const node = document.createElement("div");
    node.textContent = text;
    node.style.cssText =
      "position:fixed;left:50%;bottom:96px;transform:translateX(-50%);z-index:2147483647;" +
      "background:rgba(32,33,36,.92);color:#fff;padding:10px 16px;border-radius:18px;" +
      "font:500 14px system-ui,sans-serif;pointer-events:none;transition:opacity .4s";
    (document.body || document.documentElement).appendChild(node);
    setTimeout(() => (node.style.opacity = "0"), 1800);
    setTimeout(() => node.remove(), 2400);
  }

  function onTimeUpdate(event) {
    if (!sponsorBlock || segments.length === 0) return;
    const video = event.target;
    const t = video.currentTime;
    for (const [start, end] of segments) {
      if (t >= start && t < end - 0.5) {
        video.currentTime = end;
        toast("Sponsor bölümü atlandı");
        break;
      }
    }
  }

  async function check() {
    const id = videoIdFrom(location.href);
    if (id !== currentVideoId) {
      currentVideoId = id;
      segments = id ? await loadSegments(id) : [];
    }
    const video = document.querySelector("video");
    if (video && video !== attachedVideo) {
      if (attachedVideo) attachedVideo.removeEventListener("timeupdate", onTimeUpdate);
      video.addEventListener("timeupdate", onTimeUpdate);
      attachedVideo = video;
    }
  }

  setInterval(check, 1000);
})();
