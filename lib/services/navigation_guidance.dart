/// What follows the current maneuver in a chained announcement.
enum ChainedTail {
  /// Nothing to add.
  none,

  /// The next maneuver, spoken without a distance because it is immediate.
  maneuver,

  /// The next maneuver, prefixed with how far away it is.
  maneuverWithDistance,

  /// "then you will arrive at your destination" — only when close enough.
  arrival,

  /// "then continue for 5 km" — the destination is too far to be worth
  /// naming yet, but the driver should know the road runs a long way.
  continueAhead,
}

/// Pure navigation-guidance policy shared by the live map and unit tests.
///
/// Keeping distance selection out of the widget makes the safety-critical
/// timing rules deterministic: they depend on travel mode and current speed,
/// never on screen refresh rate or camera orientation.
class NavigationGuidance {
  NavigationGuidance._();

  /// Voice announcement distances in metres.
  ///
  /// Driving guidance scales continuously from the requested urban cue
  /// (150 m at or below 45 km/h) to the motorway cue (800 m at or above
  /// 100 km/h). The near cue repeats the instruction at the point of action.
  ///
  /// Two cues, not three. The old middle cue was carried as a `mid` field that
  /// every caller set to zero, so the branch that spoke it was unreachable —
  /// dead code that still had to be read and reasoned about at every one of
  /// these call sites. If a maneuver ever genuinely warrants three warnings,
  /// this is where it comes back, deliberately and with a test.
  static ({int far, int near}) thresholds(
    double speedKmh,
    String transportMode,
  ) {
    if (transportMode == 'walking') return (far: 60, near: 15);
    if (transportMode == 'cycling') return (far: 150, near: 30);

    final speed = speedKmh.isFinite ? speedKmh.clamp(0.0, 160.0) : 0.0;
    final progress = ((speed - 45.0) / 55.0).clamp(0.0, 1.0);
    final far = _roundTo10(150.0 + 650.0 * progress);
    final near = _roundTo10(40.0 + 80.0 * progress);
    return (far: far, near: near);
  }

  /// What to append to the point-of-action announcement, if anything.
  ///
  /// The last time a maneuver is spoken is the driver's last chance to hear
  /// what comes after it, and until now that only happened when the next
  /// maneuver was within 55 m — anything further away was shown on screen and
  /// never said aloud. So the tail is now always offered, and it carries the
  /// distance: "take the first exit, then in 300 metres take the off-ramp".
  static ChainedTail chainedTail({
    required String followDirection,
    required double gapM,
  }) {
    if (!gapM.isFinite || gapM < 0) return ChainedTail.none;
    // "Head north on…" is a lifecycle message; it is never a follow-up.
    if (followDirection == 'depart') return ChainedTail.none;

    if (followDirection == 'arrive') {
      // Announcing the destination is only useful near it. Saying "then you
      // will arrive" with eleven miles of motorway still to go — which is what
      // a field report caught — tells the driver nothing and buries the
      // maneuver they actually have to make.
      return gapM <= arrivalChainWithinM
          ? ChainedTail.arrival
          : ChainedTail.continueAhead;
    }
    // Back to back: a distance here would be noise ("then in 20 metres…").
    return gapM < immediateChainBelowM
        ? ChainedTail.maneuver
        : ChainedTail.maneuverWithDistance;
  }

  /// Below this the follow-up maneuver is effectively part of the current one,
  /// so it is chained without a distance.
  static const immediateChainBelowM = 55.0;

  /// The destination is only worth naming inside this range; beyond it the
  /// driver is told how far the road runs instead.
  static const arrivalChainWithinM = 3000.0;

  /// Remaining route distance to a maneuver.
  ///
  /// A straight-line GPS distance is wrong on loops, ramps and parallel
  /// carriageways: the point can be physically close while still hundreds of
  /// metres ahead along the route. Route progress gives the distance the
  /// driver will actually travel.
  static double remainingToManeuver({
    required double maneuverProgressM,
    required double routeProgressM,
  }) {
    if (!maneuverProgressM.isFinite || !routeProgressM.isFinite) return 0;
    return (maneuverProgressM - routeProgressM).clamp(0.0, double.infinity);
  }

  /// The distance to speak in an announcement, or 0 for "do it now".
  ///
  /// Always derived from how far the driver actually still has to go. It is
  /// tempting to reach for the threshold that let the announcement through —
  /// it is right there, and it is roughly the right size — but it is a policy
  /// constant, not a measurement: an announcement triggered at 200 m by an
  /// 800 m window would then say "in 800 metres", which is worse than saying
  /// nothing. Rounded to 50 m because nobody gives directions in metres of
  /// precision.
  static int spokenDistanceM(double remainingM, {required int imminentBelowM}) {
    if (!remainingM.isFinite || remainingM <= imminentBelowM) return 0;
    return (remainingM / 50).round() * 50;
  }

  static int _roundTo10(double value) => (value / 10).round() * 10;
}
