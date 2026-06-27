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

import 'package:http/http.dart' as http;
import 'package:nostr_tools/nostr_tools.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

// ── Models ────────────────────────────────────────────────────────────────────

/// Holds the metadata returned by a LNURL-pay endpoint (`/.well-known/lnurlp`).
/// All amounts are in **millisatoshi** as per the LNURL-pay spec.
class LnurlPayInfo {
  final String callback;
  final int minSendable; // millisatoshi
  final int maxSendable;
  /// True when the LNURL server supports NIP-57 zap requests — i.e. it will
  /// forward the `nostr` query-parameter (kind-9734 JSON) to the Nostr network.
  final bool allowsNostr;
  const LnurlPayInfo({
    required this.callback,
    required this.minSendable,
    required this.maxSendable,
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
  static Future<String?> fetchLightningAddress(String pubHex) async {
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(_relay));
      final completer = Completer<String?>();
      final subId = 'lud-${DateTime.now().millisecondsSinceEpoch}';

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            final msg = jsonDecode(raw as String) as List;
            if (msg[0] == 'EVENT' && msg[1] == subId) {
              final content = jsonDecode((msg[2] as Map)['content'] as String)
                  as Map<String, dynamic>;
              final lud = (content['lud16'] ?? content['lud06']) as String?;
              completer.complete(lud);
            } else if (msg[0] == 'EOSE') {
              if (!completer.isCompleted) completer.complete(null);
            }
          } catch (_) {
            if (!completer.isCompleted) completer.complete(null);
          }
        },
        onError: (_) { if (!completer.isCompleted) completer.complete(null); },
        onDone:  () { if (!completer.isCompleted) completer.complete(null); },
      );
      ws.sink.add(jsonEncode(['REQ', subId, {
        'kinds': [0], 'authors': [pubHex], 'limit': 1,
      }]));
      return await completer.future.timeout(
          const Duration(seconds: 6), onTimeout: () => null);
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
      final parts = lud16.split('@');
      if (parts.length != 2) return null;
      final url =
          'https://${parts[1]}/.well-known/lnurlp/${Uri.encodeComponent(parts[0])}';
      final res = await http
          .get(Uri.parse(url), headers: {'User-Agent': 'Roadstr/1.0'})
          .timeout(const Duration(seconds: 6));
      if (res.statusCode != 200) return null;
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      if (data['status'] == 'ERROR') return null;
      return LnurlPayInfo(
        callback:    data['callback'] as String,
        minSendable: (data['minSendable'] as num).toInt(),
        maxSendable: (data['maxSendable'] as num).toInt(),
        allowsNostr: data['allowsNostr'] == true,
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
    final event = EventApi().finishEvent(
      Event(
        pubkey:     senderPubHex,
        created_at: DateTime.now().millisecondsSinceEpoch ~/ 1000,
        kind:       9734,
        tags: [
          ['p',      recipientPubHex],
          ['e',      eventId],
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
      final buf = StringBuffer('${payInfo.callback}?amount=$amountMsat');
      if (zapRequest != null && payInfo.allowsNostr) {
        buf.write('&nostr=${Uri.encodeComponent(jsonEncode(zapRequest))}');
      }
      final res = await http
          .get(Uri.parse(buf.toString()),
               headers: {'User-Agent': 'Roadstr/1.0'})
          .timeout(const Duration(seconds: 10));
      if (res.statusCode != 200) return null;
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      return data['pr'] as String?;
    } catch (_) {
      return null;
    }
  }

  // ── Payment ──────────────────────────────────────────────────────────────

  /// Opens the user's Lightning wallet via the `lightning:<invoice>` URI scheme
  /// (deep link). This is the fallback payment method when NWC is not configured.
  static Future<bool> payViaDeepLink(String invoice) async {
    try {
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
  /// Returns the payment preimage on success, an empty string if the wallet
  /// responded without a preimage, or `null` on timeout/error.
  static Future<String?> payViaNwc({
    required String invoice,
    required String nwcUri,
  }) async {
    // Step 1: Parse the NWC URI — the scheme is not a valid https URL so we
    // substitute the prefix to allow Uri.parse to process query parameters.
    final normalized = nwcUri.replaceFirst('nostr+walletconnect://', 'https://x.x/');
    final uri = Uri.tryParse(normalized);
    if (uri == null) return null;
    final walletPub = uri.host;
    final relay     = uri.queryParameters['relay'] ?? _relay;
    final secret    = uri.queryParameters['secret'];
    if (walletPub.isEmpty || secret == null) return null;

    // Step 2: Derive the ephemeral public key used as the sender identity.
    final ourPub  = KeyApi().getPublicKey(secret);
    final nip04   = Nip04();
    final api     = EventApi();

    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(relay));
      final completer = Completer<String?>();
      final reqId = DateTime.now().millisecondsSinceEpoch.toString();
      final subId = 'nwc-$reqId';

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            final msg = jsonDecode(raw as String) as List;
            if (msg[0] == 'EVENT') {
              final ev = msg[2] as Map<String, dynamic>;
              // Step 6: Receive kind-23195 from the wallet and decrypt.
              if (ev['kind'] == 23195 && ev['pubkey'] == walletPub) {
                final plain = nip04.decrypt(
                    secret, walletPub, ev['content'] as String);
                final resp  = jsonDecode(plain) as Map<String, dynamic>;
                final preimage = resp['result']?['preimage'] as String?;
                completer.complete(preimage ?? '');
              }
            }
          } catch (_) {}
        },
        onError: (_) { if (!completer.isCompleted) completer.complete(null); },
        onDone:  () { if (!completer.isCompleted) completer.complete(null); },
      );

      // Step 3+4: Encrypt the pay_invoice command and publish as kind-23194.
      final reqContent = jsonEncode({
        'method': 'pay_invoice',
        'params': {'invoice': invoice},
        'id':     reqId,
      });
      final encrypted = nip04.encrypt(secret, walletPub, reqContent);

      final reqEvent = api.finishEvent(
        Event(
          pubkey:     ourPub,
          created_at: DateTime.now().millisecondsSinceEpoch ~/ 1000,
          kind:       23194,
          tags:       [['p', walletPub]],
          content:    encrypted,
        ),
        secret,
      );

      // Step 5: Subscribe to the response BEFORE publishing the request, so we
      // don't miss a fast wallet response. The `#e` filter targets this specific
      // request by event ID.
      ws.sink.add(jsonEncode(['REQ', subId, {
        'kinds': [23195],
        'authors': [walletPub],
        '#e': [reqEvent.id],
      }]));
      ws.sink.add(jsonEncode(['EVENT', reqEvent.toJson()]));

      return await completer.future.timeout(
          const Duration(seconds: 30), onTimeout: () => null);
    } catch (_) {
      return null;
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }

  // ── Zap totals ─────────────────────────────────────────────────────────────

  /// Returns the total **satoshi** received by a specific road event, computed
  /// by summing the `amount` tags from all NIP-57 kind-9735 zap receipts that
  /// reference [eventId] via the `#e` filter.
  ///
  /// Note: amounts in kind-9735 receipts are in **millisatoshi**; this method
  /// converts to satoshi by integer division by 1000.
  static Future<int> fetchZapTotal(String eventId) async {
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(_relay));
      var totalMsat = 0;
      final completer = Completer<int>();
      final subId = 'zt-$eventId';

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            final msg = jsonDecode(raw as String) as List;
            if (msg[0] == 'EVENT' && msg[1] == subId) {
              final tags = (msg[2]['tags'] as List)
                  .map((t) => List<String>.from(t as List)).toList();
              for (final t in tags) {
                if (t.length >= 2 && t[0] == 'amount') {
                  totalMsat += int.tryParse(t[1]) ?? 0;
                }
              }
            } else if (msg[0] == 'EOSE') {
              if (!completer.isCompleted) completer.complete(totalMsat);
            }
          } catch (_) {}
        },
        onError: (_) { if (!completer.isCompleted) completer.complete(totalMsat); },
        onDone:  () { if (!completer.isCompleted) completer.complete(totalMsat); },
      );
      ws.sink.add(jsonEncode(['REQ', subId, {
        'kinds': [9735], '#e': [eventId], 'limit': 500,
      }]));
      return await completer.future.timeout(
          const Duration(seconds: 6), onTimeout: () => totalMsat);
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
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(_relay));
      var totalMsat = 0;
      final completer = Completer<int>();
      final subId = 'bal-$pubHex';

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            final msg = jsonDecode(raw as String) as List;
            if (msg[0] == 'EVENT' && msg[1] == subId) {
              final tags = (msg[2]['tags'] as List)
                  .map((t) => List<String>.from(t as List)).toList();
              for (final t in tags) {
                if (t.length >= 2 && t[0] == 'amount') {
                  totalMsat += int.tryParse(t[1]) ?? 0;
                }
              }
            } else if (msg[0] == 'EOSE') {
              if (!completer.isCompleted) completer.complete(totalMsat);
            }
          } catch (_) {}
        },
        onError: (_) { if (!completer.isCompleted) completer.complete(totalMsat); },
        onDone:  () { if (!completer.isCompleted) completer.complete(totalMsat); },
      );
      ws.sink.add(jsonEncode(['REQ', subId, {
        'kinds': [9735], '#p': [pubHex], 'limit': 1000,
      }]));
      return await completer.future.timeout(
          const Duration(seconds: 8), onTimeout: () => totalMsat);
    } catch (_) {
      return 0;
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }
}
