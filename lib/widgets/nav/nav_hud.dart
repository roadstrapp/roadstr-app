// The turn-by-turn heads-up display: the manoeuvre banner at the top of the
// screen and the progress panel at the bottom.
//
// Both are driven entirely by the route and the live GPS figures handed to
// them, so they can be rendered in isolation from any state or map.
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../services/routing_service.dart';
import '../../theme/app_theme.dart';
import '../../utils/units.dart';
import '../speedometer_widget.dart';
import 'maneuver_symbol.dart';

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
    final l = AppLocalizations.of(context);
    final land = MediaQuery.of(context).orientation == Orientation.landscape;
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
          _stepIcon(step, boxSz, colors),
          const SizedBox(width: 12),
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                Text(_displayInstruction(step, l),
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
                    Text(_distLabel(liveDist, l.now),
                        style: TextStyle(
                            color: colors.accent,
                            fontSize: fsSub,
                            fontWeight: FontWeight.w700)),
                  ])
                else
                  Text(_distLabel(liveDist, l.now),
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
              ManeuverSymbol(
                step: nextStep!,
                size: land ? 28 : 40,
                colors: colors,
                showBackground: false,
              ),
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
                  Text(l.thenManeuver(_uncapitalised(nextStep!.instruction)),
                      style: TextStyle(
                          color: colors.textPrimary,
                          fontSize: land ? 13 : 20,
                          fontWeight: FontWeight.w600),
                      maxLines: land ? 2 : 3,
                      overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 3),
                  Text(_distLabel(distToNextStepM, ''),
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

  String _displayInstruction(RouteStep value, AppLocalizations l) {
    if (value.direction != 'arrive') return value.instruction;
    return switch (value.modifier) {
      'left' => l.arrivalAheadLeft,
      'right' => l.arrivalAheadRight,
      _ => l.arrivalAhead,
    };
  }

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

  Widget _stepIcon(RouteStep s, double boxSz, RoadstrColors c) {
    return ManeuverSymbol(step: s, size: boxSz, colors: c);
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
  final SpeedometerStyle speedometerStyle;
  const NavPanel(
      {super.key,
      required this.route,
      required this.speed,
      required this.bottomInset,
      required this.colors,
      required this.onStop,
      this.remainingDistM = 0,
      this.remainingSecs = 0,
      this.speedLimit,
      this.speedometerStyle = SpeedometerStyle.classic});

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
            speedKmh: speed,
            size: speedoSz,
            speedLimit: speedLimit,
            style: speedometerStyle),
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
