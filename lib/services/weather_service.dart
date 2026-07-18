import 'dart:convert';
import 'package:latlong2/latlong.dart';
import '../l10n/app_localizations.dart';
import 'bounded_http.dart';

class WeatherData {
  final double tempC;
  final int code;
  final double windKmh;

  const WeatherData({
    required this.tempC,
    required this.code,
    required this.windKmh,
  });

  String get emoji {
    if (code == 0) return '☀️';
    if (code <= 2) return '🌤️';
    if (code == 3) return '☁️';
    if (code <= 48) return '🌫️';
    if (code <= 55) return '🌦️';
    if (code <= 65) return '🌧️';
    if (code <= 77) return '🌨️';
    if (code <= 82) return '🌦️';
    return '⛈️';
  }

  String localizedDescription(AppLocalizations l) {
    if (code == 0) return l.weatherClear;
    if (code <= 2) return l.weatherPartlyCloudy;
    if (code == 3) return l.weatherCloudy;
    if (code <= 48) return l.weatherFog;
    if (code <= 55) return l.weatherLightRain;
    if (code <= 65) return l.weatherRain;
    if (code <= 77) return l.weatherSnow;
    if (code <= 82) return l.weatherShowers;
    return l.weatherThunderstorm;
  }
}

class WeatherService {
  static Future<WeatherData?> fetch(LatLng pos) async {
    try {
      // Privacy: the destination is often the user's home. Weather is a
      // regional quantity — Open-Meteo's own grid is ~1–11 km — so 2 decimals
      // (~1.1 km) gives identical results while denying Open-Meteo (and any
      // network observer) a street-level fix on where the user is heading.
      final uri = Uri.parse(
        'https://api.open-meteo.com/v1/forecast'
        '?latitude=${pos.latitude.toStringAsFixed(2)}'
        '&longitude=${pos.longitude.toStringAsFixed(2)}'
        '&current=temperature_2m,weather_code,wind_speed_10m'
        '&wind_speed_unit=kmh',
      );
      final res = await BoundedHttp.get(
        uri,
        headers: {'User-Agent': 'Roadstr/1.0'},
        maxBytes: 1024 * 1024,
        timeout: const Duration(seconds: 6),
      );
      if (res.statusCode != 200) return null;
      final j = jsonDecode(res.body) as Map<String, dynamic>;
      final cur = j['current'] as Map<String, dynamic>;
      return WeatherData(
        tempC: (cur['temperature_2m'] as num).toDouble(),
        code: (cur['weather_code'] as num).toInt(),
        windKmh: (cur['wind_speed_10m'] as num).toDouble(),
      );
    } catch (_) {
      return null;
    }
  }
}
