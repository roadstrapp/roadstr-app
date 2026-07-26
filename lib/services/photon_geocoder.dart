import 'dart:convert';

import 'package:latlong2/latlong.dart';

import 'bounded_http.dart';
import 'routing_service.dart' show NominatimResult;

/// Typo-tolerant, prefix-based geocoder backed by komoot's public **Photon**
/// service (OpenStreetMap data, no API key, free for reasonable use).
///
/// **Why alongside Nominatim.** Nominatim is a strict full-text matcher: every
/// token of the query has to be found, spelled correctly, in the indexed name.
/// Two very common real-world inputs therefore return *nothing at all*:
///
///   * a typo — "via attillio monti";
///   * a longer-than-OSM name — the user types "via attilio monti" while OSM
///     has the street as "via monti".
///
/// Photon is built on Elasticsearch with fuzzy matching and edge n-grams, so it
/// answers both, and it answers while the user is still typing (it is designed
/// as an autocomplete backend — typically a few hundred ms against Nominatim's
/// seconds). Nominatim is still queried in parallel because it remains better
/// at exact, fully-qualified addresses and at house-number interpolation.
class PhotonGeocoder {
  static const _endpoint = 'https://photon.komoot.io/api/';

  /// Languages Photon actually has localised indexes for. Anything else must
  /// be sent without `lang` — the service hard-rejects an unknown value with
  /// `{"lang":[{"message":"Language is not supported…"}]}` and no results
  /// (verified live), so this list must not be widened optimistically.
  static const _supportedLangs = {'en', 'de', 'fr'};

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
    if (q.isEmpty) return [];
    try {
      final bias = near == null
          ? ''
          : '&lat=${near.latitude.toStringAsFixed(5)}'
              '&lon=${near.longitude.toStringAsFixed(5)}'
              // Pulls nearby hits up without hard-filtering distant ones —
              // "Via Roma" in the next town over must stay reachable.
              '&location_bias_scale=0.3&zoom=12';
      final lang = _supportedLangs.contains(languageCode)
          ? '&lang=$languageCode'
          : '';
      final uri = Uri.parse('$_endpoint?q=${Uri.encodeQueryComponent(q)}'
          '&limit=$limit$bias$lang');
      final res = await BoundedHttp.get(
        uri,
        headers: {'User-Agent': 'Roadstr/1.0'},
        maxBytes: 2 * 1024 * 1024,
        // Short on purpose: Photon is the "fast" provider of the pair. If it
        // cannot answer within this budget, Nominatim's reply is already due.
        timeout: const Duration(seconds: 4),
      );
      if (res.statusCode != 200) return [];
      final data = jsonDecode(res.body) as Map<String, dynamic>;
      final features = (data['features'] as List?) ?? const [];
      final out = <NominatimResult>[];
      for (final f in features) {
        // Parse defensively: one malformed feature must skip only itself.
        try {
          final parsed = _fromFeature(f as Map<String, dynamic>);
          if (parsed != null) out.add(parsed);
        } catch (_) {}
      }
      return out;
    } catch (_) {
      return [];
    }
  }

  /// Converts one Photon GeoJSON feature into the app-wide result type.
  static NominatimResult? _fromFeature(Map<String, dynamic> f) {
    final coords = (f['geometry'] as Map?)?['coordinates'] as List?;
    if (coords == null || coords.length < 2) return null;
    final lon = (coords[0] as num).toDouble();
    final lat = (coords[1] as num).toDouble();
    if (!lat.isFinite ||
        !lon.isFinite ||
        lat < -90 ||
        lat > 90 ||
        lon < -180 ||
        lon > 180) {
      return null;
    }

    final p = (f['properties'] as Map?)?.cast<String, dynamic>() ?? {};
    String? str(String key) {
      final v = p[key];
      if (v is! String) return null;
      // Network input: strip control characters before it reaches a widget.
      final s = v.replaceAll(RegExp(r'[\u0000-\u001f]'), ' ').trim();
      return s.isEmpty ? null : (s.length <= 160 ? s : s.substring(0, 160));
    }

    final name = str('name');
    final street = str('street');
    final houseNumber = str('housenumber');
    final city = str('city') ?? str('town') ?? str('village') ?? str('county');
    final state = str('state');
    final country = str('country');

    // Same "road house-number, city" shape Nominatim results are normalised to,
    // so both providers render identically in the suggestion list.
    String short;
    if (street != null) {
      short = houseNumber != null ? '$street $houseNumber' : street;
      if (city != null) short += ', $city';
    } else if (name != null) {
      short = city != null && city != name ? '$name, $city' : name;
    } else if (city != null) {
      short = city;
    } else {
      return null; // nothing nameable to show
    }

    final display = [
      if (name != null && name != street) name,
      if (street != null)
        houseNumber != null ? '$street $houseNumber' : street,
      city,
      state,
      country,
    ].whereType<String>().toSet().join(', ');

    return NominatimResult(
      displayName: display.isEmpty ? short : display,
      shortName: short,
      position: LatLng(lat, lon),
      // Photon exposes the OSM tag as osm_key/osm_value — the exact pair
      // NominatimResult already maps to an emoji and a category label.
      cls: str('osm_key'),
      type: str('osm_value'),
      city: city,
    );
  }
}
