// Locale (language) provider for Roadstr.
//
// Persists the user's language override in the Hive settings box under the
// key "language". When locale is null, MaterialApp uses the device's system
// locale. Setting an explicit Locale overrides that behaviour.
//
// The init method must be called (and awaited) before runApp so that the
// first frame renders in the correct language without a rebuild flash.
import 'package:flutter/material.dart';
import 'package:hive_flutter/hive_flutter.dart';

/// [ChangeNotifier] that holds the app-level locale override.
///
/// Consumed by [RoadstrApp] via `context.watch<LocaleProvider>()` to rebuild
/// the [MaterialApp.locale] when the user changes language in Settings.
class LocaleProvider extends ChangeNotifier {
  static const _key = 'language';
  Locale? _locale;

  /// The currently active locale override, or `null` to follow the device locale.
  Locale? get locale => _locale;

  /// Reads the persisted language code from Hive and reconstructs the [Locale].
  /// Called once in [main] before [runApp].
  Future<void> init() async {
    final code = Hive.box('settings').get(_key) as String?;
    _locale = code != null ? Locale(code) : null;
  }

  /// Updates the locale, persists it to Hive, and notifies listeners.
  ///
  /// Pass `null` to revert to the device system locale (removes the stored key).
  Future<void> setLocale(Locale? locale) async {
    _locale = locale;
    final box = Hive.box('settings');
    if (locale != null) {
      await box.put(_key, locale.languageCode);
    } else {
      await box.delete(_key);
    }
    notifyListeners();
  }
}
