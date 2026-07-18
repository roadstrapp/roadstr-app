// Lightning Network zap service for Roadstr.
//
// Implements the complete NIP-57 zap flow on top of the LNURL-pay protocol:
//
//   Step 1 — Fetch the recipient's Lightning address (lud16 field in kind-0).
//   Step 2 — Resolve the LNURL-pay endpoint (/.well-known/lnurlp/<user>).
//   Step 3 — Build a NIP-57 kind-9734 zap request event (signed by the sender).
//   Step 4 — POST/GET the LNURL callback to obtain a BOLT-11 invoice.
//   Step 5 — Pay the invoice: via NWC (NIP-47) if configured, else deep-link.
//
// NWC (Nostr Wallet Connect, NIP-47) uses NIP-04 symmetric encryption
// (secp256k1 ECDH + AES-256-CBC) to send a pay_invoice command to a wallet
// daemon listening on a Nostr relay.
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:bech32/bech32.dart';
import 'package:http/http.dart' as http;
import 'package:nostr_tools/nostr_tools.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import 'bolt11_invoice.dart';
import 'nostr_event_verify.dart';

// ── Models ────────────────────────────────────────────────────────────────────

/// Holds the metadata returned by a LNURL-pay endpoint (`/.well-known/lnurlp`).
/// All amounts are in **millisatoshi** as per the LNURL-pay spec.
class LnurlPayInfo {
  final String callback;
  final int minSendable; // millisatoshi
  final int maxSendable;
  final String metadata;
  final String? nostrPubkey;

  /// True when the LNURL server supports NIP-57 zap requests — i.e. it will
  /// forward the `nostr` query-parameter (kind-9734 JSON) to the Nostr network.
  final bool allowsNostr;
  const LnurlPayInfo({
    required this.callback,
    required this.minSendable,
    required this.maxSendable,
    required this.metadata,
    required this.nostrPubkey,
    required this.allowsNostr,
  });
}

// ── ZapService ────────────────────────────────────────────────────────────────

/// Stateless service (all methods are `static`) that orchestrates LNURL-pay
/// and NIP-57/NIP-47 zap flows for road-event tips.
class ZapService {
  static const _relay = 'wss://relay.damus.io';

  // ── Data fetching ──────────────────────────────────────────────────────────

  /// Fetches the recipient's Lightning address from their NIP-01 kind-0 metadata.
  ///
  /// Checks `lud16` (Lightning address format, e.g. `user@domain.com`) first,
  /// then falls back to `lud06` (raw LNURL bech32). Returns `null` if the
  /// profile has no Lightning address or the relay does not respond within 6 s.
  ///
  /// The kind-0 event is verified (author, id hash, Schnorr signature) before
  /// its content is trusted: a malicious relay that could substitute the
  /// Lightning address would redirect every zap to an attacker's wallet.
  static Future<String?> fetchLightningAddress(String pubHex) async {
    if (!_isHex32(pubHex)) return null;
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(_relay));
      final completer = Completer<String?>();
      final subId = randomSubId();
      var latestCreatedAt = -1;
      String? latestAddress;

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            if (raw is! String || raw.length > 256 * 1024) return;
            final msg = jsonDecode(raw) as List;
            if (msg[0] == 'EVENT' && msg[1] == subId) {
              final json = (msg[2] as Map).cast<String, dynamic>();
              if (json['pubkey'] != pubHex || !verifyEventJson(json)) return;
              final content =
                  jsonDecode(json['content'] as String) as Map<String, dynamic>;
              final lud = (content['lud16'] ?? content['lud06']) as String?;
              final createdAt = json['created_at'] as int? ?? -1;
              if (createdAt > latestCreatedAt) {
                latestCreatedAt = createdAt;
                latestAddress = lud;
              }
            } else if (msg[0] == 'EOSE' && msg[1] == subId) {
              if (!completer.isCompleted) completer.complete(latestAddress);
            }
          } catch (_) {}
        },
        onError: (_) {
          if (!completer.isCompleted) completer.complete(null);
        },
        onDone: () {
          if (!completer.isCompleted) completer.complete(null);
        },
      );
      ws.sink.add(jsonEncode([
        'REQ',
        subId,
        {
          'kinds': [0],
          'authors': [pubHex],
          'limit': 1,
        }
      ]));
      return await completer.future
          .timeout(const Duration(seconds: 6), onTimeout: () => null);
    } catch (_) {
      return null;
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }

  /// Resolves a Lightning address (lud16 format: `user@domain`) to its LNURL-pay
  /// metadata by fetching `https://<domain>/.well-known/lnurlp/<user>`.
  ///
  /// Returns `null` if the server is unreachable or returns an error status.
  static Future<LnurlPayInfo?> fetchLnurlPayInfo(String lud16) async {
    try {
      Uri metadataUri;
      if (lud16.toLowerCase().startsWith('lnurl1')) {
        final decoded = _decodeLnurl(lud16);
        if (decoded == null) return null;
        metadataUri = decoded;
      } else {
        final address = lud16.trim();
        if (address.length > 254) return null;
        final parts = address.split('@');
        if (parts.length != 2) return null;
        final user = parts[0].trim();
        final domain = parts[1].trim();
        if (user.isEmpty || domain.isEmpty || user.contains('/')) return null;
        metadataUri = Uri(
          scheme: 'https',
          host: domain,
          pathSegments: ['.well-known', 'lnurlp', user],
        );
      }
      if (!_isSafeHttpsUri(metadataUri)) return null;
      final data = await _boundedJsonGet(
        metadataUri,
        timeout: const Duration(seconds: 6),
      );
      if (data == null) return null;
      if (data['status'] == 'ERROR') return null;
      final callback = Uri.tryParse(data['callback'] as String? ?? '');
      final min = (data['minSendable'] as num?)?.toInt();
      final max = (data['maxSendable'] as num?)?.toInt();
      final metadata = data['metadata'] as String?;
      final allowsNostr = data['allowsNostr'] == true;
      final nostrPubkey = data['nostrPubkey'] as String?;
      if (callback == null ||
          !_isSafeHttpsUri(callback) ||
          min == null ||
          max == null ||
          metadata == null ||
          metadata.length > 65536 ||
          !_isValidLnurlMetadata(metadata) ||
          (allowsNostr && (nostrPubkey == null || !_isHex32(nostrPubkey))) ||
          min <= 0 ||
          max < min) {
        return null;
      }
      return LnurlPayInfo(
        callback: callback.toString(),
        minSendable: min,
        maxSendable: max,
        metadata: metadata,
        nostrPubkey: allowsNostr ? nostrPubkey!.toLowerCase() : null,
        allowsNostr: allowsNostr,
      );
    } catch (_) {
      return null;
    }
  }

  // ── NIP-57 Zap request ─────────────────────────────────────────────────────

  /// Creates and signs a NIP-57 kind-9734 **zap request** event with the
  /// sender's private key.
  ///
  /// NIP-57: the zap request is a Nostr event sent by the **payer** to indicate
  /// intent to zap. It is NOT published to relays — instead it is passed as a
  /// URL-encoded `nostr` query parameter to the LNURL callback. The LNURL server
  /// verifies the signature and, once the invoice is paid, publishes a kind-9735
  /// **zap receipt** to Nostr on behalf of both parties.
  ///
  /// Parameters:
  ///   - [senderPrivHex] / [senderPubHex]: the zapper's key pair.
  ///   - [recipientPubHex]: the road-event author's public key.
  ///   - [eventId]: the kind-1315 event being zapped.
  ///   - [amountMsat]: the zap amount in **millisatoshi**.
  static Map<String, dynamic> buildZapRequest({
    required String senderPrivHex,
    required String senderPubHex,
    required String recipientPubHex,
    required String eventId,
    required int amountMsat,
  }) {
    if (!_isHex32(senderPrivHex) ||
        !_isHex32(senderPubHex) ||
        !_isHex32(recipientPubHex) ||
        !_isHex32(eventId) ||
        amountMsat <= 0 ||
        amountMsat > 21000000 * 100000000000) {
      throw const FormatException('Invalid zap request fields');
    }
    if (KeyApi().getPublicKey(senderPrivHex).toLowerCase() !=
        senderPubHex.toLowerCase()) {
      throw const FormatException('Nostr key pair does not match');
    }
    final event = EventApi().finishEvent(
      Event(
        pubkey: senderPubHex,
        created_at: DateTime.now().millisecondsSinceEpoch ~/ 1000,
        kind: 9734,
        tags: [
          ['p', recipientPubHex],
          ['e', eventId],
          ['amount', amountMsat.toString()],
          ['relays', _relay],
        ],
        content: '',
      ),
      senderPrivHex,
    );
    return event.toJson();
  }

  // ── LNURL invoice ──────────────────────────────────────────────────────────

  /// Requests a BOLT-11 invoice from the LNURL-pay callback URL.
  ///
  /// If [zapRequest] is provided AND the server supports Nostr (`allowsNostr`),
  /// the NIP-57 kind-9734 JSON is appended as a `nostr=<url-encoded>` query
  /// parameter, enabling the server to publish a kind-9735 zap receipt.
  /// When [zapRequest] is `null` this degrades to a plain LNURL-pay flow.
  ///
  /// Returns the BOLT-11 `pr` (payment request) string, or `null` on failure.
  static Future<String?> getInvoice({
    required LnurlPayInfo payInfo,
    required int amountMsat,
    Map<String, dynamic>? zapRequest,
  }) async {
    try {
      if (amountMsat < payInfo.minSendable ||
          amountMsat > payInfo.maxSendable) {
        return null;
      }
      final callback = Uri.tryParse(payInfo.callback);
      if (callback == null || !_isSafeHttpsUri(callback)) return null;
      final query = <String, String>{
        ...callback.queryParameters,
        'amount': amountMsat.toString(),
      };
      if (zapRequest != null && payInfo.allowsNostr) {
        query['nostr'] = jsonEncode(zapRequest);
      }
      final data = await _boundedJsonGet(
        callback.replace(queryParameters: query),
        timeout: const Duration(seconds: 10),
      );
      if (data == null) return null;
      if (data['status'] == 'ERROR') return null;
      final invoice = data['pr'] as String?;
      if (invoice == null) return null;
      final decoded = Bolt11Invoice.tryParse(invoice);
      final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
      if (decoded == null ||
          decoded.amountMsat != amountMsat ||
          decoded.isExpiredAt(now) ||
          !decoded.descriptionMatches(
            query['nostr'] ?? payInfo.metadata,
          )) {
        return null;
      }
      return invoice;
    } catch (_) {
      return null;
    }
  }

  // ── Payment ──────────────────────────────────────────────────────────────

  /// Opens the user's Lightning wallet via the `lightning:<invoice>` URI scheme
  /// (deep link). This is the fallback payment method when NWC is not configured.
  static Future<bool> payViaDeepLink(String invoice) async {
    try {
      final decoded = Bolt11Invoice.tryParse(invoice);
      final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
      if (decoded == null || decoded.isExpiredAt(now)) return false;
      return await launchUrl(
        Uri.parse('lightning:$invoice'),
        mode: LaunchMode.externalApplication,
      );
    } catch (_) {
      return false;
    }
  }

  /// Pays [invoice] via Nostr Wallet Connect (NIP-47).
  ///
  /// NIP-47 / NWC flow:
  ///   1. Parse the `nostr+walletconnect://<walletPubkey>?relay=...&secret=...` URI.
  ///   2. Derive our ephemeral public key from `secret`.
  ///   3. Encrypt the `pay_invoice` command with NIP-04 (ECDH + AES-256-CBC)
  ///      using `secret` (our private key) and `walletPubkey` (wallet's public key).
  ///   4. Publish the encrypted request as kind-23194 to the wallet's relay.
  ///   5. Subscribe to kind-23195 responses from the wallet, filtered by our
  ///      request event ID (`#e` tag).
  ///   6. Decrypt the response and extract the `preimage` to confirm payment.
  ///
  /// Returns a verified payment preimage on success, or `null` on timeout,
  /// malformed/unbound response, invalid signature or payment-hash mismatch.
  static Future<String?> payViaNwc({
    required String invoice,
    required String nwcUri,
  }) async {
    // Dart parses custom hierarchical schemes directly. The previous host
    // substitution made `uri.host` equal to the dummy host instead of the
    // wallet pubkey, breaking every valid NWC URI.
    final uri = Uri.tryParse(nwcUri.trim());
    if (uri == null || uri.scheme != 'nostr+walletconnect') return null;
    if (uri.userInfo.isNotEmpty ||
        uri.path.isNotEmpty ||
        uri.queryParametersAll['secret']?.length != 1 ||
        (uri.queryParametersAll['relay']?.length ?? 0) > 1) {
      return null;
    }
    final walletPub = uri.host;
    final relay = uri.queryParameters['relay'] ?? _relay;
    final secret = uri.queryParameters['secret'];
    final relayUri = Uri.tryParse(relay);
    if (!_isHex32(walletPub) ||
        secret == null ||
        !_isHex32(secret) ||
        relayUri == null ||
        relayUri.scheme != 'wss' ||
        relayUri.host.isEmpty) {
      return null;
    }
    final decodedInvoice = Bolt11Invoice.tryParse(invoice);
    final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
    if (decodedInvoice == null || decodedInvoice.isExpiredAt(now)) return null;

    WebSocketChannel? ws;
    try {
      // Step 2: Derive the ephemeral public key used as the sender identity.
      final ourPub = KeyApi().getPublicKey(secret);
      final nip04 = Nip04();
      final api = EventApi();
      ws = WebSocketChannel.connect(relayUri);
      final completer = Completer<String?>();
      final subId = randomSubId();

      late final dynamic reqEvent;

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            if (raw is! String || raw.length > 256 * 1024) return;
            final msg = jsonDecode(raw) as List;
            if (msg.length >= 3 && msg[0] == 'EVENT' && msg[1] == subId) {
              final ev = (msg[2] as Map).cast<String, dynamic>();
              // Step 6: Receive kind-23195 from the wallet and decrypt.
              final tags = (ev['tags'] as List?)
                      ?.whereType<List>()
                      .map((t) => t.map((e) => e.toString()).toList())
                      .toList() ??
                  const <List<String>>[];
              final boundToRequest = tags.any(
                  (t) => t.length >= 2 && t[0] == 'e' && t[1] == reqEvent.id);
              final addressedToUs = tags
                  .any((t) => t.length >= 2 && t[0] == 'p' && t[1] == ourPub);
              if (ev['kind'] == 23195 &&
                  ev['pubkey'] == walletPub &&
                  boundToRequest &&
                  addressedToUs &&
                  verifyEventJson(ev)) {
                final plain =
                    nip04.decrypt(secret, walletPub, ev['content'] as String);
                final resp = jsonDecode(plain) as Map<String, dynamic>;
                if (resp['result_type'] != 'pay_invoice' ||
                    resp['error'] != null) {
                  completer.complete(null);
                  return;
                }
                final result = resp['result'] as Map?;
                final preimage = result?['preimage'] as String?;
                completer.complete(
                    preimage != null && decodedInvoice.preimageMatches(preimage)
                        ? preimage
                        : null);
              }
            }
          } catch (_) {}
        },
        onError: (_) {
          if (!completer.isCompleted) completer.complete(null);
        },
        onDone: () {
          if (!completer.isCompleted) completer.complete(null);
        },
      );

      // Step 3+4: Encrypt the pay_invoice command and publish as kind-23194.
      final reqContent = jsonEncode({
        'method': 'pay_invoice',
        'params': {'invoice': invoice},
      });
      final encrypted = nip04.encrypt(secret, walletPub, reqContent);

      reqEvent = api.finishEvent(
        Event(
          pubkey: ourPub,
          created_at: DateTime.now().millisecondsSinceEpoch ~/ 1000,
          kind: 23194,
          tags: [
            ['p', walletPub]
          ],
          content: encrypted,
        ),
        secret,
      );

      // Step 5: Subscribe to the response BEFORE publishing the request, so we
      // don't miss a fast wallet response. The `#e` filter targets this specific
      // request by event ID.
      ws.sink.add(jsonEncode([
        'REQ',
        subId,
        {
          'kinds': [23195],
          'authors': [walletPub],
          '#e': [reqEvent.id],
        }
      ]));
      ws.sink.add(jsonEncode(['EVENT', reqEvent.toJson()]));

      return await completer.future
          .timeout(const Duration(seconds: 30), onTimeout: () => null);
    } catch (_) {
      return null;
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }

  // ── Zap totals ─────────────────────────────────────────────────────────────

  /// Returns the total **millisatoshi** received by a specific road event,
  /// computed
  /// by summing the `amount` tags from all NIP-57 kind-9735 zap receipts that
  /// reference [eventId] via the `#e` filter.
  ///
  /// Note: amounts in kind-9735 receipts are in **millisatoshi**; this method
  static Future<int> fetchZapTotal(
      String eventId, String recipientPubHex) async {
    final signer = await _resolveZapSigner(recipientPubHex);
    if (signer == null) return 0;
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(_relay));
      var totalMsat = 0;
      final seen = <String>{};
      final completer = Completer<int>();
      final subId = randomSubId();

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            if (raw is! String || raw.length > 256 * 1024) return;
            final msg = jsonDecode(raw) as List;
            if (msg[0] == 'EVENT' && msg[1] == subId) {
              final event = (msg[2] as Map).cast<String, dynamic>();
              final id = event['id'] as String?;
              if (id != null && seen.add(id)) {
                totalMsat += _verifiedReceiptAmount(
                      event,
                      eventId: eventId,
                      recipientPub: recipientPubHex,
                      receiptSigner: signer,
                    ) ??
                    0;
              }
            } else if (msg[0] == 'EOSE' && msg[1] == subId) {
              if (!completer.isCompleted) completer.complete(totalMsat);
            }
          } catch (_) {}
        },
        onError: (_) {
          if (!completer.isCompleted) completer.complete(totalMsat);
        },
        onDone: () {
          if (!completer.isCompleted) completer.complete(totalMsat);
        },
      );
      ws.sink.add(jsonEncode([
        'REQ',
        subId,
        {
          'kinds': [9735],
          '#e': [eventId],
          'limit': 500,
        }
      ]));
      return await completer.future
          .timeout(const Duration(seconds: 6), onTimeout: () => totalMsat);
    } catch (_) {
      return 0;
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }

  /// Returns the total **satoshi** received by [pubHex] across all their events,
  /// by summing `amount` tags from kind-9735 receipts that tag this public key
  /// (`#p` filter). Used to display the user's lifetime zap earnings on the
  /// profile screen.
  static Future<int> fetchBalance(String pubHex) async {
    final signer = await _resolveZapSigner(pubHex);
    if (signer == null) return 0;
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(_relay));
      var totalMsat = 0;
      final seen = <String>{};
      final completer = Completer<int>();
      final subId = randomSubId();

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            if (raw is! String || raw.length > 256 * 1024) return;
            final msg = jsonDecode(raw) as List;
            if (msg[0] == 'EVENT' && msg[1] == subId) {
              final event = (msg[2] as Map).cast<String, dynamic>();
              final id = event['id'] as String?;
              if (id != null && seen.add(id)) {
                totalMsat += _verifiedReceiptAmount(
                      event,
                      recipientPub: pubHex,
                      receiptSigner: signer,
                    ) ??
                    0;
              }
            } else if (msg[0] == 'EOSE' && msg[1] == subId) {
              if (!completer.isCompleted) completer.complete(totalMsat);
            }
          } catch (_) {}
        },
        onError: (_) {
          if (!completer.isCompleted) completer.complete(totalMsat);
        },
        onDone: () {
          if (!completer.isCompleted) completer.complete(totalMsat);
        },
      );
      ws.sink.add(jsonEncode([
        'REQ',
        subId,
        {
          'kinds': [9735],
          '#p': [pubHex],
          'limit': 1000,
        }
      ]));
      return await completer.future
          .timeout(const Duration(seconds: 8), onTimeout: () => totalMsat);
    } catch (_) {
      return 0;
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }

  /// A relay is not proof of payment. NIP-57 ultimately trusts the recipient's
  /// LNURL provider, so a receipt is counted only when it is signed by the
  /// provider advertised in LNURL metadata and all invoice/request bindings
  /// agree. A preimage is checked when present, but the spec makes it optional
  /// and it cannot make a rogue provider's receipt independently trustworthy.
  static int? _verifiedReceiptAmount(
    Map<String, dynamic> receipt, {
    String? eventId,
    String? recipientPub,
    required String receiptSigner,
  }) {
    try {
      if (receipt['kind'] != 9735 ||
          receipt['pubkey'] != receiptSigner ||
          !verifyEventJson(receipt)) {
        return null;
      }
      final tags = (receipt['tags'] as List)
          .map((t) => List<String>.from(t as List))
          .toList();
      final bolt11Values = <String>[];
      final descriptionValues = <String>[];
      final preimageValues = <String>[];
      final receiptRecipients = <String>[];
      final receiptEvents = <String>[];
      for (final t in tags) {
        if (t.length < 2) continue;
        if (t[0] == 'bolt11') bolt11Values.add(t[1]);
        if (t[0] == 'description') descriptionValues.add(t[1]);
        if (t[0] == 'preimage') preimageValues.add(t[1]);
        if (t[0] == 'p') receiptRecipients.add(t[1]);
        if (t[0] == 'e') receiptEvents.add(t[1]);
      }
      if (bolt11Values.length != 1 ||
          descriptionValues.length != 1 ||
          preimageValues.length > 1 ||
          receiptRecipients.length != 1 ||
          receiptEvents.length > 1) {
        return null;
      }
      final bolt11 = bolt11Values.single;
      final description = descriptionValues.single;
      final invoice = Bolt11Invoice.tryParse(bolt11);
      if (invoice == null || !invoice.descriptionMatches(description)) {
        return null;
      }
      if (preimageValues.isNotEmpty &&
          !invoice.preimageMatches(preimageValues.single)) {
        return null;
      }

      final request = (jsonDecode(description) as Map).cast<String, dynamic>();
      if (request['kind'] != 9734 || !verifyEventJson(request)) return null;
      final requestTags = (request['tags'] as List)
          .map((t) => List<String>.from(t as List))
          .toList();
      final amounts = <String>[];
      final requestEvents = <String>[];
      final requestRecipients = <String>[];
      for (final t in requestTags) {
        if (t.length < 2) continue;
        if (t[0] == 'amount') amounts.add(t[1]);
        if (t[0] == 'e') requestEvents.add(t[1]);
        if (t[0] == 'p') requestRecipients.add(t[1]);
      }
      if (amounts.length != 1 ||
          requestRecipients.length != 1 ||
          requestEvents.length > 1 ||
          int.tryParse(amounts.single) != invoice.amountMsat ||
          receiptRecipients.single != requestRecipients.single ||
          (recipientPub != null && requestRecipients.single != recipientPub) ||
          (eventId != null &&
              (requestEvents.length != 1 || requestEvents.single != eventId)) ||
          (requestEvents.isEmpty != receiptEvents.isEmpty) ||
          (requestEvents.isNotEmpty &&
              receiptEvents.single != requestEvents.single)) {
        return null;
      }
      return invoice.amountMsat;
    } catch (_) {
      return null;
    }
  }

  static bool _isHex32(String value) =>
      RegExp(r'^[0-9a-fA-F]{64}$').hasMatch(value);

  static Future<String?> _resolveZapSigner(String recipientPubHex) async {
    final address = await fetchLightningAddress(recipientPubHex);
    if (address == null) return null;
    final info = await fetchLnurlPayInfo(address);
    return info?.allowsNostr == true ? info!.nostrPubkey : null;
  }

  static bool _isSafeHttpsUri(Uri uri) {
    if (uri.scheme != 'https' || uri.host.isEmpty || uri.userInfo.isNotEmpty) {
      return false;
    }
    final host = uri.host.toLowerCase();
    if (host == 'localhost' || host.endsWith('.localhost')) return false;
    final ip = RegExp(r'^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$')
        .firstMatch(host);
    if (ip != null) {
      final octets = [for (var i = 1; i <= 4; i++) int.parse(ip.group(i)!)];
      if (octets.any((v) => v > 255) ||
          octets[0] == 10 ||
          octets[0] == 127 ||
          (octets[0] == 169 && octets[1] == 254) ||
          (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31) ||
          (octets[0] == 192 && octets[1] == 168)) {
        return false;
      }
    }
    return true;
  }

  static Future<Map<String, dynamic>?> _boundedJsonGet(
    Uri uri, {
    required Duration timeout,
  }) async {
    if (!await _isSafeHttpsTarget(uri)) return null;
    final client = http.Client();
    try {
      final request = http.Request('GET', uri)
        ..followRedirects = false
        ..headers['User-Agent'] = 'Roadstr/1.0'
        ..headers['Accept'] = 'application/json';
      final response = await client.send(request).timeout(timeout);
      const maxBytes = 1024 * 1024;
      if (response.statusCode != 200 ||
          (response.contentLength ?? 0) > maxBytes) {
        return null;
      }
      final bytes = <int>[];
      await for (final chunk in response.stream.timeout(timeout)) {
        if (bytes.length + chunk.length > maxBytes) return null;
        bytes.addAll(chunk);
      }
      final decoded = jsonDecode(utf8.decode(bytes));
      return decoded is Map
          ? decoded.map((key, value) => MapEntry(key.toString(), value))
          : null;
    } catch (_) {
      return null;
    } finally {
      client.close();
    }
  }

  static Future<bool> _isSafeHttpsTarget(Uri uri) async {
    if (!_isSafeHttpsUri(uri)) return false;
    try {
      final addresses = await InternetAddress.lookup(uri.host)
          .timeout(const Duration(seconds: 4));
      return addresses.isNotEmpty && addresses.every(_isPublicAddress);
    } catch (_) {
      return false;
    }
  }

  static bool _isPublicAddress(InternetAddress address) {
    if (address.type == InternetAddressType.IPv4) {
      final b = address.rawAddress;
      return !(b[0] == 0 ||
          b[0] == 10 ||
          b[0] == 127 ||
          (b[0] == 100 && b[1] >= 64 && b[1] <= 127) ||
          (b[0] == 169 && b[1] == 254) ||
          (b[0] == 172 && b[1] >= 16 && b[1] <= 31) ||
          (b[0] == 192 && b[1] == 168) ||
          (b[0] == 198 && (b[1] == 18 || b[1] == 19)) ||
          b[0] >= 224);
    }
    final b = address.rawAddress;
    if (b.length != 16) return false;
    final unspecifiedOrLoopback =
        b.take(15).every((v) => v == 0) && (b[15] == 0 || b[15] == 1);
    final linkLocal = b[0] == 0xfe && (b[1] & 0xc0) == 0x80;
    final uniqueLocal = (b[0] & 0xfe) == 0xfc;
    final multicast = b[0] == 0xff;
    return !(unspecifiedOrLoopback || linkLocal || uniqueLocal || multicast);
  }

  static bool _isValidLnurlMetadata(String raw) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List || decoded.isEmpty || decoded.length > 100) {
        return false;
      }
      return decoded.every((item) =>
          item is List &&
          item.length == 2 &&
          item[0] is String &&
          item[1] is String);
    } catch (_) {
      return false;
    }
  }

  static Uri? _decodeLnurl(String encoded) {
    try {
      final decoded =
          const Bech32Codec().decode(encoded.toLowerCase(), encoded.length);
      if (decoded.hrp != 'lnurl') return null;
      final bytes = Bolt11Invoice.convertFiveBitWords(decoded.data);
      if (bytes == null || bytes.length > 2048) return null;
      final uri = Uri.tryParse(utf8.decode(bytes));
      return uri != null && _isSafeHttpsUri(uri) ? uri : null;
    } catch (_) {
      return null;
    }
  }
}
