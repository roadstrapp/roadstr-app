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
//    devices on the Amber path, where the app never sees the nsec. Second
//    honest limit: the *shape* of the tag is itself a hint. Apps using NIP-78
//    normally pick a readable 'd' ("myapp-settings"), so a 64-char hex one on
//    kind 30078 stands out — a relay can list that pattern and get a set of
//    likely Roadstr users without knowing any pubkey. The value is protected;
//    the fact that the app was used is only obscured, not hidden.
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

import 'package:amberflutter/amberflutter.dart';
import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:hive/hive.dart';
import 'package:nostr_tools/nostr_tools.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../models/favorite_place.dart';
import 'favorites_crypto.dart';
import 'favorites_sync_protocol.dart';
import 'nip44.dart';
import 'nostr_event_verify.dart';
import 'nostr_protocol_codec.dart';
import 'nostr_relay_ingress.dart';
import 'nostr_relay_message.dart';

/// Result of [FavoritesSyncService.pull].
class FavSyncPull {
  final List<FavoritePlace>? favorites;

  /// True when a snapshot exists but is sealed with a sync passphrase that
  /// was not provided (or was wrong) — the caller should prompt and retry.
  final bool needsPassphrase;

  const FavSyncPull.ok(List<FavoritePlace> this.favorites)
      : needsPassphrase = false;
  const FavSyncPull.none()
      : favorites = null,
        needsPassphrase = false;
  const FavSyncPull.locked()
      : favorites = null,
        needsPassphrase = true;
}

class FavoritesSyncService {
  /// Two relays were not enough for the one thing in this app the user cannot
  /// regenerate. Road events expire and the community re-reports them;
  /// favourites are home and work, and if every relay holding them is down
  /// after a reinstall they are simply gone.
  ///
  /// Measured 2026-08-03, 30 rounds over ten minutes, each a full connect +
  /// REQ + EOSE rather than a ping:
  ///
  ///   relay.damus.io    8/30  ( 27 %)   22 × HTTP 503
  ///   nos.lol          30/30  (100 %)
  ///   purplerelay.com  30/30  (100 %)   fastest of the three
  ///
  /// So damus refuses roughly three connections in four, not the one in four
  /// an earlier version of this comment claimed — which means that with the
  /// old two-relay set the sync was single-homed on nos.lol most of the time,
  /// for the only data here that cannot be re-created. Re-measure before
  /// trusting these figures again; relay availability is not a constant.
  ///
  /// purplerelay.com was picked on a different criterion than the road-event
  /// relays: not reach, but how little it aggregates. Asked for kind 1315 it
  /// answers empty, i.e. it is not hoovering up everything that passes — and
  /// every extra relay here is another archive of the encrypted snapshot and
  /// another place the targeted "does this npub use Roadstr?" probe works (see
  /// ENUMERATION above). For the same reason bridges and search indexers are
  /// disqualified outright, whatever their uptime: relay.mostr.pub replicates
  /// elsewhere by design and relay.nostr.band feeds a public search index.
  ///
  /// Qualified end to end before being added, on kind 30078 rather than a
  /// regular kind: publish, read back, then publish again under the same 'd'
  /// tag and confirm the relay keeps exactly one event, the newer. A relay
  /// that stored both would quietly break last-write-wins.
  static const _defaultRelays = FavoritesSyncProtocol.defaultRelays;
  static const _legacyDTag = FavoritesSyncProtocol.legacyDTag;
  static const kind = FavoritesSyncProtocol.kind;

  /// Hive key for the user's own relay, opt-in and empty by default.
  static const kCustomRelayKey = 'fav_sync_custom_relay';

  /// The relays a sync touches: the three above plus the user's own, if set.
  ///
  /// **Why this is opt-in and not derived from NIP-65.** Reading the user's
  /// published relay list would look like the obvious answer to "two relays is
  /// thin", and it is the wrong one here. That list is public: publishing the
  /// snapshot to it would hand an attacker the signed, authoritative list of
  /// places to look for this user's 'd' tag, turning the targeted probe in the
  /// ENUMERATION note above from a guess into a lookup. It is also chosen for
  /// social reach, which is the opposite of the criterion these three were
  /// picked on — bridges and search indexers would walk straight back in.
  ///
  /// What a personal relay does give, and no public one can, is a retention
  /// promise: none of the three above declares a retention policy, so none of
  /// them owes the user their favourites tomorrow. Somebody running or paying
  /// for a relay has that guarantee, and this is how they use it.
  List<String> get relays {
    final custom = customRelay;
    return custom == null ? _defaultRelays : [..._defaultRelays, custom];
  }

  /// The configured personal relay, or null when unset or no longer valid.
  String? get customRelay {
    final raw = _box.get(kCustomRelayKey);
    return raw is String ? normaliseRelayUrl(raw) : null;
  }

  /// Validates a user-typed relay URL, returning the cleaned form or null.
  ///
  /// Deliberately strict, because this string decides where an encrypted
  /// snapshot of the user's home address gets published: TLS only (`ws://`
  /// would put the traffic, and the fact that it is Roadstr traffic, in the
  /// clear on the local network), no embedded credentials, no query or
  /// fragment, and a host that actually looks like one.
  static String? normaliseRelayUrl(String input) {
    return FavoritesSyncProtocol.normaliseRelayUrl(input);
  }

  // Hive keys (encrypted settings box).
  static const _kLastTs = 'fav_sync_last_ts';
  static const _kLegacyCleaned = 'fav_sync_legacy_cleaned';

  final _eventApi = EventApi();

  Box get _box => Hive.box('settings');

  /// Per-user 'd' tag — see ENUMERATION in the threat model above.
  static String hashedDTag(String pubKeyHex) =>
      FavoritesSyncProtocol.hashedDTag(pubKeyHex);

  /// Serialises every push, across instances. The snapshot is a *replaceable*
  /// event: two overlapping pushes both read the same high-water mark before
  /// either persists its own, so they can be signed with the same created_at
  /// and finish in any order — and the relay would then keep whichever event
  /// id happens to sort lower, quietly resurrecting favourites the user had
  /// just deleted. Editing favourites fires an auto-push on every change, so
  /// overlapping pushes are the normal case, not an exotic one. Static because
  /// the settings screen and the map each hold their own instance while the
  /// event they overwrite is one and the same.
  static Future<void> _pushChain = Future.value();

  /// Publishes [favorites], NIP-44 encrypted (optionally passphrase-wrapped),
  /// to all configured relays. Returns true if at least one relay accepted.
  ///
  /// Calls queue behind each other in call order, so the last edit made is the
  /// last snapshot published.
  Future<bool> push({
    required List<FavoritePlace> favorites,
    required String pubKeyHex,
    String? privKeyHex, // null when signing via Amber
    String? passphrase, // optional second encryption factor
  }) {
    final result = _pushChain.then((_) => _pushSerialised(
          favorites: favorites,
          pubKeyHex: pubKeyHex,
          privKeyHex: privKeyHex,
          passphrase: passphrase,
        ));
    // The chain must survive a failed push, otherwise one error would strand
    // every later one behind a future that never completes.
    _pushChain = result.then((_) {}, onError: (_) {});
    return result;
  }

  Future<bool> _pushSerialised({
    required List<FavoritePlace> favorites,
    required String pubKeyHex,
    String? privKeyHex,
    String? passphrase,
  }) async {
    var plaintext = FavoritesSyncProtocol.encodeFavorites(favorites);
    if (passphrase != null && passphrase.isNotEmpty) {
      // Off the UI isolate: this runs on every favourite edit via auto-push,
      // so a synchronous key derivation here would freeze the app during
      // ordinary use, not just on an explicit export.
      plaintext = FavoritesSyncProtocol.wrapPassphraseEnvelope(
        await FavoritesCrypto.encryptAsync(plaintext, passphrase),
      );
    }
    if (utf8.encode(plaintext).length >
        FavoritesSyncProtocol.maxPlaintextBytes) {
      return false;
    }
    final encrypted = await _encrypt(
      FavoritesSyncProtocol.padToBucket(plaintext),
      pubKeyHex,
      privKeyHex,
    );
    if (encrypted == null) return false;

    final ts = _nextCreatedAt();
    final signedJson = await _signDraft(
      draft: FavoritesSyncProtocol.snapshotDraft(
        pubkey: pubKeyHex,
        createdAt: ts,
        encryptedContent: encrypted,
      ),
      privKeyHex: privKeyHex,
    );
    if (signedJson == null) return false;

    var anyOk = false;
    await Future.wait(relays.map((url) async {
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
    if (!FavoritesSyncProtocol.passesRollbackGuard(
      fetchedCreatedAt: fetchedTs,
      lastCreatedAt: lastTs,
    )) {
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
          // Off the UI isolate: auto-pull runs at app startup, where a
          // synchronous derivation would stall the first frames.
          decoded = jsonDecode(await FavoritesCrypto.decryptAsync(
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
          .take(FavoritePlace.maxStoredItems)
          .toList();
      await _box.put(_kLastTs, fetchedTs);
      return FavSyncPull.ok(favs);
    } on FormatException {
      return const FavSyncPull.none();
    }
  }

  // ── privacy helpers ──────────────────────────────────────────────────────

  /// Pads [s] with trailing spaces (legal after any top-level JSON value) to
  /// the next [FavoritesSyncProtocol.padBucket] multiple, so ciphertext length
  /// no longer tracks the favorites count. Capped at NIP-44's 65535-byte
  /// plaintext limit.
  @visibleForTesting
  static String padToBucket(String s) => FavoritesSyncProtocol.padToBucket(s);

  /// created_at = start of the current UTC hour, monotonically bumped above
  /// the persisted high-water mark so a same-hour re-publish (or a publish
  /// right after a pull) still wins replaceable-event ordering.
  int _nextCreatedAt() {
    final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
    final last = _box.get(_kLastTs) as int? ?? 0;
    return FavoritesSyncProtocol.nextCreatedAt(
      nowUnixSeconds: now,
      lastCreatedAt: last,
    );
  }

  /// Best-effort removal of the legacy fixed-'d' event: overwrite its content
  /// with an empty replaceable event, then publish a NIP-09 deletion request
  /// for the whole replaceable address.
  Future<void> _cleanupLegacy(String pubKeyHex, String privKeyHex) async {
    final wipe = await _signDraft(
      draft: FavoritesSyncProtocol.legacyWipeDraft(
        pubkey: pubKeyHex,
        createdAt: _nextCreatedAt(),
      ),
      privKeyHex: privKeyHex,
    );
    final del = await _signDraft(
      draft: FavoritesSyncProtocol.legacyDeletionDraft(
        pubkey: pubKeyHex,
        createdAt: _nextCreatedAt(),
      ),
      privKeyHex: privKeyHex,
    );
    await Future.wait(relays.map((url) async {
      if (wipe != null) await _publishOne(url, wipe);
      if (del != null) await _publishOne(url, del);
    }));
  }

  // ── signing (nsec locally; Amber via NIP-55) ─────────────────────────────

  Future<Map<String, dynamic>?> _signDraft({
    required NostrEventDraft draft,
    required String? privKeyHex,
  }) async {
    final unsigned = Event(
      pubkey: draft.pubkey,
      created_at: draft.createdAt,
      kind: draft.kind,
      tags: draft.tags,
      content: draft.content,
    );
    unsigned.id = _eventApi.getEventHash(unsigned);
    if (privKeyHex != null) {
      return _eventApi.finishEvent(unsigned, privKeyHex).toJson();
    }
    try {
      final result = await Amberflutter().signEvent(
        eventJson: jsonEncode(unsigned.toJson()),
        currentUser: draft.pubkey,
      );
      final sig = result['signature'] as String?;
      if (sig == null || sig.isEmpty) return null;
      return unsigned.toJson()..['sig'] = sig;
    } catch (_) {
      return null;
    }
  }

  // ── encryption (nsec: local NIP-44; Amber: NIP-55 nip44_encrypt/decrypt) ──

  Future<String?> _encrypt(
      String plaintext, String pubKeyHex, String? privKeyHex) async {
    if (privKeyHex != null) {
      return Nip44.encrypt(privKeyHex, pubKeyHex, plaintext);
    }
    try {
      final result = await Amberflutter().nip44Encrypt(
          plaintext: plaintext, currentUser: pubKeyHex, pubKey: pubKeyHex);
      final out = result['signature'] as String?;
      return (out != null && out.isNotEmpty) ? out : null;
    } catch (_) {
      return null;
    }
  }

  Future<String?> _decrypt(
      String ciphertext, String pubKeyHex, String? privKeyHex) async {
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
        relays.map((url) => _fetchLatest(url, pubKeyHex, dTag)));
    return FavoritesSyncProtocol.newestSnapshot(results);
  }

  Future<bool> _publishOne(String url, Map<String, dynamic> eventJson) async {
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(url));
      // A relay that refuses the upgrade (damus answers 503 under load)
      // must fail here, inside the try, instead of escaping as an
      // unhandled asynchronous error and leaving a REQ on a dead socket.
      await ws.ready.timeout(const Duration(seconds: 5));
      final completer = Completer<bool>();
      ws.stream.listen((raw) {
        if (completer.isCompleted) return;
        final message = NostrRelayMessageDecoder.decode(raw).message;
        // The OK must name the event we just sent: a relay answering
        // "accepted" for some other id would otherwise report a successful
        // sync for a snapshot it never stored.
        if (message is NostrRelayOkMessage &&
            message.eventId == eventJson['id']) {
          completer.complete(message.accepted == true);
        }
      }, onError: (_) {
        if (!completer.isCompleted) completer.complete(false);
      }, onDone: () {
        if (!completer.isCompleted) completer.complete(false);
      });
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
      // A relay that refuses the upgrade (damus answers 503 under load)
      // must fail here, inside the try, instead of escaping as an
      // unhandled asynchronous error and leaving a REQ on a dead socket.
      await ws.ready.timeout(const Duration(seconds: 5));
      final completer = Completer<Map<String, dynamic>?>();
      final subId = randomSubId();
      final ingress = NostrRelayIngress([
        NostrIngressRule(
          name: 'favorite-snapshot',
          subscriptionId: subId,
          routes: const {kind: NostrIngressRoute.favoriteSnapshot},
          maxEvents: 4,
        ),
      ]);
      ws.stream.listen((raw) {
        if (completer.isCompleted) return;
        try {
          final message = NostrRelayMessageDecoder.decode(raw).message;
          if (message is NostrRelayEventMessage) {
            // `limit: 1` is only a relay hint. Stop before signature
            // verification if a hostile relay ignores it and floods events.
            final json = message.event;
            final decision = ingress.inspect(
              subscriptionId: message.subscriptionId,
              claimedKind: json['kind'],
            );
            if (decision.limitReached) {
              completer.complete(null);
              return;
            }
            if (!decision.shouldVerify) return;
            // Relays are untrusted. Before using anything they send:
            // (1) cheap size guard — drop grotesquely oversized content
            //     before spending CPU hashing it;
            // (2) the author must be exactly us — a relay could ignore the
            //     REQ's authors filter and return someone else's event;
            // (3) kind and 'd' must match what was asked — a relay could
            //     answer with a different (validly signed) event of ours;
            // (4) verify id + signature.
            if (FavoritesSyncProtocol.snapshotEventIsBound(
              json,
              pubkey: pubKeyHex,
              dTag: dTag,
              verifySignature: () => _verifyEvent(json),
            )) {
              completer.complete(json);
            }
          } else if (message is NostrRelayEoseMessage &&
              message.subscriptionId == subId) {
            if (!completer.isCompleted) completer.complete(null);
          }
        } catch (_) {
          if (!completer.isCompleted) completer.complete(null);
        }
      }, onError: (_) {
        if (!completer.isCompleted) completer.complete(null);
      }, onDone: () {
        if (!completer.isCompleted) completer.complete(null);
      });
      ws.sink.add(jsonEncode(FavoritesSyncProtocol.fetchRequest(
        subscriptionId: subId,
        pubkey: pubKeyHex,
        dTag: dTag,
      )));
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
