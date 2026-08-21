import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../services/routing_service.dart';
import '../../theme/app_theme.dart';

/// The visual grammar used by every navigation maneuver.
///
/// All routes enter from the bottom. The accent path is the path to take;
/// muted paths are alternatives to ignore. This makes an exit visually
/// different from a merge instead of relying on ambiguous Material glyphs.
enum ManeuverVisualKind {
  straight,
  slightLeft,
  left,
  sharpLeft,
  slightRight,
  right,
  sharpRight,
  uTurnLeft,
  uTurnRight,
  forkLeft,
  forkRight,
  mergeLeft,
  mergeRight,
  rampLeft,
  rampRight,
  exitLeft,
  exitRight,
  roundabout,
  arrive,
  depart,
  ferry,
}

class ManeuverVisual {
  final ManeuverVisualKind kind;
  final int? roundaboutExit;
  final int? roundaboutArmCount;

  const ManeuverVisual(this.kind,
      {this.roundaboutExit, this.roundaboutArmCount});

  factory ManeuverVisual.fromStep(RouteStep step) {
    final direction = step.direction.toLowerCase().trim();
    final modifier = step.modifier.toLowerCase().trim();
    final left = modifier.contains('left');
    final right = modifier.contains('right');

    switch (direction) {
      case 'arrive':
        return const ManeuverVisual(ManeuverVisualKind.arrive);
      case 'depart':
        return const ManeuverVisual(ManeuverVisualKind.depart);
      case 'roundabout':
      case 'rotary':
        return ManeuverVisual(
          ManeuverVisualKind.roundabout,
          // Left null when the router did not give a count, or gave one the
          // route validator dropped: the symbol then shows a roundabout
          // rather than claiming a specific exit.
          roundaboutExit: step.exitNumber?.clamp(1, kMaxRoundaboutArms),
          roundaboutArmCount:
              step.roundaboutArmCount?.clamp(3, kMaxRoundaboutArms),
        );
      case 'fork':
      case 'use lane':
        return ManeuverVisual(
            left ? ManeuverVisualKind.forkLeft : ManeuverVisualKind.forkRight);
      case 'merge':
        return ManeuverVisual(left
            ? ManeuverVisualKind.mergeLeft
            : ManeuverVisualKind.mergeRight);
      case 'on ramp':
        return ManeuverVisual(
            left ? ManeuverVisualKind.rampLeft : ManeuverVisualKind.rampRight);
      case 'off ramp':
        return ManeuverVisual(
            left ? ManeuverVisualKind.exitLeft : ManeuverVisualKind.exitRight);
      case 'ferry':
        return const ManeuverVisual(ManeuverVisualKind.ferry);
    }

    return ManeuverVisual(switch (modifier) {
      'slight left' => ManeuverVisualKind.slightLeft,
      'left' => ManeuverVisualKind.left,
      'sharp left' => ManeuverVisualKind.sharpLeft,
      'slight right' => ManeuverVisualKind.slightRight,
      'right' => ManeuverVisualKind.right,
      'sharp right' => ManeuverVisualKind.sharpRight,
      'uturn left' => ManeuverVisualKind.uTurnLeft,
      'uturn right' => ManeuverVisualKind.uTurnRight,
      'uturn' =>
        right ? ManeuverVisualKind.uTurnRight : ManeuverVisualKind.uTurnLeft,
      _ => ManeuverVisualKind.straight,
    });
  }
}

/// Scalable, deterministic navigation symbol.
///
/// Raster assets cannot encode arbitrary roundabout exits or preserve precise
/// left/right semantics across all sizes. A painter gives the main banner and
/// compact next-step preview the exact same visual language.
class ManeuverSymbol extends StatelessWidget {
  final RouteStep step;
  final double size;
  final RoadstrColors colors;
  final bool showBackground;

  const ManeuverSymbol({
    super.key,
    required this.step,
    required this.size,
    required this.colors,
    this.showBackground = true,
  });

  @override
  Widget build(BuildContext context) {
    final visual = ManeuverVisual.fromStep(step);
    return Container(
      width: size,
      height: size,
      decoration: showBackground
          ? BoxDecoration(
              gradient: colors.accentGloss,
              borderRadius: BorderRadius.circular(size * 0.16),
            )
          : null,
      padding: EdgeInsets.all(size * (showBackground ? 0.12 : 0.04)),
      child: CustomPaint(
        painter: ManeuverSymbolPainter(
          visual: visual,
          // On the filled tile the arrow becomes the light element and the
          // accent moves to the background, so the pair swaps roles. Without
          // the swap the symbol would be accent-on-accent and vanish.
          accent: showBackground ? colors.onAccent : colors.accent,
          muted: (showBackground ? colors.onAccent : colors.textSecondary)
              .withValues(alpha: 0.34),
          // Backs the roundabout exit badge: it has to contrast with the
          // number drawn over it, which is now white.
          surface: showBackground ? colors.accent : colors.surface2,
        ),
      ),
    );
  }
}

class ManeuverSymbolPainter extends CustomPainter {
  final ManeuverVisual visual;
  final Color accent;
  final Color muted;
  final Color surface;

  const ManeuverSymbolPainter({
    required this.visual,
    required this.accent,
    required this.muted,
    required this.surface,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    canvas.save();
    canvas.scale(size.width / 100, size.height / 100);

    switch (visual.kind) {
      case ManeuverVisualKind.straight:
        _straight(canvas);
        break;
      case ManeuverVisualKind.depart:
        _depart(canvas);
        break;
      case ManeuverVisualKind.slightLeft:
        _slight(canvas, -1);
        break;
      case ManeuverVisualKind.slightRight:
        _slight(canvas, 1);
        break;
      case ManeuverVisualKind.left:
        _turn(canvas, -1);
        break;
      case ManeuverVisualKind.right:
        _turn(canvas, 1);
        break;
      case ManeuverVisualKind.sharpLeft:
        _sharp(canvas, -1);
        break;
      case ManeuverVisualKind.sharpRight:
        _sharp(canvas, 1);
        break;
      case ManeuverVisualKind.uTurnLeft:
        _uTurn(canvas, -1);
        break;
      case ManeuverVisualKind.uTurnRight:
        _uTurn(canvas, 1);
        break;
      case ManeuverVisualKind.forkLeft:
        _fork(canvas, -1);
        break;
      case ManeuverVisualKind.forkRight:
        _fork(canvas, 1);
        break;
      case ManeuverVisualKind.mergeLeft:
        _merge(canvas, -1);
        break;
      case ManeuverVisualKind.mergeRight:
        _merge(canvas, 1);
        break;
      case ManeuverVisualKind.rampLeft:
        _ramp(canvas, -1, isExit: false);
        break;
      case ManeuverVisualKind.rampRight:
        _ramp(canvas, 1, isExit: false);
        break;
      case ManeuverVisualKind.exitLeft:
        _ramp(canvas, -1, isExit: true);
        break;
      case ManeuverVisualKind.exitRight:
        _ramp(canvas, 1, isExit: true);
        break;
      case ManeuverVisualKind.roundabout:
        _roundabout(canvas, visual.roundaboutExit, visual.roundaboutArmCount);
        break;
      case ManeuverVisualKind.arrive:
        _arrive(canvas);
        break;
      case ManeuverVisualKind.ferry:
        _ferry(canvas);
        break;
    }
    canvas.restore();
  }

  Paint _paint(Color color, {double width = 9}) => Paint()
    ..color = color
    ..style = PaintingStyle.stroke
    ..strokeWidth = width
    ..strokeCap = StrokeCap.round
    ..strokeJoin = StrokeJoin.round;

  void _route(Canvas canvas, Path path) =>
      canvas.drawPath(path, _paint(accent));

  void _alternative(Canvas canvas, Path path, {double width = 7}) =>
      canvas.drawPath(path, _paint(muted, width: width));

  void _arrow(Canvas canvas, Offset tip, double angle,
      {Color? color, double length = 13}) {
    final p = _paint(color ?? accent, width: 7);
    const spread = 0.58;
    final back = angle + math.pi;
    canvas.drawLine(
      tip,
      Offset(
        tip.dx + length * math.cos(back - spread),
        tip.dy + length * math.sin(back - spread),
      ),
      p,
    );
    canvas.drawLine(
      tip,
      Offset(
        tip.dx + length * math.cos(back + spread),
        tip.dy + length * math.sin(back + spread),
      ),
      p,
    );
  }

  void _straight(Canvas canvas) {
    _route(
        canvas,
        Path()
          ..moveTo(50, 88)
          ..lineTo(50, 15));
    _arrow(canvas, const Offset(50, 15), -math.pi / 2);
  }

  void _depart(Canvas canvas) {
    canvas.drawCircle(const Offset(50, 86), 7, Paint()..color = accent);
    _route(
        canvas,
        Path()
          ..moveTo(50, 78)
          ..lineTo(50, 15));
    _arrow(canvas, const Offset(50, 15), -math.pi / 2);
  }

  void _slight(Canvas canvas, int side) {
    final end = Offset(50 + side * 25, 15);
    final path = Path()
      ..moveTo(50, 88)
      ..cubicTo(50, 58, 55 + side * 12, 37, end.dx, end.dy);
    _route(canvas, path);
    _arrow(canvas, end, -math.pi / 2 + side * 0.45);
  }

  void _turn(Canvas canvas, int side) {
    final end = Offset(50 + side * 37, 35);
    final path = Path()
      ..moveTo(50, 88)
      ..lineTo(50, 55)
      ..cubicTo(50, 42, 57 + side * 10, 35, end.dx, end.dy);
    _route(canvas, path);
    _arrow(canvas, end, side > 0 ? 0 : math.pi);
  }

  void _sharp(Canvas canvas, int side) {
    final end = Offset(50 + side * 34, 68);
    final path = Path()
      ..moveTo(50, 88)
      ..lineTo(50, 43)
      ..cubicTo(50, 31, 63 + side * 18, 42, end.dx, end.dy);
    _route(canvas, path);
    _arrow(canvas, end, side > 0 ? 0.75 : math.pi - 0.75);
  }

  void _uTurn(Canvas canvas, int side) {
    final startX = 50.0 + side * 10;
    final endX = 50.0 - side * 14;
    final path = Path()
      ..moveTo(startX, 88)
      ..lineTo(startX, 40)
      ..cubicTo(startX, 20, endX, 20, endX, 40)
      ..lineTo(endX, 68);
    _route(canvas, path);
    _arrow(canvas, Offset(endX, 68), math.pi / 2);
  }

  void _fork(Canvas canvas, int side) {
    final selected = Offset(50 + side * 28, 16);
    final other = Offset(50 - side * 25, 18);
    _alternative(
      canvas,
      Path()
        ..moveTo(50, 88)
        ..lineTo(50, 55)
        ..cubicTo(50, 43, other.dx - side * 4, 30, other.dx, other.dy),
    );
    _route(
      canvas,
      Path()
        ..moveTo(50, 88)
        ..lineTo(50, 55)
        ..cubicTo(50, 43, selected.dx - side * 4, 30, selected.dx, selected.dy),
    );
    _arrow(canvas, selected, -math.pi / 2 + side * 0.48);
  }

  void _merge(Canvas canvas, int side) {
    // Muted line is the carriageway being joined; the accent route is the
    // ramp the driver is actually on. A "merge left" modifier means that
    // carriageway is to the driver's left, which puts the ramp — the accent
    // path — on the right at the bottom. The two were swapped: the accent
    // path started on the same side as the modifier instead of the opposite
    // one, so every merge rendered as its own mirror image.
    _alternative(
      canvas,
      Path()
        ..moveTo(50 + side * 27, 88)
        ..cubicTo(50 + side * 18, 67, 50 + side * 8, 54, 50, 48)
        ..lineTo(50, 15),
    );
    final path = Path()
      ..moveTo(50 - side * 25, 88)
      ..cubicTo(50 - side * 20, 65, 50 - side * 8, 52, 50, 47)
      ..lineTo(50, 15);
    _route(canvas, path);
    _arrow(canvas, const Offset(50, 15), -math.pi / 2);
  }

  void _ramp(Canvas canvas, int side, {required bool isExit}) {
    // The motorway/through lane stays muted and vertical. The accent branch is
    // the action, so a right exit can never look like a left-side merge.
    _alternative(
      canvas,
      Path()
        ..moveTo(50, 90)
        ..lineTo(50, 12),
      width: isExit ? 10 : 7,
    );
    final end = Offset(50 + side * 36, 24);
    final path = Path()
      ..moveTo(50, 90)
      ..lineTo(50, 58)
      ..cubicTo(50, 46, 62 + side * 12, 35, end.dx, end.dy);
    _route(canvas, path);
    _arrow(canvas, end, -math.pi / 2 + side * 0.78);
  }

  void _roundabout(Canvas canvas, int? exitNumber, int? roundaboutArmCount) {
    const center = Offset(50, 48);
    const radius = 24.0;
    // Exit ordinal and total arms are independent. A third exit can belong to
    // a 4-, 5- or 6-arm roundabout; OSM topology supplies the latter. When it
    // is unavailable, keep a regular four-arm fallback (expanded only as much
    // as needed to represent a higher known exit) instead of pretending the
    // ordinal itself describes the junction.
    final n = exitNumber?.clamp(1, kMaxRoundaboutArms);
    final arms = roundaboutArmCount?.clamp(3, kMaxRoundaboutArms) ??
        (n == null ? 4 : math.max(4, n + 1));
    final exitIndex = n?.clamp(1, arms);
    final step = 2 * math.pi / arms;
    const entryAngle = math.pi / 2;
    final exitAngle = exitIndex == null ? null : entryAngle - exitIndex * step;

    canvas.drawCircle(center, radius, _paint(muted, width: 8));
    for (var i = 1; i < arms; i++) {
      final a = entryAngle - i * step;
      final inner = Offset(
          center.dx + radius * math.cos(a), center.dy + radius * math.sin(a));
      final outer =
          Offset(center.dx + 35 * math.cos(a), center.dy + 35 * math.sin(a));
      canvas.drawLine(inner, outer, _paint(muted, width: 6));
    }

    canvas.drawLine(const Offset(50, 90), const Offset(50, 72), _paint(accent));
    final arcRect = Rect.fromCircle(center: center, radius: radius);
    if (exitIndex != null && exitAngle != null) {
      canvas.drawArc(
        arcRect,
        entryAngle,
        -exitIndex * step,
        false,
        _paint(accent),
      );
      final ringPoint = Offset(center.dx + radius * math.cos(exitAngle),
          center.dy + radius * math.sin(exitAngle));
      final tip = Offset(center.dx + 39 * math.cos(exitAngle),
          center.dy + 39 * math.sin(exitAngle));
      canvas.drawLine(ringPoint, tip, _paint(accent));
      _arrow(canvas, tip, exitAngle);
    } else {
      // Unknown exit: show only the direction of circulation, not a fabricated
      // selected arm.
      canvas.drawArc(arcRect, entryAngle, -step * 0.72, false, _paint(accent));
      final a = entryAngle - step * 0.72;
      final tip = Offset(
          center.dx + radius * math.cos(a), center.dy + radius * math.sin(a));
      _arrow(canvas, tip, a - math.pi / 2, length: 10);
    }

    if (n == null) return;
    // The exit ordinal is the one thing a driver reads at a glance, often at
    // speed, so it is sized for legibility rather than for the badge.
    final textPainter = TextPainter(
      text: TextSpan(
        text: '$n',
        style: TextStyle(
          color: accent,
          fontSize: 20,
          fontWeight: FontWeight.w800,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    // Measure first, then fit the badge around the digits. A roundabout may
    // have up to [kMaxRoundaboutArms] arms, so this has to hold two digits
    // without clipping — a fixed radius tuned for "3" silently crops "12".
    final badgeRadius = math.max(15.0, textPainter.width / 2 + 5);
    canvas.drawCircle(
      center,
      badgeRadius,
      Paint()
        ..color = surface.withValues(alpha: 0.72)
        ..style = PaintingStyle.fill,
    );
    textPainter.paint(
      canvas,
      Offset(center.dx - textPainter.width / 2,
          center.dy - textPainter.height / 2),
    );
  }

  void _arrive(Canvas canvas) {
    final pole = _paint(accent, width: 8);
    canvas.drawLine(const Offset(34, 86), const Offset(34, 18), pole);
    final flag = Path()
      ..moveTo(38, 22)
      ..lineTo(77, 22)
      ..lineTo(68, 42)
      ..lineTo(38, 42)
      ..close();
    canvas.drawPath(flag, Paint()..color = accent);
  }

  void _ferry(Canvas canvas) {
    _straight(canvas);
    final wave = _paint(muted, width: 5);
    for (final y in [68.0, 81.0]) {
      final path = Path()
        ..moveTo(15, y)
        ..cubicTo(25, y - 7, 35, y + 7, 45, y)
        ..cubicTo(55, y - 7, 65, y + 7, 85, y);
      canvas.drawPath(path, wave);
    }
  }

  @override
  bool shouldRepaint(ManeuverSymbolPainter oldDelegate) =>
      oldDelegate.visual.kind != visual.kind ||
      oldDelegate.visual.roundaboutExit != visual.roundaboutExit ||
      oldDelegate.visual.roundaboutArmCount != visual.roundaboutArmCount ||
      oldDelegate.accent != accent ||
      oldDelegate.muted != muted ||
      oldDelegate.surface != surface;
}
