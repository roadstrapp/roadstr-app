# Localisation: what is done and what needs a native speaker

Everything below was written for this work without a native-speaker review. The tests prove that
every string exists in all 27 languages and that every vocabulary is registered and parses; they
cannot prove the words are what a person of that language would say.

## 1. Search vocabularies

One file per language in `android/app/src/main/kotlin/app/roadstr/core/discovery/lexicon/`
(`LexiconXx.kt`). The English vocabulary is merged behind every other one, so English words are
understood in every language.

| Tier | Languages | What the vocabulary covers |
|---|---|---|
| Full | en, it, de, fr, es, pt, nl | 52–53 categories, 14–15 attributes, 18 cuisines, the "near me / in / near / near my destination / along the route / open now" phrases and filler words |
| Core | bg, cs, da, el, et, fi, ga, hr, hu, ja, lt, lv, mt, pl, ro, ru, sk, sl, sv, zh | 18–19 categories (restaurant, fast food, café, bar, pub, supermarket, convenience store, pharmacy, fuel, charging station, parking, hotel, hospital, ATM, bank, post office, police, cinema, train station), 4 attributes (vegan, vegetarian, gluten-free, open 24 hours), one cuisine line, and the same phrases |

A search for a category a core language does not list (for example "bakery" or "airport") is not
understood in that language unless the English word is typed; the ordinary search then runs, so
nothing breaks, it just is not interpreted.

### Priorities for a native review

1. **Scripts without spaces or with their own tokenisation:** ja, zh (particles such as の / 的, no word boundaries), then el, bg, ru. They use the project's own Unicode normaliser (D-04); the parser tests cover a handful of phrases each.
2. **Languages that inflect the place name:** fi, hu, et, lv, lt, pl, cs, sk, sl, hr, ru. "Near Helsinki" is "Helsingin lähellä": the vocabulary matches stems and prefixes, and the town is then looked up as typed. A reviewer should add the common case endings of "in / near" phrases.
3. **The rarest:** ga, mt.
4. **Everything else in the core tier**, then the full-tier languages (their vocabularies are larger and so are the chances of a missing everyday word).

### How to review

- Read the file; each line is `kind NAME: word, word, …`. Kinds are documented in `Lexicon.kt`.
- To add a category to a core language, copy the line of that category from `LexiconEn.kt`, translate the words, keep the name before the colon.
- Run `./gradlew :app:testDebugUnitTest --tests '*Lexicon*' --tests '*NaturalQueryParser*'` in `android/` and `flutter test test/native_world_discovery_contract_test.dart`.
- Add the phrases you checked to `NaturalQueryParserTest` so the review is kept.

## 2. Interface strings

76 strings (each in 27 languages) were added by this work, all through the generator
`tools/kotlin_rewrite/generate_android_nostr_strings.dart`:

| Group | Count | Where it shows |
|---|---|---|
| `native_websearch_*` | 37 | Settings → Search → Web results in search, the web part of the search list, problems |
| `native_browser_*` | 18 | The in-app browser (optional variant) |
| `native_search_web_*` | 8 | The offer, question and results of the web part of the search |
| `native_search_notice_*` | 6 | The one-line notes under a place search |
| `native_history_*` | 4 | The route history |
| `native_settings_web_results*` | 2 | The row in Settings |
| `native_map_whats_here` | 1 | The long-press menu |

Things a reviewer should check in particular: the privacy sentences (`native_websearch_privacy`,
`native_websearch_google`, `native_websearch_link_note`) say exactly what is sent and to whom; "SearXNG instance"
is translated as "instance" in most languages and left as the product name where a translation would
confuse; informal versus formal address ("tu"/"vous", "du"/"Sie") follows the existing app strings
where they exist and the informal form elsewhere.

## 3. Known gaps, stated plainly

- No language was reviewed by a native speaker.
- Core-tier languages understand about a third of the categories.
- Right-to-left languages are not among the 27, so no right-to-left layout was needed or tested.
- The numbers and dates in the results use the device formats; opening hours are shown as the map data has them.
