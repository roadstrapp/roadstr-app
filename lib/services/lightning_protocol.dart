import 'dart:convert';

import 'bolt11_invoice.dart';
import 'nostr_protocol_codec.dart';

/// Parsed NIP-47 connection material.
///
/// This deliberately is not a record/data object with a verbose `toString`:
/// [secret] is a private key and must not appear in diagnostics.
class NwcConnection {
  const NwcConnection._({
    required this.walletPubkey,
    required this.secret,
    required this.relayUri,
  });

  final String walletPubkey;
  final String secret;
  final Uri relayUri;

  static NwcConnection? tryParse(
    String raw, {
    required String fallbackRelay,
  }) {
    try {
      final uri = Uri.tryParse(raw.trim());
      if (uri == null || uri.scheme != 'nostr+walletconnect') return null;
      if (uri.userInfo.isNotEmpty ||
          uri.path.isNotEmpty ||
          uri.queryParametersAll['secret']?.length != 1 ||
          (uri.queryParametersAll['relay']?.length ?? 0) > 1) {
        return null;
      }
      final walletPubkey = uri.host;
      final secret = uri.queryParameters['secret'];
      final relayUri = Uri.tryParse(
        uri.queryParameters['relay'] ?? fallbackRelay,
      );
      if (!_isHex32(walletPubkey) ||
          secret == null ||
          !_isHex32(secret) ||
          relayUri == null ||
          relayUri.scheme != 'wss' ||
          relayUri.host.isEmpty) {
        return null;
      }
      return NwcConnection._(
        walletPubkey: walletPubkey,
        secret: secret,
        relayUri: relayUri,
      );
    } catch (_) {
      return null;
    }
  }

  @override
  String toString() => 'NwcConnection(<redacted>)';
}

/// Deterministic NIP-47 behavior around the NIP-04 crypto/socket adapters.
abstract final class NwcProtocol {
  static String payInvoiceCommand(String invoice) => jsonEncode({
        'method': 'pay_invoice',
        'params': {'invoice': invoice},
      });

  static NostrEventDraft requestDraft({
    required String clientPubkey,
    required int createdAt,
    required String walletPubkey,
    required String encryptedContent,
  }) {
    if (!_isHex32(clientPubkey) || !_isHex32(walletPubkey)) {
      throw const FormatException('Invalid NWC event key');
    }
    return NostrEventDraft(
      pubkey: clientPubkey,
      createdAt: createdAt,
      kind: 23194,
      tags: [
        ['p', walletPubkey],
      ],
      content: encryptedContent,
    );
  }

  static List<Object?> responseRequest({
    required String subscriptionId,
    required String walletPubkey,
    required String requestEventId,
  }) {
    if (!_isHex32(walletPubkey) || !_isHex32(requestEventId)) {
      throw const FormatException('Invalid NWC response filter');
    }
    return [
      'REQ',
      subscriptionId,
      {
        'kinds': [23195],
        'authors': [walletPubkey],
        '#e': [requestEventId],
      }
    ];
  }

  /// Checks only deterministic event bindings; signature verification remains
  /// an explicit caller responsibility.
  static bool responseEventIsBound(
    Map<String, dynamic> event, {
    required String walletPubkey,
    required String requestEventId,
    required String clientPubkey,
  }) {
    try {
      final tags = (event['tags'] as List?)
              ?.whereType<List>()
              .map((tag) => tag.map((value) => value.toString()).toList())
              .toList() ??
          const <List<String>>[];
      final boundToRequest = tags.any((tag) =>
          tag.length >= 2 && tag[0] == 'e' && tag[1] == requestEventId);
      final addressedToClient = tags.any(
          (tag) => tag.length >= 2 && tag[0] == 'p' && tag[1] == clientPubkey);
      return event['kind'] == 23195 &&
          event['pubkey'] == walletPubkey &&
          boundToRequest &&
          addressedToClient;
    } catch (_) {
      return false;
    }
  }

  /// Classifies the decrypted NIP-47 response while preserving the shipped
  /// distinction between a protocol failure (complete with null) and a
  /// malformed typed field (ignore and keep waiting for another response).
  static NwcResponseDecision inspectResponse(
    Map<String, dynamic> response,
    Bolt11Invoice invoice,
  ) {
    try {
      if (response['result_type'] != 'pay_invoice' ||
          response['error'] != null) {
        return const NwcResponseDecision.failure();
      }
      final rawResult = response['result'];
      if (rawResult != null && rawResult is! Map) {
        return const NwcResponseDecision.ignored();
      }
      final rawPreimage = (rawResult as Map?)?['preimage'];
      if (rawPreimage != null && rawPreimage is! String) {
        return const NwcResponseDecision.ignored();
      }
      final preimage = rawPreimage as String?;
      return preimage != null && invoice.preimageMatches(preimage)
          ? NwcResponseDecision.success(preimage)
          : const NwcResponseDecision.failure();
    } catch (_) {
      return const NwcResponseDecision.ignored();
    }
  }
}

class NwcResponseDecision {
  const NwcResponseDecision._({
    required this.shouldComplete,
    required this.preimage,
  });

  const NwcResponseDecision.ignored()
      : this._(shouldComplete: false, preimage: null);

  const NwcResponseDecision.failure()
      : this._(shouldComplete: true, preimage: null);

  const NwcResponseDecision.success(String preimage)
      : this._(shouldComplete: true, preimage: preimage);

  final bool shouldComplete;
  final String? preimage;
}

class Nip57ReceiptEnvelope {
  Nip57ReceiptEnvelope._({
    required this.bolt11,
    required this.description,
    required this.preimage,
    required this.recipient,
    required this.eventId,
  });

  final String bolt11;
  final String description;
  final String? preimage;
  final String recipient;
  final String? eventId;
}

/// Deterministic NIP-57 layouts and receipt bindings.
abstract final class Nip57Protocol {
  static const maxAmountMsat = 21000000 * 100000000000;

  static NostrEventDraft zapRequestDraft({
    required String senderPubkey,
    required int createdAt,
    required String recipientPubkey,
    required String eventId,
    required int amountMsat,
    required List<String> relays,
  }) {
    if (!_isHex32(senderPubkey) ||
        !_isHex32(recipientPubkey) ||
        !_isHex32(eventId) ||
        amountMsat <= 0 ||
        amountMsat > maxAmountMsat) {
      throw const FormatException('Invalid zap request fields');
    }
    return NostrEventDraft(
      pubkey: senderPubkey,
      createdAt: createdAt,
      kind: 9734,
      tags: [
        ['p', recipientPubkey],
        ['e', eventId],
        ['amount', amountMsat.toString()],
        ['relays', ...List<String>.from(relays)],
      ],
      content: '',
    );
  }

  /// Checks the receipt header and exact cardinality of security-relevant tags.
  /// [signatureValid] must be the result of canonical-id and Schnorr checking.
  static Nip57ReceiptEnvelope? inspectReceipt(
    Map<String, dynamic> receipt, {
    required String receiptSigner,
    required bool Function() verifySignature,
  }) {
    try {
      if (receipt['kind'] != 9735 ||
          receipt['pubkey'] != receiptSigner ||
          !verifySignature()) {
        return null;
      }
      final tags = (receipt['tags'] as List)
          .map((tag) => List<String>.from(tag as List))
          .toList();
      final bolt11Values = <String>[];
      final descriptionValues = <String>[];
      final preimageValues = <String>[];
      final recipients = <String>[];
      final events = <String>[];
      for (final tag in tags) {
        if (tag.length < 2) continue;
        if (tag[0] == 'bolt11') bolt11Values.add(tag[1]);
        if (tag[0] == 'description') descriptionValues.add(tag[1]);
        if (tag[0] == 'preimage') preimageValues.add(tag[1]);
        if (tag[0] == 'p') recipients.add(tag[1]);
        if (tag[0] == 'e') events.add(tag[1]);
      }
      if (bolt11Values.length != 1 ||
          descriptionValues.length != 1 ||
          preimageValues.length > 1 ||
          recipients.length != 1 ||
          events.length > 1) {
        return null;
      }
      return Nip57ReceiptEnvelope._(
        bolt11: bolt11Values.single,
        description: descriptionValues.single,
        preimage: preimageValues.isEmpty ? null : preimageValues.single,
        recipient: recipients.single,
        eventId: events.isEmpty ? null : events.single,
      );
    } catch (_) {
      return null;
    }
  }

  /// Completes receipt validation after [envelope.description] has been parsed
  /// into [request] and its signature has been checked by the caller.
  static int? boundReceiptAmount({
    required Nip57ReceiptEnvelope envelope,
    required Bolt11Invoice invoice,
    required Map<String, dynamic> request,
    required bool Function() verifyRequestSignature,
    String? eventId,
    String? recipientPubkey,
  }) {
    try {
      if (!invoice.descriptionMatches(envelope.description) ||
          (envelope.preimage != null &&
              !invoice.preimageMatches(envelope.preimage!)) ||
          request['kind'] != 9734 ||
          !verifyRequestSignature()) {
        return null;
      }
      final requestTags = (request['tags'] as List)
          .map((tag) => List<String>.from(tag as List))
          .toList();
      final amounts = <String>[];
      final requestEvents = <String>[];
      final requestRecipients = <String>[];
      for (final tag in requestTags) {
        if (tag.length < 2) continue;
        if (tag[0] == 'amount') amounts.add(tag[1]);
        if (tag[0] == 'e') requestEvents.add(tag[1]);
        if (tag[0] == 'p') requestRecipients.add(tag[1]);
      }
      if (amounts.length != 1 ||
          requestRecipients.length != 1 ||
          requestEvents.length > 1 ||
          int.tryParse(amounts.single) != invoice.amountMsat ||
          envelope.recipient != requestRecipients.single ||
          (recipientPubkey != null &&
              requestRecipients.single != recipientPubkey) ||
          (eventId != null &&
              (requestEvents.length != 1 || requestEvents.single != eventId)) ||
          (requestEvents.isEmpty != (envelope.eventId == null)) ||
          (requestEvents.isNotEmpty &&
              envelope.eventId != requestEvents.single)) {
        return null;
      }
      return invoice.amountMsat;
    } catch (_) {
      return null;
    }
  }
}

bool _isHex32(String value) => RegExp(r'^[0-9a-fA-F]{64}$').hasMatch(value);
