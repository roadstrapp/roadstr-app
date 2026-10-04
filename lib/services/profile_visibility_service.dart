import 'dart:convert';

import 'package:amberflutter/amberflutter.dart';

import 'nostr_nip19.dart';
import 'nostr_relay_service.dart';
import 'app_secure_storage.dart';

/// Persists and publishes the Roadstr profile-visibility preference.
///
/// The local setting is the source of truth for the current user's UI.  When
/// the user changes it, a signed replaceable Nostr event is published so other
/// Roadstr clients can make the same pseudonymous/clear presentation choice.
class ProfileVisibilityService {
  static const storageKey = 'roadstr_profile_public';
  static const _st = appSecureStorage;

  static Future<void> publish({required bool isPublic}) async {
    final pub = await _st.read(key: 'nostr_pub_hex');
    final flavor = await _st.read(key: 'nostr_flavor');
    if (pub == null || flavor == null) return;

    final relay = NostrRelayService();
    try {
      await relay.connect();
      if (flavor == 'amber') {
        final unsigned = NostrRelayService.buildProfileVisibilityMap(
            pubKeyHex: pub, isPublic: isPublic);
        final result = await Amberflutter().signEvent(
          currentUser: NostrNip19.encodePublicKey(pub),
          eventJson: jsonEncode(unsigned),
        );
        final signed =
            jsonDecode(result['event'] as String) as Map<String, dynamic>;
        await relay.publishRawEvent(signed, expectedUnsigned: unsigned);
      } else {
        final priv = await _st.read(key: 'nostr_priv_hex');
        if (priv == null) return;
        await relay.publishProfileVisibility(
          privKeyHex: priv,
          pubKeyHex: pub,
          isPublic: isPublic,
        );
      }
    } finally {
      relay.dispose();
    }
  }
}
