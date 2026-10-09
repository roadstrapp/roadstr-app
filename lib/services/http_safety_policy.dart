/// Deterministic HTTP safety rules shared by the live Dart client and the
/// native rewrite parity fixture.
///
/// Socket ownership, DNS resolution and cancellation stay in the platform
/// client. This boundary covers the decisions that must not drift while that
/// client changes: response budgets, redirect forwarding and the one allowed
/// cleartext routing exception.
library;

enum RoutingEndpointDecision {
  accepted,
  invalid,
  cleartextRejected,
}

class BoundedHttpPolicy {
  const BoundedHttpPolicy._();

  /// Redirects can forward API keys and precise coordinates to a new origin.
  static const followRedirects = false;

  static void validateMaxBytes(int maxBytes) {
    if (maxBytes <= 0) throw ArgumentError.value(maxBytes, 'maxBytes');
  }

  /// Checks a declared body size before any response bytes are buffered.
  static bool acceptsContentLength(int? contentLength, int maxBytes) {
    validateMaxBytes(maxBytes);
    return contentLength == null ||
        (contentLength >= 0 && contentLength <= maxBytes);
  }

  /// Checks one streamed chunk without overflowing the accumulated count.
  static bool acceptsChunk({
    required int receivedBytes,
    required int chunkBytes,
    required int maxBytes,
  }) {
    validateMaxBytes(maxBytes);
    if (receivedBytes < 0 || chunkBytes < 0 || receivedBytes > maxBytes) {
      return false;
    }
    return chunkBytes <= maxBytes - receivedBytes;
  }
}

class RoutingEndpointPolicy {
  const RoutingEndpointPolicy._();

  static const cleartextAllowedHosts = {
    'localhost',
    '127.0.0.1',
    '10.0.2.2',
  };

  /// Accepts only absolute HTTP(S) URLs without embedded credentials or
  /// fragments. Explicit `http` is admitted only for the loopback names carved
  /// out by Android's network security configuration.
  static RoutingEndpointDecision graphHopperDecision(String server) {
    try {
      final uri = Uri.parse(server);
      final scheme = uri.scheme.toLowerCase();
      final host = uri.host.toLowerCase();
      final schemeDelimiter = server.indexOf('://');
      var hasRawUserInfo = false;
      if (schemeDelimiter >= 0) {
        final authorityStart = schemeDelimiter + 3;
        var authorityEnd = server.length;
        for (final separator in const ['/', '?', '#']) {
          final index = server.indexOf(separator, authorityStart);
          if (index >= authorityStart && index < authorityEnd) {
            authorityEnd = index;
          }
        }
        hasRawUserInfo =
            server.substring(authorityStart, authorityEnd).contains('@');
      }
      if (host.isEmpty ||
          (scheme != 'http' && scheme != 'https') ||
          hasRawUserInfo ||
          uri.hasFragment ||
          (uri.hasPort && (uri.port < 1 || uri.port > 65535))) {
        return RoutingEndpointDecision.invalid;
      }
      if (scheme == 'http' && !cleartextAllowedHosts.contains(host)) {
        return RoutingEndpointDecision.cleartextRejected;
      }
      return RoutingEndpointDecision.accepted;
    } on FormatException {
      return RoutingEndpointDecision.invalid;
    }
  }
}
