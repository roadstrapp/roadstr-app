// Encrypted, cross-device sync of saved favorites via Nostr.
//
// Favorites are serialised to JSON, optionally wrapped in a passphrase layer
// (PBKDF2 + AES-256-GCM — see FavoritesCrypto), padded to a coarse size
// bucket, encrypted end-to-end with NIP-44 (self-encrypted: sender and
// recipient are the same pubkey, so only the holder of the matching privkey
// can ever decrypt it), and published as a kind-30078 ("arbitrary app data",
// NIP-78) parameterized-replaceable event. Re-publishing with the same 'd'
// tag overwrites the previous snapshot.
//
// Threat model — favorites are home/work addresses, i.e. the most
// wrench-attack-sensitive data a Nostr+Bitcoin user can publish. Relays are
// assumed malicious. Defenses, layer by layer:
//
//  • CONTENT: NIP-44 ciphertext only. No name, address or coordinate ever
//    appears in tags or content. Decryption key = the user's own nsec,
//    never on any relay.
//  • PASSPHRASE (optional second factor): with a sync passphrase set, the
//    plaintext is first sealed with PBKDF2+AES-GCM. Even a full nsec
//    compromise (phishing, malicious client) is then NOT enough to decrypt
//    the snapshot an attacker fetched or archived from relays.
//  • ENUMERATION: the 'd' tag is sha256("roadstr-favorites:"+pubkey), unique
//    per user, so a single REQ {#d:["roadstr-favorites"]} can no longer list
//    every Roadstr user on a relay (previously: a one-query shopping list of
//    npubs that drive and probably hold bitcoin). Honest limit: the tag is
//    derived from public info, so a TARGETED "does npub X use Roadstr?"
//    check is still computable — a per-user secret can't be shared across
//    devices on the Amber path, where the app never sees the nsec.
//  • SIZE: plaintext is padded with trailing spaces (JSON-legal) to a 4 KiB
//    bucket before encryption, hiding the favorites count and its growth
//    over time from ciphertext-length analysis (NIP-44's own padding is too
//    fine-grained to hide either).
//  • TIMING: created_at is rounded down to the start of the UTC hour (with
//    a persisted monotonic bump so replaceable-event ordering survives),
//    instead of broadcasting the exact second the user edited favorites.
//  • ROLLBACK: pull queries ALL relays in parallel and keeps the newest
//    verified snapshot; a persisted high-water mark rejects anything older
//    than what this device has already seen or published, so a malicious
//    relay cannot resurrect deleted favorites or restore a stale address by
//    replaying an old, validly-signed event.
//  • RELAY SET: relay.nostr.band was removed — it feeds a public search
//    indexer, the single worst place to park privacy-sensitive events.
import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:amberflutter/amberflutter.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:hive/hive.dart';
import 'package:nostr_tools/nostr_tools.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../models/favorite_place.dart';
import 'favorites_crypto.dart';
import 'nip44.dart';
import 'nostr_event_verify.dart';

/// Result of [FavoritesSyncService.pull].
class FavSyncPull {
  final List<FavoritePlace>? favorites;

  /// True when a snapshot exists but is sealed with a sync passphrase that
  /// was not provided (or was wrong) — the caller should prompt and retry.
  final bool needsPassphrase;

  const FavSyncPull.ok(List<FavoritePlace> this.favorites)
      : needsPassphrase = false;
  const FavSyncPull.none() : favorites = null, needsPassphrase = false;
  const FavSyncPull.locked() : favorites = null, needsPassphrase = true;
}

class FavoritesSyncService {
  // NB: relay.nostr.band deliberately absent — see threat model above.
  static const _relays = [
    'wss://relay.damus.io',
    'wss://nos.lol',
  ];
  static const _legacyDTag = 'roadstr-favorites';
  static const kind = 30078;

  /// Max accepted event content length on pull. Legit content is ≤ 65535
  /// plaintext bytes → ~88 KB of base64; anything bigger is a hostile relay
  /// trying to waste memory/CPU and is dropped before hashing/verification.
  static const _maxContentChars = 200000;

  /// Plaintext size bucket (bytes). Every snapshot is padded up to a
  /// multiple of this before encryption, capped at NIP-44's 65535 limit.
  static const _padBucket = 4096;

  // Hive keys (encrypted settings box).
  static const _kLastTs = 'fav_sync_last_ts';
  static const _kLegacyCleaned = 'fav_sync_legacy_cleaned';

  final _eventApi = EventApi();

  Box get _box => Hive.box('settings');

  /// Per-user 'd' tag — see ENUMERATION in the threat model above.
  static String hashedDTag(String pubKeyHex) =>
      sha256.convert(utf8.encode('$_legacyDTag:$pubKeyHex')).toString();

  /// Publishes [favorites], NIP-44 encrypted (optionally passphrase-wrapped),
  /// to all configured relays. Returns true if at least one relay accepted.
  Future<bool> push({
    required List<FavoritePlace> favorites,
    required String pubKeyHex,
    String? privKeyHex, // null when signing via Amber
    String? passphrase, // optional second encryption factor
  }) async {
    var plaintext = jsonEncode(favorites.map((f) => f.toMap()).toList());
    if (passphrase != null && passphrase.isNotEmpty) {
      plaintext = jsonEncode({
        'v': 1,
        'encrypted': true,
        ...FavoritesCrypto.encrypt(plaintext, passphrase),
      });
    }
    final encrypted =
        await _encrypt(padToBucket(plaintext), pubKeyHex, privKeyHex);
    if (encrypted == null) return false;

    final ts = _nextCreatedAt();
    final signedJson = await _signEvent(
      pubKeyHex: pubKeyHex,
      privKeyHex: privKeyHex,
      kind: kind,
      tags: [
        ['d', hashedDTag(pubKeyHex)],
      ],
      content: encrypted,
      createdAt: ts,
    );
    if (signedJson == null) return false;

    var anyOk = false;
    await Future.wait(_relays.map((url) async {
      if (await _publishOne(url, signedJson)) anyOk = true;
    }));
    if (anyOk) {
      await _box.put(_kLastTs, ts);
      // One-time hygiene: wipe + request deletion of the old fingerprintable
      // 'roadstr-favorites' event. nsec path only — on Amber each event is an
      // extra signing popup, and the cleanup is best-effort anyway (archiving
      // relays keep history regardless).
      if (privKeyHex != null &&
          _box.get(_kLegacyCleaned, defaultValue: false) != true) {
        await _cleanupLegacy(pubKeyHex, privKeyHex);
        await _box.put(_kLegacyCleaned, true);
      }
    }
    return anyOk;
  }

  /// Fetches, verifies and decrypts the newest favorites snapshot across all
  /// relays. Anti-rollback: snapshots older than the locally persisted
  /// high-water mark are ignored.
  Future<FavSyncPull> pull({
    required String pubKeyHex,
    String? privKeyHex,
    String? passphrase,
  }) async {
    var best = await _fetchNewest(pubKeyHex, hashedDTag(pubKeyHex));
    // Migration: fall back to the legacy tag for snapshots pushed by
    // pre-hashed-d versions of the app.
    best ??= await _fetchNewest(pubKeyHex, _legacyDTag);
    if (best == null) return const FavSyncPull.none();

    final fetchedTs = best['created_at'] as int? ?? 0;
    final lastTs = _box.get(_kLastTs) as int?;
    if (lastTs != null && fetchedTs < lastTs) {
      // Older than what this device already saw/published → stale relay or
      // deliberate replay of an outdated (validly signed) snapshot.
      return const FavSyncPull.none();
    }

    final content = best['content'] as String?;
    if (content == null || content.isEmpty) return const FavSyncPull.none();
    final plaintext = await _decrypt(content, pubKeyHex, privKeyHex);
    if (plaintext == null) return const FavSyncPull.none();

    try {
      var decoded = jsonDecode(plaintext);
      if (decoded is Map && decoded['encrypted'] == true) {
        // Passphrase-wrapped snapshot.
        if (passphrase == null || passphrase.isEmpty) {
          return const FavSyncPull.locked();
        }
        try {
          decoded = jsonDecode(FavoritesCrypto.decrypt(
              decoded.cast<String, dynamic>(), passphrase));
        } on FavoritesDecryptException {
          return const FavSyncPull.locked(); // wrong passphrase → re-prompt
        }
      }
      if (decoded is! List) return const FavSyncPull.none();
      final favs = decoded
          .whereType<Map>()
          .map((m) => FavoritePlace.fromMapSafe(m))
          .whereType<FavoritePlace>()
          .toList();
      await _box.put(_kLastTs, fetchedTs);
      return FavSyncPull.ok(favs);
    } on FormatException {
      return const FavSyncPull.none();
    }
  }

  // ── privacy helpers ──────────────────────────────────────────────────────

  /// Pads [s] with trailing spaces (legal after any top-level JSON value) to
  /// the next [_padBucket] multiple, so ciphertext length no longer tracks
  /// the favorites count. Capped at NIP-44's 65535-byte plaintext limit.
  @visibleForTesting
  static String padToBucket(String s) {
    final len = utf8.encode(s).length;
    if (len >= 65535) return s;
    var target = ((len + _padBucket - 1) ~/ _padBucket) * _padBucket;
    target = math.min(target, 65535);
    return s + ' ' * (target - len);
  }

  /// created_at = start of the current UTC hour, monotonically bumped above
  /// the persisted high-water mark so a same-hour re-publish (or a publish
  /// right after a pull) still wins replaceable-event ordering.
  int _nextCreatedAt() {
    final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
    final hourStart = now - now % 3600;
    final last = _box.get(_kLastTs) as int? ?? 0;
    return math.max(hourStart, last + 1);
  }

  /// Best-effort removal of the legacy fixed-'d' event: overwrite its content
  /// with an empty replaceable event, then publish a NIP-09 deletion request
  /// for the whole replaceable address.
  Future<void> _cleanupLegacy(String pubKeyHex, String privKeyHex) async {
    final wipe = await _signEvent(
      pubKeyHex: pubKeyHex,
      privKeyHex: privKeyHex,
      kind: kind,
      tags: [
        ['d', _legacyDTag],
      ],
      content: '',
      createdAt: _nextCreatedAt(),
    );
    final del = await _signEvent(
      pubKeyHex: pubKeyHex,
      privKeyHex: privKeyHex,
      kind: 5,
      tags: [
        ['a', '$kind:$pubKeyHex:$_legacyDTag'],
      ],
      content: '',
      createdAt: _nextCreatedAt(),
    );
    await Future.wait(_relays.map((url) async {
      if (wipe != null) await _publishOne(url, wipe);
      if (del != null) await _publishOne(url, del);
    }));
  }

  // ── signing (nsec locally; Amber via NIP-55) ─────────────────────────────

  Future<Map<String, dynamic>?> _signEvent({
    required String pubKeyHex,
    required String? privKeyHex,
    required int kind,
    required List<List<String>> tags,
    required String content,
    required int createdAt,
  }) async {
    final unsigned = Event(
      pubkey: pubKeyHex,
      created_at: createdAt,
      kind: kind,
      tags: tags,
      content: content,
    );
    unsigned.id = _eventApi.getEventHash(unsigned);
    if (privKeyHex != null) {
      return _eventApi.finishEvent(unsigned, privKeyHex).toJson();
    }
    try {
      final result = await Amberflutter().signEvent(
        eventJson: jsonEncode(unsigned.toJson()),
        currentUser: pubKeyHex,
      );
      final sig = result['signature'] as String?;
      if (sig == null || sig.isEmpty) return null;
      return unsigned.toJson()..['sig'] = sig;
    } catch (_) {
      return null;
    }
  }

  // ── encryption (nsec: local NIP-44; Amber: NIP-55 nip44_encrypt/decrypt) ──

  Future<String?> _encrypt(String plaintext, String pubKeyHex, String? privKeyHex) async {
    if (privKeyHex != null) return Nip44.encrypt(privKeyHex, pubKeyHex, plaintext);
    try {
      final result = await Amberflutter().nip44Encrypt(
        plaintext: plaintext, currentUser: pubKeyHex, pubKey: pubKeyHex);
      final out = result['signature'] as String?;
      return (out != null && out.isNotEmpty) ? out : null;
    } catch (_) {
      return null;
    }
  }

  Future<String?> _decrypt(String ciphertext, String pubKeyHex, String? privKeyHex) async {
    if (privKeyHex != null) {
      try {
        return Nip44.decrypt(privKeyHex, pubKeyHex, ciphertext);
      } catch (_) {
        return null;
      }
    }
    try {
      final result = await Amberflutter().nip44Decrypt(
        ciphertext: ciphertext, currentUser: pubKeyHex, pubKey: pubKeyHex);
      final out = result['signature'] as String?;
      return (out != null && out.isNotEmpty) ? out : null;
    } catch (_) {
      return null;
    }
  }

  // ── relay I/O (short-lived one-off connections, mirrors fetchProfile) ────

  /// Queries every relay in parallel and returns the newest verified event,
  /// so one stale (or lying) relay can never shadow a newer snapshot held by
  /// an honest one.
  Future<Map<String, dynamic>?> _fetchNewest(
      String pubKeyHex, String dTag) async {
    final results = await Future.wait(
        _relays.map((url) => _fetchLatest(url, pubKeyHex, dTag)));
    Map<String, dynamic>? best;
    for (final r in results) {
      if (r == null) continue;
      if (best == null ||
          (r['created_at'] as int? ?? 0) > (best['created_at'] as int? ?? 0)) {
        best = r;
      }
    }
    return best;
  }

  Future<bool> _publishOne(String url, Map<String, dynamic> eventJson) async {
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(url));
      final completer = Completer<bool>();
      ws.stream.listen((raw) {
        if (completer.isCompleted) return;
        try {
          final msg = jsonDecode(raw as String) as List;
          if (msg[0] == 'OK') completer.complete(msg[2] == true);
        } catch (_) {}
      },
          onError: (_) { if (!completer.isCompleted) completer.complete(false); },
          onDone:  () { if (!completer.isCompleted) completer.complete(false); });
      ws.sink.add(jsonEncode(['EVENT', eventJson]));
      return await completer.future
          .timeout(const Duration(seconds: 6), onTimeout: () => false);
    } catch (_) {
      return false;
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }

  Future<Map<String, dynamic>?> _fetchLatest(
      String url, String pubKeyHex, String dTag) async {
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(url));
      final completer = Completer<Map<String, dynamic>?>();
      final subId = randomSubId();
      ws.stream.listen((raw) {
        if (completer.isCompleted) return;
        try {
          final msg = jsonDecode(raw as String) as List;
          if (msg[0] == 'EVENT' && msg[1] == subId) {
            final json = (msg[2] as Map).cast<String, dynamic>();
            // Relays are untrusted. Before using anything they send:
            // (1) cheap size guard — drop grotesquely oversized content
            //     before spending CPU hashing it;
            // (2) the author must be exactly us — a relay could ignore the
            //     REQ's authors filter and return someone else's event;
            // (3) kind and 'd' must match what was asked — a relay could
            //     answer with a different (validly signed) event of ours;
            // (4) verify id + signature.
            final content = json['content'];
            if (content is String && content.length > _maxContentChars) return;
            final tags = (json['tags'] as List?) ?? const [];
            final dMatches = tags.any((t) =>
                t is List && t.length >= 2 && t[0] == 'd' && t[1] == dTag);
            if (json['pubkey'] == pubKeyHex &&
                json['kind'] == kind &&
                dMatches &&
                _verifyEvent(json)) {
              completer.complete(json);
            }
          } else if (msg[0] == 'EOSE' && msg[1] == subId) {
            if (!completer.isCompleted) completer.complete(null);
          }
        } catch (_) {
          if (!completer.isCompleted) completer.complete(null);
        }
      },
          onError: (_) { if (!completer.isCompleted) completer.complete(null); },
          onDone:  () { if (!completer.isCompleted) completer.complete(null); });
      ws.sink.add(jsonEncode(['REQ', subId, {
        'kinds': [kind], 'authors': [pubKeyHex], '#d': [dTag], 'limit': 1,
      }]));
      return await completer.future
          .timeout(const Duration(seconds: 6), onTimeout: () => null);
    } catch (_) {
      return null;
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }

  bool _verifyEvent(Map<String, dynamic> json) => verifyEventJson(json);
}
