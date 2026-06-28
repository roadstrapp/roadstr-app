import 'package:latlong2/latlong.dart';

/// A user-saved place (e.g. "Home", "Office") stored in Hive under key
/// 'favorites' as a JSON-encoded list.
class FavoritePlace {
  final String label;
  final String address;
  final LatLng position;

  const FavoritePlace({
    required this.label,
    required this.address,
    required this.position,
  });

  Map<String, dynamic> toMap() => {
    'label':   label,
    'address': address,
    'lat':     position.latitude,
    'lon':     position.longitude,
  };

  static FavoritePlace? fromMapSafe(Map<dynamic, dynamic> m) {
    try {
      final lat = (m['lat'] as num?)?.toDouble() ?? double.nan;
      final lon = (m['lon'] as num?)?.toDouble() ?? double.nan;
      if (!lat.isFinite || lat < -90 || lat > 90) return null;
      if (!lon.isFinite || lon < -180 || lon > 180) return null;
      final label   = m['label']   as String?;
      final address = m['address'] as String?;
      if (label == null || label.isEmpty) return null;
      return FavoritePlace(
        label:    label,
        address:  address ?? '',
        position: LatLng(lat, lon),
      );
    } catch (_) {
      return null;
    }
  }
}
