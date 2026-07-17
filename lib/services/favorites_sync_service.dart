// Encrypted, cross-device sync of saved favorites via Nostr.
//
// Favorites are serialised to JSON, encrypted end-to-end with NIP-44
// (self-encrypted: sender and recipient are the same pubkey, so only the
// holder of the matching privkey — you, on any of your devices — can ever
// decrypt it), and published as a kind-30078 ("arbitrary app data", NIP-78)
// parameterized-replaceable event. Re-publishing with the same 'd' tag
// overwrites the previous snapshot, so relays never accumulate stale copies
// and there is nothing for anyone else to read: relays only ever see
// ciphertext under your pubkey, with no place name, address or category
// visible — anonymous to every observer but you.
import 'dart:async';
import 'dart:convert';

import 'package:amberflutter/amberflutter.dart';
import 'package:nostr_tools/nostr_tools.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../models/favorite_place.dart';
import 'nip44.dart';
import 'nostr_event_verify.dart';

class FavoritesSyncService {
  static const _relays = [
    'wss://relay.damus.io',
    'wss://nos.lol',
    'wss://relay.nostr.band',
  ];
  static const _dTag = 'roadstr-favorites';
  static const kind = 30078;

  final _eventApi = EventApi();

  /// Publishes [favorites], NIP-44 encrypted, to all configured relays.
  /// Returns true if at least one relay accepted the event.
  Future<bool> push({
    required List<FavoritePlace> favorites,
    required String pubKeyHex,
    String? privKeyHex, // null when signing via Amber
  }) async {
    final plaintext = jsonEncode(favorites.map((f) => f.toMap()).toList());
    final encrypted = await _encrypt(plaintext, pubKeyHex, privKeyHex);
    if (encrypted == null) return false;

    final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
    final unsigned = Event(
      pubkey: pubKeyHex,
      created_at: now,
      kind: kind,
      tags: [
        ['d', _dTag],
      ],
      content: encrypted,
    );
    unsigned.id = _eventApi.getEventHash(unsigned);

    final Map<String, dynamic> signedJson;
    if (privKeyHex != null) {
      signedJson = _eventApi.finishEvent(unsigned, privKeyHex).toJson();
    } else {
      final result = await Amberflutter().signEvent(
        eventJson: jsonEncode(unsigned.toJson()),
        currentUser: pubKeyHex,
      );
      final sig = result['signature'] as String?;
      if (sig == null || sig.isEmpty) return false;
      signedJson = unsigned.toJson()..['sig'] = sig;
    }

    var anyOk = false;
    await Future.wait(_relays.map((url) async {
      if (await _publishOne(url, signedJson)) anyOk = true;
    }));
    return anyOk;
  }

  /// Fetches and decrypts the latest favorites snapshot from any reachable
  /// relay. Returns null if none exists yet, no relay is reachable, or
  /// decryption fails everywhere (wrong key / not the same identity).
  Future<List<FavoritePlace>?> pull({
    required String pubKeyHex,
    String? privKeyHex,
  }) async {
    for (final url in _relays) {
      final json = await _fetchLatest(url, pubKeyHex);
      if (json == null) continue;
      final content = json['content'] as String?;
      if (content == null || content.isEmpty) continue;
      final plaintext = await _decrypt(content, pubKeyHex, privKeyHex);
      if (plaintext == null) continue;
      try {
        final list = jsonDecode(plaintext) as List;
        return list
            .whereType<Map>()
            .map((m) => FavoritePlace.fromMapSafe(m))
            .whereType<FavoritePlace>()
            .toList();
      } catch (_) {
        continue;
      }
    }
    return null;
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

  Future<Map<String, dynamic>?> _fetchLatest(String url, String pubKeyHex) async {
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(url));
      final completer = Completer<Map<String, dynamic>?>();
      final subId = 'fav-${DateTime.now().millisecondsSinceEpoch}';
      ws.stream.listen((raw) {
        if (completer.isCompleted) return;
        try {
          final msg = jsonDecode(raw as String) as List;
          if (msg[0] == 'EVENT' && msg[1] == subId) {
            final json = (msg[2] as Map).cast<String, dynamic>();
            // Relays are untrusted: (1) the author must be exactly us — a relay
            // could ignore the REQ's authors filter and return someone else's
            // event; (2) verify id + signature before using content. (Decryption
            // with our self-key would also fail for a foreign author, but
            // rejecting up front avoids trusting relay-supplied filtering.)
            if (json['pubkey'] == pubKeyHex && _verifyEvent(json)) {
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
        'kinds': [kind], 'authors': [pubKeyHex], '#d': [_dTag], 'limit': 1,
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
