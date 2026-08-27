import 'dart:math' as math;

/// Where the camera actually is, or where it should end up.
class CameraFollowState {
  final double lat;
  final double lng;
  final double zoom;
  final double rotDeg;

  const CameraFollowState({
    required this.lat,
    required this.lng,
    required this.zoom,
    required this.rotDeg,
  });
}

/// Pure camera-easing policy, extracted from `MapScreen._startFollowTicker`
/// so it can be driven by any map engine's controller instead of being
/// wired directly to `flutter_map`'s.
///
/// No map, no widget, no controller — a pure function of "where the camera
/// is" and "where it should be", the same policy/mechanism split the
/// rendering-engine investigation kept landing on: this is the policy, and
/// it is unit-testable without rendering a single frame.
class CameraFollowEasing {
  CameraFollowEasing._();

  /// Smoothing time constant (ms) — see `MapScreen._followTauMs` for the
  /// tuning rationale (350 ms tracks real motion while ironing out GPS
  /// jitter at low speed). Kept identical rather than re-derived, since this
  /// value was tuned against real driving, not against a formula.
  static const tauMs = 350.0;

  /// Ceiling on rotation speed (°/s) — see `MapScreen._maxFollowTurnDegPerSec`.
  /// Well above any real vehicle's yaw rate (~30°/s even in a tight
  /// roundabout), so it only stretches artificial jumps — a heading filter
  /// correction after a suppressed bearing near a roundabout, for instance —
  /// into something the eye can follow, never slows genuine turning.
  static const maxTurnDegPerSec = 90.0;

  /// One frame of easing from [from] toward [target], [dtMs] milliseconds
  /// later. Returns null when the inputs would produce a non-finite or
  /// out-of-range result (mirrors the finite/range guard in the original
  /// ticker) — the caller should skip that frame rather than apply garbage.
  static CameraFollowState? step({
    required CameraFollowState from,
    required CameraFollowState target,
    required int dtMs,
  }) {
    final t = 1 - math.exp(-dtMs / tauMs);

    var rotDelta = target.rotDeg - from.rotDeg;
    while (rotDelta > 180) {
      rotDelta -= 360;
    }
    while (rotDelta < -180) {
      rotDelta += 360;
    }
    var rotStep = rotDelta * t;
    final maxStep = maxTurnDegPerSec * dtMs / 1000.0;
    if (rotStep > maxStep) rotStep = maxStep;
    if (rotStep < -maxStep) rotStep = -maxStep;
    final rot = from.rotDeg + rotStep;

    final zoom = (from.zoom + (target.zoom - from.zoom) * t).clamp(1.0, 22.0);
    final lat = from.lat + (target.lat - from.lat) * t;
    final lng = from.lng + (target.lng - from.lng) * t;

    if (!lat.isFinite ||
        lat < -90 ||
        lat > 90 ||
        !lng.isFinite ||
        lng < -180 ||
        lng > 180 ||
        !zoom.isFinite ||
        !rot.isFinite) {
      return null;
    }
    return CameraFollowState(lat: lat, lng: lng, zoom: zoom, rotDeg: rot);
  }

  /// Whether [current] is close enough to [target] that easing may stop —
  /// the same catch-up threshold the original ticker uses to stand down
  /// instead of running a 60 Hz timer against a camera already in place.
  static bool hasCaughtUp(CameraFollowState current, CameraFollowState target) {
    var rotDelta = target.rotDeg - current.rotDeg;
    while (rotDelta > 180) {
      rotDelta -= 360;
    }
    while (rotDelta < -180) {
      rotDelta += 360;
    }
    if (rotDelta.abs() >= 0.05) return false;
    if ((target.zoom - current.zoom).abs() >= 0.005) return false;
    // Same flat-earth approximation used elsewhere for short distances —
    // half a metre is well below GPS accuracy, no need for a great-circle
    // formula to decide "close enough".
    final dLat = (target.lat - current.lat) * 111320;
    final dLng = (target.lng - current.lng) *
        111320 *
        math.cos(current.lat * math.pi / 180);
    return dLat * dLat + dLng * dLng < 0.25;
  }
}
