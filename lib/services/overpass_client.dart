import 'dart:async';
import 'dart:convert';

import 'bounded_http.dart';

/// Thrown when an Overpass mirror answers with a non-200 status.
class OverpassException implements Exception {
  final int statusCode;
  const OverpassException(this.statusCode);

  @override
  String toString() => 'Overpass HTTP $statusCode';
}

/// Shared access to the public Overpass API mirrors.
///
/// The speed-limit, speed-camera, ZTL and POI services each used to carry
/// their own copy of this: the same mirror list, the same round-robin index,
/// the same POST + status check + `elements` decode. Retiring a dead mirror
/// therefore meant remembering four files — and the copies had already drifted
/// in how they encoded the request body.
///
/// Each service owns an instance, so one service exhausting a mirror does not
/// move the others off a mirror that is working for them.
class OverpassClient {
  /// Public mirrors, in preference order.
  ///
  /// NB: overpass.osm.ch is deliberately absent — it serves a Switzerland-only
  /// extract and answers queries for the rest of the world with an empty
  /// success, which reads as "nothing here" rather than as a failure and so
  /// never triggers a fallback. Do not add it back.
  static const mirrors = [
    'https://overpass-api.de/api/interpreter',
    'https://overpass.openstreetmap.fr/api/interpreter',
  ];

  /// Formats a latitude or longitude for an Overpass `around:` clause.
  ///
  /// Not `$value`: Dart prints doubles below 1e-6 in exponent form, and
  /// `around:60,51.5,5e-7` is a syntax error Overpass rejects outright. Small
  /// as that window is, it is real — it covers the Greenwich meridian through
  /// London. Seven decimals is about a centimetre, far beyond GPS precision.
  static String coord(double value) => value.toStringAsFixed(7);

  int _idx = 0;

  /// The mirrors this instance may use. Overridden in tests to keep every
  /// request — including the hedged one below — on a local server.
  List<String> get availableMirrors => mirrors;

  /// The mirror the next request will use.
  String get currentMirror => availableMirrors[_idx % availableMirrors.length];

  /// Moves to the next mirror. Callers that manage their own backoff call this
  /// when a request fails.
  void rotate() {
    _idx = (_idx + 1) % availableMirrors.length;
  }

  /// Runs [query] against the current mirror and returns its `elements`.
  ///
  /// Throws on transport failure or a non-200 status. That matters: a caller
  /// that caches "no results" must be able to tell a genuine empty answer from
  /// a failed request, otherwise a dead mirror silently becomes "there is
  /// nothing around you" for as long as the cache lives.
  Future<List<Map<String, dynamic>>> fetchElements(
    String query, {
    required int maxBytes,
    required Duration timeout,
  }) =>
      // whereType inside _fetchFrom, not cast: `cast` is a lazy view that
      // throws on *access*, so a malformed element would blow up inside the
      // caller's parsing loop — outside the try that is supposed to handle a
      // bad response. Filtering eagerly means callers only ever see
      // well-formed elements.
      _fetchFrom(currentMirror, query, maxBytes: maxBytes, timeout: timeout);

  /// Tries every mirror in turn, returning null only when all of them fail.
  ///
  /// For interactive queries, where there is no backoff timer to retry later
  /// and the user is waiting for an answer now.
  Future<List<Map<String, dynamic>>?> fetchElementsAnyMirror(
    String query, {
    required int maxBytes,
    required Duration timeout,
  }) async {
    for (var attempt = 0; attempt < availableMirrors.length; attempt++) {
      try {
        return await fetchElements(query, maxBytes: maxBytes, timeout: timeout);
      } catch (_) {
        rotate();
      }
    }
    return null;
  }

  /// Like [fetchElementsAnyMirror], but does not wait for a busy mirror to
  /// time out before trying the next one.
  ///
  /// The public mirrors are free and frequently loaded: measured on a live run,
  /// the first query of a session regularly spent the entire timeout on
  /// overpass-api.de before the fallback answered in under a second — ten
  /// seconds of nothing happening after the user tapped a button. Here the
  /// preferred mirror gets [hedgeAfter] to itself, and only if it has not
  /// answered by then is the next one asked as well, with the first usable
  /// answer winning — and becoming the preferred mirror for the next query.
  /// The extra request is therefore only sent when it is actually needed, and
  /// stops being needed once the faster mirror has been found.
  Future<List<Map<String, dynamic>>?> fetchElementsHedged(
    String query, {
    required int maxBytes,
    required Duration timeout,
    Duration hedgeAfter = const Duration(milliseconds: 1500),
  }) async {
    final winner = Completer<List<Map<String, dynamic>>?>();
    var pending = availableMirrors.length;

    void attempt(String mirror) {
      _fetchFrom(mirror, query, maxBytes: maxBytes, timeout: timeout).then(
        (elements) {
          if (winner.isCompleted) return;
          // Whoever answered first is the mirror to start with next time, so a
          // mirror that is having a bad day stops costing the hedge delay on
          // every single query.
          final index = availableMirrors.indexOf(mirror);
          if (index >= 0) _idx = index;
          winner.complete(elements);
        },
        onError: (Object _) {
          pending--;
          // Only the last failure is an answer: an earlier one must not cut
          // short a mirror that is still working on it.
          if (pending <= 0 && !winner.isCompleted) winner.complete(null);
        },
      );
    }

    attempt(currentMirror);
    final hedge = Timer(hedgeAfter, () {
      if (winner.isCompleted) return;
      for (var i = 0; i < availableMirrors.length; i++) {
        if (i != _idx % availableMirrors.length) attempt(availableMirrors[i]);
      }
    });
    try {
      return await winner.future.timeout(timeout, onTimeout: () => null);
    } finally {
      hedge.cancel();
    }
  }

  Future<List<Map<String, dynamic>>> _fetchFrom(
    String mirror,
    String query, {
    required int maxBytes,
    required Duration timeout,
  }) async {
    final res = await BoundedHttp.post(
      Uri.parse(mirror),
      headers: const {
        'Content-Type': 'application/x-www-form-urlencoded',
        'User-Agent': 'Roadstr/1.0 (navigation app)',
      },
      body: 'data=${Uri.encodeQueryComponent(query)}',
      maxBytes: maxBytes,
      timeout: timeout,
    );
    if (res.statusCode != 200) throw OverpassException(res.statusCode);
    final data = jsonDecode(res.body) as Map<String, dynamic>;
    final elements = data['elements'] as List?;
    if (elements == null) return const [];
    return elements.whereType<Map<String, dynamic>>().toList(growable: false);
  }
}
