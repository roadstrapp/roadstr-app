import 'dart:math' as math;

/// Calculates sunrise and sunset times using the NOAA simplified solar algorithm.
///
/// Returns UTC [DateTime] values. Returns null for rise or set in polar
/// day/night conditions where the sun never crosses the horizon.
class SunCalc {
  static ({DateTime? rise, DateTime? set}) sunTimes(
      double lat, double lng, DateTime date) {
    final doy = _dayOfYear(date);
    final B = 2 * math.pi * (doy - 1) / 365.0;

    // Solar declination (Spencer 1971, radians)
    final decl = 0.006918
        - 0.399912 * math.cos(B)    + 0.070257 * math.sin(B)
        - 0.006758 * math.cos(2*B)  + 0.000907 * math.sin(2*B)
        - 0.002697 * math.cos(3*B)  + 0.001480 * math.sin(3*B);

    // Equation of time in minutes
    final eot = 229.18 * (
        0.000075
        + 0.001868 * math.cos(B)   - 0.032077 * math.sin(B)
        - 0.014615 * math.cos(2*B) - 0.040890 * math.sin(2*B));

    final latRad = lat * math.pi / 180.0;
    // Zenith 90.833° accounts for sun disc radius + atmospheric refraction
    final cosZenith = math.cos(90.833 * math.pi / 180.0);
    final cosHa = (cosZenith - math.sin(latRad) * math.sin(decl))
                / (math.cos(latRad) * math.cos(decl));

    // Polar day or polar night — no crossing
    if (cosHa < -1 || cosHa > 1) return (rise: null, set: null);

    final ha = math.acos(cosHa) * 180.0 / math.pi; // degrees
    final solarNoon = 720.0 - 4.0 * lng - eot;     // minutes from UTC midnight
    final riseMin = solarNoon - 4.0 * ha;
    final setMin  = solarNoon + 4.0 * ha;

    final base = DateTime.utc(date.year, date.month, date.day);
    return (
      rise: base.add(Duration(seconds: (riseMin * 60).round())),
      set:  base.add(Duration(seconds: (setMin  * 60).round())),
    );
  }

  static int _dayOfYear(DateTime d) =>
      d.difference(DateTime(d.year, 1, 1)).inDays + 1;
}
