// Runs in the page, in an isolated world the page's own scripts cannot reach. It reads the page's
// JSON-LD scripts and a few Open Graph tags and sends ONE message to the background script.
// It never evaluates what it reads, never fetches anything and never changes the page.
(function () {
  "use strict";

  var MAX_BLOCKS = 8;
  var MAX_CHARS = 65536;
  var MAX_VALUE = 160;
  var META_KEYS = [
    "og:title", "og:site_name", "og:url", "og:type", "og:latitude", "og:longitude",
    "og:street-address", "og:locality", "og:postal-code", "og:country-name", "og:phone_number",
    "place:location:latitude", "place:location:longitude"
  ];

  try {
    var blocks = [];
    var scripts = document.querySelectorAll('script[type="application/ld+json"]');
    for (var i = 0; i < scripts.length && blocks.length < MAX_BLOCKS; i++) {
      var text = scripts[i].textContent;
      if (typeof text === "string" && text.length > 0 && text.length <= MAX_CHARS) blocks.push(text);
    }

    var og = {};
    var found = false;
    for (var k = 0; k < META_KEYS.length; k++) {
      var meta = document.querySelector('meta[property="' + META_KEYS[k] + '"]');
      var value = meta && meta.getAttribute("content");
      if (value) {
        og[META_KEYS[k]] = value.slice(0, MAX_VALUE);
        found = true;
      }
    }

    if (blocks.length === 0 && !found) return;
    browser.runtime.sendMessage({ v: 1, url: location.href, jsonLd: blocks, og: og });
  } catch (e) {
    // A page that breaks the reader simply has nothing to offer.
  }
})();
