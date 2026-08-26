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
    // Under 50 m callers that supply a label ("now") use it; the rest showed a
    // bare "0" with no unit — on the final approach the whole panel read "0".
    if (metres < 50) {
      return nowLabel.isNotEmpty ? nowLabel : (imperial ? '0 ft' : '0 m');
    }
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

  /// Format [metres] of altitude for on-screen display. Whole units only —
  /// GPS altitude jitters by several metres between fixes, so a decimal
  /// place would show precision the fix does not actually have.
  static String fmtAltitude(double metres) => imperial
      ? '${(metres / 0.3048).round()} ft'
      : '${metres.round()} m';

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
    if (!imperial) {
      // Above a kilometre, say kilometres. "In 4567 metres" is a number nobody
      // can act on at speed — and it is the one distance the driver hears most,
      // because the motorway cue fires at 800 m and chained instructions can
      // reach much further ahead.
      if (metres >= 1000) return _inKilometres(_decimal(metres / 1000, lang), lang);
      return _inMeters(metres, lang);
    }
    final ft = (metres / 0.3048).round();
    if (ft < 500) {
      final rounded = (ft / 10).round() * 10;
      return _inFeet(rounded, lang);
    }
    final mi = metres / 1609.344;
    final miStr = mi < 10 ? mi.toStringAsFixed(1) : mi.round().toString();
    return _inMiles(miStr, lang);
  }

  /// The same distance phrase as [ttsDistPrefix], reshaped to sit inside a
  /// sentence rather than open one: "…, then in 300 metres take the exit".
  ///
  /// Derived from the prefix instead of duplicating the per-language tables,
  /// which would be two places to keep in step for no gain. Only two things
  /// differ: the trailing separator the prefix form ends with, and the initial
  /// capital. Lowercasing is safe here because every language that reaches
  /// this either has no letter case (ja, zh) or, in these phrases, opens with
  /// a preposition rather than a noun.
  static String ttsDistInline(int metres, String lang) {
    final prefix = ttsDistPrefix(metres, lang).trimRight();
    if (prefix.isEmpty) return '';
    final trimmed = prefix.replaceFirst(RegExp(r'[,、，]$'), '');
    if (trimmed.isEmpty) return '';
    return trimmed[0].toLowerCase() + trimmed.substring(1);
  }

  /// Whether [lang] separates words with spaces. Japanese and Chinese do not,
  /// and inserting one makes a spoken phrase read as two broken fragments.
  static bool usesWordSpacing(String lang) => lang != 'ja' && lang != 'zh';

  /// Joins a distance phrase to an instruction with the right spacing.
  static String joinDistance(String distance, String instruction, String lang) {
    if (distance.isEmpty) return instruction;
    return usesWordSpacing(lang)
        ? '$distance $instruction'
        : '$distance$instruction';
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

  /// Formats a distance with one decimal below ten, whole above, using the
  /// decimal separator the language is read with. A speech engine pronounces
  /// "4.5" and "4,5" differently, and only one of them is Italian.
  static String _decimal(double value, String lang) {
    final text =
        value < 10 ? value.toStringAsFixed(1) : value.round().toString();
    const commaDecimal = {'it', 'es', 'fr', 'pt', 'de', 'nl', 'pl', 'ru', 'cs',
        'sk', 'hu', 'ro', 'bg', 'hr', 'lt', 'lv', 'et', 'sl', 'da', 'fi', 'sv',
        'el', 'mt'};
    return commaDecimal.contains(lang) ? text.replaceAll('.', ',') : text;
  }

  static String _inKilometres(String km, String lang) => switch (lang) {
        'it' => 'Tra $km chilometri, ',
        'es' => 'En $km kilómetros, ',
        'fr' => 'Dans $km kilomètres, ',
        'ja' => '$kmキロ先で、',
        'zh' => '在$km公里后，',
        'pt' => 'Em $km quilómetros, ',
        _ => 'In $km kilometres, ',
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
