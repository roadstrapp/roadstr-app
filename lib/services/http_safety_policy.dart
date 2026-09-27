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

  /// Mirrors the shipped GraphHopper validator exactly: URLs need a host, and
  /// explicit `http` is admitted only for the loopback names carved out by
  /// Android's network security configuration. Other schemes and user-info are
  /// currently left to the HTTP client and are fixture-locked for compatibility
  /// until a separately approved tightening changes that behavior.
  static RoutingEndpointDecision graphHopperDecision(String server) {
    final uri = Uri.tryParse(server);
    if (uri == null || uri.host.isEmpty) {
      return RoutingEndpointDecision.invalid;
    }
    if (uri.scheme == 'http' && !cleartextAllowedHosts.contains(uri.host)) {
      return RoutingEndpointDecision.cleartextRejected;
    }
    return RoutingEndpointDecision.accepted;
  }
}
