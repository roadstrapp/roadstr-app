import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/bounded_http.dart';

void main() {
  late HttpServer server;

  setUp(() async {
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
  });

  tearDown(() => server.close(force: true));

  Uri uri(String path) =>
      Uri.parse('http://${server.address.host}:${server.port}$path');

  test('rejects an oversized response before buffering it', () async {
    server.listen((request) async {
      request.response.contentLength = 1024;
      request.response.add(List.filled(1024, 1));
      await request.response.close();
    });

    await expectLater(
      BoundedHttp.get(
        uri('/large'),
        maxBytes: 32,
        timeout: const Duration(seconds: 2),
      ),
      throwsA(isA<HttpException>()),
    );
  });

  test('does not follow redirects to another origin', () async {
    server.listen((request) async {
      request.response.statusCode = HttpStatus.found;
      request.response.headers
          .set(HttpHeaders.locationHeader, 'https://example.com/');
      await request.response.close();
    });

    final response = await BoundedHttp.get(
      uri('/redirect'),
      maxBytes: 32,
      timeout: const Duration(seconds: 2),
    );

    expect(response.statusCode, HttpStatus.found);
    expect(response.request!.url.host, server.address.host);
  });
}
