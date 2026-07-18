// Pragmatic parser for the OpenStreetMap `opening_hours` tag.
//
// This is the same data source OsmAnd uses for its open/closed indicator: the
// per-POI `opening_hours` string in OSM (retrieved here via Nominatim
// `extratags=1` and Overpass). The syntax is a small language; a live sample of
// 120 POIs in central Milan showed ~91% use the plain "weekday-range + time-
// range(s)" form, ~3% are `24/7`, and ~6% use month/holiday/week scoping.
//
// This parser fully handles the common form (weekday ranges and lists, multiple
// time spans, overnight spans, per-day `off`) plus `24/7`. Anything it does not
// understand — month scoping (`Nov-Mar: …`), week numbers, comments, astronomical
// times (`sunset`) — yields [OpenState.unknown] so the caller shows the raw
// string WITHOUT a possibly-wrong open/closed badge. Public/school-holiday
// tokens (`PH`, `SH`) make the result unknown: without a holiday calendar,
// silently ignoring `PH off` could tell a driver that a closed place is open.
library;

enum OpenState { open, closed, unknown }

/// The evaluated state of an `opening_hours` string at a given instant.
class OpeningStatus {
  final OpenState state;

  /// Local time the state next flips, when known:
  ///   - [OpenState.open]  → when it closes.
  ///   - [OpenState.closed] → when it next opens (may be a later day).
  /// Null when unknown or (for `24/7`) never.
  final DateTime? nextChange;

  const OpeningStatus(this.state, this.nextChange);
  static const unknown = OpeningStatus(OpenState.unknown, null);
}

class OpeningHours {
  static const _days = {
    'mo': 0,
    'tu': 1,
    'we': 2,
    'th': 3,
    'fr': 4,
    'sa': 5,
    'su': 6,
  };

  // A day-selector + its time list (or `off`). Day selector is optional
  // (absent = every day). Times are `HH:MM-HH:MM`, comma-separated.
  static final _group = RegExp(
    r'((?:mo|tu|we|th|fr|sa|su|ph|sh)(?:\s*-\s*(?:mo|tu|we|th|fr|sa|su))?'
    r'(?:\s*,\s*(?:mo|tu|we|th|fr|sa|su|ph|sh)(?:\s*-\s*(?:mo|tu|we|th|fr|sa|su))?)*)?'
    r'\s*'
    r'(off|closed|(?:\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}(?:\s*,\s*\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2})*))',
    caseSensitive: false,
  );

  // Features this parser deliberately refuses to guess at.
  static final _tooComplex = RegExp(
    r'\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|week|sunrise|sunset|dawn|dusk|easter)\b'
    r'|"|\[',
    caseSensitive: false,
  );

  /// Evaluates [raw] at [now] (local time). Returns [OpeningStatus.unknown]
  /// for empty input or any construct outside the supported subset.
  static OpeningStatus evaluate(String raw, DateTime now) {
    final s = raw.trim();
    if (s.isEmpty) return OpeningStatus.unknown;
    if (s == '24/7' || s == '00:00-24:00' || s == 'Mo-Su 00:00-24:00') {
      return const OpeningStatus(OpenState.open, null);
    }
    if (_tooComplex.hasMatch(s)) return OpeningStatus.unknown;
    // Without a jurisdiction-aware holiday calendar, ignoring PH/SH would
    // make `Mo-Fr 09:00-18:00; PH off` falsely report open on a holiday.
    if (RegExp(r'\b(?:PH|SH)\b', caseSensitive: false).hasMatch(s)) {
      return OpeningStatus.unknown;
    }

    final matches = _group.allMatches(s).toList();
    // OSM accepts both semicolons and commas between complete rules. Commas
    // inside a time/day list are already consumed by `_group`; any remaining
    // comma is therefore a rule separator, not unsupported content.
    final unsupported =
        s.replaceAll(_group, '').replaceAll(RegExp(r'[\s;,]+'), '');
    if (matches.isEmpty || unsupported.isNotEmpty) {
      return OpeningStatus.unknown;
    }

    // week[dayIndex] = list of [startMin, endMin] intervals (0..1440).
    final week = List.generate(7, (_) => <List<int>>[]);
    var matched = false;
    var invalid = false;
    for (final m in matches) {
      matched = true;
      final daysSpec = m.group(1);
      final timesSpec = m.group(2)!.toLowerCase();
      final days = _parseDays(daysSpec);
      if (days.isEmpty) continue; // e.g. a lone `PH off` — unevaluable, skip
      if (timesSpec == 'off' || timesSpec == 'closed') {
        for (final d in days) {
          week[d].clear();
        }
        continue;
      }
      for (final span in timesSpec.split(',')) {
        final t = _parseSpan(span.trim());
        if (t == null) {
          invalid = true;
          continue;
        }
        final (start, end) = t;
        for (final d in days) {
          if (end > start) {
            week[d].add([start, end]);
          } else if (end < start) {
            // Overnight: split across midnight into the following day.
            week[d].add([start, 1440]);
            week[(d + 1) % 7].add([0, end]);
          }
        }
      }
    }
    if (!matched || invalid) return OpeningStatus.unknown;

    // Merge overlapping/adjacent intervals so `08:00-12:00,10:00-18:00`
    // correctly remains open until 18:00 rather than claiming a 12:00 close.
    for (var day = 0; day < week.length; day++) {
      final intervals = week[day]..sort((a, b) => a[0].compareTo(b[0]));
      final merged = <List<int>>[];
      for (final interval in intervals) {
        if (merged.isEmpty || interval[0] > merged.last[1]) {
          merged.add([...interval]);
        } else if (interval[1] > merged.last[1]) {
          merged.last[1] = interval[1];
        }
      }
      week[day] = merged;
    }

    final nowMin = now.hour * 60 + now.minute;
    final today = now.weekday - 1; // DateTime: Mon=1 → 0

    for (final iv in week[today]) {
      if (nowMin >= iv[0] && nowMin < iv[1]) {
        final closeMin = iv[1];
        final change = DateTime(now.year, now.month, now.day)
            .add(Duration(minutes: closeMin));
        return OpeningStatus(OpenState.open, change);
      }
    }

    // Closed now — find the next opening within the next 7 days.
    for (var offset = 0; offset < 8; offset++) {
      final d = (today + offset) % 7;
      final starts = week[d].map((iv) => iv[0]).toList()..sort();
      for (final start in starts) {
        if (offset == 0 && start <= nowMin) continue;
        final change = DateTime(now.year, now.month, now.day)
            .add(Duration(days: offset, minutes: start));
        return OpeningStatus(OpenState.closed, change);
      }
    }
    return const OpeningStatus(OpenState.closed, null);
  }

  /// Expands a day selector (`Mo-Fr`, `Mo,We,Fr`, `Sa`) to 0-based indices
  /// (Mo=0…Su=6). Null/empty selector = every day. PH/SH are dropped.
  static Set<int> _parseDays(String? spec) {
    if (spec == null || spec.trim().isEmpty) return {0, 1, 2, 3, 4, 5, 6};
    final out = <int>{};
    for (final part in spec.toLowerCase().split(',')) {
      final p = part.trim();
      if (p.contains('-')) {
        final ends = p.split('-');
        final a = _days[ends[0].trim()];
        final b = _days[ends[1].trim()];
        if (a == null || b == null) continue;
        // Inclusive range, wrapping across the week end (e.g. Fr-Mo).
        for (var i = a;; i = (i + 1) % 7) {
          out.add(i);
          if (i == b) break;
        }
      } else {
        final d = _days[p];
        if (d != null) out.add(d);
      }
    }
    return out;
  }

  /// Parses `HH:MM-HH:MM` → (startMin, endMin). `24:00` = 1440. Null if malformed.
  static (int, int)? _parseSpan(String span) {
    final parts = span.split('-');
    if (parts.length != 2) return null;
    final a = _parseTime(parts[0].trim(), allow24: false);
    final b = _parseTime(parts[1].trim(), allow24: true);
    if (a == null || b == null || a == b) return null;
    return (a, b);
  }

  static int? _parseTime(String t, {required bool allow24}) {
    final m = RegExp(r'^(\d{1,2}):(\d{2})$').firstMatch(t);
    if (m == null) return null;
    final h = int.parse(m.group(1)!);
    final min = int.parse(m.group(2)!);
    if (h > 24 || min > 59 || (h == 24 && (!allow24 || min != 0))) {
      return null;
    }
    return h * 60 + min;
  }
}
