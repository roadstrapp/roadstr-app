// GPS positioning service for Roadstr.
//
// Wraps the geolocator package to provide a broadcast stream of GpsData
// samples. An Android foreground service notification is shown while the stream
// is active so that the OS does not kill the location listener when the app is
// in the background during active navigation.
import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';

/// A snapshot of the device's GPS state at a given instant.
class GpsData {
  final LatLng position;

  /// Speed in km/h (converted from the native m/s value; clamped to 0 if negative).
  final double speedKmh;

  /// Horizontal accuracy radius in metres; see [isReliable].
  final double accuracy;

  /// Compass bearing in degrees (0–360, clockwise from north). `null` if the
  /// device could not determine heading (e.g. stationary without magnetometer).
  final double? heading;
  final double altitude;
  final DateTime timestamp;

  const GpsData({
    required this.position,
    required this.speedKmh,
    required this.accuracy,
    this.heading,
    required this.altitude,
    required this.timestamp,
  });

  /// Returns `true` when the horizontal accuracy is better than 30 m —
  /// sufficient for turn-by-turn navigation decisions.
  bool get isReliable => accuracy < 30;
}

/// Manages the device GPS subscription and exposes a typed [GpsData] stream.
class GpsService {
  final _controller = StreamController<GpsData>.broadcast();
  Stream<GpsData> get stream => _controller.stream;
  StreamSubscription<Position>? _subscription;
  bool _disposed = false;
  GpsData? _lastData;
  GpsData? get lastData => _lastData;

  /// Android-specific location settings used by `geolocator`.
  ///
  /// - `distanceFilter: 0` — emit every sample; the app does its own filtering.
  /// - `intervalDuration: 500 ms` — 2 Hz updates balances battery and navigation
  ///   smoothness.
  /// - `foregroundNotificationConfig`: required to keep the location listener
  ///   alive when the screen is off or the app is minimised. `enableWakeLock`
  ///   prevents the CPU from sleeping mid-navigation.
  static final _locationSettings = AndroidSettings(
    accuracy: LocationAccuracy.bestForNavigation,
    distanceFilter: 0,
    intervalDuration: const Duration(milliseconds: 500),
    // CRITICAL for de-Googled devices (GrapheneOS, /e/OS, LineageOS without
    // gapps). Without this flag geolocator binds to the Google Play Services
    // "fused" location provider; on a device that has no Play Services that
    // provider never emits a fix, so the map stays stuck on the fallback
    // position forever — no lock even after minutes of searching or repeated
    // recenter taps (exactly the reported GrapheneOS symptom). forcing the
    // raw AOSP android.location.LocationManager reads the GNSS hardware
    // directly and works on EVERY Android device. It is also a privacy win —
    // no location request ever touches Google Play Services — which fits this
    // app's audience. Trade-off vs fused: marginally higher battery and no
    // sensor-fusion smoothing, both negligible for active outdoor driving
    // navigation (and the app already filters/smooths fixes itself).
    // DO NOT remove this without a de-Googled-device regression test.
    forceLocationManager: true,
    foregroundNotificationConfig: const ForegroundNotificationConfig(
      notificationText: 'GPS active',
      notificationTitle: 'Roadstr',
      enableWakeLock: false,
    ),
  );

  /// Returns `true` if the device's location service (GPS hardware) is enabled.
  /// Call this before [start] to decide whether to prompt the user.
  Future<bool> isServiceEnabled() => Geolocator.isLocationServiceEnabled();

  /// Checks an existing permission without triggering an Android prompt.
  /// Used by the opt-in startup centering setting.
  Future<bool> hasGrantedPermission() async {
    final permission = await Geolocator.checkPermission();
    return permission == LocationPermission.whileInUse ||
        permission == LocationPermission.always;
  }

  /// Opens the Android system location settings screen so the user can enable GPS.
  Future<void> openSettings() => Geolocator.openLocationSettings();

  /// Starts the GPS stream.
  ///
  /// Requests [LocationPermission] if not already granted.
  /// Returns `true` on success, `false` if the service is disabled or permission
  /// was denied/revoked.
  Future<bool> start() async {
    if (_disposed) return false;
    if (_subscription != null) return true; // already active
    final serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) return false;
    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) return false;
    }
    if (permission == LocationPermission.deniedForever) return false;
    _subscription = Geolocator.getPositionStream(
      locationSettings: _locationSettings,
    ).listen(_onPosition, onError: _onError);
    return true;
  }

  /// Returns the last cached fix from the OS, instantly and without waiting for
  /// a fresh satellite lock. Used to move the map to roughly the right place the
  /// moment the user asks for their location, while the accurate
  /// [LocationAccuracy.bestForNavigation] stream warms up (a cold first fix can
  /// take several seconds). Returns null when the OS has no cached position.
  Future<GpsData?> lastKnown() async {
    try {
      // forceAndroidLocationManager: same de-Googled-device reason as the
      // stream — the cached fix must come from the AOSP LocationManager, not
      // the absent Play Services provider.
      final pos = await Geolocator.getLastKnownPosition(
          forceAndroidLocationManager: true);
      if (pos == null ||
          !pos.latitude.isFinite ||
          !pos.longitude.isFinite ||
          pos.latitude < -90 ||
          pos.latitude > 90 ||
          pos.longitude < -180 ||
          pos.longitude > 180) {
        return null;
      }
      return GpsData(
        position: LatLng(pos.latitude, pos.longitude),
        speedKmh: 0,
        accuracy: pos.accuracy,
        heading: pos.heading >= 0 ? pos.heading : null,
        altitude: pos.altitude,
        timestamp: pos.timestamp,
      );
    } catch (_) {
      return null;
    }
  }

  void _onPosition(Position pos) {
    if (_disposed || _controller.isClosed) return;
    // Guard against NaN/Infinity coordinates that some Android devices emit
    // during the initial GPS fix acquisition phase. Passing a NaN value into
    // LatLng would cause an assertion failure (debug) or silent map corruption
    // (release). We discard those readings and wait for a valid fix.
    if (!pos.latitude.isFinite ||
        !pos.longitude.isFinite ||
        pos.latitude < -90 ||
        pos.latitude > 90 ||
        pos.longitude < -180 ||
        pos.longitude > 180) {
      return;
    }

    final double speedKmh = (pos.speed < 0 ? 0.0 : pos.speed * 3.6).toDouble();
    final data = GpsData(
      position: LatLng(pos.latitude, pos.longitude),
      speedKmh: speedKmh,
      accuracy: pos.accuracy,
      heading: pos.heading >= 0 ? pos.heading : null,
      altitude: pos.altitude,
      timestamp: pos.timestamp,
    );
    _lastData = data;
    _controller.add(data);
  }

  void _onError(Object error) {
    debugPrint('[GPS] error: $error');
  }

  /// Solicits a fresh one-shot fix from the GPS hardware and emits it on
  /// [stream], bypassing the periodic stream cadence. Used by the recenter
  /// button so the cursor snaps to the *current* position, not the last
  /// stream sample. Fails silently — the periodic stream keeps running.
  Future<void> refresh() async {
    if (_disposed) return;
    try {
      final pos = await Geolocator.getCurrentPosition(
        // AndroidSettings with forceLocationManager (NOT the base
        // LocationSettings) so this one-shot fix also bypasses Play Services —
        // on a de-Googled device the plain fused path would never return.
        // No timeLimit: raw-GNSS cold start can take longer than a few
        // seconds, and a timeout here just leaves the recenter feeling dead.
        locationSettings: AndroidSettings(
          accuracy: LocationAccuracy.bestForNavigation,
          forceLocationManager: true,
        ),
      );
      _onPosition(pos);
    } catch (e) {
      debugPrint('[GPS] refresh failed: $e');
    }
  }

  Future<void> stop() async {
    await _subscription?.cancel();
    _subscription = null;
  }

  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    await stop();
    await _controller.close();
  }
}
