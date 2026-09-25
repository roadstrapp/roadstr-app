import 'dart:convert';

import 'package:crypto/crypto.dart';

/// Immutable input to the NIP-01 event-id calculation.
///
/// Signatures deliberately do not live here: this type is the deterministic
/// boundary shared by the local nsec signer, Amber and the native rewrite.
class NostrEventDraft {
  NostrEventDraft({
    required this.pubkey,
    required this.createdAt,
    required this.kind,
    required List<List<String>> tags,
    required this.content,
  }) : tags = List.unmodifiable(
          tags.map((tag) => List<String>.unmodifiable(tag)),
        );

  final String pubkey;
  final int createdAt;
  final int kind;
  final List<List<String>> tags;
  final String content;

  /// Exact NIP-01 serialization hashed to obtain an event id.
  String get canonicalJson => jsonEncode([
        0,
        pubkey,
        createdAt,
        kind,
        tags,
        content,
      ]);

  String get id => sha256.convert(utf8.encode(canonicalJson)).toString();

  /// Field order matches `nostr_tools`' Event.toJson(), including the empty
  /// signature expected by Amber's unsigned-event API.
  Map<String, dynamic> toJson({String signature = ''}) => {
        'id': id,
        'pubkey': pubkey,
        'created_at': createdAt,
        'kind': kind,
        'tags': tags.map((tag) => tag.toList(growable: false)).toList(),
        'content': content,
        'sig': signature,
      };
}

/// Rebuilds the deterministic portion of a relay event.
///
/// Throws for malformed field types. Callers at an untrusted boundary should
/// catch the error and reject the event, as [verifyEventJson] already does.
NostrEventDraft nostrEventDraftFromJson(Map<String, dynamic> json) =>
    NostrEventDraft(
      pubkey: json['pubkey'] as String,
      createdAt: json['created_at'] as int,
      kind: json['kind'] as int,
      tags: (json['tags'] as List)
          .map((tag) => List<String>.from(tag as List))
          .toList(),
      content: json['content'] as String? ?? '',
    );

/// Geohash encoder used by both report tags and area subscriptions.
String roadstrGeohash(double latitude, double longitude, int precision) {
  const alphabet = '0123456789bcdefghjkmnpqrstuvwxyz';
  var minLat = -90.0, maxLat = 90.0;
  var minLon = -180.0, maxLon = 180.0;
  var isLon = true, bits = 0, count = 0;
  final result = StringBuffer();
  while (result.length < precision) {
    if (isLon) {
      final middle = (minLon + maxLon) / 2;
      if (longitude >= middle) {
        bits = (bits << 1) | 1;
        minLon = middle;
      } else {
        bits <<= 1;
        maxLon = middle;
      }
    } else {
      final middle = (minLat + maxLat) / 2;
      if (latitude >= middle) {
        bits = (bits << 1) | 1;
        minLat = middle;
      } else {
        bits <<= 1;
        maxLat = middle;
      }
    }
    isLon = !isLon;
    if (++count == 5) {
      result.write(alphabet[bits]);
      bits = 0;
      count = 0;
    }
  }
  return result.toString();
}

/// Exact Roadstr event layouts layered on top of NIP-01.
abstract final class RoadstrNostrEvents {
  static NostrEventDraft report({
    required String pubkey,
    required int createdAt,
    required double latitude,
    required double longitude,
    required String category,
    required int expiresAt,
    required String content,
    int? speedLimit,
  }) =>
      NostrEventDraft(
        pubkey: pubkey,
        createdAt: createdAt,
        kind: 1315,
        tags: [
          ['lat', latitude.toStringAsFixed(6)],
          ['lon', longitude.toStringAsFixed(6)],
          ['g', roadstrGeohash(latitude, longitude, 4)],
          ['g', roadstrGeohash(latitude, longitude, 5)],
          ['g', roadstrGeohash(latitude, longitude, 6)],
          ['t', category],
          ['expiration', '$expiresAt'],
          if (speedLimit != null) ['maxspeed', '$speedLimit'],
        ],
        content: content,
      );

  static NostrEventDraft vote({
    required String pubkey,
    required int createdAt,
    required String eventId,
    required bool stillThere,
  }) =>
      NostrEventDraft(
        pubkey: pubkey,
        createdAt: createdAt,
        kind: 1316,
        tags: [
          ['e', eventId],
          ['status', stillThere ? 'still_there' : 'no_longer_there'],
        ],
        content: '',
      );

  static NostrEventDraft update({
    required String ownerPubkey,
    required int createdAt,
    required String eventId,
    required int speedLimit,
    required double latitude,
    required double longitude,
    required String content,
    String? requestId,
  }) {
    _requireSpeedLimit(speedLimit);
    return NostrEventDraft(
      pubkey: ownerPubkey,
      createdAt: createdAt,
      kind: 1317,
      tags: [
        ['e', eventId],
        ['p', ownerPubkey],
        ['g', roadstrGeohash(latitude, longitude, 4)],
        ['g', roadstrGeohash(latitude, longitude, 5)],
        ['g', roadstrGeohash(latitude, longitude, 6)],
        ['maxspeed', '$speedLimit'],
        if (requestId != null) ['request', requestId],
      ],
      content: content,
    );
  }

  static NostrEventDraft editRequest({
    required String requesterPubkey,
    required String ownerPubkey,
    required int createdAt,
    required String eventId,
    required int speedLimit,
    required double latitude,
    required double longitude,
  }) {
    _requireSpeedLimit(speedLimit);
    return NostrEventDraft(
      pubkey: requesterPubkey,
      createdAt: createdAt,
      kind: 1318,
      tags: [
        ['e', eventId],
        ['p', ownerPubkey],
        ['g', roadstrGeohash(latitude, longitude, 4)],
        ['g', roadstrGeohash(latitude, longitude, 5)],
        ['g', roadstrGeohash(latitude, longitude, 6)],
        ['maxspeed', '$speedLimit'],
      ],
      content: '',
    );
  }

  static NostrEventDraft profileVisibility({
    required String pubkey,
    required int createdAt,
    required bool isPublic,
  }) =>
      NostrEventDraft(
        pubkey: pubkey,
        createdAt: createdAt,
        kind: 30078,
        tags: const [
          ['d', 'roadstr-profile-visibility'],
          ['client', 'roadstr'],
        ],
        content: jsonEncode({'public': isPublic}),
      );

  static void _requireSpeedLimit(int value) {
    if (value < 5 || value > 300) {
      throw const FormatException('Invalid speed limit');
    }
  }
}

/// NIP-01 messages whose field and filter order are part of Roadstr parity.
abstract final class NostrRelayWire {
  static List<Object?> publish(Map<String, dynamic> event) => ['EVENT', event];

  static List<Object?> areaRequest({
    required String subscriptionId,
    required List<String> geohashes,
    required int now,
  }) =>
      [
        'REQ',
        subscriptionId,
        {
          'kinds': [1315, 1317, 1318],
          '#g': List<String>.from(geohashes),
          'since': now - 30 * 86400,
          'limit': 500,
        }
      ];

  static List<Object?> confirmationRequest({
    required String subscriptionId,
    required List<String> eventIds,
    required int now,
  }) =>
      [
        'REQ',
        subscriptionId,
        {
          'kinds': [1316],
          '#e': List<String>.from(eventIds),
          'since': now - 30 * 86400,
          'limit': 1000,
        }
      ];

  static List<Object?> close(String subscriptionId) =>
      ['CLOSE', subscriptionId];

  static String encode(List<Object?> message) => jsonEncode(message);
}
