// The turn-by-turn heads-up display: the manoeuvre banner at the top of the
// screen and the progress panel at the bottom.
//
// Both are driven entirely by the route and the live GPS figures handed to
// them, so they can be rendered in isolation from any state or map.
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../services/routing_service.dart';
import '../../theme/app_theme.dart';
import '../map/map_chrome.dart';
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
    this.voiceMuted = false,
    this.onToggleVoice,
  });

  /// Voice guidance state and toggle.
  ///
  /// Rendered at the end of this column rather than positioned by hand on the
  /// stack: it must sit below the next-step tile, and that tile's height moves
  /// with orientation, text length, and whether it is shown at all. Anchoring
  /// it to the layout keeps it right without a magic offset that would drift
  /// the first time any of those changed.
  final bool voiceMuted;
  final VoidCallback? onToggleVoice;

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
        // Inset from the screen edges and rounded at the bottom: the panel
        // becomes an object resting over the map rather than a bar welded to
        // the top of it, and the sliver of map now visible down each side is
        // what sells the depth.
        margin: EdgeInsets.fromLTRB(10, topInset + 6, 10, 0),
        padding: EdgeInsets.fromLTRB(18, vPad, 18, vPad),
        decoration: BoxDecoration(
          // The gradient is the "modern" themes' whole point, and the driving
          // panels are where it earns its place. Null on every other theme,
          // where the flat colour applies exactly as before.
          gradient: colors.panelGradient,
          color: colors.panelGradient == null ? colors.surface2 : null,
          borderRadius: BorderRadius.circular(RoadstrColors.panelRadius),
          border: Border.all(color: colors.panelEdge, width: 1),
          boxShadow: colors.panelShadow,
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
                        fontWeight: FontWeight.w700,
                        height: 1.15,
                        // Large type set at default tracking looks loose; a
                        // slight negative pull tightens the phrase into one
                        // shape the eye takes in at a glance.
                        letterSpacing: -0.4),
                    maxLines: land ? 1 : 3,
                    overflow: TextOverflow.ellipsis),
                SizedBox(height: land ? 4 : 7),
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
            margin: const EdgeInsets.only(left: 10, top: 8),
            // 15% narrower than the half-screen tile this used to be, so it
            // covers less map. Type and padding come down with it rather than
            // the text being clipped: the same words still fit in the same
            // number of lines, just set slightly smaller.
            width: MediaQuery.of(context).size.width * 0.425,
            padding: EdgeInsets.symmetric(
                horizontal: land ? 13 : 20, vertical: land ? 12 : 18),
            decoration: BoxDecoration(
              gradient: colors.panelGradient,
              color: colors.panelGradient == null ? colors.surface3 : null,
              // Rounded on every corner now that it floats clear of the panel
              // above, and a step smaller in radius, edge and shadow than the
              // main panel — subordinate by weight rather than only by size.
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                  color: colors.panelEdge.withValues(alpha: 0.5), width: 1),
              boxShadow: [
                BoxShadow(
                    color: Colors.black.withValues(alpha: 0.12),
                    blurRadius: 16,
                    spreadRadius: -3,
                    offset: const Offset(0, 4))
              ],
            ),
            child: Row(children: [
              ManeuverSymbol(
                step: nextStep!,
                size: land ? 26 : 36,
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
                          fontSize: land ? 12 : 18,
                          fontWeight: FontWeight.w600),
                      maxLines: land ? 2 : 3,
                      overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 3),
                  Text(_distLabel(distToNextStepM, ''),
                      style: TextStyle(
                          color: colors.textSecondary,
                          fontSize: land ? 10 : 15,
                          fontWeight: FontWeight.w500)),
                ],
              )),
            ]),
          ),
        ),
      if (onToggleVoice != null)
        Align(
          alignment: Alignment.centerLeft,
          child: Padding(
            padding: const EdgeInsets.only(left: 10, top: 8),
            child: MapFab(
              onTap: onToggleVoice!,
              colors: colors,
              child: Icon(
                  voiceMuted
                      ? Icons.volume_off_rounded
                      : Icons.volume_up_rounded,
                  color: colors.onAccent
                      .withValues(alpha: voiceMuted ? 0.45 : 1.0),
                  size: 22),
            ),
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

/// Height of the stop-navigation button.
const double _stopBtnH = 66.0;
const Color _stopBtnRed = Color(0xFFFF4444);

/// Vertical wash for the stop button, written as (pixels from the top, how
/// far to white at that point).
///
/// Absolute pixels rather than fractions because the shape was tuned by eye
/// against the real control: "1 px of red then 6 px to 46% white" is the
/// instruction, and fractions would hide it.
///
/// The curve is front-loaded on purpose. A straight ramp puts a 50% pink
/// through the middle of the button, and pink — not white — becomes the
/// colour the eye reads. Letting the red go quickly hands most of the height
/// to near-white while keeping the transition free of any visible step.
const List<(double, double)> _stopBtnRamp = [
  (0.0, 0.72), // already mostly white at the top edge — no red cap at all
  (22.0, 0.87),
  (34.0, 0.96),
  (56.0, 1.00), // pure white for the last 10 px
  (_stopBtnH, 1.00),
];

/// Colour the ramp resolves to at its far end.
///
/// On a dark panel a white button would be the brightest object on the screen
/// at night, pulling the eye away from the road; it resolves into a near-black
/// blue instead, so the red cap still marks the control while the body sinks
/// into the panel.
const Color _stopBtnDarkEnd = Color(0xFF0E1424);

LinearGradient _stopBtnGradientFor(bool dark) {
  final end = dark ? _stopBtnDarkEnd : Colors.white;
  return LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [
      for (final (_, whiteness) in _stopBtnRamp)
        Color.lerp(_stopBtnRed, end, whiteness)!,
    ],
    stops: [
      for (final (px, _) in _stopBtnRamp) px / _stopBtnH,
    ],
  );
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
        gradient: colors.panelGradient,
        color: colors.panelGradient == null ? colors.surface2 : null,
        // Rounded only at the top: the bar still meets the bottom edge of the
        // screen, so rounding there would leave a stripe of map under the
        // system gesture area with nothing in it.
        borderRadius: const BorderRadius.vertical(
            top: Radius.circular(RoadstrColors.panelRadius)),
        border: Border(
          top: BorderSide(color: colors.panelEdge, width: 1),
        ),
        boxShadow: colors.panelShadow,
      ),
      padding: EdgeInsets.only(left: 18, right: 14, top: vTop, bottom: vBot),
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
              // Time to arrival leads. What a driver is deciding — whether to
              // stop, whether they will make an appointment — depends on how
              // long is left, not on how many kilometres remain; the distance
              // is the supporting detail, so it takes the secondary style.
              Text(_timeLabel(l),
                  style: TextStyle(
                      color: colors.textPrimary,
                      fontSize: fsDist,
                      fontWeight: FontWeight.w800,
                      height: 1.05,
                      letterSpacing: -0.8)),
              SizedBox(height: land ? 1 : 3),
              Row(children: [
                // Remaining distance — updates every GPS tick
                Text(_distLabel,
                    style: TextStyle(
                        color: colors.textSecondary,
                        fontSize: fsSub,
                        fontWeight: FontWeight.w600)),
                if (!land) ...[
                  Text('  ·  ',
                      style: TextStyle(
                          color: colors.textSecondary, fontSize: fsSub)),
                  // Estimated time of arrival
                  Text(l.etaArrivalLabel(_etaLabel),
                      style: TextStyle(
                          color: colors.textSecondary,
                          fontSize: fsSub,
                          fontWeight: FontWeight.w600)),
                ],
              ]),
            ])),
        // Stop navigation
        GestureDetector(
          onTap: onStop,
          child: Container(
              // Upright: narrow at the base and tall, so it reads as a
              // distinct control rather than as another wide info tile
              // competing with the figures beside it.
              width: 46,
              height: _stopBtnH,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                gradient: _stopBtnGradientFor(colors.isDark),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                    color: _stopBtnRed.withValues(alpha: 0.45)),
              ),
              child: Icon(Icons.close_rounded,
                  // Brighter red on the dark body, where the deeper shade
                  // used on white would disappear.
                  color: colors.isDark
                      ? const Color(0xFFFF6B6B)
                      : const Color(0xFFD32F2F),
                  size: 26)),
        ),
      ]),
    );
  }
}
