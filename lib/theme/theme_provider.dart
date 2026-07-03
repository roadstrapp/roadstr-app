import 'dart:async';
import 'package:flutter/material.dart';
import 'package:hive/hive.dart';
import 'app_theme.dart';
import '../services/sun_calc.dart';

class ThemeProvider extends ChangeNotifier {
  static const _key        = 'themeId';
  static const _autoDarkKey = 'autoDark';

  Box? _box;
  AppThemeId _current = AppThemeId.lightNostr;

  /// Whether automatic dark mode (sunset/sunrise) is enabled.
  bool _autoDarkEnabled = true;
  /// True when the current time/position is between sunset and sunrise.
  bool _autoDarkActive = false;

  // Last position used for the sun calculation (debounce ~10 km).
  double? _lastLat, _lastLng;
  Timer? _autoDarkTimer;

  AppThemeId get current => _current;

  /// Effective theme: switches to dark variant when auto-dark is active,
  /// but preserves the user's accent-colour choice (Nostr vs Bitcoin).
  AppThemeId get effective {
    if (_autoDarkEnabled && _autoDarkActive) {
      switch (_current) {
        case AppThemeId.lightNostr:   return AppThemeId.darkNostr;
        case AppThemeId.lightBitcoin: return AppThemeId.darkBitcoin;
        case AppThemeId.darkNostr:
        case AppThemeId.darkBitcoin:  return _current;
      }
    }
    return _current;
  }

  ThemeData get themeData         => AppTheme.build(_current);
  ThemeData get effectiveThemeData => AppTheme.build(effective);
  bool      get autoDarkEnabled   => _autoDarkEnabled;

  Future<void> init() async {
    try {
      _box = Hive.isBoxOpen('settings')
          ? Hive.box('settings')
          : await Hive.openBox('settings');
      final saved = _box?.get(_key, defaultValue: 0) as int;
      _current = AppThemeIdExt.fromIndex(saved);
      _autoDarkEnabled = _box?.get(_autoDarkKey, defaultValue: true) as bool;
    } catch (_) {
      _box = null;
    }
    notifyListeners();
  }

  void setTheme(AppThemeId id) {
    _current = id;
    try { _box?.put(_key, id.index2); } catch (_) {}
    notifyListeners();
  }

  void setAutoDarkEnabled(bool v) {
    _autoDarkEnabled = v;
    try { _box?.put(_autoDarkKey, v); } catch (_) {}
    if (!v) {
      _autoDarkTimer?.cancel();
      _autoDarkActive = false;
      notifyListeners();
    } else if (_lastLat != null && _lastLng != null) {
      // Re-enabled with known position: recalculate immediately.
      // Also reset debounce so _recalcAutoDark runs (not skipped by distance check).
      _recalcAutoDark(_lastLat!, _lastLng!);
    } else {
      notifyListeners();
    }
  }

  /// Called from the GPS stream with the current latitude/longitude.
  /// Debounces: only recalculates when the device has moved > ~10 km.
  void onPositionUpdate(double lat, double lng) {
    if (!_autoDarkEnabled) return;
    final prev = (_lastLat, _lastLng);
    if (prev.$1 != null && prev.$2 != null) {
      final dlat = lat - prev.$1!;
      final dlng = lng - prev.$2!;
      // ~0.09° ≈ 10 km — cheap flat-earth approximation, good enough here
      if (dlat * dlat + dlng * dlng < 0.0081) return;
    }
    _lastLat = lat;
    _lastLng = lng;
    _recalcAutoDark(lat, lng);
  }

  void _recalcAutoDark(double lat, double lng) {
    _autoDarkTimer?.cancel();
    final now = DateTime.now().toUtc();
    final times = SunCalc.sunTimes(lat, lng, now);

    bool active;
    DateTime? nextTransition;

    if (times.rise == null || times.set == null) {
      // Polar day/night — estimate by declination direction
      final doy = now.difference(DateTime.utc(now.year, 1, 1)).inDays;
      active = lat > 0 ? doy > 355 || doy < 80 : doy > 80 && doy < 355;
    } else {
      active = now.isBefore(times.rise!) || now.isAfter(times.set!);

      if (active) {
        // Dark now — next transition is tomorrow's sunrise (or today's if not yet past)
        if (now.isBefore(times.rise!)) {
          nextTransition = times.rise!;
        } else {
          // After sunset — next is tomorrow's sunrise
          final tomorrow = now.add(const Duration(days: 1));
          final t2 = SunCalc.sunTimes(lat, lng, tomorrow);
          nextTransition = t2.rise;
        }
      } else {
        // Light now — next transition is today's sunset
        nextTransition = times.set!;
      }
    }

    final changed = active != _autoDarkActive;
    _autoDarkActive = active;
    if (changed) notifyListeners();

    // Schedule a timer to flip exactly at the next sunrise/sunset.
    if (nextTransition != null) {
      final delay = nextTransition.difference(now);
      if (delay.inSeconds > 0) {
        _autoDarkTimer = Timer(delay, () => _recalcAutoDark(lat, lng));
      }
    }
  }

  @override
  void dispose() {
    _autoDarkTimer?.cancel();
    super.dispose();
  }
}
