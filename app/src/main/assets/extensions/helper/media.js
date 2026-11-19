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

  function report() {
    const video = playingVideo();
    if (!video) return;
    const r = video.getBoundingClientRect();
    if (r.width < 40 || r.height < 40) return;
    const key = [r.left, r.top, r.width, r.height, window.innerWidth].map(Math.round).join(",");
    if (key === lastReport) return;
    try {
      if (!port) {
        port = browser.runtime.connect({ name: "gt-media" });
        port.onDisconnect.addListener(() => (port = null));
      }
      port.postMessage({
        type: "video-rect",
        x: r.left, y: r.top, w: r.width, h: r.height,
        vw: window.innerWidth, vh: window.innerHeight,
      });
      lastReport = key;
    } catch (e) {
      port = null;
    }
  }

  let pending = 0;
  function schedule(delay) {
    clearTimeout(pending);
    pending = setTimeout(report, delay);
  }
  document.addEventListener("playing", (e) => { if (e.target instanceof HTMLVideoElement) schedule(300); }, true);
  window.addEventListener("resize", () => schedule(300), true);
  window.addEventListener("scroll", () => schedule(250), true);
  setInterval(report, 2000);
})();
