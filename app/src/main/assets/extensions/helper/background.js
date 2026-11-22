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

// Veri tasarrufu: sitelere "Save-Data: on" başlığı (Firefox bunu kendisi göndermez). Başlık motorun kendi kural
// sistemiyle (declarativeNetRequest) eklenir; istekler eklentiye uğramaz, sayfa yüklemesi yavaşlamaz.
// (0.10.0'da webRequest ile ekleniyordu; her istek eklentiyi beklediği için sayfalar yavaşlıyordu.)
const SAVE_DATA_RULE = 1;
function setSaveData(on) {
  const dnr = browser.declarativeNetRequest;
  if (!dnr || !dnr.updateSessionRules) return;
  const rule = {
    id: SAVE_DATA_RULE,
    priority: 1,
    action: {
      type: "modifyHeaders",
      requestHeaders: [{ header: "Save-Data", operation: "set", value: "on" }],
    },
    condition: { resourceTypes: ["main_frame", "sub_frame", "stylesheet", "script", "image", "font", "xmlhttprequest", "media"] },
  };
  dnr
    .updateSessionRules({ removeRuleIds: [SAVE_DATA_RULE], addRules: on ? [rule] : [] })
    .catch(() => {});
}
