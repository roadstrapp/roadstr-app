// Shared Nostr event verification.
//
// Relays are untrusted: any event JSON received from a WebSocket must have
// its id recomputed from the canonical serialization and its BIP-340 Schnorr
// signature checked against the claimed pubkey before the content is used.
// Without this, a malicious relay can fabricate road events, confirmations,
// profiles — or worse, swap the Lightning address in a kind-0 profile and
// silently redirect zaps.
import 'dart:math';

import 'package:nostr_tools/nostr_tools.dart';

import 'nostr_protocol_codec.dart';

final _eventApi = EventApi();
final _rng = Random.secure();

/// Random 16-hex-char NIP-01 subscription id.
///
/// Subscription ids are chosen by the client and visible to the relay in
/// plaintext. Semantic prefixes ("fav-", "prof-", "rs-"…) let a relay
/// fingerprint the app and the *purpose* of each request — e.g. a favorites
/// fetch announced itself as one, undoing the work of hiding the event's
/// 'd' tag. Every subscription must use an opaque random id instead.
String randomSubId() =>
    List.generate(16, (_) => _rng.nextInt(16).toRadixString(16)).join();

/// Returns true when [json] is a well-formed Nostr event whose id matches the
/// canonical hash AND whose signature verifies against its pubkey.
/// Any malformed or forged event returns false — never throws.
bool verifyEventJson(Map<String, dynamic> json) {
  try {
    final draft = nostrEventDraftFromJson(json);
    final ev = Event(
      kind: draft.kind,
      tags: draft.tags.map((tag) => tag.toList()).toList(),
      content: draft.content,
      created_at: draft.createdAt,
      id: json['id'] as String,
      sig: json['sig'] as String,
      pubkey: draft.pubkey,
    );
    if (draft.id != ev.id) return false;
    return _eventApi.verifySignature(ev);
  } catch (_) {
    return false;
  }
}
