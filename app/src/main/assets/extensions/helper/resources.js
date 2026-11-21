"use strict";
/*
 * Sayfadaki indirilebilir öğelerin taranması (yalnızca uygulama istediğinde; sayfaya dokunulmaz).
 *
 * Bulunanlar: <video>/<audio> kaynakları, indirme bağlantıları (pdf, zip, apk, mp3… veya "download" işaretli),
 * büyük görseller, sayfanın yüklediği video akışları (.m3u8) ve doğrudan oynatılan medya dosyaları.
 * Gömülü oynatıcılar (iframe) da taranır: her çerçeve bulduklarını ana çerçeveye iletir.
 */
(function () {
  const isTop = window.top === window;
  const FILE_EXT = /\.(mp4|m4v|webm|mkv|mov|avi|3gp|flv|mpg|mpeg|wmv|ogv|mp3|m4a|aac|ogg|oga|opus|wav|flac|wma|weba|m3u8|pdf|docx?|xlsx?|pptx?|odt|ods|odp|rtf|csv|epub|mobi|djvu|zip|rar|7z|tar|gz|tgz|bz2|xz|zst|iso|apk|apks|xapk|exe|msi|dmg|deb|rpm|appimage)(?:$|[?#])/i;
  const MEDIA_EXT = /\.(mp4|m4v|webm|mov|mp3|m4a|aac|ogg|opus|wav|flac)(?:$|[?#])/i;
  const STREAM_EXT = /\.m3u8(?:$|[?#])/i;
  const MAX_ITEMS = 300;

  function abs(url) {
    try { return url ? new URL(url, document.baseURI).href : null; } catch (e) { return null; }
  }
  const usable = (url) => !!url && /^https?:/i.test(url);

  /** srcset içindeki en büyük görsel. */
  function largestFromSrcset(img) {
    const set = img.getAttribute("srcset");
    if (!set) return null;
    let best = null;
    let bestW = 0;
    for (const part of set.split(",")) {
      const [u, d] = part.trim().split(/\s+/);
      const w = d && d.endsWith("w") ? parseInt(d, 10) : d && d.endsWith("x") ? parseFloat(d) * 1000 : 1;
      if (w > bestW) { bestW = w; best = u; }
    }
    return abs(best);
  }

  function collect() {
    const items = new Map();
    let blobVideo = false;
    const add = (url, data) => {
      if (!usable(url) || items.size >= MAX_ITEMS) return;
      const before = items.get(url) || {};
      items.set(url, Object.assign({ url }, before, data));
    };

    for (const v of document.querySelectorAll("video")) {
      const sources = [v.currentSrc, v.getAttribute("src"), ...Array.from(v.querySelectorAll("source"), (s) => s.getAttribute("src"))];
      if ((v.currentSrc || "").startsWith("blob:")) blobVideo = true;
      for (const s of sources) {
        add(abs(s), {
          type: "video",
          w: v.videoWidth || 0,
          h: v.videoHeight || 0,
          duration: isFinite(v.duration) ? Math.round(v.duration) : 0,
          poster: abs(v.getAttribute("poster")),
        });
      }
    }
    for (const a of document.querySelectorAll("audio")) {
      for (const s of [a.currentSrc, a.getAttribute("src"), ...Array.from(a.querySelectorAll("source"), (x) => x.getAttribute("src"))]) {
        add(abs(s), { type: "audio", duration: isFinite(a.duration) ? Math.round(a.duration) : 0 });
      }
    }
    for (const a of document.querySelectorAll("a[href]")) {
      const url = abs(a.getAttribute("href"));
      if (!usable(url)) continue;
      if (a.hasAttribute("download") || FILE_EXT.test(url)) {
        const name = a.getAttribute("download") || (a.textContent || a.title || "").trim();
        add(url, { type: "link", text: name.replace(/\s+/g, " ").slice(0, 120) });
      }
    }
    for (const img of document.querySelectorAll("img")) {
      const w = img.naturalWidth;
      const h = img.naturalHeight;
      if (w < 200 || h < 200) continue;
      add(largestFromSrcset(img) || abs(img.currentSrc || img.getAttribute("src")), { type: "image", w, h, text: (img.alt || "").slice(0, 120) });
    }
    // Sayfanın oynatıcısının yüklediği akışlar ve medya dosyaları (ör. HLS listeleri).
    try {
      for (const entry of performance.getEntriesByType("resource")) {
        const url = entry.name;
        if (!usable(url)) continue;
        if (STREAM_EXT.test(url)) add(url, { type: "stream" });
        else if ((entry.initiatorType === "video" || entry.initiatorType === "audio") && MEDIA_EXT.test(url)) add(url, { type: entry.initiatorType });
      }
    } catch (e) {}
    return { items: Array.from(items.values()), blob: blobVideo };
  }

  /** Bu çerçevenin ve alt çerçevelerinin bulduklarını toplar. */
  function collectWithFrames(id, timeout) {
    return new Promise((resolve) => {
      const merged = collect();
      const pending = new Set();
      const listener = (event) => {
        const data = event.data;
        if (!data || data.gtResourcesReply !== id) return;
        pending.delete(event.source);
        merged.items.push(...(data.items || []));
        merged.blob = merged.blob || !!data.blob;
        if (pending.size === 0) finish();
      };
      let done = false;
      const finish = () => {
        if (done) return;
        done = true;
        window.removeEventListener("message", listener);
        resolve(merged);
      };
      window.addEventListener("message", listener);
      for (let i = 0; i < window.frames.length; i++) {
        try {
          pending.add(window.frames[i]);
          window.frames[i].postMessage({ gtResourcesCollect: id }, "*");
        } catch (e) {}
      }
      if (pending.size === 0) finish();
      setTimeout(finish, timeout);
    });
  }

  // Alt çerçeve: istek gelince bulduklarını (kendi alt çerçeveleriyle birlikte) üst çerçeveye gönderir.
  window.addEventListener("message", (event) => {
    const id = event.data && event.data.gtResourcesCollect;
    if (!id || isTop) return;
    collectWithFrames(id, 500).then((result) => {
      try { event.source.postMessage({ gtResourcesReply: id, items: result.items, blob: result.blob }, "*"); } catch (e) {}
    });
  });

  if (!isTop) return;

  // Ana çerçeve: uygulamayla bağlantı. Uygulama "collect" ister, sonuç "resources" olarak döner.
  let attempts = 0;
  function connect() {
    let port;
    try {
      port = browser.runtime.connectNative("gtResources");
    } catch (e) {
      return;
    }
    port.onMessage.addListener((message) => {
      if (!message || message.type !== "collect") return;
      collectWithFrames(message.id, 800).then((result) => {
        try {
          port.postMessage({ type: "resources", id: message.id, items: result.items.slice(0, MAX_ITEMS), blob: result.blob, title: document.title });
        } catch (e) {}
      });
    });
    port.onDisconnect.addListener(() => {
      // Uygulama dinleyiciyi sekme açıldıktan sonra kaydetmiş olabilir: birkaç kez yeniden dene.
      if (++attempts <= 5) setTimeout(connect, 1000 * attempts);
    });
  }
  connect();
})();
