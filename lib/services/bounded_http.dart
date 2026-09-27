import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import 'http_safety_policy.dart';

/// HTTP helper that enforces a response limit while bytes are streaming.
/// Checking `Response.bodyBytes.length` after `http.get` is too late: an
/// untrusted server may already have forced the process to buffer hundreds of
/// megabytes. Redirects are disabled to keep API keys and precise coordinates
/// from being forwarded to a different origin.
class BoundedHttp {
  const BoundedHttp._();

  static Future<http.Response> get(
    Uri uri, {
    Map<String, String>? headers,
    required int maxBytes,
    required Duration timeout,
  }) {
    final request = http.Request('GET', uri);
    if (headers != null) request.headers.addAll(headers);
    return _send(request, maxBytes: maxBytes, timeout: timeout);
  }

  static Future<http.Response> post(
    Uri uri, {
    Map<String, String>? headers,
    Object? body,
    required int maxBytes,
    required Duration timeout,
  }) {
    final request = http.Request('POST', uri);
    if (headers != null) request.headers.addAll(headers);
    if (body != null) request.body = body.toString();
    return _send(request, maxBytes: maxBytes, timeout: timeout);
  }

  static Future<http.Response> _send(
    http.Request request, {
    required int maxBytes,
    required Duration timeout,
  }) async {
    BoundedHttpPolicy.validateMaxBytes(maxBytes);
    request.followRedirects = BoundedHttpPolicy.followRedirects;
    final client = http.Client();
    try {
      final streamed = await client.send(request).timeout(timeout);
      if (!BoundedHttpPolicy.acceptsContentLength(
        streamed.contentLength,
        maxBytes,
      )) {
        throw const HttpException('HTTP response is too large');
      }
      final builder = BytesBuilder(copy: false);
      var received = 0;
      // Enforce a total body deadline, not merely a per-chunk inactivity
      // timeout. Otherwise a slowloris peer could keep the request alive by
      // sending one byte just before every timeout.
      await (() async {
        await for (final chunk in streamed.stream) {
          if (!BoundedHttpPolicy.acceptsChunk(
            receivedBytes: received,
            chunkBytes: chunk.length,
            maxBytes: maxBytes,
          )) {
            throw const HttpException('HTTP response is too large');
          }
          received += chunk.length;
          builder.add(chunk);
        }
      })()
          .timeout(timeout);
      return http.Response.bytes(
        builder.takeBytes(),
        streamed.statusCode,
        headers: streamed.headers,
        reasonPhrase: streamed.reasonPhrase,
        persistentConnection: streamed.persistentConnection,
        request: request,
      );
    } finally {
      client.close();
    }
  }
}
