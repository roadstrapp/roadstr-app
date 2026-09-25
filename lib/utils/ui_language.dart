import 'dart:ui' show Locale, PlatformDispatcher;

import 'package:flutter/widgets.dart' show basicLocaleListResolution;
import 'package:hive/hive.dart';

import '../l10n/app_localizations.dart';

/// The language the app is showing right now, as a two-letter code: what the
/// user picked in Settings, or — when they left it on "system default" — what
/// Flutter resolves the phone's locale list to.
///
/// **Why this exists.** Anything that has to pick a language *without* a
/// widget context (route requests, the voice engine started in `initState`)
/// used to guess: the MapLibre screen asked every routing server for Italian,
/// and both screens started the voice engine in Italian whenever the language
/// setting was empty. So on a Spanish or German phone the on-screen text was
/// in the right language and the turn-by-turn was Italian — read aloud in a
/// German (or English) voice, accent and all.
///
/// [stored] is the Hive `'language'` value (empty or absent = follow the
/// device). With no override it resolves the same way `MaterialApp` does, so
/// this always agrees with `Localizations.localeOf(context)`.
String resolveUiLanguage({
  required String? stored,
  required List<Locale> deviceLocales,
  List<Locale>? supported,
}) {
  if (stored != null && stored.isNotEmpty) return stored;
  return basicLocaleListResolution(
    deviceLocales,
    supported ?? AppLocalizations.supportedLocales,
  ).languageCode;
}

/// [resolveUiLanguage] against the live settings box and the phone's locales.
String currentUiLanguage() => resolveUiLanguage(
      stored: Hive.box('settings').get('language') as String?,
      deviceLocales: PlatformDispatcher.instance.locales,
    );
