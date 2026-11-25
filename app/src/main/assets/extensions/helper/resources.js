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
  const FILE_EXT = /\.(mp4|m4v|webm|mkv|mov|avi|3gp|flv|mpg|mpeg|wmv|ogv|mp3|m4a|aac|ogg|oga|opus|wav|flac|wma|weba|mid|midi|amr|m3u8|pdf|docx?|xlsx?|pptx?|odt|ods|odp|odg|rtf|csv|tsv|txt|md|json|xml|epub|mobi|azw3|fb2|djvu|cbz|cbr|srt|vtt|ass|ssa|sub|zip|rar|7z|tar|gz|tgz|bz2|xz|zst|lz|lzma|cab|iso|img|bin|apk|apks|xapk|aab|obb|exe|msi|dmg|pkg|deb|rpm|appimage|jar|ipa|torrent|ttf|otf|woff2?|psd|ai|eps|sketch|fig|xcf|raw|dng|cr2|nef|heic|stl|obj|fbx|3mf|gpx|kml|kmz|ics|vcf|sql|db|sqlite|bak|dat|ovpn|conf|mbtiles)(?:$|[?#])/i;
  const MEDIA_EXT = /\.(mp4|m4v|webm|mkv|mov|mp3|m4a|aac|ogg|opus|wav|flac)(?:$|[?#])/i;
  // Akış listesi: adı .m3u8 ile bitmese de (ör. /master.m3u8/index veya ?format=m3u8) adresinde m3u8 geçer.
  const STREAM_EXT = /\.m3u8|[?&=\/]m3u8\b|mpegurl/i;
  // Uzun oynatmada tarayıcının kaynak kaydı (varsayılan 250 kayıt) dolar ve akış listesi kaydedilmeyebilir.
  try { performance.setResourceTimingBufferSize(2000); } catch (e) {}
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
    // Her öğe bulunduğu çerçevenin adresini taşır: gömülü oynatıcıların dosyaları çoğu zaman yalnızca oynatıcının
    // kendi adresinden (Referer) istenince verilir.
    const ref = location.href;
    const add = (url, data) => {
      if (!usable(url) || items.size >= MAX_ITEMS) return;
      const before = items.get(url) || {};
      items.set(url, Object.assign({ url, ref }, before, data));
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
      const entries = performance.getEntriesByType("resource");
      let streamFound = false;
      for (const entry of entries) {
        const url = entry.name;
        if (usable(url) && STREAM_EXT.test(url)) { add(url, { type: "stream" }); streamFound = true; }
      }
      for (const entry of entries) {
        const url = entry.name;
        if (!usable(url) || STREAM_EXT.test(url) || !MEDIA_EXT.test(url)) continue;
        const media = entry.initiatorType === "video" || entry.initiatorType === "audio";
        // Oynatıcının kendisinin (fetch/XHR) indirdiği medya dosyaları da alınır; ancak sayfada akış listesi varsa
        // bunlar çoğu zaman listenin küçük parçalarıdır ve tek tek listelenmez.
        const fetched = (entry.initiatorType === "xmlhttprequest" || entry.initiatorType === "fetch" || entry.initiatorType === "other") &&
          !streamFound && (entry.decodedBodySize || entry.transferSize || 0) > 512 * 1024;
        if (media || fetched) add(url, { type: /\.(mp3|m4a|aac|ogg|opus|wav|flac)(?:$|[?#])/i.test(url) ? "audio" : "video" });
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

  // --- Makale çıkarma ("Sonra oku" ve sesli okuma) ---------------------------------------------------------
  // Sayfadaki asıl metni bulur: <article> varsa o, yoksa en çok paragraf metni içeren kap. Başlıklar, paragraflar,
  // liste öğeleri ve alıntılar sırasıyla alınır; menü, reklam, yorum gibi bölümler atlanır.
  const SKIP = /(^|[\s_-])(nav|navbox|menu|footer|header|comment|comments|share|social|related|sidebar|advert|ad|ads|promo|cookie|newsletter|breadcrumb|infobox|toc|reference|references|reflist|caption|subscribe|recommended)([\s_-]|$)/i;
  function visibleText(el) {
    return (el.innerText || el.textContent || "").replace(/\s+/g, " ").trim();
  }
  function skipped(el) {
    for (let node = el; node && node !== document.body; node = node.parentElement) {
      const tag = node.tagName;
      if (tag === "NAV" || tag === "FOOTER" || tag === "ASIDE" || tag === "FORM" || tag === "BUTTON" ||
          tag === "TABLE" || tag === "FIGURE" || tag === "FIGCAPTION" || tag === "SUP") return true;
      const name = (node.className && node.className.baseVal === undefined ? node.className : "") + " " + (node.id || "");
      if (SKIP.test(name) && node.tagName !== "ARTICLE" && node.tagName !== "MAIN") return true;
      if (node.getAttribute && node.getAttribute("aria-hidden") === "true") return true;
    }
    return false;
  }
  function bestContainer() {
    const article = document.querySelectorAll("article");
    if (article.length === 1 && visibleText(article[0]).length > 500) return article[0];
    const scores = new Map();
    document.querySelectorAll("p").forEach((p) => {
      const len = visibleText(p).length;
      if (len < 40 || skipped(p)) return;
      const parent = p.parentElement;
      if (!parent) return;
      scores.set(parent, (scores.get(parent) || 0) + len);
      const grand = parent.parentElement;
      if (grand) scores.set(grand, (scores.get(grand) || 0) + len / 2);
    });
    let best = null;
    let bestScore = 0;
    scores.forEach((score, el) => { if (score > bestScore) { best = el; bestScore = score; } });
    return bestScore > 300 ? best : null;
  }
  function extractArticle() {
    const root = bestContainer();
    if (!root) return null;
    const blocks = [];
    let total = 0;
    root.querySelectorAll("h1, h2, h3, h4, p, li, blockquote, pre").forEach((el) => {
      if (total > 200000 || skipped(el)) return;
      // İç içe öğeler iki kez alınmasın (ör. li içindeki p).
      if (el.parentElement && el.parentElement.closest("p, li, blockquote, pre") && root.contains(el.parentElement)) return;
      const text = visibleText(el);
      if (!text || (el.tagName === "LI" && text.length < 3)) return;
      const kind = /^H[1-4]$/.test(el.tagName) ? "h" : el.tagName === "LI" ? "li" : el.tagName === "BLOCKQUOTE" ? "q" : "p";
      blocks.push({ k: kind, t: text });
      total += text.length;
    });
    if (total < 300) return null;
    const meta = (name) => {
      const el = document.querySelector('meta[property="' + name + '"], meta[name="' + name + '"]');
      return el ? el.getAttribute("content") : "";
    };
    const h1 = document.querySelector("h1");
    return {
      title: meta("og:title") || (h1 ? visibleText(h1) : "") || document.title,
      site: meta("og:site_name") || location.hostname.replace(/^www\./, ""),
      byline: meta("author") || "",
      lang: document.documentElement.lang || "",
      blocks: blocks,
    };
  }

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
      if (message && message.type === "article") {
        let article = null;
        try { article = extractArticle(); } catch (e) {}
        try { port.postMessage({ type: "article", id: message.id, article: article }); } catch (e) {}
        return;
      }
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
