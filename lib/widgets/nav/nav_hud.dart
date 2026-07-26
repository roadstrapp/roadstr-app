// The turn-by-turn heads-up display: the manoeuvre banner at the top of the
// screen and the progress panel at the bottom.
//
// Both are driven entirely by the route and the live GPS figures handed to
// them, so they can be rendered in isolation from any state or map.
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../services/routing_service.dart';
import '../../theme/app_theme.dart';
import '../../utils/units.dart';
import '../speedometer_widget.dart';

class NavInstruction extends StatelessWidget {
  final RouteStep step;

  /// The maneuver that follows the current one — shown as a small preview row.
  final RouteStep? nextStep;

  /// Distance of the current segment (= how far after THIS maneuver until the next).
  final double distToNextStepM;
  final RouteResult route;
  final int stepIdx;
  final RoadstrColors colors;
  final double topInset;

  /// Live GPS distance to the next maneuver point.
  final double distToNextM;
  const NavInstruction({
    super.key,
    required this.step,
    required this.route,
    required this.stepIdx,
    required this.colors,
    this.nextStep,
    this.distToNextStepM = 0,
    this.topInset = 0,
    this.distToNextM = 0,
  });

  @override
  Widget build(BuildContext context) {
    final land = MediaQuery.of(context).orientation == Orientation.landscape;
    final iconSz = land ? 36.0 : 56.0;
    final boxSz = land ? 60.0 : 96.0;
    final fsMain = land ? 18.0 : 26.0;
    final fsSub = land ? 14.0 : 19.0;
    final vPad = land ? 10.0 : 20.0;
    final showNext = nextStep != null && nextStep!.direction != 'arrive';
    // For long straight sections (highway, state road, etc.) display the live
    // remaining distance more prominently so the driver knows how far to go.
    final liveDist = distToNextM > 0 ? distToNextM : step.distanceM;
    final isLongStraight = step.distanceM > 2000 &&
        (step.direction == 'new name' ||
            step.direction == 'continue' ||
            step.direction == 'notification' ||
            step.direction == 'use lane' ||
            step.direction == 'depart');

    // The outer Column has NO background — only the instruction Container has it.
    // This prevents the surface2 colour from spilling into the transparent area
    // beside the compact chip (which caused the "white box" artefact).
    return Column(mainAxisSize: MainAxisSize.min, children: [
      // ── Main instruction panel ─────────────────────────────────────────────
      Container(
        padding: EdgeInsets.fromLTRB(16, topInset + vPad, 16, vPad),
        decoration: BoxDecoration(
          color: colors.surface2,
          boxShadow: [
            BoxShadow(
                color: Colors.black.withValues(alpha: 0.18),
                blurRadius: 12,
                offset: const Offset(0, 3))
          ],
        ),
        child: Row(children: [
          _stepIcon(step, boxSz, iconSz, colors),
          const SizedBox(width: 12),
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                Text(step.instruction,
                    style: TextStyle(
                        color: colors.textPrimary,
                        fontSize: fsMain,
                        fontWeight: FontWeight.w600),
                    maxLines: land ? 1 : 3,
                    overflow: TextOverflow.ellipsis),
                const SizedBox(height: 4),
                // Long straight road: show live distance prominently (large, accent).
                // Short manoeuvre: standard secondary distance label.
                if (isLongStraight)
                  Row(children: [
                    Icon(Icons.straight_rounded,
                        color: colors.accent, size: fsSub),
                    const SizedBox(width: 4),
                    Text(_distLabel(liveDist, AppLocalizations.of(context).now),
                        style: TextStyle(
                            color: colors.accent,
                            fontSize: fsSub,
                            fontWeight: FontWeight.w700)),
                  ])
                else
                  Text(_distLabel(liveDist, AppLocalizations.of(context).now),
                      style: TextStyle(
                          color: colors.textSecondary, fontSize: fsSub)),
              ])),
        ]),
      ),
      // ── Next-step preview tile (left-anchored, own background) ─────────────
      // Fixed width = exactly half the panel above (which is edge-to-edge,
      // i.e. half the screen width) so the tile never grows with long text —
      // it stays half as wide as the main instruction bar. Long instructions
      // wrap over the available lines and only then ellipsise.
      //
      // Sizing is a quarter shorter than it used to be (vertical padding
      // 28→20, icon 48→36, type 26/22→20/17): the tile is a preview of what
      // comes after the manoeuvre, so it must not eat the map. The type shrank
      // in step with the box so the same instructions still fit on three lines.
      if (showNext)
        Align(
          alignment: Alignment.centerLeft,
          child: Container(
            width: MediaQuery.of(context).size.width * 0.5,
            padding: EdgeInsets.symmetric(
                horizontal: land ? 16 : 24, vertical: land ? 12 : 20),
            decoration: BoxDecoration(
              color: colors.surface3,
              borderRadius:
                  const BorderRadius.only(bottomRight: Radius.circular(20)),
              boxShadow: [
                BoxShadow(
                    color: Colors.black.withValues(alpha: 0.15),
                    blurRadius: 8,
                    offset: const Offset(2, 4))
              ],
            ),
            child: Row(children: [
              Icon(_directionIcon(nextStep!.direction, nextStep!.modifier),
                  color: colors.accent, size: land ? 23 : 36),
              const SizedBox(width: 10),
              // Expanded (not Flexible) so the text takes the whole remaining
              // width of the fixed tile and wraps there, instead of letting the
              // Row grow to fit long instructions.
              Expanded(
                  child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  // "then …" makes it unmistakable that this is the manoeuvre
                  // AFTER the one in the main banner, not the current one.
                  Text(
                      AppLocalizations.of(context)
                          .thenManeuver(_uncapitalised(nextStep!.instruction)),
                      style: TextStyle(
                          color: colors.textPrimary,
                          fontSize: land ? 13 : 20,
                          fontWeight: FontWeight.w600),
                      maxLines: land ? 2 : 3,
                      overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 3),
                  Text(_distLabel(nextStep!.distanceM, ''),
                      style: TextStyle(
                          color: colors.textSecondary,
                          fontSize: land ? 11 : 17,
                          fontWeight: FontWeight.w500)),
                ],
              )),
            ]),
          ),
        ),
    ]);
  }

  String _distLabel(double m, String nowLabel) =>
      Units.fmtDist(m, nowLabel: nowLabel);

  /// Lowercases the first letter of a router instruction so it reads naturally
  /// after the "then" prefix ("Continue on Via Roma" → "then continue on Via
  /// Roma"). Acronyms and road codes are left untouched: a second uppercase
  /// character (or a digit) means the word is not an ordinary sentence start,
  /// so "SS16 exit" stays "SS16 exit".
  static String _uncapitalised(String s) {
    if (s.length < 2) return s;
    final second = s[1];
    if (second.toUpperCase() == second && second.toLowerCase() != second) {
      return s; // "SS16", "NW" …
    }
    return '${s[0].toLowerCase()}${s.substring(1)}';
  }

  /// Returns either a roundabout custom icon (when exit data is available) or
  /// the standard direction icon box.
  Widget _stepIcon(RouteStep s, double boxSz, double iconSz, RoadstrColors c) {
    final isRound = s.direction == 'roundabout' || s.direction == 'rotary';
    if (isRound && s.exitNumber != null && s.exitNumber! >= 1) {
      return SizedBox(
        width: boxSz,
        height: boxSz,
        child: CustomPaint(
          painter: RoundaboutPainter(
            exitNumber: s.exitNumber!.clamp(1, 6),
            accent: c.accent,
            ring: c.border,
            island: c.surface3,
          ),
        ),
      );
    }
    return Container(
      width: boxSz,
      height: boxSz,
      decoration: BoxDecoration(
          color: c.accentSoft, borderRadius: BorderRadius.circular(12)),
      child: Icon(_directionIcon(s.direction, s.modifier),
          color: c.accent, size: iconSz),
    );
  }

  IconData _directionIcon(String direction, String modifier) {
    // Map direction+modifier to a meaningful icon.
    switch (direction) {
      case 'arrive':
        return Icons.flag_rounded;
      case 'depart':
        return Icons.play_arrow_rounded;
      case 'roundabout':
      case 'rotary':
        return Icons.roundabout_right;
      case 'merge':
        return Icons.merge;
      case 'fork':
        return modifier.contains('left') ? Icons.fork_left : Icons.fork_right;
      case 'on ramp':
      case 'off ramp':
        return modifier.contains('left') ? Icons.ramp_left : Icons.ramp_right;
      case 'end of road':
      case 'turn':
      case 'new name':
        return switch (modifier) {
          'left' => Icons.turn_left,
          'right' => Icons.turn_right,
          'slight left' => Icons.turn_slight_left,
          'slight right' => Icons.turn_slight_right,
          'sharp left' => Icons.turn_sharp_left,
          'sharp right' => Icons.turn_sharp_right,
          'uturn' => Icons.u_turn_left,
          _ => Icons.straight,
        };
      default:
        return Icons.straight;
    }
  }
}

class NavPanel extends StatelessWidget {
  final RouteResult route;
  final double speed;
  final double bottomInset;
  final RoadstrColors colors;
  final VoidCallback onStop;

  /// Live remaining distance in metres (updated every GPS tick).
  final double remainingDistM;

  /// Live remaining seconds (estimated from remaining distance).
  final double remainingSecs;
  final int? speedLimit;
  const NavPanel(
      {super.key,
      required this.route,
      required this.speed,
      required this.bottomInset,
      required this.colors,
      required this.onStop,
      this.remainingDistM = 0,
      this.remainingSecs = 0,
      this.speedLimit});

  String get _distLabel {
    final m = remainingDistM > 0 ? remainingDistM : route.totalDistanceM;
    return Units.fmtDist(m);
  }

  String _timeLabel(AppLocalizations l) {
    final secs = remainingSecs > 0 ? remainingSecs : route.totalDurationS;
    final m = (secs / 60).round();
    if (m < 60) return l.durationMin(m);
    final h = m ~/ 60;
    final rem = m % 60;
    return l.durationHourMin(h, rem);
  }

  String get _etaLabel {
    final secs = remainingSecs > 0 ? remainingSecs : route.totalDurationS;
    final arr = DateTime.now().add(Duration(seconds: secs.round()));
    return '${arr.hour.toString().padLeft(2, '0')}:'
        '${arr.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final land = MediaQuery.of(context).orientation == Orientation.landscape;
    final speedoSz = land ? 70.0 : 110.0;
    // Bumped for at-a-glance legibility while driving. The row height is
    // governed by the speedometer (70/110 px), so larger type does not grow
    // the bar — the text column still fits well within that height.
    final fsDist = land ? 24.0 : 34.0;
    final fsSub = land ? 15.5 : 19.0;
    final vTop = land ? 6.0 : 14.0;
    final vBot = land
        ? (bottomInset > 0 ? bottomInset + 4 : 8.0)
        : (bottomInset > 0 ? bottomInset : 16.0);
    return Container(
      decoration: BoxDecoration(
        color: colors.surface2,
        border: Border(top: BorderSide(color: colors.border, width: 0.5)),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withValues(alpha: 0.12),
              blurRadius: 12,
              offset: const Offset(0, -2))
        ],
      ),
      padding: EdgeInsets.only(left: 16, right: 16, top: vTop, bottom: vBot),
      child: Row(children: [
        SpeedometerWidget(
            speedKmh: speed, size: speedoSz, speedLimit: speedLimit),
        const SizedBox(width: 12),
        Expanded(
            child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
              // Remaining distance — updates every GPS tick
              Text(_distLabel,
                  style: TextStyle(
                      color: colors.textPrimary,
                      fontSize: fsDist,
                      fontWeight: FontWeight.bold)),
              Row(children: [
                // Remaining time
                Text(_timeLabel(l),
                    style: TextStyle(
                        color: colors.textSecondary, fontSize: fsSub)),
                if (!land) ...[
                  Text('  ·  ',
                      style: TextStyle(
                          color: colors.textSecondary, fontSize: fsSub)),
                  // Estimated time of arrival
                  Text(l.etaArrivalLabel(_etaLabel),
                      style: TextStyle(
                          color: colors.textSecondary, fontSize: fsSub)),
                ],
              ]),
            ])),
        // Stop navigation
        GestureDetector(
          onTap: onStop,
          child: Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: const Color(0xFFFF4444).withValues(alpha: 0.12),
                shape: BoxShape.circle,
                border: Border.all(
                    color: const Color(0xFFFF4444).withValues(alpha: 0.4)),
              ),
              child: const Icon(Icons.close_rounded,
                  color: Color(0xFFFF4444), size: 24)),
        ),
      ]),
    );
  }
}

/// Draws a roundabout diagram with the highlighted exit arc for exit N (1-6).
///
/// Canvas geometry (y-down, angles CW-positive):
///   entry  = bottom = π/2
///   traffic flows CCW (right-hand traffic) → negative sweep
///   exit N = π/2 - N × 75°  (canvas angle, counting CCW from entry)
///   highlighted arc sweeps CCW (negative) from entry to exit N
class RoundaboutPainter extends CustomPainter {
  final int exitNumber; // 1-6
  final Color accent;
  final Color ring;
  final Color island;

  const RoundaboutPainter({
    required this.exitNumber,
    required this.accent,
    required this.ring,
    required this.island,
  });

  // 75° per exit in radians; CCW in canvas = subtract from entry angle
  static const _kStep = 75.0 * math.pi / 180.0;

  @override
  void paint(Canvas canvas, Size size) {
    final cx = size.width / 2;
    final cy = size.height / 2;
    final outerR = size.width * 0.42;
    final innerR = outerR * 0.50;
    final arrowLen = size.width * 0.14;
    final arrowTip = size.width * 0.06;
    final strokeW = size.width * 0.09;
    final arrowW = size.width * 0.06;

    // ── Background ring ───────────────────────────────────────────────────────
    canvas.drawCircle(
        Offset(cx, cy),
        (outerR + innerR) / 2,
        Paint()
          ..color = ring.withValues(alpha: 0.5)
          ..strokeWidth = strokeW
          ..style = PaintingStyle.stroke);

    // ── Island fill ───────────────────────────────────────────────────────────
    canvas.drawCircle(Offset(cx, cy), innerR - strokeW * 0.5,
        Paint()..color = island.withValues(alpha: 0.4));

    // ── Angles ────────────────────────────────────────────────────────────────
    // entry at bottom (canvas π/2); exits go CCW = subtract
    const entryC = math.pi / 2.0;
    final n = exitNumber.clamp(1, 6);
    final exitC = entryC - n * _kStep; // canvas angle of the exit
    // Sweep is CCW (negative). Clamp so exit 5-6 don't wrap past full circle.
    final sweep = (-n * _kStep).clamp(-5.8, -0.25);

    // ── Highlighted arc (accent, CCW from entry to exit) ─────────────────────
    canvas.drawArc(
      Rect.fromCircle(center: Offset(cx, cy), radius: (outerR + innerR) / 2),
      entryC,
      sweep,
      false,
      Paint()
        ..color = accent
        ..strokeWidth = strokeW
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.butt,
    );

    // ── Entry arrow: from outside inward at bottom ────────────────────────────
    final entryRing =
        Offset(cx + outerR * math.cos(entryC), cy + outerR * math.sin(entryC));
    final entryOut = Offset(cx + (outerR + arrowLen) * math.cos(entryC),
        cy + (outerR + arrowLen) * math.sin(entryC));
    canvas.drawLine(
        entryOut,
        entryRing,
        Paint()
          ..color = ring
          ..strokeWidth = arrowW
          ..strokeCap = StrokeCap.round);

    // ── Exit arrow: from ring outward at exit angle (accent) ──────────────────
    final exitRing =
        Offset(cx + outerR * math.cos(exitC), cy + outerR * math.sin(exitC));
    final exitOut = Offset(cx + (outerR + arrowLen) * math.cos(exitC),
        cy + (outerR + arrowLen) * math.sin(exitC));
    final exitPaint = Paint()
      ..color = accent
      ..strokeWidth = arrowW
      ..strokeCap = StrokeCap.round;
    canvas.drawLine(exitRing, exitOut, exitPaint);

    // Arrowhead chevron at tip
    final back = exitC + math.pi;
    const hw = 0.4;
    canvas.drawLine(
        exitOut,
        Offset(exitOut.dx + arrowTip * math.cos(back + hw),
            exitOut.dy + arrowTip * math.sin(back + hw)),
        exitPaint);
    canvas.drawLine(
        exitOut,
        Offset(exitOut.dx + arrowTip * math.cos(back - hw),
            exitOut.dy + arrowTip * math.sin(back - hw)),
        exitPaint);
  }

  @override
  bool shouldRepaint(RoundaboutPainter old) =>
      old.exitNumber != exitNumber || old.accent != accent;
}
