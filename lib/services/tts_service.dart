import 'package:flutter_tts/flutter_tts.dart';

/// Text-to-speech service for turn-by-turn navigation voice guidance.
/// All audio is generated on-device by the platform TTS engine; no data
/// is sent to any external server.
class TtsService {
  final FlutterTts _tts = FlutterTts();
  String _lang = 'en';

  Future<void> init(String languageCode) async {
    _lang = languageCode;
    await _tts.setLanguage(_bcp47(languageCode));
    await _tts.setSpeechRate(0.95);
    await _tts.setVolume(1.0);
    await _tts.setPitch(1.0);
    // Non-blocking: navigation keeps running while TTS speaks.
    await _tts.awaitSpeakCompletion(false);
  }

  Future<void> updateLanguage(String languageCode) async {
    _lang = languageCode;
    await _tts.setLanguage(_bcp47(languageCode));
  }

  /// Speaks [text] immediately, interrupting any ongoing speech.
  Future<void> speak(String text) async {
    await _tts.stop();
    await _tts.speak(text);
  }

  /// Announces an upcoming maneuver with a distance prefix.
  /// [instruction] is the already-localised turn instruction (e.g. "Turn right on Via Roma").
  /// [distMeters] is the remaining distance to the maneuver point.
  Future<void> announceManeuver(String instruction, int distMeters) async {
    final prefix = distMeters > 0 ? _inMeters(distMeters, _lang) : '';
    await speak('$prefix$instruction');
  }

  Future<void> announceArrival() => speak(_arrived(_lang));

  Future<void> stop() async => _tts.stop();

  void dispose() => _tts.stop();

  // ── Helpers ─────────────────────────────────────────────────────────────────

  static String _bcp47(String lang) {
    const map = {
      'it': 'it-IT', 'en': 'en-US', 'fr': 'fr-FR', 'de': 'de-DE',
      'es': 'es-ES', 'pt': 'pt-PT', 'nl': 'nl-NL', 'pl': 'pl-PL',
      'ru': 'ru-RU', 'zh': 'zh-CN', 'ja': 'ja-JP', 'bg': 'bg-BG',
      'cs': 'cs-CZ', 'da': 'da-DK', 'el': 'el-GR', 'et': 'et-EE',
      'fi': 'fi-FI', 'ga': 'ga-IE', 'hr': 'hr-HR', 'hu': 'hu-HU',
      'lt': 'lt-LT', 'lv': 'lv-LV', 'mt': 'mt-MT', 'ro': 'ro-RO',
      'sk': 'sk-SK', 'sl': 'sl-SI', 'sv': 'sv-SE',
    };
    return map[lang] ?? 'en-US';
  }

  static String _inMeters(int m, String lang) {
    switch (lang) {
      case 'it': return 'Tra $m metri, ';
      case 'fr': return 'Dans $m mètres, ';
      case 'de': return 'In $m Metern, ';
      case 'es': return 'En $m metros, ';
      case 'pt': return 'Em $m metros, ';
      case 'nl': return 'Over $m meter, ';
      case 'pl': return 'Za $m metrów, ';
      case 'ru': return 'Через $m метров, ';
      case 'zh': return '在${m}米后，';
      case 'ja': return '${m}メートル先で、';
      case 'bg': return 'След $m метра, ';
      case 'cs': return 'Za $m metrů, ';
      case 'da': return 'Om $m meter, ';
      case 'el': return 'Σε $m μέτρα, ';
      case 'et': return '$m meetri pärast, ';
      case 'fi': return '$m metrin päästä, ';
      case 'hr': return 'Za $m metara, ';
      case 'hu': return '$m méter múlva, ';
      case 'lt': return 'Po $m metrų, ';
      case 'lv': return 'Pēc $m metriem, ';
      case 'ro': return 'Peste $m metri, ';
      case 'sk': return 'Za $m metrov, ';
      case 'sl': return 'Čez $m metrov, ';
      case 'sv': return 'Om $m meter, ';
      case 'ga': return 'I $m méadar, ';
      case 'mt': return 'Fi $m metru, ';
      default:   return 'In $m meters, ';
    }
  }

  static String _arrived(String lang) {
    switch (lang) {
      case 'it': return 'Sei arrivato a destinazione';
      case 'fr': return 'Vous êtes arrivé à destination';
      case 'de': return 'Sie haben Ihr Ziel erreicht';
      case 'es': return 'Has llegado a tu destino';
      case 'pt': return 'Chegou ao seu destino';
      case 'nl': return 'U bent op uw bestemming aangekomen';
      case 'pl': return 'Dotarłeś do celu';
      case 'ru': return 'Вы прибыли в пункт назначения';
      case 'zh': return '您已到达目的地';
      case 'ja': return '目的地に到着しました';
      case 'bg': return 'Пристигнахте на местоназначението';
      case 'cs': return 'Dorazili jste do cíle';
      case 'da': return 'Du er ankommet til din destination';
      case 'el': return 'Φτάσατε στον προορισμό σας';
      case 'et': return 'Olete sihtkohta jõudnud';
      case 'fi': return 'Olet saapunut määränpäähäsi';
      case 'hr': return 'Stigli ste na odredište';
      case 'hu': return 'Megérkezett úti céljához';
      case 'lt': return 'Pasiekėte savo tikslą';
      case 'lv': return 'Esat sasniedzis galamērķi';
      case 'ro': return 'Ați ajuns la destinație';
      case 'sk': return 'Dorazili ste do cieľa';
      case 'sl': return 'Prispeli ste na cilj';
      case 'sv': return 'Du har nått din destination';
      case 'ga': return 'Tá tú tagtha ar an gceann scríbe';
      case 'mt': return 'Wasalt fid-destinazzjoni tiegħek';
      default:   return 'You have arrived at your destination';
    }
  }
}
