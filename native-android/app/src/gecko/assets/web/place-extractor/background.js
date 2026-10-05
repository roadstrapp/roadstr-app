// Hands the content script's message to Roadstr, the only recipient. Anything that did not come
// from this extension's own content script, or is not shaped like the message, is dropped here;
// Roadstr checks it again, strictly, before using a word of it.
"use strict";

browser.runtime.onMessage.addListener(function (message, sender) {
  if (!sender || sender.id !== browser.runtime.id) return;
  if (!message || message.v !== 1 || typeof message.url !== "string") return;
  if (!Array.isArray(message.jsonLd) || !message.og || typeof message.og !== "object") return;
  browser.runtime.sendNativeMessage("roadstrExtractor", message);
});
