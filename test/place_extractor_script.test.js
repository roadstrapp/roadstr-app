// Runs the bundled page extension's scripts against a fake page and a fake `browser` API.
// Started by native_place_extractor_script_test.dart; run by hand with `node --test`.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const dir = path.join(__dirname, '..', 'native-android', 'app', 'src', 'gecko', 'assets', 'web', 'place-extractor');
const read = (name) => fs.readFileSync(path.join(dir, name), 'utf8');

function page({ blocks = [], meta = {}, url = 'https://www.example.org/menu', throwOnSend = false } = {}) {
  const sent = [];
  const asked = [];
  const document = {
    querySelectorAll(selector) {
      assert.equal(selector, 'script[type="application/ld+json"]');
      return blocks.map((textContent) => ({ textContent }));
    },
    querySelector(selector) {
      asked.push(selector);
      const key = /property="([^"]+)"/.exec(selector)[1];
      return key in meta ? { getAttribute: () => meta[key] } : null;
    },
  };
  const browser = {
    runtime: {
      sendMessage(message) {
        if (throwOnSend) throw new Error('boom');
        sent.push(message);
      },
    },
  };
  vm.runInNewContext(read('extractor.js'), { document, browser, location: { href: url } });
  return { sent, asked };
}

test('a page with place data sends one message with the scripts and the allowed meta tags', () => {
  const { sent } = page({
    blocks: ['{"@type":"Restaurant"}', '{"@type":"Place"}'],
    meta: { 'og:title': 'Trattoria Verde', 'og:latitude': '45.43', 'og:image': 'https://x/y.png' },
  });
  assert.equal(sent.length, 1);
  // Objects made inside the script's own context have another prototype: compare their JSON.
  assert.deepEqual(JSON.parse(JSON.stringify(sent[0])), {
    v: 1,
    url: 'https://www.example.org/menu',
    jsonLd: ['{"@type":"Restaurant"}', '{"@type":"Place"}'],
    og: { 'og:title': 'Trattoria Verde', 'og:latitude': '45.43' },
  });
});

test('only the known meta tags are even looked at', () => {
  const { asked } = page({ meta: { 'og:title': 'x' } });
  assert.ok(asked.length > 0);
  for (const selector of asked) assert.match(selector, /^meta\[property="(og|place):[a-z_:-]+"\]$/);
  assert.ok(!asked.some((s) => s.includes('og:image')));
});

test('a page with nothing to say sends nothing', () => {
  assert.equal(page({}).sent.length, 0);
  assert.equal(page({ blocks: ['', '   '.slice(0, 0)] }).sent.length, 0);
});

test('oversized scripts are dropped and the number of scripts is capped', () => {
  const big = 'x'.repeat(65537);
  const many = Array.from({ length: 12 }, (_, i) => `{"n":${i}}`);
  const { sent } = page({ blocks: [big, ...many] });
  assert.equal(sent[0].jsonLd.length, 8);
  assert.ok(sent[0].jsonLd.every((b) => b.length <= 65536));
});

test('long meta values are cut', () => {
  const { sent } = page({ meta: { 'og:title': 't'.repeat(500) } });
  assert.equal(sent[0].og['og:title'].length, 160);
});

test('a failing send never breaks the page', () => {
  assert.doesNotThrow(() => page({ blocks: ['{}'], throwOnSend: true }));
});

function background(message, senderId = 'place-extractor@roadstr.app') {
  const natives = [];
  let listener;
  const browser = {
    runtime: {
      id: 'place-extractor@roadstr.app',
      onMessage: { addListener: (l) => { listener = l; } },
      sendNativeMessage: (app, m) => natives.push([app, m]),
    },
  };
  vm.runInNewContext(read('background.js'), { browser });
  listener(message, senderId === null ? null : { id: senderId });
  return natives;
}

const good = { v: 1, url: 'https://www.example.org/', jsonLd: [], og: {} };

test('the background script hands a good message to Roadstr and nobody else', () => {
  assert.deepEqual(JSON.parse(JSON.stringify(background(good))), [['roadstrExtractor', good]]);
});

test('anything not from the extension or not shaped like the message is dropped', () => {
  assert.equal(background(good, 'someone-else@example.org').length, 0);
  assert.equal(background(good, null).length, 0);
  assert.equal(background(null).length, 0);
  assert.equal(background({ ...good, v: 2 }).length, 0);
  assert.equal(background({ ...good, url: 5 }).length, 0);
  assert.equal(background({ ...good, jsonLd: 'x' }).length, 0);
  assert.equal(background({ ...good, og: null }).length, 0);
});
