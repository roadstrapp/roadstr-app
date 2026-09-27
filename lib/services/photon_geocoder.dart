import 'package:latlong2/latlong.dart';

import 'bounded_http.dart';
import 'search_provider_protocol.dart';
import 'search_response_protocol.dart';

/// Typo-tolerant, prefix-based geocoder backed by komoot's public **Photon**
/// service (OpenStreetMap data, no API key, free for reasonable use).
///
/// **Why alongside Nominatim.** Nominatim is a strict full-text matcher: every
/// token of the query has to be found, spelled correctly, in the indexed name.
/// Two very common real-world inputs therefore return *nothing at all*:
///
///   * a typo — "via robberto ricci";
///   * a longer-than-OSM name — the user types "via roberto ricci" while OSM
///     has the street as "via ricci".
///
/// Photon is built on Elasticsearch with fuzzy matching and edge n-grams, so it
/// answers both, and it answers while the user is still typing (it is designed
/// as an autocomplete backend — typically a few hundred ms against Nominatim's
/// seconds). Nominatim is still queried in parallel because it remains better
/// at exact, fully-qualified addresses and at house-number interpolation.
class PhotonGeocoder {
  /// Longest query worth sending. A place name never approaches this; the cap
  /// exists so a pasted wall of text cannot be turned into a giant URL or into
  /// a quadratic amount of client-side fuzzy scoring on every keystroke.
  static const maxQueryLength = SearchProviderProtocol.photonMaxQueryLength;

  /// Searches for [query], biased toward [near] when a GPS fix is available.
  ///
  /// Returns an empty list on any failure: this is one of several parallel
  /// providers, and a dead mirror must never break the whole search.
  static Future<List<NominatimResult>> search(
    String query, {
    LatLng? near,
    String languageCode = 'en',
    int limit = 8,
  }) async {
    final q = query.trim();
    if (q.isEmpty || q.length > maxQueryLength) return [];
    try {
      // Two decimals ≈ 1 km. The bias only needs to say which town the user is
      // in — `zoom=12` is a town-sized hint anyway — so there is no reason to
      // hand a third party the metre-accurate position of the driver. This
      // deliberately discloses less than the ±0.25° viewbox already sent to
      // Nominatim, which is coarser still.
      // Pulls nearby hits up without hard-filtering distant ones — "Via Roma"
      // in the next town over must stay reachable. Unsupported language
      // codes are deliberately omitted because Photon rejects them outright.
      final request = SearchProviderProtocol.photonSearch(
        q,
        latitude: near?.latitude,
        longitude: near?.longitude,
        languageCode: languageCode,
        limit: limit,
      )!;
      final res = await BoundedHttp.get(
        request.uri,
        headers: request.headers,
        maxBytes: 2 * 1024 * 1024,
        // Short on purpose: Photon is the "fast" provider of the pair. If it
        // cannot answer within this budget, Nominatim's reply is already due.
        timeout: const Duration(seconds: 4),
      );
      if (res.statusCode != 200) return [];
      return SearchResponseProtocol.parsePhoton(res.body);
    } catch (_) {
      return [];
    }
  }
}
