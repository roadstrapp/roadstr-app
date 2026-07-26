import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/overpass_client.dart';

void main() {
  test('exposes exactly the vetted worldwide mirrors', () {
    // Regression guard: overpass.osm.ch is a Switzerland-only extract that
    // answers HTTP 200 with zero elements everywhere else, which reads as
    // "nothing here" instead of as a failure. It must never come back.
    expect(OverpassClient.mirrors, isNotEmpty);
    expect(OverpassClient.mirrors.any((m) => m.contains('osm.ch')), isFalse);
    for (final m in OverpassClient.mirrors) {
      expect(Uri.parse(m).scheme, 'https');
    }
  });

  test('rotate cycles through the mirrors and wraps around', () {
    final c = OverpassClient();
    final seen = <String>[];
    for (var i = 0; i < OverpassClient.mirrors.length; i++) {
      seen.add(c.currentMirror);
      c.rotate();
    }
    expect(seen, OverpassClient.mirrors);
    expect(c.currentMirror, OverpassClient.mirrors.first); // wrapped
  });

  group('against a local server', () {
    late HttpServer server;
    late List<String> bodies;

    setUp(() async {
      bodies = [];
      server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    });

    tearDown(() => server.close(force: true));

    /// Serves [statusCode]/[payload] and records every request body.
    void serve(int statusCode, String payload) {
      server.listen((req) async {
        bodies.add(await utf8.decoder.bind(req).join());
        req.response.statusCode = statusCode;
        req.response.write(payload);
        req.response.close();
      });
    }

    /// A client whose only mirror is the local test server.
    OverpassClient clientForServer() => _TestClient(
        'http://${server.address.host}:${server.port}/api/interpreter');

    test('posts the query form-encoded and returns the elements', () async {
      serve(200, jsonEncode({
        'elements': [
          {'type': 'node', 'id': 1},
          {'type': 'way', 'id': 2},
        ]
      }));
      final els = await clientForServer().fetchElements('out json;',
          maxBytes: 1024 * 1024, timeout: const Duration(seconds: 5));
      expect(els, hasLength(2));
      expect(els.first['id'], 1);
      expect(bodies.single, 'data=out+json%3B');
    });

    test('a response without elements is an empty list, not a crash', () async {
      serve(200, jsonEncode({'version': 0.6}));
      final els = await clientForServer().fetchElements('q',
          maxBytes: 1024, timeout: const Duration(seconds: 5));
      expect(els, isEmpty);
    });

    test('a non-200 throws so callers can back off', () async {
      serve(429, 'too many requests');
      expect(
        () => clientForServer().fetchElements('q',
            maxBytes: 1024, timeout: const Duration(seconds: 5)),
        throwsA(isA<OverpassException>()
            .having((e) => e.statusCode, 'statusCode', 429)),
      );
    });

    test('fetchElementsAnyMirror returns null when every mirror fails',
        () async {
      serve(503, 'unavailable');
      final els = await clientForServer().fetchElementsAnyMirror('q',
          maxBytes: 1024, timeout: const Duration(seconds: 5));
      expect(els, isNull);
      // One attempt per mirror, no more.
      expect(bodies, hasLength(OverpassClient.mirrors.length));
    });
  });
}

/// [OverpassClient] pinned to a single URL, so the mirror behaviour can be
/// exercised without touching the public Overpass instances.
class _TestClient extends OverpassClient {
  _TestClient(this.url);
  final String url;

  /// As many entries as the real list, so mirror-rotation behaviour is
  /// exercised faithfully, but all of them served locally — the hedged fetch
  /// reads this list too and must never reach the public instances.
  @override
  List<String> get availableMirrors =>
      List.filled(OverpassClient.mirrors.length, url);
}
