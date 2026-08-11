import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

// SVG/PNG cursors live in assets/cursors/ (pointing UP = direction of
// travel). UserMarker applies a heading rotation on top.

enum CursorStyle {
  arrow,
  formula1,
  suv,
  racing,
  electric,
  city,
  classic500,
  bicycle,
  ostrich;

  static const storageKey = 'movementCursorStyle';

  /// Styles the user may choose for normal map use and car navigation.
  /// Walking and cycling have mode-specific cursors and are never persisted
  /// through this preference.
  static const drivingStyles = [
    CursorStyle.arrow,
    CursorStyle.formula1,
    CursorStyle.suv,
    CursorStyle.racing,
    CursorStyle.electric,
    CursorStyle.city,
    CursorStyle.classic500,
  ];

  static CursorStyle fromStorage(Object? value) {
    if (value is! String) return CursorStyle.arrow;
    for (final style in drivingStyles) {
      if (style.name == value) return style;
    }
    return CursorStyle.arrow;
  }

  /// Resolves the visible cursor without allowing the walking or bicycle
  /// sprites to leak into other transport modes.
  static CursorStyle resolve({
    required bool isNavigating,
    required String transportMode,
    required Object? storedDrivingStyle,
  }) {
    if (isNavigating && transportMode == 'walking') {
      return CursorStyle.ostrich;
    }
    if (isNavigating && transportMode == 'cycling') {
      return CursorStyle.bicycle;
    }
    return fromStorage(storedDrivingStyle);
  }

  String get assetPath => switch (this) {
        CursorStyle.arrow => 'assets/cursors/arrow.svg',
        CursorStyle.formula1 => 'assets/cursors/formula1.png',
        CursorStyle.suv => 'assets/cursors/suv.png',
        CursorStyle.racing => 'assets/cursors/racing.png',
        CursorStyle.electric => 'assets/cursors/electric.png',
        CursorStyle.city => 'assets/cursors/city.png',
        CursorStyle.classic500 => 'assets/cursors/classic500.png',
        CursorStyle.bicycle => 'assets/cursors/bicycle.png',
        CursorStyle.ostrich => 'assets/cursors/ostrich.png',
      };

  bool get isPng => this != CursorStyle.arrow;
}

/// The seven rainbow colours available for the map movement cursor.
///
/// The generated vehicle art is violet by default, so [violet] deliberately
/// leaves the original pixels untouched. Other choices rotate the hue at
/// render time: a single source icon can therefore serve every colour without
/// adding a separate asset for each vehicle/colour pair.
enum CursorColor {
  violet(Color(0xFF8B3DFF), 0),
  indigo(Color(0xFF5856D6), 8),
  blue(Color(0xFF0A84FF), -30),
  green(Color(0xFF34C759), -150),
  yellow(Color(0xFFFFCC00), 150),
  orange(Color(0xFFFF9500), 120),
  red(Color(0xFFFF3B30), 90);

  const CursorColor(this.value, this.hueRotationDegrees);

  static const storageKey = 'movementCursorColor';

  final Color value;
  final double hueRotationDegrees;

  static CursorColor fromStorage(Object? value) {
    if (value is! String) return CursorColor.violet;
    for (final color in CursorColor.values) {
      if (color.name == value) return color;
    }
    return CursorColor.violet;
  }

  /// Violet is already baked into the source artwork. Preserve it exactly;
  /// the other rainbow colours rotate the hue while retaining its highlights,
  /// outlines and transparent pixels.
  ColorFilter? get colorFilter {
    if (this == CursorColor.violet) return null;
    final radians = hueRotationDegrees * math.pi / 180;
    final cosine = math.cos(radians);
    final sine = math.sin(radians);
    return ColorFilter.matrix([
      0.213 + cosine * 0.787 - sine * 0.213,
      0.715 - cosine * 0.715 - sine * 0.715,
      0.072 - cosine * 0.072 + sine * 0.928,
      0,
      0,
      0.213 - cosine * 0.213 + sine * 0.143,
      0.715 + cosine * 0.285 + sine * 0.140,
      0.072 - cosine * 0.072 - sine * 0.283,
      0,
      0,
      0.213 - cosine * 0.213 - sine * 0.787,
      0.715 - cosine * 0.715 + sine * 0.715,
      0.072 + cosine * 0.928 + sine * 0.072,
      0,
      0,
      0,
      0,
      0,
      1,
      0,
    ]);
  }
}

/// Bundled frames for the walking-mode ostrich animation.
///
/// Every sequence is 150 frames long: five seconds at 30 fps. The run cycle
/// is played faster or slower from the measured walking speed; the phone
/// sequence always plays at its authored pace while the user is stationary.
class OstrichAnimationAssets {
  static const frameCount = 150;
  static const _runDirectory = 'assets/cursors/ostrich_run';
  static const _idleDirectory = 'assets/cursors/ostrich_idle';

  static String framePath({required bool running, required int frame}) {
    assert(frame >= 0 && frame < frameCount);
    final directory = running ? _runDirectory : _idleDirectory;
    return '$directory/frame-${frame.toString().padLeft(3, '0')}.png';
  }

  static Iterable<String> framePaths({required bool running}) sync* {
    for (var frame = 0; frame < frameCount; frame++) {
      yield framePath(running: running, frame: frame);
    }
  }
}

/// Renders the cursor icon at [size]×[size].
///
/// SVG styles render via [SvgPicture.asset]; generated vehicle cursors and the
/// ostrich use [Image.asset]. [cursorColor] tints either kind of cursor while
/// retaining the source icon's shading; an explicit [colorFilter] overrides it.
class CursorWidget extends StatelessWidget {
  final CursorStyle style;
  final CursorColor cursorColor;
  final bool animateOstrich;
  final bool ostrichIsMoving;
  final double ostrichSpeedKmh;
  final double size;
  final ColorFilter? colorFilter;

  const CursorWidget({
    super.key,
    required this.style,
    this.cursorColor = CursorColor.violet,
    this.animateOstrich = false,
    this.ostrichIsMoving = false,
    this.ostrichSpeedKmh = 0,
    this.size = 48,
    this.colorFilter,
  });

  @override
  Widget build(BuildContext context) {
    final effectiveColorFilter = colorFilter ?? cursorColor.colorFilter;
    if (style == CursorStyle.ostrich && animateOstrich) {
      return _AnimatedOstrichCursor(
        isMoving: ostrichIsMoving,
        speedKmh: ostrichSpeedKmh,
        size: size,
        colorFilter: effectiveColorFilter,
      );
    }
    if (style.isPng) {
      final image = Image.asset(
        style.assetPath,
        width: size,
        height: size,
        fit: BoxFit.contain,
      );
      if (effectiveColorFilter == null) return image;
      return ColorFiltered(colorFilter: effectiveColorFilter, child: image);
    }
    return SvgPicture.asset(
      style.assetPath,
      width: size,
      height: size,
      fit: BoxFit.contain,
      colorFilter: effectiveColorFilter,
    );
  }
}

/// Plays the walking cursor at 30 fps from individually bundled frames.
///
/// A GIF cannot have a playback rate driven by the live GPS speed. Rendering
/// the frames here preserves the authored five-second animation while letting
/// the run cycle accelerate and slow down with the pedestrian.
class _AnimatedOstrichCursor extends StatefulWidget {
  final bool isMoving;
  final double speedKmh;
  final double size;
  final ColorFilter? colorFilter;

  const _AnimatedOstrichCursor({
    required this.isMoving,
    required this.speedKmh,
    required this.size,
    required this.colorFilter,
  });

  @override
  State<_AnimatedOstrichCursor> createState() => _AnimatedOstrichCursorState();
}

class _AnimatedOstrichCursorState extends State<_AnimatedOstrichCursor>
    with SingleTickerProviderStateMixin {
  static const _cycleDuration = Duration(seconds: 5);
  static const _referenceWalkingSpeedKmh = 4.8;
  static const _minimumPlaybackRate = 0.45;
  static const _maximumPlaybackRate = 1.6;

  late final AnimationController _controller;
  final Map<bool, Future<void>> _warmupFutures = <bool, Future<void>>{};
  final Set<bool> _readySequences = <bool>{};
  bool _started = false;

  Duration get _runningDuration {
    final speed = widget.speedKmh.isFinite && widget.speedKmh > 0
        ? widget.speedKmh
        : _referenceWalkingSpeedKmh;
    final rate = (speed / _referenceWalkingSpeedKmh)
        .clamp(_minimumPlaybackRate, _maximumPlaybackRate);
    return Duration(
      microseconds: (_cycleDuration.inMicroseconds / rate).round(),
    );
  }

  Duration get _activeDuration =>
      widget.isMoving ? _runningDuration : _cycleDuration;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    unawaited(_startCycle());
  }

  @override
  void didUpdateWidget(_AnimatedOstrichCursor oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.isMoving != widget.isMoving) {
      _controller.stop();
      _controller.value = 0;
      unawaited(_startCycle());
      return;
    }
    if (widget.isMoving &&
        _readySequences.contains(true) &&
        (oldWidget.speedKmh - widget.speedKmh).abs() >= 0.25) {
      // Restart the repeating simulation from the current frame so speed
      // changes remain continuous rather than snapping to frame zero. Only
      // safe once the run sequence has actually finished precaching —
      // otherwise the pending _startCycle() below will pick it up once
      // warm-up completes, reading the latest speed at that point.
      _controller.repeat(period: _activeDuration);
    }
  }

  Future<void> _startCycle() async {
    final running = widget.isMoving;
    await _warmSequence(running);
    if (!mounted || running != widget.isMoving) return;
    _controller.repeat(period: _activeDuration);
  }

  // Returns a future that resolves once every frame of [running]'s sequence
  // is precached. Concurrent/repeated calls for the same value share the one
  // underlying Future.wait rather than each racing to skip it early.
  Future<void> _warmSequence(bool running) {
    return _warmupFutures.putIfAbsent(running, () async {
      await Future.wait(
        OstrichAnimationAssets.framePaths(running: running)
            .map((path) => precacheImage(AssetImage(path), context)),
      );
      _readySequences.add(running);
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
        animation: _controller,
        builder: (context, _) {
          final frame = (_controller.value * OstrichAnimationAssets.frameCount)
              .floor()
              .clamp(0, OstrichAnimationAssets.frameCount - 1);
          Widget image = Image.asset(
            OstrichAnimationAssets.framePath(
              running: widget.isMoving,
              frame: frame,
            ),
            width: widget.size,
            height: widget.size,
            fit: BoxFit.contain,
            gaplessPlayback: true,
          );
          final colorFilter = widget.colorFilter;
          if (colorFilter != null) {
            image = ColorFiltered(colorFilter: colorFilter, child: image);
          }
          return image;
        },
      );
}

/// Wraps [CursorWidget] with a cartoon "poof" transition whenever [style]
/// changes: the current icon shrinks away behind a puff of smoke, then the new
/// icon grows back out of it instead of changing abruptly.
class AnimatedCursorWidget extends StatefulWidget {
  final CursorStyle style;
  final CursorColor cursorColor;
  final bool animateOstrich;
  final bool ostrichIsMoving;
  final double ostrichSpeedKmh;
  final double size;
  final ColorFilter? colorFilter;

  const AnimatedCursorWidget({
    super.key,
    required this.style,
    this.cursorColor = CursorColor.violet,
    this.animateOstrich = false,
    this.ostrichIsMoving = false,
    this.ostrichSpeedKmh = 0,
    this.size = 48,
    this.colorFilter,
  });

  @override
  State<AnimatedCursorWidget> createState() => _AnimatedCursorWidgetState();
}

class _AnimatedCursorWidgetState extends State<AnimatedCursorWidget>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late CursorStyle _displayed;
  late CursorColor _displayedColor;

  static const _duration = Duration(milliseconds: 420);

  @override
  void initState() {
    super.initState();
    _displayed = widget.style;
    _displayedColor = widget.cursorColor;
    _ctrl = AnimationController(vsync: this, duration: _duration);
  }

  @override
  void didUpdateWidget(AnimatedCursorWidget old) {
    super.didUpdateWidget(old);
    if ((old.style != widget.style || old.cursorColor != widget.cursorColor) &&
        !_ctrl.isAnimating) {
      _ctrl.forward(from: 0).whenComplete(() {
        if (mounted) {
          setState(() {
            _displayed = widget.style;
            _displayedColor = widget.cursorColor;
          });
        }
      });
    }
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (context, _) {
        final t = _ctrl.value;
        if (t == 0) {
          return CursorWidget(
              style: _displayed,
              cursorColor: _displayedColor,
              animateOstrich: widget.animateOstrich,
              ostrichIsMoving: widget.ostrichIsMoving,
              ostrichSpeedKmh: widget.ostrichSpeedKmh,
              size: widget.size,
              colorFilter: widget.colorFilter);
        }
        // First half: shrink the old icon away. Second half: grow the new
        // one back in. The swap itself happens at t=0.5, hidden under the
        // puff, which peaks in the middle and is transparent at both ends.
        final beforeSwap = t < 0.5;
        final iconStyle = beforeSwap ? _displayed : widget.style;
        final iconColor = beforeSwap ? _displayedColor : widget.cursorColor;
        final iconT = beforeSwap ? (1 - t * 2) : (t - 0.5) * 2;
        final puffT = 1 - (t - 0.5).abs() * 2; // 0 → 1 → 0

        return SizedBox(
          width: widget.size,
          height: widget.size,
          child: Stack(alignment: Alignment.center, children: [
            if (puffT > 0)
              Opacity(
                opacity: puffT.clamp(0.0, 1.0),
                child: CustomPaint(
                  size: Size.square(widget.size * 1.3),
                  painter: _PuffCloudPainter(progress: 1 - puffT),
                ),
              ),
            Transform.scale(
              scale: 0.25 + 0.75 * iconT,
              child: Opacity(
                opacity: iconT.clamp(0.0, 1.0),
                child: CursorWidget(
                    style: iconStyle,
                    cursorColor: iconColor,
                    animateOstrich: widget.animateOstrich,
                    ostrichIsMoving: widget.ostrichIsMoving,
                    ostrichSpeedKmh: widget.ostrichSpeedKmh,
                    size: widget.size,
                    colorFilter: widget.colorFilter),
              ),
            ),
          ]),
        );
      },
    );
  }
}

/// A handful of overlapping circles that puff outward and fade — a simple,
/// deliberately cartoonish "poof" cloud (no particle system, no physics).
class _PuffCloudPainter extends CustomPainter {
  final double progress; // 0 = just appeared, 1 = fully dispersed
  const _PuffCloudPainter({required this.progress});

  // Relative (dx, dy, radiusFactor) for each puff, clustered around the centre.
  static const _puffs = [
    (0.0, 0.0, 0.34),
    (-0.28, -0.12, 0.22),
    (0.30, -0.08, 0.20),
    (-0.14, 0.26, 0.20),
    (0.20, 0.24, 0.18),
  ];

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final spread = 1.0 + progress * 0.9; // puffs drift outward as they fade
    final alpha = ((1 - progress) * 235).clamp(0, 235).toInt();
    final paint = Paint()
      ..color = Colors.white.withAlpha(alpha)
      ..style = PaintingStyle.fill;
    final outline = Paint()
      ..color = Colors.grey.withAlpha((alpha * 0.5).round())
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.2;

    for (final (dx, dy, rf) in _puffs) {
      final c = center + Offset(dx, dy) * size.shortestSide * spread;
      final r = size.shortestSide * rf * (1 + progress * 0.35);
      canvas.drawCircle(c, r, paint);
      canvas.drawCircle(c, r, outline);
    }
  }

  @override
  bool shouldRepaint(covariant _PuffCloudPainter old) =>
      old.progress != progress;
}
