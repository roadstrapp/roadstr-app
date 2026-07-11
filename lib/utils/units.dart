import 'package:hive_flutter/hive_flutter.dart';

/// Distance/speed formatting helpers that respect the metric/imperial setting.
///
/// Read the stored preference at call time so changes in Settings take effect
/// immediately without needing a restart or extra provider plumbing.
class Units {
  Units._();

  static bool get imperial =>
      Hive.box('settings').get('imperialUnits', defaultValue: false) as bool;

  /// Format [metres] for on-screen display.
  /// [nowLabel] is returned when the distance is below the minimum threshold.
  static String fmtDist(double metres, {String nowLabel = ''}) {
    if (metres < 50) return nowLabel.isNotEmpty ? nowLabel : '0';
    if (imperial) {
      final ft = metres / 0.3048;
      if (ft < 500) {
        final rounded = ((ft / 10).round() * 10).toInt();
        return '$rounded ft';
      }
      final mi = metres / 1609.344;
      if (mi < 10) return '${mi.toStringAsFixed(1)} mi';
      return '${mi.round()} mi';
    }
    if (metres < 1000) return '${metres.round()} m';
    return '${(metres / 1000).toStringAsFixed(1)} km';
  }

  /// Convert km/h → mph (display only; internal comparisons stay in km/h).
  static double toDisplaySpeed(double kmh) =>
      imperial ? kmh / 1.60934 : kmh;

  static String get speedUnit => imperial ? 'mph' : 'km/h';

  /// Build the TTS preamble for a maneuver distance announcement.
  /// Returns an empty string when [metres] is 0 (imminent turn).
  static String ttsDistPrefix(int metres, String lang) {
    if (metres <= 0) return '';
    if (!imperial) return _inMeters(metres, lang);
    final ft = (metres / 0.3048).round();
    if (ft < 500) {
      final rounded = (ft / 10).round() * 10;
      return _inFeet(rounded, lang);
    }
    final mi = metres / 1609.344;
    final miStr = mi < 10 ? mi.toStringAsFixed(1) : mi.round().toString();
    return _inMiles(miStr, lang);
  }

  static String _inMeters(int m, String lang) => switch (lang) {
        'it' => 'Tra $m metri, ',
        'es' => 'En $m metros, ',
        'fr' => 'Dans $m mètres, ',
        'ja' => '$mメートル先で、',
        'zh' => '在$m米后，',
        'pt' => 'Em $m metros, ',
        _ => 'In $m meters, ',
      };

  static String _inFeet(int ft, String lang) => switch (lang) {
        'it' => 'Tra $ft piedi, ',
        'es' => 'En $ft pies, ',
        'fr' => 'Dans $ft pieds, ',
        'ja' => '$ftフィート先で、',
        'zh' => '在$ft英尺后，',
        'pt' => 'Em $ft pés, ',
        _ => 'In $ft feet, ',
      };

  static String _inMiles(String mi, String lang) => switch (lang) {
        'it' => 'Tra $mi miglia, ',
        'es' => 'En $mi millas, ',
        'fr' => 'Dans $mi miles, ',
        'ja' => '$miマイル先で、',
        'zh' => '在$mi英里后，',
        'pt' => 'Em $mi milhas, ',
        _ => 'In $mi miles, ',
      };
}
