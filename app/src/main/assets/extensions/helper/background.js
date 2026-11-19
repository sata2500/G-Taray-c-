"use strict";
// G-Tarayıcı yardımcı eklentisi ile uygulama arasındaki köprü.
const DEFAULTS = { backgroundPlay: true, sponsorBlock: true };

// Ayarlar (arka planda oynatma, SponsorBlock) uygulamadan okunur.
browser.runtime.onMessage.addListener((message) => {
  if (message && message.type === "settings") {
    return browser.runtime
      .sendNativeMessage("gtarayici", { type: "settings" })
      .then((reply) => Object.assign({}, DEFAULTS, reply || {}))
      .catch(() => DEFAULTS);
  }
  return undefined;
});

// Uygulamaya kalıcı bağlantı: sayfalardan gelen video konumları buradan iletilir.
let nativePort = null;
function connectNative() {
  try {
    nativePort = browser.runtime.connectNative("gtarayici");
    nativePort.onDisconnect.addListener(() => {
      nativePort = null;
      setTimeout(connectNative, 2000);
    });
  } catch (e) {
    setTimeout(connectNative, 5000);
  }
}
connectNative();

browser.runtime.onConnect.addListener((port) => {
  if (port.name !== "gt-media") return;
  port.onMessage.addListener((message) => {
    if (message && message.type === "video-rect" && nativePort) {
      try {
        nativePort.postMessage(message);
      } catch (e) {}
    }
  });
});
