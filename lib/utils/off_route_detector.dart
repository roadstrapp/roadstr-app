/// Decides when a driver has genuinely left the route.
///
/// Distance to the route alone is not enough, and a field report showed why:
/// in a grid of perpendicular streets a driver turned off deliberately and
/// kept going, one block away from the route the whole time. That block was
/// under the hard "definitely off route" threshold, so nothing ever fired —
/// the app kept matching the fix to a point on the old route *behind* the
/// car, drew the remaining route trailing back from there, and in effect
/// adopted the street being driven as the correct one. The only way back was
/// a 180° turn, which is not always legal or possible.
///
/// What distinguishes that from ordinary driving is not how far away the car
/// is, it is that the gap keeps growing and stays open: a driver following
/// the route wanders a few metres around it and comes back, while a driver
/// who has left it moves away and stays away. This tracks the closest recent
/// approach and reports a deviation once the car has held a meaningfully
/// larger gap for several consecutive fixes.
class OffRouteDetector {
  /// Below this the fix is simply on the road: GPS noise, lane width, and the
  /// offset between the polyline's centreline and the lane actually driven.
  static const noiseFloorM = 30.0;

  /// Above this the driver is somewhere else whatever the trend says —
  /// retuned in 0.4.12 against a real driving trace, kept unchanged here.
  static const hardThresholdM = 55.0;

  /// How much further from the route than the closest recent approach counts
  /// as having moved away, rather than as drift around it.
  static const awayGrowthM = 15.0;

  /// How many consecutive fixes must hold that gap. At the 2 Hz fix rate this
  /// is about two seconds — long enough that a jittery fix or a wide corner
  /// cannot trigger it, short enough to reroute within a few car lengths of
  /// leaving the route rather than a block later.
  static const awaySamples = 4;

  double? _closestM;
  int _samplesAway = 0;

  /// Forgets the current trend. Call when the route itself changes, so the
  /// closest approach to the *old* route never decides anything about the new
  /// one.
  void reset() {
    _closestM = null;
    _samplesAway = 0;
  }

  /// How much larger than the fix's own uncertainty a gap has to be before it
  /// is treated as evidence of anything.
  ///
  /// The same 1.5× margin the arrival radius already applies to accuracy.
  static const accuracyMargin = 1.5;

  /// Feeds one fix's distance to the route, and reports whether the driver
  /// should be considered off route.
  ///
  /// [distM] is the perpendicular distance to the active route polyline.
  /// [accuracyM] is that fix's own horizontal accuracy radius: in a street of
  /// tall buildings multipath can park a fix tens of metres off the road it
  /// was taken on, and rerouting off a reading that uncertain is chasing the
  /// error, not the driver. Such a fix is ignored for trend purposes — it
  /// neither proves a deviation nor disproves one, so it leaves the trend
  /// exactly as it was rather than resetting it.
  bool sawDeviation(double distM, {double accuracyM = 0}) {
    if (!distM.isFinite) return false;
    if (distM > hardThresholdM) {
      reset();
      return true;
    }
    final closest = _closestM;
    if (closest == null || distM < closest) {
      // Closer than we have been: this is the new baseline, and whatever
      // trend was building did not hold.
      _closestM = distM;
      _samplesAway = 0;
      return false;
    }
    if (distM - closest < awayGrowthM) {
      // Still hovering around the closest approach — ordinary drift.
      _samplesAway = 0;
      return false;
    }
    // This looks like a deviation; the remaining question is whether this
    // particular fix is certain enough to say so. A small distance is always
    // trusted — it is what establishes the baseline above — but a large one
    // from an uncertain fix is exactly what multipath produces.
    if (accuracyM.isFinite && distM < accuracyM * accuracyMargin) return false;
    _samplesAway++;
    // Holding a gap this far out only means anything once the car is off the
    // road itself; a 15 m swing that stays inside the noise floor is a wide
    // lane, not a wrong turn.
    if (distM < noiseFloorM) return false;
    if (_samplesAway < awaySamples) return false;
    reset();
    return true;
  }
}
