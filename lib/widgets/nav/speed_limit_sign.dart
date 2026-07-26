// The round speed-limit road sign shown beside the speedometer.
import 'package:flutter/material.dart';

import '../../utils/units.dart';

/// Compass / heading-mode FAB.
/// Shows a purple navigation cursor on a white circle; the cursor rotates to
/// reflect the current map orientation. A red "N" sits at the arrow tip so
/// the user can always see which way is north. When heading mode is active the
/// border glows purple.
class SpeedLimitSign extends StatelessWidget {
  final int speedKmh;
  const SpeedLimitSign(this.speedKmh, {super.key});

  @override
  Widget build(BuildContext context) {
    // Show the limit in the user's unit. Imperial-unit users are in mph
    // countries, where the posted sign reads mph, not the km/h OSM value.
    final display = Units.imperial
        ? Units.toDisplaySpeed(speedKmh.toDouble()).round()
        : speedKmh;
    final fontSize = display >= 100 ? 23.5 : 27.0;
    return Container(
      width: 78,
      height: 78,
      decoration: BoxDecoration(
        color: Colors.white,
        shape: BoxShape.circle,
        border: Border.all(color: Colors.red, width: 6.5),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withValues(alpha: 0.25),
              blurRadius: 6,
              offset: const Offset(0, 2)),
        ],
      ),
      child: Center(
        child: Text(
          '$display',
          style: TextStyle(
            color: Colors.black,
            fontSize: fontSize,
            fontWeight: FontWeight.w900,
            height: 1,
            letterSpacing: -0.5,
          ),
        ),
      ),
    );
  }
}
