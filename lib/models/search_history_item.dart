// One previously searched destination, as persisted in the `searchHistory`
// settings box.
import 'package:latlong2/latlong.dart';

class SearchHistoryItem {
  final String label;
  final LatLng position;
  const SearchHistoryItem(this.label, this.position);

  Map<String, dynamic> toJson() => {
        'label': label,
        'lat': position.latitude,
        'lon': position.longitude,
      };

  static SearchHistoryItem? fromJsonSafe(Map<String, dynamic> j) {
    final lat = (j['lat'] as num?)?.toDouble() ?? double.nan;
    final lon = (j['lon'] as num?)?.toDouble() ?? double.nan;
    if (!lat.isFinite ||
        !lon.isFinite ||
        lat < -90 ||
        lat > 90 ||
        lon < -180 ||
        lon > 180) {
      return null;
    }
    final label = j['label'];
    if (label is! String || label.isEmpty || label.length > 300) return null;
    return SearchHistoryItem(label, LatLng(lat, lon));
  }
}
