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
    nativePort.onMessage.addListener((message) => {
      if (message && message.type === "save-data") setSaveData(message.on === true);
    });
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

// Veri tasarrufu: sitelere "Save-Data: on" başlığı (Firefox bunu kendisi göndermez). Dinleyici yalnızca
// tasarruf açıkken kayıtlıdır; kapalıyken isteklere hiç dokunulmaz.
function addSaveData(details) {
  const headers = details.requestHeaders.filter((h) => h.name.toLowerCase() !== "save-data");
  headers.push({ name: "Save-Data", value: "on" });
  return { requestHeaders: headers };
}
function setSaveData(on) {
  const listening = browser.webRequest.onBeforeSendHeaders.hasListener(addSaveData);
  if (on && !listening) {
    browser.webRequest.onBeforeSendHeaders.addListener(addSaveData, { urls: ["<all_urls>"] }, [
      "blocking",
      "requestHeaders",
    ]);
  } else if (!on && listening) {
    browser.webRequest.onBeforeSendHeaders.removeListener(addSaveData);
  }
}
