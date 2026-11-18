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

// Uygulamadan gelen komutlar (ör. resim içinde resim için videoyu tam ekran yap).
let port = null;
function connect() {
  try {
    port = browser.runtime.connectNative("gtarayici");
    port.onMessage.addListener(async (message) => {
      if (!message || !message.type) return;
      const tabs = await browser.tabs.query({});
      for (const tab of tabs) {
        browser.tabs.sendMessage(tab.id, { type: message.type }).catch(() => {});
      }
    });
    port.onDisconnect.addListener(() => {
      port = null;
      setTimeout(connect, 2000);
    });
  } catch (e) {
    setTimeout(connect, 5000);
  }
}
connect();
