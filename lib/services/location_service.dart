// Initial map position provider for Roadstr.
//
// Why IP geolocation was removed:
// IP-based geolocation typically resolves to a city or ISP location, which
// can be hundreds of kilometres from the user's actual position. For a
// navigation app that subscribes to Nostr road events by geohash, an
// inaccurate starting position would subscribe to the wrong area. The GPS
// gives an exact fix within seconds of opening the app, so the best UX is
// to start on a zoomed-out country view and jump to the GPS position when
// it becomes available.
import 'package:latlong2/latlong.dart';

/// Provides the fallback initial map position used before the GPS is ready.
class LocationService {
  /// Geographic centre of Italy — chosen as the default because Roadstr was
  /// initially developed for Italian roads. At zoom 6.0 the entire peninsula
  /// fits on screen, giving context while GPS acquires a fix.
  static const _default = LatLng(42.5, 12.5);
  static const _defaultZoom = 6.0;

  /// Returns the app's initial map position and zoom level.
  ///
  /// Always returns the hard-coded Italy centre. The map screen replaces this
  /// with the live GPS position as soon as [GpsService.stream] emits its first
  /// valid sample.
  static Future<({LatLng position, double zoom})> getInitialPosition() async =>
      (position: _default, zoom: _defaultZoom);
}
