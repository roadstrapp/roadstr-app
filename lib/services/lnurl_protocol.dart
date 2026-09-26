import 'dart:convert';

import 'package:bech32/bech32.dart';

import 'bolt11_invoice.dart';

/// Validated LNURL-pay metadata. Amounts are expressed in millisatoshi.
class LnurlPayInfo {
  final String callback;
  final int minSendable;
  final int maxSendable;
  final String metadata;
  final String? nostrPubkey;
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

/// A fully-bound LNURL callback request and the text its invoice must commit to.
class LnurlInvoiceRequest {
  final Uri uri;
  final String description;

  const LnurlInvoiceRequest({required this.uri, required this.description});
}

/// Deterministic LNURL-pay rules shared with the native Kotlin rewrite.
///
/// DNS resolution, redirect handling and HTTP limits remain in the caller: this
/// class owns only parsing, canonical request construction and BOLT-11 binding.
class LnurlProtocol {
  static const maxAddressLength = 254;
  static const maxDecodedLnurlBytes = 2048;
  static const maxMetadataLength = 65536;
  static const maxMetadataEntries = 100;

  /// Resolves either a `user@domain` address or a bech32 `lud06` value to the
  /// HTTPS endpoint from which LNURL-pay metadata must be fetched.
  static Uri? resolveMetadataUri(String input) {
    try {
      if (input.toLowerCase().startsWith('lnurl1')) {
        return _decodeLnurl(input);
      }
      final address = input.trim();
      if (address.length > maxAddressLength) return null;
      final parts = address.split('@');
      if (parts.length != 2) return null;
      final user = parts[0].trim();
      final domain = parts[1].trim();
      if (user.isEmpty || domain.isEmpty || user.contains('/')) return null;
      final uri = Uri(
        scheme: 'https',
        host: domain,
        pathSegments: ['.well-known', 'lnurlp', user],
      );
      return isSafeHttpsUri(uri) ? uri : null;
    } catch (_) {
      return null;
    }
  }

  /// Validates and normalizes an LNURL-pay metadata response.
  static LnurlPayInfo? parsePayInfo(Map<String, dynamic> data) {
    try {
      if (data['status'] == 'ERROR') return null;
      final callback = Uri.tryParse(data['callback'] as String? ?? '');
      final min = (data['minSendable'] as num?)?.toInt();
      final max = (data['maxSendable'] as num?)?.toInt();
      final metadata = data['metadata'] as String?;
      final allowsNostr = data['allowsNostr'] == true;
      final nostrPubkey = data['nostrPubkey'] as String?;
      if (callback == null ||
          !isSafeHttpsUri(callback) ||
          min == null ||
          max == null ||
          metadata == null ||
          metadata.length > maxMetadataLength ||
          !_isValidMetadata(metadata) ||
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

  /// Builds the exact callback URL and records the description hash preimage
  /// that the returned BOLT-11 invoice must contain.
  static LnurlInvoiceRequest? buildInvoiceRequest({
    required LnurlPayInfo payInfo,
    required int amountMsat,
    Map<String, dynamic>? zapRequest,
  }) {
    try {
      if (amountMsat < payInfo.minSendable ||
          amountMsat > payInfo.maxSendable) {
        return null;
      }
      final callback = Uri.tryParse(payInfo.callback);
      if (callback == null || !isSafeHttpsUri(callback)) return null;
      final query = <String, String>{
        ...callback.queryParameters,
        'amount': amountMsat.toString(),
      };
      if (zapRequest != null && payInfo.allowsNostr) {
        query['nostr'] = jsonEncode(zapRequest);
      }
      return LnurlInvoiceRequest(
        uri: callback.replace(queryParameters: query),
        description: query['nostr'] ?? payInfo.metadata,
      );
    } catch (_) {
      return null;
    }
  }

  /// Accepts an invoice only when the callback response, amount, lifetime and
  /// description hash are bound to the request that Roadstr actually sent.
  static String? validateInvoiceResponse({
    required Map<String, dynamic> data,
    required LnurlInvoiceRequest request,
    required int amountMsat,
    required int nowUnixSeconds,
  }) {
    try {
      if (data['status'] == 'ERROR') return null;
      final invoice = data['pr'] as String?;
      if (invoice == null) return null;
      final decoded = Bolt11Invoice.tryParse(invoice);
      if (decoded == null ||
          decoded.amountMsat != amountMsat ||
          decoded.isExpiredAt(nowUnixSeconds) ||
          !decoded.descriptionMatches(request.description)) {
        return null;
      }
      return invoice;
    } catch (_) {
      return null;
    }
  }

  /// Cheap lexical SSRF gate. The caller must additionally resolve DNS and
  /// reject every private/non-routable result immediately before connecting.
  static bool isSafeHttpsUri(Uri uri) {
    if (uri.scheme != 'https' || uri.host.isEmpty || uri.userInfo.isNotEmpty) {
      return false;
    }
    final host = uri.host.toLowerCase();
    if (host == 'localhost' || host.endsWith('.localhost')) return false;
    final ip = RegExp(r'^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$')
        .firstMatch(host);
    if (ip != null) {
      final octets = [for (var i = 1; i <= 4; i++) int.parse(ip.group(i)!)];
      if (octets.any((value) => value > 255) ||
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

  static bool _isValidMetadata(String raw) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List ||
          decoded.isEmpty ||
          decoded.length > maxMetadataEntries) {
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
      if (bytes == null || bytes.length > maxDecodedLnurlBytes) return null;
      final uri = Uri.tryParse(utf8.decode(bytes));
      return uri != null && isSafeHttpsUri(uri) ? uri : null;
    } catch (_) {
      return null;
    }
  }

  static bool _isHex32(String value) =>
      RegExp(r'^[0-9a-fA-F]{64}$').hasMatch(value);
}
