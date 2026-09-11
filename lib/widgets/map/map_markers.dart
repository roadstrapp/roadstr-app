// Map overlay markers: the user's own position, the destination pin, dropped
// search pins and the two kinds of road-event pin.
//
// All of these are pure leaf widgets — they take colours and geometry as
// parameters and never reach back into the map screen's state.
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';

import '../../models/road_event.dart';
import '../../theme/app_theme.dart';
import '../cursor_painter.dart';

class RoadEventPin extends StatelessWidget {
  final RoadEvent event;
  const RoadEventPin({super.key, required this.event});
  @override
  Widget build(BuildContext context) => _MapSignalPin(
        emoji: event.category.emoji,
        accent: event.category.color,
        emphasized: true,
      );
}

/// Marker for a speed camera sourced from OSM/Overpass rather than a Nostr
/// community report — same purple family as [RoadEventPin]'s speed-camera
/// color, but muted fill + white ring instead of a solid pin, so drivers can
/// tell "known static camera" apart from a live community-confirmed one.
class OsmCameraPin extends StatelessWidget {
  const OsmCameraPin({super.key});
  @override
  Widget build(BuildContext context) => _MapSignalPin(
        emoji: '📷',
        accent: RoadCategory.speedCamera.color,
      );
}

/// Marker for an OSM-sourced traffic-signal node — a plain icon at the exact
/// intersection position, same visual family (muted fill, white ring) as
/// [OsmCameraPin] so both read as "static OSM data" rather than a live
/// community report.
class TrafficLightPin extends StatelessWidget {
  const TrafficLightPin({super.key});
  @override
  Widget build(BuildContext context) => const _MapSignalPin(
        emoji: '🚦',
        accent: Color(0xFF70D69B),
      );
}

/// Marker for an OSM-sourced pedestrian crossing (`highway=crossing`) — same
/// muted "static OSM data" family as [OsmCameraPin]/[TrafficLightPin].
class CrosswalkPin extends StatelessWidget {
  const CrosswalkPin({super.key});
  @override
  Widget build(BuildContext context) => const _MapSignalPin(
        emoji: '🚸',
        accent: Color(0xFFFFB347),
      );
}

/// Marker for an OSM-sourced speed bump/hump/table (`traffic_calming=*`) —
/// same muted "static OSM data" family as [OsmCameraPin]/[TrafficLightPin].
class SpeedBumpPin extends StatelessWidget {
  const SpeedBumpPin({super.key});
  @override
  Widget build(BuildContext context) => const _MapSignalPin(
        emoji: '〰️',
        accent: Color(0xFFFF8547),
      );
}

class _MapSignalPin extends StatelessWidget {
  final String emoji;
  final Color accent;
  final bool emphasized;

  const _MapSignalPin({
    required this.emoji,
    required this.accent,
    this.emphasized = false,
  });

  @override
  Widget build(BuildContext context) {
    final colors = RoadstrColors.of(context);
    return RepaintBoundary(
      child: Container(
        padding: const EdgeInsets.all(2.5),
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              accent.withValues(alpha: emphasized ? 0.96 : 0.78),
              accent.withValues(alpha: emphasized ? 0.68 : 0.42),
            ],
          ),
          shape: BoxShape.circle,
          border: Border.all(color: Colors.white.withValues(alpha: 0.78)),
          boxShadow: [
            BoxShadow(
              color: accent.withValues(alpha: emphasized ? 0.48 : 0.28),
              blurRadius: emphasized ? 11 : 7,
              spreadRadius: emphasized ? 1 : 0,
            ),
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.36),
              blurRadius: 5,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Container(
          decoration: BoxDecoration(
            color: colors.mapOverlayDark.withValues(alpha: 0.88),
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white.withValues(alpha: 0.10)),
          ),
          alignment: Alignment.center,
          child: Text(
            emoji,
            style: TextStyle(fontSize: emphasized ? 15 : 12.5, height: 1),
          ),
        ),
      ),
    );
  }
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
  Widget build(BuildContext context) => RepaintBoundary(
        child: Stack(
          clipBehavior: Clip.none,
          alignment: Alignment.center,
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(colors: [
                  accent.withValues(alpha: 0.25),
                  accent.withValues(alpha: 0),
                ]),
                boxShadow: [
                  BoxShadow(
                    color: accent.withValues(alpha: 0.28),
                    blurRadius: 15,
                    spreadRadius: 2,
                  ),
                ],
              ),
            ),
            Transform.rotate(
              angle: heading * math.pi / 180,
              child: AnimatedCursorWidget(
                style: cursorStyle,
                cursorColor: cursorColor,
                animateOstrich: cursorStyle == CursorStyle.ostrich,
                ostrichIsMoving: ostrichIsMoving,
                ostrichSpeedKmh: ostrichSpeedKmh,
                size: 48,
              ),
            ),
          ],
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
