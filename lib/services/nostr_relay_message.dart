import 'dart:convert';

import '../config/network_config.dart';

enum NostrRelayDecodeFailure {
  nonString,
  tooLong,
  tooDeep,
  malformedJson,
  invalidEnvelope,
  unsupportedType,
}

class NostrRelayDecodeResult {
  const NostrRelayDecodeResult._({this.message, this.failure});

  const NostrRelayDecodeResult.accepted(NostrRelayMessage message)
      : this._(message: message);

  const NostrRelayDecodeResult.rejected(NostrRelayDecodeFailure failure)
      : this._(failure: failure);

  final NostrRelayMessage? message;
  final NostrRelayDecodeFailure? failure;

  bool get isAccepted => message != null;
}

sealed class NostrRelayMessage {
  const NostrRelayMessage();
}

final class NostrRelayEventMessage extends NostrRelayMessage {
  NostrRelayEventMessage({
    required this.subscriptionId,
    required Map<String, dynamic> event,
  }) : event = Map.unmodifiable(event);

  final String subscriptionId;
  final Map<String, dynamic> event;
}

final class NostrRelayEoseMessage extends NostrRelayMessage {
  const NostrRelayEoseMessage(this.subscriptionId);

  final String subscriptionId;
}

final class NostrRelayOkMessage extends NostrRelayMessage {
  const NostrRelayOkMessage({
    required this.eventId,
    required this.accepted,
    required this.reason,
  });

  final String eventId;

  /// Null means the relay supplied a malformed non-boolean status. Existing
  /// publication behavior treats that status as non-acceptance.
  final bool? accepted;
  final Object? reason;
}

final class NostrRelayNoticeMessage extends NostrRelayMessage {
  const NostrRelayNoticeMessage(this.detail);

  final Object? detail;
}

final class NostrRelayClosedMessage extends NostrRelayMessage {
  const NostrRelayClosedMessage(this.subscriptionId, this.detail);

  final String subscriptionId;
  final Object? detail;
}

final class NostrRelayAuthMessage extends NostrRelayMessage {
  const NostrRelayAuthMessage(this.challenge);

  final String challenge;
}

/// Bounded structural decoder for relay-to-client NIP-01/NIP-42 frames.
///
/// Signature verification and subscription/kind authorization deliberately
/// remain caller responsibilities. This boundary only decides whether an
/// untrusted frame is small, shallow and structurally usable.
abstract final class NostrRelayMessageDecoder {
  static const maxFrameUtf16CodeUnits = RelayLimits.maxInboundMessageChars;
  static const maxJsonNestingDepth = 64;

  static NostrRelayDecodeResult decode(Object? raw) {
    if (raw is! String) {
      return const NostrRelayDecodeResult.rejected(
        NostrRelayDecodeFailure.nonString,
      );
    }
    if (raw.length > maxFrameUtf16CodeUnits) {
      return const NostrRelayDecodeResult.rejected(
        NostrRelayDecodeFailure.tooLong,
      );
    }
    if (_exceedsNestingLimit(raw)) {
      return const NostrRelayDecodeResult.rejected(
        NostrRelayDecodeFailure.tooDeep,
      );
    }

    Object? decoded;
    try {
      decoded = jsonDecode(raw);
    } catch (_) {
      return const NostrRelayDecodeResult.rejected(
        NostrRelayDecodeFailure.malformedJson,
      );
    }
    if (decoded is! List || decoded.isEmpty || decoded[0] is! String) {
      return const NostrRelayDecodeResult.rejected(
        NostrRelayDecodeFailure.invalidEnvelope,
      );
    }

    try {
      return switch (decoded[0] as String) {
        'EVENT' => _event(decoded),
        'EOSE' => _eose(decoded),
        'OK' => _ok(decoded),
        'NOTICE' => NostrRelayDecodeResult.accepted(
            NostrRelayNoticeMessage(
              decoded.length > 1 ? decoded[1] : '',
            ),
          ),
        'CLOSED' => _closed(decoded),
        'AUTH' => _auth(decoded),
        _ => const NostrRelayDecodeResult.rejected(
            NostrRelayDecodeFailure.unsupportedType,
          ),
      };
    } catch (_) {
      return const NostrRelayDecodeResult.rejected(
        NostrRelayDecodeFailure.invalidEnvelope,
      );
    }
  }

  static NostrRelayDecodeResult _event(List<dynamic> values) {
    if (values.length < 3 || values[1] is! String || values[2] is! Map) {
      return const NostrRelayDecodeResult.rejected(
        NostrRelayDecodeFailure.invalidEnvelope,
      );
    }
    return NostrRelayDecodeResult.accepted(
      NostrRelayEventMessage(
        subscriptionId: values[1] as String,
        event: (values[2] as Map).cast<String, dynamic>(),
      ),
    );
  }

  static NostrRelayDecodeResult _eose(List<dynamic> values) {
    if (values.length < 2 || values[1] is! String) {
      return const NostrRelayDecodeResult.rejected(
        NostrRelayDecodeFailure.invalidEnvelope,
      );
    }
    return NostrRelayDecodeResult.accepted(
      NostrRelayEoseMessage(values[1] as String),
    );
  }

  static NostrRelayDecodeResult _ok(List<dynamic> values) {
    if (values.length < 3 || values[1] is! String) {
      return const NostrRelayDecodeResult.rejected(
        NostrRelayDecodeFailure.invalidEnvelope,
      );
    }
    final status = values[2];
    return NostrRelayDecodeResult.accepted(
      NostrRelayOkMessage(
        eventId: values[1] as String,
        accepted: status is bool ? status : null,
        reason: values.length > 3 ? values[3] : null,
      ),
    );
  }

  static NostrRelayDecodeResult _closed(List<dynamic> values) {
    if (values.length < 3 || values[1] is! String) {
      return const NostrRelayDecodeResult.rejected(
        NostrRelayDecodeFailure.invalidEnvelope,
      );
    }
    return NostrRelayDecodeResult.accepted(
      NostrRelayClosedMessage(values[1] as String, values[2]),
    );
  }

  static NostrRelayDecodeResult _auth(List<dynamic> values) {
    if (values.length < 2 || values[1] is! String) {
      return const NostrRelayDecodeResult.rejected(
        NostrRelayDecodeFailure.invalidEnvelope,
      );
    }
    return NostrRelayDecodeResult.accepted(
      NostrRelayAuthMessage(values[1] as String),
    );
  }

  /// Scans before `jsonDecode` so hostile nesting cannot consume an
  /// implementation-dependent amount of parser stack. Brackets inside JSON
  /// strings are ignored; malformed quoting is still classified by jsonDecode.
  static bool _exceedsNestingLimit(String input) {
    var depth = 0;
    var inString = false;
    var escaped = false;
    for (final codeUnit in input.codeUnits) {
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (codeUnit == 0x5c) {
          escaped = true;
        } else if (codeUnit == 0x22) {
          inString = false;
        }
        continue;
      }
      if (codeUnit == 0x22) {
        inString = true;
      } else if (codeUnit == 0x5b || codeUnit == 0x7b) {
        depth++;
        if (depth > maxJsonNestingDepth) return true;
      } else if (codeUnit == 0x5d || codeUnit == 0x7d) {
        if (depth > 0) depth--;
      }
    }
    return false;
  }
}
