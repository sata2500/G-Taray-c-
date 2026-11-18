"use strict";
// Uygulama ile eklenti arasındaki köprü.
const DEFAULTS = { backgroundPlay: true, sponsorBlock: true };

browser.runtime.onMessage.addListener((message) => {
  if (message && message.type === "settings") {
    return browser.runtime
      .sendNativeMessage("gtarayici", { type: "settings" })
      .then((reply) => Object.assign({}, DEFAULTS, reply || {}))
      .catch(() => DEFAULTS);
  }
  return undefined;
});

// Video oynatan sayfalar bu bağlantıları açar (sekmeler, gizli sekmeler ve yüklü web uygulamaları).
const mediaPorts = new Set();
browser.runtime.onConnect.addListener((port) => {
  if (port.name !== "gt-media") return;
  mediaPorts.add(port);
  port.onDisconnect.addListener(() => mediaPorts.delete(port));
});

function broadcast(type) {
  for (const port of mediaPorts) {
    try {
      port.postMessage({ type });
    } catch (e) {
      mediaPorts.delete(port);
    }
  }
}

// Uygulamadan gelen komutlar (ör. resim içinde resim başlıyor/bitiyor).
let nativePort = null;
function connectNative() {
  try {
    nativePort = browser.runtime.connectNative("gtarayici");
    nativePort.onMessage.addListener((message) => {
      if (message && message.type) broadcast(message.type);
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
