"use strict";
/*
 * Resim içinde resim yardımcısı: uygulama arka plana alınırken o an oynayan en büyük videoyu
 * tam ekrana alır, böylece küçük pencerede yalnızca video görünür.
 */
(function () {
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

  browser.runtime.onMessage.addListener((message) => {
    if (!message) return undefined;
    if (message.type === "pip-fullscreen") {
      const video = playingVideo();
      if (video && !document.fullscreenElement) {
        video.requestFullscreen().catch(() => {});
        return Promise.resolve(true);
      }
    }
    if (message.type === "pip-exit-fullscreen" && document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    }
    return undefined;
  });
})();
