// Shared Nostr event verification.
//
// Relays are untrusted: any event JSON received from a WebSocket must have
// its id recomputed from the canonical serialization and its BIP-340 Schnorr
// signature checked against the claimed pubkey before the content is used.
// Without this, a malicious relay can fabricate road events, confirmations,
// profiles — or worse, swap the Lightning address in a kind-0 profile and
// silently redirect zaps.
import 'package:nostr_tools/nostr_tools.dart';

final _eventApi = EventApi();

/// Returns true when [json] is a well-formed Nostr event whose id matches the
/// canonical hash AND whose signature verifies against its pubkey.
/// Any malformed or forged event returns false — never throws.
bool verifyEventJson(Map<String, dynamic> json) {
  try {
    final ev = Event(
      kind:       json['kind'] as int,
      tags:       (json['tags'] as List)
          .map((t) => List<String>.from(t as List))
          .toList(),
      content:    json['content'] as String? ?? '',
      created_at: json['created_at'] as int,
      id:         json['id'] as String,
      sig:        json['sig'] as String,
      pubkey:     json['pubkey'] as String,
    );
    if (_eventApi.getEventHash(ev) != ev.id) return false;
    return _eventApi.verifySignature(ev);
  } catch (_) {
    return false;
  }
}
