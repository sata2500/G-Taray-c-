"use strict";
// Uygulama ayarlarını (arka planda oynatma, SponsorBlock) yerel uygulamadan okur.
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
