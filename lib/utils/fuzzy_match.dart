import 'dart:math' as math;

/// Tolerant text matching for place search.
///
/// Geocoders match strings strictly, so two everyday inputs fail outright:
/// a typo ("via robberto ricci") and a name longer than the one OSM stores
/// (the user types "via roberto ricci", OSM has "via ricci"). This scores how
/// well a query describes a candidate name, allowing both — the search UI then
/// ranks by that score instead of trusting provider order.
class FuzzyMatch {
  /// Words that carry no distinguishing information in an address and must not
  /// dominate the score. Kept multilingual and deliberately small: only terms
  /// that are pure street-type markers, never actual names.
  static const _stopWords = {
    'via', 'viale', 'vicolo', 'strada', 'stradone', 'piazza', 'piazzale',
    'largo', 'corso', 'lungomare', 'contrada', 'localita', 'borgo',
    'street', 'st', 'road', 'rd', 'avenue', 'ave', 'lane', 'square',
    'rue', 'boulevard', 'bd', 'place', 'chemin', 'allee',
    'strasse', 'str', 'platz', 'weg', 'gasse',
    'calle', 'plaza', 'avenida', 'paseo',
    'rua', 'praca', 'straat', 'plein', 'gata', 'vej', 'katu',
    'di', 'de', 'del', 'della', 'dello', 'dei', 'degli', 'delle',
    'da', 'dal', 'la', 'le', 'lo', 'il', 'gli', 'las', 'los',
    'the', 'of', 'and', 'e', 'y', 'et', 'und',
  };

  /// Normalises text for comparison: lowercase, accents folded, punctuation
  /// reduced to spaces. Folding is table-based (no `intl` dependency) and
  /// covers the Latin-1/Latin-A letters used across the app's 27 locales.
  static String normalize(String s) {
    const zero = 0x30, nine = 0x39, a = 0x61, z = 0x7a, space = 0x20;
    final buf = StringBuffer();
    var pendingSpace = false;
    for (final rune in s.toLowerCase().runes) {
      // Character class by code point, not by RegExp: this runs over every
      // candidate on every keystroke, and building a RegExp per character
      // dominated the cost of the whole search ranking.
      final isAlnum =
          (rune >= a && rune <= z) || (rune >= zero && rune <= nine);
      final folded = isAlnum ? null : _accents[String.fromCharCode(rune)];
      if (!isAlnum && folded == null) {
        // Collapse any run of separators into a single space, and never emit
        // a leading one — cheaper than a trailing trim + collapse pass.
        pendingSpace = buf.isNotEmpty;
        continue;
      }
      if (pendingSpace) {
        buf.writeCharCode(space);
        pendingSpace = false;
      }
      if (folded != null) {
        buf.write(folded);
      } else {
        buf.writeCharCode(rune);
      }
    }
    return buf.toString();
  }

  static const _accents = {
    'à': 'a', 'á': 'a', 'â': 'a', 'ã': 'a', 'ä': 'a', 'å': 'a', 'ā': 'a',
    'ă': 'a', 'ą': 'a',
    'è': 'e', 'é': 'e', 'ê': 'e', 'ë': 'e', 'ē': 'e', 'ė': 'e', 'ę': 'e',
    'ě': 'e',
    'ì': 'i', 'í': 'i', 'î': 'i', 'ï': 'i', 'ī': 'i', 'į': 'i',
    'ò': 'o', 'ó': 'o', 'ô': 'o', 'õ': 'o', 'ö': 'o', 'ø': 'o', 'ō': 'o',
    'ù': 'u', 'ú': 'u', 'û': 'u', 'ü': 'u', 'ū': 'u', 'ů': 'u', 'ų': 'u',
    'ý': 'y', 'ÿ': 'y',
    'ñ': 'n', 'ń': 'n', 'ň': 'n', 'ņ': 'n',
    'ç': 'c', 'ć': 'c', 'č': 'c',
    'š': 's', 'ś': 's', 'ş': 's', 'ș': 's',
    'ž': 'z', 'ź': 'z', 'ż': 'z',
    'ł': 'l', 'ľ': 'l', 'ļ': 'l',
    'ť': 't', 'ţ': 't', 'ț': 't',
    'ď': 'd', 'đ': 'd',
    'ř': 'r', 'ŕ': 'r',
    'ğ': 'g', 'ģ': 'g',
    'ķ': 'k', 'ĺ': 'l',
    'ß': 'ss', 'æ': 'ae', 'œ': 'oe', 'ð': 'd', 'þ': 'th',
  };

  static List<String> _tokens(String s) =>
      normalize(s).split(' ').where((t) => t.isNotEmpty).toList();

  /// Similarity of two single words in 0..1.
  ///
  /// Exact match scores 1. A prefix relationship scores high, which is what
  /// makes suggestions useful while the user is still typing ("garib" →
  /// "garibaldi"). Otherwise an edit-distance ratio handles typos, and
  /// anything below ~⅔ similar counts as no match at all so unrelated words
  /// never contribute.
  static double wordScore(String a, String b) {
    if (a == b) return 1;
    if (a.isEmpty || b.isEmpty) return 0;
    final shorter = a.length <= b.length ? a : b;
    final longer = a.length <= b.length ? b : a;
    if (shorter.length >= 3 && longer.startsWith(shorter)) {
      // Slight penalty for the unmatched tail so a full match always wins.
      return 0.98 - 0.1 * (1 - shorter.length / longer.length);
    }
    final dist = _levenshtein(a, b, maxDist: 3);
    if (dist < 0) return 0;
    final ratio = 1 - dist / longer.length;
    return ratio >= 0.66 ? ratio * 0.95 : 0;
  }

  /// How well [query] and [candidate] describe the same place, in 0..1.
  ///
  /// The two texts are compared in **both** directions and combined with a
  /// harmonic mean:
  ///
  ///   * how much of the query the candidate accounts for — a candidate that
  ///     ignores half of what was typed is probably not it;
  ///   * how much of the candidate the query accounts for — this is what makes
  ///     the shorter, tighter name win.
  ///
  /// That symmetry is what recovers the real-world miss "via roberto ricci" →
  /// **Via Ricci**: the query direction alone scores it poorly (a whole word is
  /// unaccounted for), so a wrong-but-longer "Via Roberto Baldini" would beat
  /// it; the candidate direction scores Via Ricci perfectly and pulls it back
  /// to the top.
  ///
  /// Words are weighted by length, and street-type stop words ("via", "street")
  /// weigh almost nothing, so sharing only those never lifts a result.
  static double score(String query, String candidate) {
    final q = _tokens(query);
    final c = _tokens(candidate);
    if (q.isEmpty || c.isEmpty) return 0;

    final forward = _coverage(q, c);
    // A result that matches none of the meaningful words is not a result,
    // however many "via"s it shares with the query.
    if (forward.strongTokens > 0 && forward.strongMatches == 0) return 0;
    final backward = _coverage(c, q);
    if (forward.ratio <= 0 || backward.ratio <= 0) return 0;
    return (2 * forward.ratio * backward.ratio / (forward.ratio + backward.ratio))
        .clamp(0.0, 1.0);
  }

  /// Weighted fraction of [from]'s words that appear (fuzzily) in [to], plus
  /// the counts needed to reject stop-word-only matches.
  static ({double ratio, int strongTokens, int strongMatches}) _coverage(
      List<String> from, List<String> to) {
    double total = 0, matched = 0;
    var strongTokens = 0, strongMatches = 0;
    for (final ft in from) {
      final isStop = _stopWords.contains(ft);
      final w = isStop ? 0.2 : ft.length.toDouble();
      total += w;
      double best = 0;
      for (final tt in to) {
        final s = wordScore(ft, tt);
        if (s > best) best = s;
        if (best == 1) break;
      }
      matched += w * best;
      if (!isStop) {
        strongTokens++;
        if (best >= 0.66) strongMatches++;
      }
    }
    return (
      ratio: total == 0 ? 0.0 : matched / total,
      strongTokens: strongTokens,
      strongMatches: strongMatches,
    );
  }

  /// Levenshtein distance, abandoned as soon as it exceeds [maxDist]
  /// (returns -1). Bounding keeps this O(n·m) helper cheap enough to run over
  /// every suggestion on every keystroke.
  static int _levenshtein(String a, String b, {int maxDist = 3}) {
    if ((a.length - b.length).abs() > maxDist) return -1;
    var prev = List<int>.generate(b.length + 1, (i) => i);
    var cur = List<int>.filled(b.length + 1, 0);
    for (var i = 1; i <= a.length; i++) {
      cur[0] = i;
      var rowMin = cur[0];
      for (var j = 1; j <= b.length; j++) {
        final cost = a.codeUnitAt(i - 1) == b.codeUnitAt(j - 1) ? 0 : 1;
        cur[j] = math.min(
          math.min(cur[j - 1] + 1, prev[j] + 1),
          prev[j - 1] + cost,
        );
        if (cur[j] < rowMin) rowMin = cur[j];
      }
      if (rowMin > maxDist) return -1;
      final tmp = prev;
      prev = cur;
      cur = tmp;
    }
    return prev[b.length] <= maxDist ? prev[b.length] : -1;
  }
}
