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

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:nostr_tools/nostr_tools.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import 'bolt11_invoice.dart';
import 'lightning_protocol.dart';
import 'lnurl_protocol.dart';
import 'nostr_event_verify.dart';
import 'nostr_relay_ingress.dart';
import 'nostr_relay_message.dart';

// ── ZapService ────────────────────────────────────────────────────────────────

/// Stateless service (all methods are `static`) that orchestrates LNURL-pay
/// and NIP-57/NIP-47 zap flows for road-event tips.
class ZapService {
  /// Relays queried for zap receipts and Lightning addresses, and advertised
  /// to the LNURL server as the places to publish the receipt.
  ///
  /// The same four Roadstr publishes road events to (see
  /// [NostrRelayService]): a receipt that lands on only one of them still
  /// counts, and a relay that is down no longer zeroes the profile's zap
  /// total. Asking a single relay meant an outage there read as "0 sats".
  static const _relays = [
    'wss://nos.lol',
    'wss://relay.primal.net',
    'wss://nostr.oxtr.dev',
    'wss://relay.damus.io',
  ];

  /// Relay used when an NWC URI carries none of its own.
  static const _nwcFallbackRelay = 'wss://relay.damus.io';

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
  ///
  /// All relays are asked **at once** and the newest kind-0 wins. Sequentially
  /// one dead relay would have cost its full timeout before the next was even
  /// tried — on the road-event sheet that is the difference between a sheet
  /// that opens and one that hangs. Taking the newest, rather than the first
  /// to answer, also means a relay sitting on a stale profile cannot send a
  /// zap to a Lightning address the user has since changed.
  static Future<String?> fetchLightningAddress(String pubHex) async {
    if (!_isHex32(pubHex)) return null;
    final cached = _addressCache[pubHex];
    if (cached != null && DateTime.now().isBefore(cached.until)) {
      return cached.address;
    }
    final perRelay = await Future.wait([
      for (final relay in _relays) _fetchLightningAddressFrom(pubHex, relay),
    ]);
    String? best;
    var bestCreatedAt = -1;
    for (final result in perRelay) {
      if (result != null &&
          result.address.isNotEmpty &&
          result.createdAt > bestCreatedAt) {
        bestCreatedAt = result.createdAt;
        best = result.address;
      }
    }
    if (best != null) {
      // Opening two reports in a row must not re-run four sockets each time.
      if (_addressCache.length > 64) _addressCache.clear();
      _addressCache[pubHex] = (
        address: best,
        until: DateTime.now().add(const Duration(minutes: 10)),
      );
    }
    return best;
  }

  /// Short-lived Lightning-address cache, keyed by public key.
  static final _addressCache = <String, ({String address, DateTime until})>{};

  static Future<({String address, int createdAt})?> _fetchLightningAddressFrom(
      String pubHex, String relay) async {
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(relay));
      // A relay that refuses the upgrade (damus answers 503 under load)
      // must fail here, inside the try, instead of escaping as an
      // unhandled asynchronous error and leaving a REQ on a dead socket.
      await ws.ready.timeout(const Duration(seconds: 5));
      final completer = Completer<({String address, int createdAt})?>();
      final subId = randomSubId();
      var latestCreatedAt = -1;
      String? latestAddress;
      final ingress = NostrRelayIngress([
        NostrIngressRule(
          name: 'lightning-address',
          subscriptionId: subId,
          // Keep the shipped behavior: this path verifies author/signature
          // but historically trusted the kind-0 REQ instead of rechecking the
          // event kind locally. The explicit fallback records that parity gap.
          fallbackRoute: NostrIngressRoute.lightningAddress,
          maxEvents: 10,
        ),
      ]);

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            final message = NostrRelayMessageDecoder.decode(raw).message;
            if (message is NostrRelayEventMessage) {
              final json = message.event;
              final decision = ingress.inspect(
                subscriptionId: message.subscriptionId,
                claimedKind: json['kind'],
              );
              if (decision.limitReached) {
                completer.complete(latestAddress == null
                    ? null
                    : (address: latestAddress!, createdAt: latestCreatedAt));
                return;
              }
              if (!decision.shouldVerify) return;
              if (json['pubkey'] != pubHex || !verifyEventJson(json)) return;
              final content =
                  jsonDecode(json['content'] as String) as Map<String, dynamic>;
              final lud = (content['lud16'] ?? content['lud06']) as String?;
              final createdAt = json['created_at'] as int? ?? -1;
              if (createdAt > latestCreatedAt) {
                latestCreatedAt = createdAt;
                latestAddress = lud;
              }
            } else if (message is NostrRelayEoseMessage &&
                message.subscriptionId == subId) {
              if (!completer.isCompleted) {
                completer.complete(latestAddress == null
                    ? null
                    : (address: latestAddress!, createdAt: latestCreatedAt));
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
      final metadataUri = LnurlProtocol.resolveMetadataUri(lud16);
      if (metadataUri == null) return null;
      final data = await _boundedJsonGet(
        metadataUri,
        timeout: const Duration(seconds: 6),
      );
      if (data == null) return null;
      return LnurlProtocol.parsePayInfo(data);
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
        amountMsat > Nip57Protocol.maxAmountMsat) {
      throw const FormatException('Invalid zap request fields');
    }
    if (KeyApi().getPublicKey(senderPrivHex).toLowerCase() !=
        senderPubHex.toLowerCase()) {
      throw const FormatException('Nostr key pair does not match');
    }
    final draft = Nip57Protocol.zapRequestDraft(
      senderPubkey: senderPubHex,
      createdAt: DateTime.now().millisecondsSinceEpoch ~/ 1000,
      recipientPubkey: recipientPubHex,
      eventId: eventId,
      amountMsat: amountMsat,
      relays: _relays,
    );
    final event = EventApi().finishEvent(
      Event(
        pubkey: draft.pubkey,
        created_at: draft.createdAt,
        kind: draft.kind,
        tags: draft.tags,
        content: draft.content,
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
      final request = LnurlProtocol.buildInvoiceRequest(
        payInfo: payInfo,
        amountMsat: amountMsat,
        zapRequest: zapRequest,
      );
      if (request == null) return null;
      final data = await _boundedJsonGet(
        request.uri,
        timeout: const Duration(seconds: 10),
      );
      if (data == null) return null;
      return LnurlProtocol.validateInvoiceResponse(
        data: data,
        request: request,
        amountMsat: amountMsat,
        nowUnixSeconds: DateTime.now().millisecondsSinceEpoch ~/ 1000,
      );
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
    final connection = NwcConnection.tryParse(
      nwcUri,
      fallbackRelay: _nwcFallbackRelay,
    );
    if (connection == null) return null;
    final walletPub = connection.walletPubkey;
    final secret = connection.secret;
    final relayUri = connection.relayUri;
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
      // A relay that refuses the upgrade (damus answers 503 under load)
      // must fail here, inside the try, instead of escaping as an
      // unhandled asynchronous error and leaving a REQ on a dead socket.
      await ws.ready.timeout(const Duration(seconds: 5));
      final completer = Completer<String?>();
      final subId = randomSubId();
      final ingress = NostrRelayIngress([
        NostrIngressRule(
          name: 'nwc-response',
          subscriptionId: subId,
          routes: const {23195: NostrIngressRoute.nwcResponse},
        ),
      ]);

      late final dynamic reqEvent;

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            final message = NostrRelayMessageDecoder.decode(raw).message;
            if (message is NostrRelayEventMessage) {
              final ev = message.event;
              final decision = ingress.inspect(
                subscriptionId: message.subscriptionId,
                claimedKind: ev['kind'],
              );
              if (!decision.shouldVerify) return;
              // Step 6: Receive kind-23195 from the wallet and decrypt.
              if (NwcProtocol.responseEventIsBound(
                    ev,
                    walletPubkey: walletPub,
                    requestEventId: reqEvent.id,
                    clientPubkey: ourPub,
                  ) &&
                  verifyEventJson(ev)) {
                final plain =
                    nip04.decrypt(secret, walletPub, ev['content'] as String);
                final resp = jsonDecode(plain) as Map<String, dynamic>;
                final response = NwcProtocol.inspectResponse(
                  resp,
                  decodedInvoice,
                );
                if (response.shouldComplete) {
                  completer.complete(response.preimage);
                }
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
      final reqContent = NwcProtocol.payInvoiceCommand(invoice);
      final encrypted = nip04.encrypt(secret, walletPub, reqContent);
      final requestDraft = NwcProtocol.requestDraft(
        clientPubkey: ourPub,
        createdAt: DateTime.now().millisecondsSinceEpoch ~/ 1000,
        walletPubkey: walletPub,
        encryptedContent: encrypted,
      );

      reqEvent = api.finishEvent(
        Event(
          pubkey: requestDraft.pubkey,
          created_at: requestDraft.createdAt,
          kind: requestDraft.kind,
          tags: requestDraft.tags,
          content: requestDraft.content,
        ),
        secret,
      );

      // Step 5: Subscribe to the response BEFORE publishing the request, so we
      // don't miss a fast wallet response. The `#e` filter targets this specific
      // request by event ID.
      ws.sink.add(jsonEncode(NwcProtocol.responseRequest(
        subscriptionId: subId,
        walletPubkey: walletPub,
        requestEventId: reqEvent.id,
      )));
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

  /// Returns the total **millisatoshi** received by a specific road event, by
  /// summing the `amount` tags of the NIP-57 kind-9735 zap receipts that
  /// reference [eventId] via the `#e` filter.
  ///
  /// Every relay is asked at once and the receipts are merged by event id, so
  /// a receipt that only reached one of them is still counted exactly once.
  static Future<int> fetchZapTotal(
      String eventId, String recipientPubHex) async {
    final signer = await resolveZapSigner(recipientPubHex);
    if (signer == null) return 0;
    // One shared map for all four relays: a receipt stored on several of them
    // is verified once (Schnorr verification is the expensive part here) and
    // counted once.
    final receipts = <String, int>{};
    await Future.wait([
      for (final relay in _relays)
        _fetchReceiptsFrom(relay,
            eventId: eventId,
            recipientPub: recipientPubHex,
            signer: signer,
            into: receipts),
    ]);
    return receipts.values.fold<int>(0, (a, b) => a + b);
  }

  /// Receipts held by one relay: event id → verified millisatoshi.
  ///
  /// [eventId] filters by road report (`#e`); passing null sums every receipt
  /// addressed to [recipientPub] (`#p`), which is the lifetime balance.
  static Future<void> _fetchReceiptsFrom(
    String relay, {
    String? eventId,
    required String recipientPub,
    required String signer,
    required Map<String, int> into,
    int limit = 500,
    Duration timeout = const Duration(seconds: 6),
  }) async {
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(relay));
      // A relay that refuses the upgrade (damus answers 503 under load)
      // must fail here, inside the try, instead of escaping as an
      // unhandled asynchronous error and leaving a REQ on a dead socket.
      await ws.ready.timeout(const Duration(seconds: 5));
      final completer = Completer<void>();
      final subId = randomSubId();
      // [limit] is only a hint in the REQ; a relay may answer with as many
      // receipts as it wants and each one costs a Schnorr verification. Count
      // the messages that actually arrive and hang up at the ceiling, before
      // paying for the signature check.
      final ingress = NostrRelayIngress([
        NostrIngressRule(
          name: 'zap-receipts',
          subscriptionId: subId,
          routes: const {9735: NostrIngressRoute.zapReceiptQuery},
          maxEvents: limit,
        ),
      ]);

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            final message = NostrRelayMessageDecoder.decode(raw).message;
            if (message is NostrRelayEventMessage) {
              final event = message.event;
              final decision = ingress.inspect(
                subscriptionId: message.subscriptionId,
                claimedKind: event['kind'],
              );
              if (decision.limitReached) {
                completer.complete();
                return;
              }
              if (!decision.shouldVerify) return;
              final id = event['id'] as String?;
              if (id != null && !into.containsKey(id)) {
                final amount = verifiedReceiptAmount(
                  event,
                  eventId: eventId,
                  recipientPub: recipientPub,
                  receiptSigner: signer,
                );
                if (amount != null) into[id] = amount;
              }
            } else if (message is NostrRelayEoseMessage &&
                message.subscriptionId == subId) {
              if (!completer.isCompleted) completer.complete();
            }
          } catch (_) {}
        },
        onError: (_) {
          if (!completer.isCompleted) completer.complete();
        },
        onDone: () {
          if (!completer.isCompleted) completer.complete();
        },
      );
      ws.sink.add(jsonEncode([
        'REQ',
        subId,
        {
          'kinds': [9735],
          if (eventId != null) '#e': [eventId] else '#p': [recipientPub],
          'limit': limit,
        }
      ]));
      await completer.future.timeout(timeout, onTimeout: () {});
    } catch (_) {
      // A relay that fails contributes nothing; the others still counted.
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }

  /// Returns the total **satoshi** received by [pubHex] across all their events,
  /// by summing `amount` tags from kind-9735 receipts that tag this public key
  /// (`#p` filter). Used to display the user's lifetime zap earnings on the
  /// profile screen.
  static Future<int> fetchBalance(String pubHex) async {
    final signer = await resolveZapSigner(pubHex);
    if (signer == null) return 0;
    final receipts = <String, int>{};
    await Future.wait([
      for (final relay in _relays)
        // A lifetime balance can span many more receipts than one report's.
        _fetchReceiptsFrom(relay,
            recipientPub: pubHex,
            signer: signer,
            into: receipts,
            limit: 1000,
            timeout: const Duration(seconds: 8)),
    ]);
    return receipts.values.fold<int>(0, (a, b) => a + b);
  }

  /// A relay is not proof of payment. NIP-57 ultimately trusts the recipient's
  /// LNURL provider, so a receipt is counted only when it is signed by the
  /// provider advertised in LNURL metadata and all invoice/request bindings
  /// agree. A preimage is checked when present, but the spec makes it optional
  /// and it cannot make a rogue provider's receipt independently trustworthy.
  static int? verifiedReceiptAmount(
    Map<String, dynamic> receipt, {
    String? eventId,
    String? recipientPub,
    required String receiptSigner,
  }) {
    try {
      final envelope = Nip57Protocol.inspectReceipt(
        receipt,
        receiptSigner: receiptSigner,
        verifySignature: () => verifyEventJson(receipt),
      );
      if (envelope == null) return null;
      final invoice = Bolt11Invoice.tryParse(envelope.bolt11);
      if (invoice == null) return null;
      final request =
          (jsonDecode(envelope.description) as Map).cast<String, dynamic>();
      return Nip57Protocol.boundReceiptAmount(
        envelope: envelope,
        invoice: invoice,
        request: request,
        verifyRequestSignature: () => verifyEventJson(request),
        eventId: eventId,
        recipientPubkey: recipientPub,
      );
    } catch (_) {
      return null;
    }
  }

  static bool _isHex32(String value) =>
      RegExp(r'^[0-9a-fA-F]{64}$').hasMatch(value);

  static Future<String?> resolveZapSigner(String recipientPubHex) async {
    final address = await fetchLightningAddress(recipientPubHex);
    if (address == null) return null;
    final info = await fetchLnurlPayInfo(address);
    return info?.allowsNostr == true ? info!.nostrPubkey : null;
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
      // Total deadline for the body, not a per-chunk inactivity timeout: the
      // latter restarts on every byte, so a peer trickling one byte before
      // each expiry would hold the request open indefinitely. Same rule as
      // [BoundedHttp].
      await (() async {
        await for (final chunk in response.stream) {
          if (bytes.length + chunk.length > maxBytes) {
            throw const HttpException('LNURL response is too large');
          }
          bytes.addAll(chunk);
        }
      })()
          .timeout(timeout);
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
    if (!LnurlProtocol.isSafeHttpsUri(uri)) return false;
    try {
      final addresses = await InternetAddress.lookup(uri.host)
          .timeout(const Duration(seconds: 4));
      return addresses.isNotEmpty && addresses.every(_isPublicAddress);
    } catch (_) {
      return false;
    }
  }

  /// Exposed so the SSRF address filter can be tested directly — resolving a
  /// hostname that maps to a private range is not something a unit test can
  /// arrange reliably.
  @visibleForTesting
  static bool isPublicAddress(InternetAddress address) =>
      _isPublicAddress(address);

  static bool _isPublicAddress(InternetAddress address) {
    if (address.type == InternetAddressType.IPv4) {
      return _isPublicV4(address.rawAddress);
    }
    final b = address.rawAddress;
    if (b.length != 16) return false;
    // An IPv6 address can carry an IPv4 one inside it: ::ffff:127.0.0.1 and
    // ::127.0.0.1 both reach the loopback interface, and 64:ff9b::/96 does the
    // same through NAT64. Judge those by their embedded v4 address, otherwise
    // the whole private-range test below simply never looks at the bytes that
    // decide where the packet goes.
    final v4Mapped =
        b.take(10).every((v) => v == 0) && b[10] == 0xff && b[11] == 0xff;
    final v4Compatible = b.take(12).every((v) => v == 0) && !(b[12] == 0);
    final nat64 = b[0] == 0x00 &&
        b[1] == 0x64 &&
        b[2] == 0xff &&
        b[3] == 0x9b &&
        b.skip(4).take(8).every((v) => v == 0);
    if (v4Mapped || v4Compatible || nat64) {
      return _isPublicV4(b.sublist(12));
    }
    final unspecifiedOrLoopback =
        b.take(15).every((v) => v == 0) && (b[15] == 0 || b[15] == 1);
    final linkLocal = b[0] == 0xfe && (b[1] & 0xc0) == 0x80;
    final uniqueLocal = (b[0] & 0xfe) == 0xfc;
    final multicast = b[0] == 0xff;
    return !(unspecifiedOrLoopback || linkLocal || uniqueLocal || multicast);
  }

  static bool _isPublicV4(List<int> b) {
    if (b.length != 4) return false;
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
}
