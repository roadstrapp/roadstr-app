import 'package:latlong2/latlong.dart';

/// Decoder for Google's encoded-polyline format.
///
/// The road routers this app uses are asked for GeoJSON, so nothing needed
/// this until public transport arrived: the transit router returns each leg's
/// shape as an encoded polyline instead.
///
/// [precision] is a required argument on purpose. The format is usually
/// described with five decimal places, but the transit API encodes at seven
/// and states which it used in the response. Assuming five against a
/// seven-encoded line does not distort it slightly — it divides every
/// coordinate by a hundred, dropping the shape near the equator, an entire
/// continent away from the route. Passing the server's own value through is
/// the only safe reading.
List<LatLng> decodePolyline(String encoded, {required int precision}) {
  if (encoded.isEmpty) return const [];
  final factor = _pow10(precision);
  final points = <LatLng>[];
  var index = 0;
  var lat = 0;
  var lng = 0;

  while (index < encoded.length) {
    final dLat = _decodeSignedValue(encoded, index);
    if (dLat == null) return points;
    lat += dLat.value;
    index = dLat.nextIndex;

    final dLng = _decodeSignedValue(encoded, index);
    if (dLng == null) return points;
    lng += dLng.value;
    index = dLng.nextIndex;

    points.add(LatLng(lat / factor, lng / factor));
  }
  return points;
}

/// One delta and where it ended, or null if the string ran out mid-value.
///
/// Truncation is returned rather than thrown: a clipped polyline still draws
/// the part of the journey that arrived, which beats losing the itinerary.
({int value, int nextIndex})? _decodeSignedValue(String encoded, int start) {
  var index = start;
  var shift = 0;
  var result = 0;
  while (true) {
    if (index >= encoded.length) return null;
    final chunk = encoded.codeUnitAt(index++) - 63;
    result |= (chunk & 0x1f) << shift;
    if (chunk < 0x20) break;
    shift += 5;
    // A well-formed value is at most six chunks; more means corrupt input
    // that would otherwise shift into meaningless magnitudes.
    if (shift > 30) return null;
  }
  // Least significant bit is the sign, and negatives are stored inverted.
  final value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
  return (value: value, nextIndex: index);
}

double _pow10(int exponent) {
  var result = 1.0;
  for (var i = 0; i < exponent; i++) {
    result *= 10;
  }
  return result;
}
