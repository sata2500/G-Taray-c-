"use strict";
/*
 * Video konumu bildirimi (tüm siteler, yalnızca ana çerçeve).
 *
 * Oynayan videonun ekrandaki konumu uygulamaya bildirilir. Uygulama bunu resim içinde resim için kullanır:
 * - geçiş animasyonu tüm sayfadan değil videonun yerinden başlar (sourceRectHint),
 * - küçük pencere açılırken, kopya video ilk karesini çizene kadar videonun son karesi gösterilir.
 * Sayfaya veya oynatıcıya hiçbir müdahalede bulunulmaz.
 */
(function () {
  if (window.top !== window) return;

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

  function report() {
    const video = playingVideo();
    // Bu çerçevede oynayan video yoksa bir şey söylenmez (video bir iç çerçevede olabilir).
    if (!video) return;
    // Oynayan video var ama görünmüyor: yalnızca ses; uygulama küçük pencereye (PiP) geçmez.
    if (youtubeMusicAudioOnly() || !visible(video)) {
      send({ type: "video-rect", hidden: true }, "hidden");
      return;
    }
    const r = video.getBoundingClientRect();
    // Videonun gerçek piksel boyutu (Shorts gibi dikey videolarda küçük pencere dikey açılsın diye).
    const nw = video.videoWidth || 0;
    const nh = video.videoHeight || 0;
    const key = [r.left, r.top, r.width, r.height, window.innerWidth, nw, nh].map(Math.round).join(",");
    send({
      type: "video-rect",
      x: r.left, y: r.top, w: r.width, h: r.height,
      vw: window.innerWidth, vh: window.innerHeight,
      nw: nw, nh: nh,
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
