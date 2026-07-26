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

  int _idx = 0;

  /// The mirror the next request will use.
  String get currentMirror => mirrors[_idx];

  /// Moves to the next mirror. Callers that manage their own backoff call this
  /// when a request fails.
  void rotate() {
    _idx = (_idx + 1) % mirrors.length;
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
  }) async {
    final res = await BoundedHttp.post(
      Uri.parse(currentMirror),
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
    return elements?.cast<Map<String, dynamic>>() ?? const [];
  }

  /// Tries every mirror in turn, returning null only when all of them fail.
  ///
  /// For interactive queries, where there is no backoff timer to retry later
  /// and the user is waiting for an answer now.
  Future<List<Map<String, dynamic>>?> fetchElementsAnyMirror(
    String query, {
    required int maxBytes,
    required Duration timeout,
  }) async {
    for (var attempt = 0; attempt < mirrors.length; attempt++) {
      try {
        return await fetchElements(query,
            maxBytes: maxBytes, timeout: timeout);
      } catch (_) {
        rotate();
      }
    }
    return null;
  }
}
