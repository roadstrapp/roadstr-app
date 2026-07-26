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
  static double toDisplaySpeed(double kmh) => imperial ? kmh / 1.60934 : kmh;

  static String get speedUnit => imperial ? 'mph' : 'km/h';

  /// Spoken unit for TTS.  Symbols such as "km/h" are read literally by
  /// phonemizers (for example "kappaemme barra acca" in Italian), so voice
  /// alerts must always use words.
  static String speedUnitForSpeech(String lang) => switch (lang) {
        'it' => imperial ? 'miglia orarie' : 'chilometri orari',
        'en' => imperial ? 'miles per hour' : 'kilometres per hour',
        'de' => imperial ? 'Meilen pro Stunde' : 'Kilometer pro Stunde',
        'es' => imperial ? 'millas por hora' : 'kilómetros por hora',
        'fr' => imperial ? 'miles par heure' : 'kilomètres par heure',
        'pt' => imperial ? 'milhas por hora' : 'quilómetros por hora',
        'nl' => imperial ? 'mijl per uur' : 'kilometer per uur',
        'da' => imperial ? 'mil i timen' : 'kilometer i timen',
        'sv' => imperial ? 'miles per hour' : 'kilometer i timmen',
        'fi' => imperial ? 'mailia tunnissa' : 'kilometriä tunnissa',
        'pl' => imperial ? 'mil na godzinę' : 'kilometrów na godzinę',
        'cs' => imperial ? 'mil za hodinu' : 'kilometrů za hodinu',
        'sk' => imperial ? 'míľ za hodinu' : 'kilometrov za hodinu',
        'sl' => imperial ? 'milj na uro' : 'kilometrov na uro',
        'hr' => imperial ? 'milja na sat' : 'kilometara na sat',
        'hu' => imperial ? 'mérföld per óra' : 'kilométer per óra',
        'ro' => imperial ? 'mile pe oră' : 'kilometri pe oră',
        'bg' => imperial ? 'мили в час' : 'километра в час',
        'ru' => imperial ? 'миль в час' : 'километров в час',
        'uk' => imperial ? 'миль на годину' : 'кілометрів на годину',
        'el' => imperial ? 'μίλια ανά ώρα' : 'χιλιόμετρα ανά ώρα',
        'et' => imperial ? 'miili tunnis' : 'kilomeetrit tunnis',
        'lt' => imperial ? 'mylių per valandą' : 'kilometrų per valandą',
        'lv' => imperial ? 'jūdzes stundā' : 'kilometri stundā',
        'ga' => imperial ? 'míle san uair' : 'ciliméadar san uair',
        'mt' => imperial ? 'mili fis-siegħa' : 'kilometri fis-siegħa',
        'ja' => imperial ? 'マイル毎時' : 'キロ毎時',
        'zh' => imperial ? '英里每小时' : '公里每小时',
        _ => imperial ? 'miles per hour' : 'kilometres per hour',
      };

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
