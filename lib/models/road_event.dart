// Road event model for Roadstr.
//
// A road event is a Nostr kind-1315 event published by a driver to alert
// others of a hazard, traffic condition, or point of interest on the road.
// Confirmations (kind-1316) from other users increase or decrease the event's
// credibility score and ultimately its TTL visibility on the map.
import 'package:flutter/material.dart';
import 'package:latlong2/latlong.dart';
import '../l10n/app_localizations.dart';

/// The 13 categories of road events that can be reported in Roadstr.
///
/// Each category maps to a Nostr `t` tag value ([nostrKey]), an emoji, a
/// colour for the map pin, and a client-side TTL ([ttlSeconds]) that controls
/// how long the event remains visible on the map regardless of the relay's
/// `expiration` tag.
///
/// **TTL design rationale:**
/// - Transient hazards (police, fog, animal, traffic jam): 1–4 hours — these
///   situations resolve quickly and stale alerts are actively misleading.
/// - Semi-permanent conditions (construction, road condition): 7–30 days —
///   roadworks and surface damage persist for weeks.
/// - Fixed infrastructure (speed camera): 30 days — cameras rarely move;
///   users benefit from long-lived alerts.
///
/// The relay's `expiration` tag (14-day maximum for kind-1315 events) provides
/// an upper bound; the client TTL provides category-aware tighter limits.
enum RoadCategory {
  police, speedCamera, trafficJam, accident, roadClosure,
  construction, hazard, roadCondition, pothole, fog, ice, animal, other;

  static RoadCategory fromKey(String key) =>
      values.firstWhere((c) => c.nostrKey == key, orElse: () => other);

  String get nostrKey => switch (this) {
    RoadCategory.speedCamera   => 'speed_camera',
    RoadCategory.trafficJam    => 'traffic_jam',
    RoadCategory.roadClosure   => 'road_closure',
    RoadCategory.roadCondition => 'road_condition',
    _ => name,
  };

  String get emoji => switch (this) {
    RoadCategory.police        => '👮',
    RoadCategory.speedCamera   => '📷',
    RoadCategory.trafficJam    => '🚗',
    RoadCategory.accident      => '💥',
    RoadCategory.roadClosure   => '🚫',
    RoadCategory.construction  => '🚧',
    RoadCategory.hazard        => '⚠️',
    RoadCategory.roadCondition => '🛣️',
    RoadCategory.pothole       => '🕳️',
    RoadCategory.fog           => '🌫️',
    RoadCategory.ice           => '🧊',
    RoadCategory.animal        => '🦌',
    RoadCategory.other         => 'ℹ️',
  };

  String localizedLabel(AppLocalizations l) => switch (this) {
    RoadCategory.police        => l.categoryPolice,
    RoadCategory.speedCamera   => l.categorySpeedCamera,
    RoadCategory.trafficJam    => l.categoryTrafficJam,
    RoadCategory.accident      => l.categoryAccident,
    RoadCategory.roadClosure   => l.categoryRoadClosure,
    RoadCategory.construction  => l.categoryConstruction,
    RoadCategory.hazard        => l.categoryHazard,
    RoadCategory.roadCondition => l.categoryRoadCondition,
    RoadCategory.pothole       => l.categoryPothole,
    RoadCategory.fog           => l.categoryFog,
    RoadCategory.ice           => l.categoryIce,
    RoadCategory.animal        => l.categoryAnimal,
    RoadCategory.other         => l.categoryOther,
  };

  /// Non-localised English fallback label for contexts where no [BuildContext] is
  /// available (e.g. background services, Nostr relay callbacks). Prefer
  /// [localizedLabel] in widget code to respect the user's language setting.
  String get label => switch (this) {
    RoadCategory.police        => 'Police',
    RoadCategory.speedCamera   => 'Speed Camera',
    RoadCategory.trafficJam    => 'Traffic Jam',
    RoadCategory.accident      => 'Accident',
    RoadCategory.roadClosure   => 'Road Closure',
    RoadCategory.construction  => 'Construction',
    RoadCategory.hazard        => 'Hazard',
    RoadCategory.roadCondition => 'Road Condition',
    RoadCategory.pothole       => 'Pothole',
    RoadCategory.fog           => 'Fog',
    RoadCategory.ice           => 'Ice',
    RoadCategory.animal        => 'Animal',
    RoadCategory.other         => 'Other',
  };

  Color get color => switch (this) {
    RoadCategory.police        => const Color(0xFF2563EB),
    RoadCategory.speedCamera   => const Color(0xFF7C3AED),
    RoadCategory.trafficJam    => const Color(0xFFD97706),
    RoadCategory.accident      => const Color(0xFFDC2626),
    RoadCategory.roadClosure   => const Color(0xFF991B1B),
    RoadCategory.construction  => const Color(0xFFF59E0B),
    RoadCategory.hazard        => const Color(0xFFF59E0B),
    RoadCategory.roadCondition => const Color(0xFF92400E),
    RoadCategory.pothole       => const Color(0xFF6B7280),
    RoadCategory.fog           => const Color(0xFF9CA3AF),
    RoadCategory.ice           => const Color(0xFF60A5FA),
    RoadCategory.animal        => const Color(0xFF16A34A),
    RoadCategory.other         => const Color(0xFF6B7280),
  };

  /// Client-side TTL (seconds) controlling maximum map visibility per category.
  /// This supplements the Nostr event `expiration` tag, which is a relay-level
  /// hint and may be absent or set to 14 days regardless of event type.
  int get ttlSeconds => switch (this) {
    RoadCategory.police        => 4 * 3600,       // 4 h
    RoadCategory.speedCamera   => 30 * 86400,     // 30 d
    RoadCategory.trafficJam    => 1 * 3600,       // 1 h
    RoadCategory.accident      => 4 * 3600,       // 4 h
    RoadCategory.roadClosure   => 24 * 3600,      // 24 h
    RoadCategory.construction  => 15 * 86400,     // 15 d
    RoadCategory.hazard        => 4 * 3600,       // 4 h
    RoadCategory.roadCondition => 15 * 86400,     // 15 d
    RoadCategory.pothole       => 7 * 86400,      // 7 d
    RoadCategory.fog           => 4 * 3600,       // 4 h
    RoadCategory.ice           => 4 * 3600,       // 4 h
    RoadCategory.animal        => 1 * 3600,       // 1 h
    RoadCategory.other         => 4 * 3600,       // 4 h
  };
}

/// A single road event decoded from a Nostr kind-1315 event.
///
/// The Nostr protocol encodes geographic events using:
///   - `lat` / `lon` tags for exact coordinates.
///   - `g` tags at multiple geohash precision levels (4, 5, 6) to enable
///     efficient relay-side filtering with a single `#g` filter.
///   - `t` tag for the event category (maps to [RoadCategory.nostrKey]).
///   - `expiration` tag (Unix timestamp) for relay-level TTL enforcement.
///
/// [confirmations] and [denials] are mutable because they are incremented
/// in-place as kind-1316 events arrive from the relay stream, avoiding a full
/// map rebuild on every confirmation.
class RoadEvent {
  final String id;
  /// The author's Nostr public key (hex-encoded).
  final String pubkey;
  final RoadCategory category;
  final LatLng position;
  final String comment;
  /// Unix timestamp (seconds) when the event was created.
  final int createdAt;
  /// Optional relay-level expiration timestamp from the Nostr `expiration` tag.
  final int? expiresAt;
  int confirmations;
  int denials;

  RoadEvent({
    required this.id,
    required this.pubkey,
    required this.category,
    required this.position,
    required this.comment,
    required this.createdAt,
    this.expiresAt,
    this.confirmations = 0,
    this.denials = 0,
  });

  /// Returns `true` if the event should be hidden from the map.
  ///
  /// An event is expired when EITHER the relay's `expiration` tag has passed OR
  /// the client-side category TTL has elapsed — whichever comes first.
  bool get isExpired {
    final now = DateTime.now().millisecondsSinceEpoch ~/ 1000;
    // Use only the category-specific TTL for local display filtering.
    // The NIP-40 `expiration` tag (expiresAt) is intentionally ignored here:
    // it is set to 14 days on every event so the relay auto-deletes stale
    // entries, but category TTLs are much shorter (1 h – 15 d) and govern
    // how long each event type remains visible on the map.
    return now > createdAt + category.ttlSeconds;
  }

  /// Returns a human-readable relative age string using [AppLocalizations] for i18n.
  ///
  /// [l] must be obtained from `AppLocalizations.of(context)!` inside a widget.
  /// This method is intentionally not a getter — the [AppLocalizations] dependency
  /// makes it unsuitable for use outside of widget build methods.
  String ageLabel(AppLocalizations l) {
    final mins = (DateTime.now().millisecondsSinceEpoch ~/ 1000 - createdAt) ~/ 60;
    if (mins < 60)   return l.minutesAgo(mins);
    if (mins < 1440) return l.hoursAgo(mins ~/ 60);
    return l.daysAgo(mins ~/ 1440);
  }

  /// Constructs a [RoadEvent] from a raw Nostr kind-1315 event JSON map.
  ///
  /// Returns `null` if required tags (`lat`, `lon`, `t`) are missing, if
  /// coordinates are non-finite, or if the event is already expired.
  /// Coordinate validation mirrors the check in [GpsService._onPosition] —
  /// NaN/Infinity from a malicious or buggy relay must not reach [LatLng].
  static RoadEvent? fromNostr(Map<String, dynamic> json) {
    try {
      final tags = (json['tags'] as List)
          .map((t) => List<String>.from(t as List))
          .toList();

      String? latStr, lonStr, tKey;
      int? exp;
      for (final t in tags) {
        if (t.isEmpty) continue;
        switch (t[0]) {
          case 'lat': latStr = t[1];
          case 'lon': lonStr = t[1];
          case 't':   tKey   = t[1];
          case 'expiration': exp = int.tryParse(t[1]);
        }
      }
      if (latStr == null || lonStr == null || tKey == null) return null;

      final lat = double.tryParse(latStr) ?? double.nan;
      final lon = double.tryParse(lonStr) ?? double.nan;
      if (!lat.isFinite || lat < -90 || lat > 90) return null;
      if (!lon.isFinite || lon < -180 || lon > 180) return null;

      return RoadEvent(
        id:        json['id'] as String,
        pubkey:    json['pubkey'] as String,
        category:  RoadCategory.fromKey(tKey),
        position:  LatLng(lat, lon),
        comment:   json['content'] as String? ?? '',
        createdAt: json['created_at'] as int,
        expiresAt: exp,
      );
    } catch (_) {
      return null;
    }
  }
}
