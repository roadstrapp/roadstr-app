import 'dart:convert';
import 'dart:math' as math;

import 'package:crypto/crypto.dart';

import '../models/favorite_place.dart';
import 'nostr_protocol_codec.dart';

/// Deterministic NIP-78 wire/privacy policy around the NIP-44 and signer
/// adapters used by [FavoritesSyncService].
abstract final class FavoritesSyncProtocol {
  static const defaultRelays = [
    'wss://relay.damus.io',
    'wss://nos.lol',
    'wss://purplerelay.com',
  ];
  static const legacyDTag = 'roadstr-favorites';
  static const kind = 30078;
  static const maxContentChars = 200000;
  static const maxPlaintextBytes = 65535;
  static const padBucket = 4096;

  static String? normaliseRelayUrl(String input) {
    final trimmed = input.trim();
    if (trimmed.isEmpty || trimmed.length > 200) return null;
    final uri = Uri.tryParse(trimmed);
    if (uri == null ||
        uri.scheme != 'wss' ||
        uri.host.isEmpty ||
        !uri.host.contains('.') ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment) {
      return null;
    }
    final path = uri.path == '/' ? '' : uri.path;
    final normalised = uri.replace(path: path).toString();
    return defaultRelays.contains(normalised) ? null : normalised;
  }

  static String hashedDTag(String pubKeyHex) =>
      sha256.convert(utf8.encode('$legacyDTag:$pubKeyHex')).toString();

  static String encodeFavorites(List<FavoritePlace> favorites) =>
      jsonEncode(favorites.map((favorite) => favorite.toMap()).toList());

  static String wrapPassphraseEnvelope(Map<String, dynamic> encrypted) =>
      jsonEncode({
        'v': 1,
        'encrypted': true,
        ...encrypted,
      });

  static String padToBucket(String value) {
    final length = utf8.encode(value).length;
    if (length > maxPlaintextBytes) {
      throw ArgumentError.value(
        length,
        'value',
        'NIP-44 plaintext exceeds 65535 bytes',
      );
    }
    if (length == maxPlaintextBytes) return value;
    var target = ((length + padBucket - 1) ~/ padBucket) * padBucket;
    target = math.min(target, maxPlaintextBytes);
    return value + ' ' * (target - length);
  }

  static int nextCreatedAt({
    required int nowUnixSeconds,
    required int lastCreatedAt,
  }) {
    final hourStart = nowUnixSeconds - nowUnixSeconds % 3600;
    return math.max(hourStart, lastCreatedAt + 1);
  }

  static NostrEventDraft snapshotDraft({
    required String pubkey,
    required int createdAt,
    required String encryptedContent,
  }) =>
      NostrEventDraft(
        pubkey: pubkey,
        createdAt: createdAt,
        kind: kind,
        tags: [
          ['d', hashedDTag(pubkey)],
        ],
        content: encryptedContent,
      );

  static NostrEventDraft legacyWipeDraft({
    required String pubkey,
    required int createdAt,
  }) =>
      NostrEventDraft(
        pubkey: pubkey,
        createdAt: createdAt,
        kind: kind,
        tags: const [
          ['d', legacyDTag],
        ],
        content: '',
      );

  static NostrEventDraft legacyDeletionDraft({
    required String pubkey,
    required int createdAt,
  }) =>
      NostrEventDraft(
        pubkey: pubkey,
        createdAt: createdAt,
        kind: 5,
        tags: [
          ['a', '$kind:$pubkey:$legacyDTag'],
        ],
        content: '',
      );

  static List<Object?> fetchRequest({
    required String subscriptionId,
    required String pubkey,
    required String dTag,
  }) =>
      [
        'REQ',
        subscriptionId,
        {
          'kinds': [kind],
          'authors': [pubkey],
          '#d': [dTag],
          'limit': 1,
        }
      ];

  /// Checks cheap content/author/kind/tag bindings before invoking Schnorr.
  static bool snapshotEventIsBound(
    Map<String, dynamic> event, {
    required String pubkey,
    required String dTag,
    required bool Function() verifySignature,
  }) {
    try {
      final content = event['content'];
      if (content is String && content.length > maxContentChars) return false;
      final tags = (event['tags'] as List?) ?? const [];
      final dMatches = tags.any((tag) =>
          tag is List && tag.length >= 2 && tag[0] == 'd' && tag[1] == dTag);
      return event['pubkey'] == pubkey &&
          event['kind'] == kind &&
          dMatches &&
          verifySignature();
    } catch (_) {
      return false;
    }
  }

  /// First event wins ties, matching the shipped relay-order reduction.
  static Map<String, dynamic>? newestSnapshot(
    Iterable<Map<String, dynamic>?> events,
  ) {
    Map<String, dynamic>? best;
    for (final event in events) {
      if (event == null) continue;
      if (best == null ||
          (event['created_at'] as int? ?? 0) >
              (best['created_at'] as int? ?? 0)) {
        best = event;
      }
    }
    return best;
  }

  static bool passesRollbackGuard({
    required int fetchedCreatedAt,
    required int? lastCreatedAt,
  }) =>
      lastCreatedAt == null || fetchedCreatedAt >= lastCreatedAt;
}
