import 'dart:convert';

import 'package:latlong2/latlong.dart';

typedef NominatimReverseDetail = ({
  String display,
  String? wikiQuery,
  String? openingHours,
  String label,
});

/// The app-wide normalized result produced by every place-search provider.
class NominatimResult {
  final String displayName;
  final String shortName;
  final LatLng position;
  final String? cls;
  final String? type;
  final String? city;
  final String? openingHours;
  final double? distanceM;
  final String? brand;

  const NominatimResult({
    required this.displayName,
    required this.shortName,
    required this.position,
    this.cls,
    this.type,
    this.city,
    this.openingHours,
    this.distanceM,
    this.brand,
  });

  static const maxRemoteTextChars = 300;

  static String? clampRemoteText(dynamic value,
      [int max = maxRemoteTextChars]) {
    if (value is! String) return null;
    final clean = value.trim();
    if (clean.isEmpty) return null;
    return clean.length <= max ? clean : clean.substring(0, max);
  }

  String get emoji => _categoryEmoji(cls, type);

  String get categoryLabel {
    if (cls == 'highway') {
      final parts = displayName.split(',').map((p) => p.trim()).toList();
      return parts.skip(2).where((p) => p.isNotEmpty).take(2).join(', ');
    }
    final mapped = _categoryLabel(cls, type);
    if (mapped != null) return mapped;
    final parts = displayName.split(',');
    return parts.length > 1 ? parts[1].trim() : '';
  }

  static String _categoryEmoji(String? cls, String? type) {
    switch (cls) {
      case 'highway':
        return '🛣️';
      case 'place':
        return switch (type) {
          'city' || 'town' => '🏙️',
          'village' || 'hamlet' => '🏘️',
          'suburb' || 'neighbourhood' => '🏡',
          _ => '📍',
        };
      case 'amenity':
        return switch (type) {
          'restaurant' || 'fast_food' || 'food_court' => '🍽️',
          'cafe' || 'coffee_shop' => '☕',
          'bar' || 'pub' || 'nightclub' => '🍺',
          'hospital' || 'clinic' || 'doctors' => '🏥',
          'pharmacy' => '💊',
          'school' || 'kindergarten' => '🏫',
          'university' || 'college' => '🎓',
          'bank' || 'atm' => '🏦',
          'fuel' || 'charging_station' => '⛽',
          'parking' => '🅿️',
          'police' => '👮',
          'post_office' => '📮',
          'library' => '📚',
          'theatre' || 'cinema' => '🎭',
          'place_of_worship' => '⛪',
          'marketplace' => '🛒',
          'townhall' => '🏛️',
          _ => '📍',
        };
      case 'tourism':
        return switch (type) {
          'museum' => '🏛️',
          'hotel' || 'hostel' || 'motel' || 'guest_house' => '🏨',
          'attraction' || 'monument' || 'viewpoint' => '🗺️',
          'artwork' || 'gallery' => '🎨',
          'camp_site' => '⛺',
          'theme_park' || 'zoo' => '🎡',
          _ => '🗺️',
        };
      case 'shop':
        return switch (type) {
          'supermarket' || 'convenience' => '🛒',
          'bakery' => '🥖',
          'clothes' || 'fashion' => '👗',
          'electronics' => '📱',
          'books' => '📚',
          'florist' => '💐',
          _ => '🛍️',
        };
      case 'office':
        return switch (type) {
          'government' || 'administrative' => '🏛️',
          'company' || 'commercial' => '🏢',
          'ngo' || 'association' => '🏢',
          _ => '🏢',
        };
      case 'building':
        return switch (type) {
          'public' || 'government' => '🏛️',
          'hospital' => '🏥',
          'school' || 'university' => '🎓',
          _ => '🏗️',
        };
      case 'natural':
        return switch (type) {
          'beach' => '🏖️',
          'water' || 'lake' => '💧',
          'peak' || 'hill' => '⛰️',
          'wood' || 'forest' => '🌲',
          _ => '🌿',
        };
      case 'leisure':
        return switch (type) {
          'park' || 'garden' => '🌳',
          'sports_centre' || 'stadium' => '🏟️',
          'swimming_pool' => '🏊',
          _ => '🎭',
        };
      case 'historic':
        return '🏛️';
      case 'railway':
        return '🚉';
      case 'aeroway':
        return '✈️';
      case 'waterway':
        return '🌊';
      case 'landuse':
        return '🗺️';
      default:
        return '📍';
    }
  }

  static String? _categoryLabel(String? cls, String? type) {
    switch (cls) {
      case 'highway':
        return 'Road';
      case 'place':
        return switch (type) {
          'city' => 'City',
          'town' => 'Town',
          'village' => 'Village',
          _ => 'Place',
        };
      case 'amenity':
        return switch (type) {
          'restaurant' => 'Restaurant',
          'fast_food' => 'Fast food',
          'cafe' => 'Café',
          'bar' || 'pub' => 'Bar / Pub',
          'hospital' => 'Hospital',
          'pharmacy' => 'Pharmacy',
          'school' => 'School',
          'university' => 'University',
          'bank' => 'Bank',
          'atm' => 'ATM',
          'fuel' => 'Petrol station',
          'parking' => 'Parking',
          'police' => 'Police',
          'post_office' => 'Post office',
          'library' => 'Library',
          'theatre' => 'Theatre',
          'cinema' => 'Cinema',
          'place_of_worship' => 'Place of worship',
          'townhall' => 'Town hall',
          _ => 'Service',
        };
      case 'tourism':
        return switch (type) {
          'museum' => 'Museum',
          'hotel' || 'hostel' || 'motel' => 'Hotel',
          'attraction' || 'monument' => 'Attraction / Monument',
          'artwork' => 'Artwork',
          'gallery' => 'Gallery',
          _ => 'Tourism',
        };
      case 'shop':
        return 'Shop';
      case 'office':
        return 'Office';
      case 'historic':
        return 'Historic site';
      case 'leisure':
        return 'Leisure';
      case 'natural':
        return 'Natural area';
      case 'railway':
        return 'Railway / Station';
      case 'aeroway':
        return 'Airport';
      default:
        return null;
    }
  }

  factory NominatimResult.fromJson(Map<String, dynamic> json) {
    final lat = double.tryParse(json['lat'] as String) ?? double.nan;
    final lon = double.tryParse(json['lon'] as String) ?? double.nan;
    if (!lat.isFinite ||
        !lon.isFinite ||
        lat < -90 ||
        lat > 90 ||
        lon < -180 ||
        lon > 180) {
      throw const FormatException('Nominatim: invalid coordinates');
    }
    final display = clampRemoteText(json['display_name']);
    if (display == null) throw const FormatException('Nominatim: no name');
    final clsValue = json['class'] as String?;
    final address = (json['address'] as Map<String, dynamic>?) ?? {};
    final road = clampRemoteText(address['road'], 120);
    final houseNumber = clampRemoteText(address['house_number'], 24);
    final city = clampRemoteText(
      address['city'] ??
          address['town'] ??
          address['village'] ??
          address['hamlet'] ??
          address['municipality'],
      120,
    );

    String short;
    if (road != null && houseNumber != null) {
      short = '$road $houseNumber';
      if (city != null) short += ', $city';
    } else if (road != null && clsValue == 'highway') {
      short = road;
      if (city != null) short += ', $city';
    } else {
      short = display.split(',').first.trim();
    }

    final extraTags = (json['extratags'] as Map<String, dynamic>?) ?? {};
    return NominatimResult(
      displayName: display,
      shortName: short,
      position: LatLng(lat, lon),
      cls: clampRemoteText(clsValue, 80),
      type: clampRemoteText(json['type'], 80),
      city: city,
      brand: clampRemoteText(extraTags['brand'], 160),
    );
  }
}

/// Socket-free decoding and normalization of every shipped search response.
class SearchResponseProtocol {
  static List<NominatimResult> parseNominatimSearch(String body) {
    try {
      final values = jsonDecode(body) as List;
      final results = <NominatimResult>[];
      for (final value in values) {
        try {
          results.add(
            NominatimResult.fromJson(value as Map<String, dynamic>),
          );
        } catch (_) {}
      }
      return results;
    } catch (_) {
      return const [];
    }
  }

  static NominatimReverseDetail? parseNominatimReverse(String body) {
    try {
      final data = jsonDecode(body) as Map<String, dynamic>;
      final display = data['display_name'] as String? ?? '';
      final address = data['address'] as Map<String, dynamic>? ?? {};
      final extraTags = data['extratags'] as Map<String, dynamic>? ?? {};
      final openingHours = (extraTags['opening_hours'] as String?)?.trim();

      bool isNumber(String? value) =>
          value == null || RegExp(r'^\d+$').hasMatch(value.trim());

      final poiName = [
        data['name'] as String?,
        address['tourism'] as String?,
        address['amenity'] as String?,
        address['historic'] as String?,
        address['leisure'] as String?,
        address['suburb'] as String?,
        address['quarter'] as String?,
        address['neighbourhood'] as String?,
      ].where((s) => s != null && s.isNotEmpty && !isNumber(s)).firstOrNull;
      final city = [
        address['city'] as String?,
        address['town'] as String?,
        address['village'] as String?,
        address['municipality'] as String?,
        address['county'] as String?,
      ].where((s) => s != null && s.isNotEmpty && !isNumber(s)).firstOrNull;

      final String? wikiQuery;
      if (poiName != null) {
        wikiQuery =
            city != null && city != poiName ? '$poiName $city' : poiName;
      } else {
        wikiQuery = city;
      }

      return (
        display: display,
        wikiQuery: wikiQuery,
        openingHours: openingHours != null && openingHours.isNotEmpty
            ? openingHours
            : null,
        label: shortLabelFrom(
          display,
          address,
          name: data['name'] as String?,
        ),
      );
    } catch (_) {
      return null;
    }
  }

  static List<NominatimResult> parsePhoton(String body) {
    try {
      final data = jsonDecode(body) as Map<String, dynamic>;
      final features = data['features'] as List? ?? const [];
      final results = <NominatimResult>[];
      for (final value in features) {
        try {
          final parsed = _photonFeature(value as Map<String, dynamic>);
          if (parsed != null) results.add(parsed);
        } catch (_) {}
      }
      return results;
    } catch (_) {
      return const [];
    }
  }

  static List<Map<String, dynamic>> parseOverpassElements(String body) {
    final data = jsonDecode(body) as Map<String, dynamic>;
    final elements = data['elements'] as List?;
    if (elements == null) return const [];
    return elements.whereType<Map<String, dynamic>>().toList(growable: false);
  }

  static NominatimResult? overpassElementToResult(
    Map<String, dynamic> element,
    LatLng center, {
    String? fallbackName,
  }) {
    final tags = (element['tags'] as Map?)?.cast<String, dynamic>();
    var name = tags?['name'] as String?;
    if (name == null || name.isEmpty) {
      name = (tags?['brand'] as String?)?.trim();
    }
    if (name == null || name.isEmpty) name = fallbackName;
    if (name == null || name.isEmpty) return null;
    double? lat = (element['lat'] as num?)?.toDouble();
    double? lon = (element['lon'] as num?)?.toDouble();
    if (lat == null || lon == null) {
      final value = element['center'] as Map?;
      lat = (value?['lat'] as num?)?.toDouble();
      lon = (value?['lon'] as num?)?.toDouble();
    }
    if (lat == null || lon == null || !lat.isFinite || !lon.isFinite) {
      return null;
    }
    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;

    final cls = tags?['shop'] != null
        ? 'shop'
        : tags?['amenity'] != null
            ? 'amenity'
            : tags?['tourism'] != null
                ? 'tourism'
                : null;
    final type = NominatimResult.clampRemoteText(
      tags?['shop'] ?? tags?['amenity'] ?? tags?['tourism'],
      80,
    );
    final position = LatLng(lat, lon);
    final safeName = NominatimResult.clampRemoteText(name, 120);
    if (safeName == null) return null;
    return NominatimResult(
      displayName: safeName,
      shortName: safeName,
      position: position,
      cls: cls,
      type: type,
      openingHours:
          NominatimResult.clampRemoteText(tags?['opening_hours'], 300),
      distanceM: const Distance().as(LengthUnit.Meter, center, position),
    );
  }

  static String shortLabelFrom(
    String display,
    Map<String, dynamic> address, {
    String? name,
  }) {
    const maxPart = 80;
    String? text(Object? value) {
      if (value is! String) return null;
      final clean = value.replaceAll(RegExp(r'[\u0000-\u001f]'), ' ').trim();
      if (clean.isEmpty) return null;
      return clean.length <= maxPart
          ? clean
          : '${clean.substring(0, maxPart)}…';
    }

    final city = text(address['city']) ??
        text(address['town']) ??
        text(address['village']) ??
        text(address['hamlet']) ??
        text(address['municipality']);
    final road = text(address['road']) ?? text(address['pedestrian']);
    final houseNumber = text(address['house_number']);
    final poi = text(name) ??
        text(address['amenity']) ??
        text(address['shop']) ??
        text(address['tourism']) ??
        text(address['historic']) ??
        text(address['leisure']);
    if (poi != null && !RegExp(r'^\d+$').hasMatch(poi)) {
      return city != null && city != poi ? '$poi, $city' : poi;
    }
    if (road != null) {
      final street = houseNumber != null ? '$road $houseNumber' : road;
      return city != null ? '$street, $city' : street;
    }
    final parts = display
        .split(',')
        .map(text)
        .whereType<String>()
        .where((part) => !RegExp(r'^\d+$').hasMatch(part))
        .toList();
    if (parts.isEmpty) return city ?? display.split(',').first.trim();
    return parts.length > 1 && city != null && parts.first != city
        ? '${parts.first}, $city'
        : parts.first;
  }

  static NominatimResult? _photonFeature(Map<String, dynamic> feature) {
    final coordinates = (feature['geometry'] as Map?)?['coordinates'] as List?;
    if (coordinates == null || coordinates.length < 2) return null;
    final lon = (coordinates[0] as num).toDouble();
    final lat = (coordinates[1] as num).toDouble();
    if (!lat.isFinite ||
        !lon.isFinite ||
        lat < -90 ||
        lat > 90 ||
        lon < -180 ||
        lon > 180) {
      return null;
    }

    final properties =
        (feature['properties'] as Map?)?.cast<String, dynamic>() ?? {};
    String? text(String key) {
      final value = properties[key];
      if (value is! String) return null;
      final clean = value.replaceAll(RegExp(r'[\u0000-\u001f]'), ' ').trim();
      if (clean.isEmpty) return null;
      return clean.length <= 160 ? clean : clean.substring(0, 160);
    }

    final name = text('name');
    final street = text('street');
    final houseNumber = text('housenumber');
    final city =
        text('city') ?? text('town') ?? text('village') ?? text('county');
    final state = text('state');
    final country = text('country');

    String short;
    if (street != null) {
      short = houseNumber != null ? '$street $houseNumber' : street;
      if (city != null) short += ', $city';
    } else if (name != null) {
      short = city != null && city != name ? '$name, $city' : name;
    } else if (city != null) {
      short = city;
    } else {
      return null;
    }

    final display = [
      if (name != null && name != street) name,
      if (street != null) houseNumber != null ? '$street $houseNumber' : street,
      city,
      state,
      country,
    ].whereType<String>().toSet().join(', ');
    return NominatimResult(
      displayName: display.isEmpty ? short : display,
      shortName: short,
      position: LatLng(lat, lon),
      cls: text('osm_key'),
      type: text('osm_value'),
      city: city,
    );
  }
}
