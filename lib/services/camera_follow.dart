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

  /// Shifts a camera centre [headingDeg]° ahead of [lat]/[lng] so the GPS
  /// marker sits lower in frame — more road ahead, less behind, and clear of
  /// a bottom nav panel. Same formula as `MapScreen._navCameraCenter` (18%
  /// of the screen height, converted to metres at [zoom]) — which despite
  /// its own doc comments calling this "~1/3" and, at its call site, "~2/5"
  /// from the bottom actually places it at 50% − 18% = 32% from the bottom;
  /// neither comment matches the number the formula computes. One
  /// deliberate difference: MapScreen hardcodes an assumed 800px screen
  /// height, so the shift is only correct on devices close to that height;
  /// [screenHeightPx] takes the real one instead (pass
  /// `MediaQuery.of(context).size.height`), so the 32%-from-bottom placement
  /// — and the clearance it buys over the NavPanel/speedometer — holds on
  /// any device, not just an 800px-tall one.
  ///
  /// [pitchDeg] is a rough attempt at compensating for camera tilt — a
  /// pitched 3D camera does not shift the on-screen marker by the same
  /// number of pixels per ground-metre that a flat top-down one does, and
  /// this formula cannot know the real ratio without asking the actual
  /// projection (screen position for a pitched camera is not a simple
  /// function of ground distance and pitch alone — it also depends on the
  /// SDK's own camera/FOV model). Treat this as a coarse fallback only:
  /// [shiftByMetres] below, driven by a distance measured against the live
  /// controller, is what the map screen actually uses once navigation is
  /// under way; this formula only covers the brief window before that first
  /// measurement lands.
  static (double lat, double lng) navCameraCenter(
      double lat, double lng, double headingDeg, double zoom,
      {double screenHeightPx = 800.0, double pitchDeg = 0.0}) {
    const earthM = 40075016.0;
    final mpp = earthM / (256.0 * math.pow(2, zoom));
    final pitchCompensation =
        1.0 / math.max(math.cos(pitchDeg * math.pi / 180.0), 0.5);
    final shiftM = 0.18 * screenHeightPx * mpp * pitchCompensation;
    const degPerM = 1.0 / 111320.0;
    final rad = headingDeg * math.pi / 180.0;
    final dLat = math.cos(rad) * shiftM * degPerM;
    final dLng = math.sin(rad) *
        shiftM *
        degPerM /
        math.max(math.cos(lat * math.pi / 180.0), 0.001);
    return ((lat + dLat).clamp(-89.9, 89.9), lng + dLng);
  }

  /// Shifts [lat]/[lng] by exactly [shiftM] metres in the [headingDeg]
  /// direction — no zoom/pitch guessing, for when the real on-screen shift
  /// needed has already been measured (e.g. via the map controller's own
  /// screen↔ground projection) rather than estimated from a formula.
  static (double lat, double lng) shiftByMetres(
      double lat, double lng, double headingDeg, double shiftM) {
    const degPerM = 1.0 / 111320.0;
    final rad = headingDeg * math.pi / 180.0;
    final dLat = math.cos(rad) * shiftM * degPerM;
    final dLng = math.sin(rad) *
        shiftM *
        degPerM /
        math.max(math.cos(lat * math.pi / 180.0), 0.001);
    return ((lat + dLat).clamp(-89.9, 89.9), lng + dLng);
  }
}

/// Decides whether a follow-camera frame changed the view enough to be worth
/// sending to the map at all.
///
/// The follow ticker runs at ~30 Hz for the whole drive and every frame it
/// sends is a native camera update: a full re-render of a tilted 3D vector
/// map, plus the platform-channel round trip. At walking or stop-and-go speed
/// most of those frames move the view by a fraction of a pixel — the same
/// picture rendered again, at 30 Hz, for nothing. (At 20 km/h and zoom 17 a
/// frame is about 0.4 dp of travel.)
///
/// This measures how much the view has changed since the last frame that was
/// *actually sent*, as an on-screen distance, and lets a frame through only
/// once that reaches [minChangeDp] — or once [maxGapMs] has passed with any
/// visible change at all, so a very slow crawl still advances steadily rather
/// than in long lurches. The easing itself keeps running every tick; only the
/// expensive send is gated, so the camera never falls behind, it just stops
/// re-drawing itself between changes nobody could see.
///
/// The three components are converted to the same unit so they can be added:
/// ground travel by the map's metres per dp, and rotation and zoom by what
/// they do to a point at [edgeRadiusDp] from the centre — where they show up
/// first. Turning at 30°/s moves that point ~7 dp a frame, so cornering and
/// zooming are never throttled; only a steady crawl is.
class CameraFrameGate {
  CameraFrameGate({
    this.minChangeDp = 0.6,
    this.maxGapMs = 150,
    this.edgeRadiusDp = 400,
  });

  /// The smallest on-screen change worth a frame, in logical pixels.
  final double minChangeDp;

  /// The longest a visible change may wait before being sent regardless.
  final int maxGapMs;

  /// Distance from the screen centre at which rotation and zoom are measured.
  final double edgeRadiusDp;

  /// A change below this is not "visible change at all" — a camera that has
  /// genuinely stopped must not be re-sent every [maxGapMs] forever.
  static const _negligibleDp = 0.05;

  CameraFollowState? _sent;
  int _sentAtMs = 0;

  /// Whether [next], arriving at [nowMs], should be sent. Does not record it:
  /// call [markSent] once it actually has been.
  bool shouldSend(CameraFollowState next, int nowMs) {
    final sent = _sent;
    if (sent == null) return true;
    final change = changeDp(sent, next);
    if (change >= minChangeDp) return true;
    return change > _negligibleDp && nowMs - _sentAtMs >= maxGapMs;
  }

  void markSent(CameraFollowState state, int nowMs) {
    _sent = state;
    _sentAtMs = nowMs;
  }

  /// Forget what was last sent, so the next frame always goes out — after the
  /// ticker has been idle, or something else (a snap, an animation) moved the
  /// camera behind this gate's back.
  void reset() => _sent = null;

  /// How far, on screen, the view moved from [a] to [b], in logical pixels.
  double changeDp(CameraFollowState a, CameraFollowState b) {
    // Flat-earth distance, same approximation as [hasCaughtUp]: a frame is
    // centimetres to metres, nowhere near where it stops being accurate.
    final dLat = (b.lat - a.lat) * 111320;
    final dLng = (b.lng - a.lng) * 111320 * math.cos(a.lat * math.pi / 180);
    final metres = math.sqrt(dLat * dLat + dLng * dLng);
    final metresPerDp =
        78271.51696 * math.cos(b.lat * math.pi / 180).abs() / math.pow(2, b.zoom);
    final travel = metresPerDp > 0 ? metres / metresPerDp : 0.0;

    var rot = (b.rotDeg - a.rotDeg) % 360;
    if (rot > 180) rot -= 360;
    if (rot < -180) rot += 360;
    final turn = rot.abs() * math.pi / 180 * edgeRadiusDp;

    // Scale changes by 2^Δzoom; at the edge radius that is this many pixels.
    final zoom = (math.pow(2, (b.zoom - a.zoom).abs()) - 1) * edgeRadiusDp;

    return travel + turn + zoom;
  }
}
