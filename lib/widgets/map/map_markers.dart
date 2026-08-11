// Map overlay markers: the user's own position, the destination pin, dropped
// search pins and the two kinds of road-event pin.
//
// All of these are pure leaf widgets — they take colours and geometry as
// parameters and never reach back into the map screen's state.
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../../models/road_event.dart';
import '../cursor_painter.dart';

class RoadEventPin extends StatelessWidget {
  final RoadEvent event;
  const RoadEventPin({super.key, required this.event});
  @override
  Widget build(BuildContext context) => Container(
        decoration: BoxDecoration(
          color: event.category.color,
          shape: BoxShape.circle,
          border: Border.all(color: Colors.white, width: 1.5),
          boxShadow: const [BoxShadow(color: Colors.black38, blurRadius: 4)],
        ),
        child: Center(
          child: Text(event.category.emoji,
              style: const TextStyle(fontSize: 16, height: 1)),
        ),
      );
}

/// Marker for a speed camera sourced from OSM/Overpass rather than a Nostr
/// community report — same purple family as [RoadEventPin]'s speed-camera
/// color, but muted fill + white ring instead of a solid pin, so drivers can
/// tell "known static camera" apart from a live community-confirmed one.
class OsmCameraPin extends StatelessWidget {
  const OsmCameraPin({super.key});
  @override
  Widget build(BuildContext context) => Container(
        decoration: BoxDecoration(
          color: RoadCategory.speedCamera.color.withValues(alpha: 0.55),
          shape: BoxShape.circle,
          border: Border.all(color: Colors.white, width: 1.2),
          boxShadow: const [BoxShadow(color: Colors.black26, blurRadius: 3)],
        ),
        child: const Center(
          child: Text('📷', style: TextStyle(fontSize: 13, height: 1)),
        ),
      );
}

class PinMarker extends StatelessWidget {
  const PinMarker({super.key});
  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 32,
          height: 32,
          decoration: BoxDecoration(
            color: const Color(0xFF7C3AED),
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white, width: 2.5),
            boxShadow: [
              BoxShadow(
                  color: const Color(0xFF7C3AED).withValues(alpha: 0.45),
                  blurRadius: 8,
                  offset: const Offset(0, 3))
            ],
          ),
          child: const Icon(Icons.place_rounded, color: Colors.white, size: 18),
        ),
        CustomPaint(
          size: const Size(12, 10),
          painter: PinStemPainter(),
        ),
      ],
    );
  }
}

class PinStemPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = ui.Paint()
      ..color = const Color(0xFF7C3AED)
      ..style = ui.PaintingStyle.fill;
    final path = ui.Path()
      ..moveTo(size.width / 2 - 4, 0)
      ..lineTo(size.width / 2 + 4, 0)
      ..lineTo(size.width / 2, size.height)
      ..close();
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(PinStemPainter _) => false;
}

class UserMarker extends StatelessWidget {
  final double heading;
  final Color accent;
  final CursorStyle cursorStyle;
  final CursorColor cursorColor;
  final bool ostrichIsMoving;
  final double ostrichSpeedKmh;
  const UserMarker({
    super.key,
    required this.heading,
    required this.accent,
    this.cursorStyle = CursorStyle.arrow,
    this.cursorColor = CursorColor.violet,
    this.ostrichIsMoving = false,
    this.ostrichSpeedKmh = 0,
  });
  @override
  Widget build(BuildContext context) => Transform.rotate(
        angle: heading * math.pi / 180,
        child: AnimatedCursorWidget(
          style: cursorStyle,
          cursorColor: cursorColor,
          animateOstrich: cursorStyle == CursorStyle.ostrich,
          ostrichIsMoving: ostrichIsMoving,
          ostrichSpeedKmh: ostrichSpeedKmh,
          size: 48,
        ),
      );
}

class DestinationMarker extends StatelessWidget {
  final Color color;
  final bool arrived;
  const DestinationMarker(
      {super.key, required this.color, this.arrived = false});
  @override
  Widget build(BuildContext context) =>
      Column(mainAxisSize: MainAxisSize.min, children: [
        Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
                color: color,
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(
                      color: color.withValues(alpha: 0.4),
                      blurRadius: 8,
                      spreadRadius: 2)
                ]),
            child: Icon(arrived ? Icons.check_rounded : Icons.flag_rounded,
                color: Colors.white, size: 18)),
        CustomPaint(
            size: const Size(2, 12), painter: PinLinePainter(color: color)),
      ]);
}

class PinLinePainter extends CustomPainter {
  final Color color;
  const PinLinePainter({required this.color});
  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawLine(
        Offset(size.width / 2, 0),
        Offset(size.width / 2, size.height),
        Paint()
          ..color = color
          ..strokeWidth = 2);
  }

  @override
  bool shouldRepaint(_) => false;
}
