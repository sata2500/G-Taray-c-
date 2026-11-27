"use strict";
/*
 * Video konumu bildirimi (tüm siteler, tüm çerçeveler).
 *
 * Oynayan videonun ekrandaki konumu uygulamaya bildirilir. Uygulama bunu resim içinde resim için kullanır:
 * - geçiş animasyonu tüm sayfadan değil videonun yerinden başlar (sourceRectHint),
 * - küçük pencere açılırken, kopya video ilk karesini çizene kadar videonun son karesi gösterilir.
 * Sayfaya veya oynatıcıya hiçbir müdahalede bulunulmaz.
 */
(function () {
  const isTop = window.top === window;
  // Gömülü oynatıcılar (iframe): alt çerçeve bulduğu videoyu üst çerçeveye iletir; her üst çerçeve kendi içindeki
  // iframe'in konumunu ekleyerek bir üste aktarır. Ana çerçeve sonucu uygulamaya bildirir.
  let childReport = null;
  let childReportAt = 0;
  window.addEventListener("message", (event) => {
    const data = event.data && event.data.gtVideoRect;
    if (!data) return;
    let frame = null;
    for (const f of document.querySelectorAll("iframe, frame")) {
      try { if (f.contentWindow === event.source) { frame = f; break; } } catch (e) {}
    }
    if (!frame) return;
    const fr = frame.getBoundingClientRect();
    const moved = data.hidden ? data : Object.assign({}, data, {
      x: data.x + fr.left + frame.clientLeft,
      y: data.y + fr.top + frame.clientTop,
    });
    childReport = moved;
    childReportAt = Date.now();
    if (isTop) schedule(50);
    else forward(moved);
  });

  function forward(rect) {
    try { window.parent.postMessage({ gtVideoRect: rect }, "*"); } catch (e) {}
  }

  let port = null;
  let lastReport = "";

  function playingVideo() {
    let best = null;
    let bestArea = 0;
    for (const video of document.querySelectorAll("video")) {
      if (video.paused || video.ended || video.readyState < 2) continue;
      const rect = video.getBoundingClientRect();
      const area = rect.width * rect.height;
      if (area > bestArea) {
        best = video;
        bestArea = area;
      }
    }
    return best;
  }

  // YouTube Music'te şarkı modunda video öğesi oynar ama yerine kapak resmi gösterilir; yalnızca ses vardır.
  // İki işaret: oynatıcının çalma kipi (şarkı) ya da kapak resminin görünür olması (video kipinde gizlenir).
  function youtubeMusicAudioOnly() {
    if (location.hostname !== "music.youtube.com") return false;
    const player = document.querySelector("ytmusic-player");
    if (!player) return false;
    if (player.getAttribute("playback-mode") === "ATV_PREFERRED") return true;
    const image = player.querySelector("#song-image");
    if (!image) return false;
    try {
      if (typeof image.checkVisibility === "function" &&
          !image.checkVisibility({ opacityProperty: true, visibilityProperty: true })) return false;
    } catch (e) {}
    const r = image.getBoundingClientRect();
    return r.width > 40 && r.height > 40;
  }

  // Video gerçekten görünüyor mu: gizli, saydam, çok küçük veya ekran dışında değil.
  function visible(video) {
    try {
      if (typeof video.checkVisibility === "function" &&
          !video.checkVisibility({ opacityProperty: true, visibilityProperty: true })) return false;
    } catch (e) {}
    const r = video.getBoundingClientRect();
    if (r.width < 40 || r.height < 40) return false;
    return r.bottom > 0 && r.right > 0 && r.top < window.innerHeight && r.left < window.innerWidth;
  }

  function send(message, key) {
    if (key === lastReport) return;
    try {
      if (!isTop) return;
      if (!port) {
        port = browser.runtime.connect({ name: "gt-media" });
        port.onDisconnect.addListener(() => (port = null));
      }
      port.postMessage(message);
      lastReport = key;
    } catch (e) {
      port = null;
    }
  }

  /** Bu çerçevedeki oynayan videonun durumu (yoksa null). */
  function measure() {
    const video = playingVideo();
    if (!video) return null;
    // Oynayan video var ama görünmüyor: yalnızca ses; uygulama küçük pencereye (PiP) geçmez.
    if (youtubeMusicAudioOnly() || !visible(video)) return { hidden: true };
    const r = video.getBoundingClientRect();
    // Videonun gerçek piksel boyutu (Shorts gibi dikey videolarda küçük pencere dikey açılsın diye).
    const nw = video.videoWidth || 0;
    const nh = video.videoHeight || 0;
    // Görüntünün öğe içinde gerçekten çizildiği alan (oran korunur; üst/alt ya da yan siyah boşluklar dışarıda).
    let x = r.left, y = r.top, w = r.width, h = r.height;
    const fit = (getComputedStyle(video).objectFit || "contain");
    if (nw > 0 && nh > 0 && (fit === "contain" || fit === "scale-down")) {
      const scale = Math.min(w / nw, h / nh);
      const cw = nw * scale, ch = nh * scale;
      x += (w - cw) / 2; y += (h - ch) / 2; w = cw; h = ch;
    }
    return { x: x, y: y, w: w, h: h, nw: nw, nh: nh };
  }

  function report() {
    const own = measure();
    if (!isTop) {
      if (own) forward(own);
      return;
    }
    // Ana çerçevede video yoksa iç çerçeveden gelen (son birkaç saniyedeki) bilgi kullanılır.
    const rect = own || (Date.now() - childReportAt < 5000 ? childReport : null);
    if (!rect) return;
    if (rect.hidden) {
      send({ type: "video-rect", hidden: true }, "hidden");
      return;
    }
    const key = [rect.x, rect.y, rect.w, rect.h, window.innerWidth, rect.nw, rect.nh].map(Math.round).join(",");
    send({
      type: "video-rect",
      x: rect.x, y: rect.y, w: rect.w, h: rect.h,
      vw: window.innerWidth, vh: window.innerHeight,
      nw: rect.nw, nh: rect.nh,
    }, key);
  }

  let pending = 0;
  function schedule(delay) {
    clearTimeout(pending);
    pending = setTimeout(report, delay);
  }
  document.addEventListener("playing", (e) => { if (e.target instanceof HTMLVideoElement) schedule(300); }, true);
  // Aynı öğede yeni video (ör. sıradaki Shorts) başlayınca boyutu değişir.
  document.addEventListener("resize", (e) => { if (e.target instanceof HTMLVideoElement) schedule(100); }, true);
  document.addEventListener("loadedmetadata", (e) => { if (e.target instanceof HTMLVideoElement) schedule(200); }, true);
  window.addEventListener("resize", () => schedule(300), true);
  window.addEventListener("scroll", () => schedule(250), true);
  setInterval(report, 2000);
})();
