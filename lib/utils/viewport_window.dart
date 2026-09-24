import 'dart:math' as math;

/// The patch of ground a tilted MapLibre camera can actually show, as a
/// rectangle in the camera's own frame — used to decide which markers are
/// worth building at all.
///
/// **Why this exists.** The `maplibre` package's `WidgetLayer` depends on the
/// map camera, so it rebuilds on *every* camera change, and for every marker
/// it projects the point and builds a `Positioned` + `Transform` around the
/// child — including markers completely off screen (there is a TODO in the
/// package for exactly that). The follow camera moves ~30 times a second for
/// the whole drive, and in a city the crosswalk, traffic-light, speed-bump and
/// camera caches together hold well over a thousand points within the fetch
/// radius, of which a few dozen are ever on screen. That was tens of
/// thousands of widget builds a second, on the UI thread, for nothing.
///
/// Culling against this window before the markers reach the layer removes
/// almost all of that without changing a pixel of what is drawn: the window is
/// deliberately generous, so what it excludes could not be seen anyway.
///
/// **The geometry.** MapLibre's camera has a fixed vertical field of view of
/// 36.87° (its distance to the map centre is 1.5 × the screen height, in
/// pixels), pitched by `pitch` from straight down. Unprojecting the top and
/// bottom screen edges onto the ground gives how far ahead and behind the
/// centre the view reaches; the lateral extent follows from the horizontal
/// field of view at the far edge, where it is widest. All of it is computed in
/// metres at the map centre's latitude.
class ViewportWindow {
  ViewportWindow._({
    required this.centerLat,
    required this.centerLng,
    required this.forwardMetres,
    required this.backwardMetres,
    required this.halfWidthMetres,
    required double bearingDeg,
  })  : _sinB = math.sin(bearingDeg * _deg2rad),
        _cosB = math.cos(bearingDeg * _deg2rad),
        _metresPerDegLng =
            _metresPerDegLat * math.cos(centerLat * _deg2rad).abs();

  /// Builds the window for a camera looking at ([centerLat], [centerLng]).
  ///
  /// [screenWidthDp] and [screenHeightDp] are the map's logical pixels — the
  /// same unit MapLibre's zoom levels are defined in (512-pixel tiles).
  ///
  /// [margin] scales every extent and [extraMetres] is added to it. The
  /// defaults are generous on purpose: a marker built for nothing costs a
  /// little, a marker that should be on screen and is missing is a bug, and
  /// the window is only recomputed when the camera has moved enough to matter,
  /// not on every frame.
  factory ViewportWindow({
    required double centerLat,
    required double centerLng,
    required double zoom,
    required double bearingDeg,
    required double pitchDeg,
    required double screenWidthDp,
    required double screenHeightDp,
    double margin = 1.5,
    double extraMetres = 100,
  }) {
    final metresPerDp = _metresPerDp(zoom, centerLat);
    final e = groundExtentsDp(
      pitchDeg: pitchDeg,
      screenWidthDp: screenWidthDp,
      screenHeightDp: screenHeightDp,
    );
    return ViewportWindow._(
      centerLat: centerLat,
      centerLng: centerLng,
      forwardMetres: e.forward * metresPerDp * margin + extraMetres,
      backwardMetres: e.backward * metresPerDp * margin + extraMetres,
      halfWidthMetres: e.halfWidth * metresPerDp * margin + extraMetres,
      bearingDeg: bearingDeg,
    );
  }

  static const _deg2rad = math.pi / 180;
  static const _metresPerDegLat = 111320.0;

  /// Tangent of half MapLibre's fixed vertical field of view: the camera sits
  /// 1.5 screen-heights from the map centre, so this is 0.5 / 1.5.
  static const _tanHalfFov = 1 / 3;

  /// The steepest tilt MapLibre allows. Past this the far-edge formula would
  /// run away towards the horizon, so it is never used beyond it.
  static const _maxPitchDeg = 60.0;

  final double centerLat;
  final double centerLng;

  /// How far ahead of the centre (along the camera's heading) the window
  /// reaches, in metres, margin included.
  final double forwardMetres;

  /// How far behind the centre it reaches.
  final double backwardMetres;

  /// Half the window's width, side to side.
  final double halfWidthMetres;

  final double _sinB;
  final double _cosB;
  final double _metresPerDegLng;

  /// Whether ([lat], [lng]) falls inside the window.
  bool contains(double lat, double lng) {
    final north = (lat - centerLat) * _metresPerDegLat;
    final east = (lng - centerLng) * _metresPerDegLng;
    // Into the camera's frame: forward along its heading, lateral to its right.
    final ahead = east * _sinB + north * _cosB;
    final aside = east * _cosB - north * _sinB;
    return ahead <= forwardMetres &&
        ahead >= -backwardMetres &&
        aside.abs() <= halfWidthMetres;
  }

  /// Metres of ground one logical pixel covers at [zoom], at [lat].
  static double _metresPerDp(double zoom, double lat) =>
      78271.51696 * math.cos(lat * _deg2rad).abs() / math.pow(2, zoom);

  /// How far the view reaches ahead, behind and to each side of the map
  /// centre, in units of *ground pixels* — metres per pixel of 1 — before any
  /// margin. Public for tests, which check it against an independent pinhole
  /// projection rather than against the formulas that produced it.
  static ({double forward, double backward, double halfWidth}) groundExtentsDp({
    required double pitchDeg,
    required double screenWidthDp,
    required double screenHeightDp,
  }) {
    final pitch = pitchDeg.clamp(0.0, _maxPitchDeg) * _deg2rad;
    final halfFov = math.atan(_tanHalfFov);
    // The camera is 1.5 screen-heights from the centre, along its view axis.
    final toCentre = 1.5 * screenHeightDp;
    final height = toCentre * math.cos(pitch);
    final forward = height * (math.tan(pitch + halfFov) - math.tan(pitch));
    final backward = height * (math.tan(pitch) - math.tan(pitch - halfFov));
    // Widest at the far edge, where the slant distance is greatest. Using the
    // slant distance rather than the depth along the view axis overestimates
    // by 1/cos(half fov) ≈ 5% — the safe direction.
    final slantFar = height / math.cos(pitch + halfFov);
    final halfWidth =
        slantFar * (screenWidthDp / screenHeightDp) * _tanHalfFov;
    return (forward: forward, backward: backward, halfWidth: halfWidth);
  }
}
