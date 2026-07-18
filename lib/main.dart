// Roadstr — decentralised navigation + traffic alerts on Nostr.
//
// Application entry point. Handles the synchronous initialisation sequence
// that must complete before the first frame is drawn:
//
//   1. WidgetsFlutterBinding.ensureInitialized — required before any async
//      platform channel calls.
//   2. Hive initialisation and opening the settings box — providers read
//      persisted settings (theme, language, routing API keys) before runApp.
//   3. SystemChrome configuration:
//      - Orientation lock: portrait + both landscape modes (no upside-down).
//      - Edge-to-edge display mode: the map fills the entire screen including
//        status bar and navigation bar areas. Both bars are made transparent
//        with light icon tints so they blend with the map tiles.
//   4. ThemeProvider and LocaleProvider are pre-initialised so the first
//      build uses the correct theme/locale without a flicker.
//   5. runApp is called with a MultiProvider tree wrapping RoadstrApp.
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:path_provider/path_provider.dart';
import 'package:provider/provider.dart';
import 'l10n/app_localizations.dart';
import 'providers/locale_provider.dart';
import 'theme/theme_provider.dart';
import 'theme/app_theme.dart';
import 'screens/map_screen.dart';
import 'screens/onboarding_screen.dart';
import 'services/kokoro/kokoro_voices.dart';

Future<void> main() async {
  // Step 1: Required before any async platform-channel calls.
  WidgetsFlutterBinding.ensureInitialized();

  // In release builds, silence debugPrint entirely: routing logs, TTS phrases
  // and speed-limit lookups contain street names and coordinates that must
  // not end up in logcat.
  if (kReleaseMode) {
    debugPrint = (String? message, {int? wrapWidth}) {};
  }

  // Step 2: Hive must be ready before providers try to read persisted settings.
  // The settings box is AES-encrypted (favorites and search history contain
  // saved place coordinates); the key lives in the Android Keystore via
  // FlutterSecureStorage. Existing plaintext boxes are migrated once.
  await Hive.initFlutter();
  try {
    await _openEncryptedSettingsBox();
  } catch (error) {
    debugPrint('[Storage] Protected settings unavailable: $error');
    runApp(const _StorageUnavailableApp());
    return;
  }

  // Step 3a: Allow portrait and landscape (left/right); disallow upside-down.
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);

  // Step 3b: Edge-to-edge mode — the map renders under the system bars.
  // Both bars are made transparent so only the map tiles show through.
  SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
    systemNavigationBarColor: Colors.transparent,
    systemNavigationBarDividerColor: Colors.transparent,
    systemNavigationBarIconBrightness: Brightness.light,
  ));

  // Step 4: Pre-initialise providers so the first frame uses persisted settings.
  final themeProvider = ThemeProvider();
  await themeProvider.init();
  final localeProvider = LocaleProvider();
  await localeProvider.init();

  // Step 5: Launch the app with the provider tree.
  runApp(MultiProvider(
    providers: [
      ChangeNotifierProvider.value(value: themeProvider),
      ChangeNotifierProvider.value(value: localeProvider),
    ],
    child: const RoadstrApp(),
  ));
}

/// Opens the Hive 'settings' box encrypted with an AES-256 key held in the
/// platform secure storage (Android Keystore-backed). On first run after the
/// update, an existing plaintext box is migrated in place: its content is
/// copied into the encrypted box and the plaintext file is deleted.
///
/// Failure policy: fail closed while preserving the original file. Favorites,
/// history, parking position and the optional sync passphrase must never be
/// silently opened in plaintext or erased after a transient Keystore error.
Future<void> _openEncryptedSettingsBox() async {
  const st = FlutterSecureStorage();
  List<int>? key;
  try {
    final stored = await st.read(key: 'hive_settings_key');
    if (stored != null) {
      key = base64Decode(stored);
    } else {
      key = Hive.generateSecureKey();
      await st.write(key: 'hive_settings_key', value: base64Encode(key));
    }
  } catch (_) {
    throw StateError(
        'Secure storage is unavailable; settings were not opened.');
  }

  if (key.length != 32) {
    throw StateError('The settings encryption key is invalid.');
  }

  final cipher = HiveAesCipher(key);
  final documents = await getApplicationDocumentsDirectory();
  final settingsFile = File('${documents.path}/settings.hive');
  final migrationBackup = File('${settingsFile.path}.migration-backup');
  try {
    await Hive.openBox('settings', encryptionCipher: cipher);
    // A backup can remain only if the process was killed after committing the
    // encrypted box but before cleanup. A successful encrypted open proves it
    // is now obsolete.
    if (await migrationBackup.exists()) await migrationBackup.delete();
  } catch (encryptedError) {
    // The box exists from a previous version in plaintext (or is corrupted).
    // Re-open it without a cipher, copy everything into the encrypted box,
    // and delete the plaintext file.
    try {
      if (Hive.isBoxOpen('settings')) await Hive.box('settings').close();
      // Recover a migration interrupted after the original plaintext file was
      // moved aside. Preserve any partial replacement for diagnostics instead
      // of overwriting it.
      if (await migrationBackup.exists()) {
        if (await settingsFile.exists()) {
          final failed = File(
              '${settingsFile.path}.failed-${DateTime.now().millisecondsSinceEpoch}');
          await settingsFile.rename(failed.path);
        }
        await migrationBackup.copy(settingsFile.path);
      }
      final plain = await Hive.openBox('settings');
      final data = Map<dynamic, dynamic>.of(plain.toMap());
      final sourcePath = plain.path;
      if (sourcePath == null) {
        await plain.close();
        throw StateError('Cannot locate the legacy settings database.');
      }
      final source = File(sourcePath);
      final backup = File('$sourcePath.migration-backup');
      await source.copy(backup.path);
      await plain.deleteFromDisk();
      try {
        final enc = await Hive.openBox('settings', encryptionCipher: cipher);
        await enc.putAll(data);
        await enc.flush();
        await backup.delete();
        debugPrint('[Hive] settings box migrated to encrypted storage');
      } catch (_) {
        if (Hive.isBoxOpen('settings')) await Hive.box('settings').close();
        await Hive.deleteBoxFromDisk('settings');
        await backup.copy(sourcePath);
        rethrow;
      }
    } catch (_) {
      throw StateError(
          'Settings could not be decrypted or migrated: $encryptedError');
    }
  }
}

class _StorageUnavailableApp extends StatelessWidget {
  const _StorageUnavailableApp();

  @override
  Widget build(BuildContext context) => MaterialApp(
        debugShowCheckedModeBanner: false,
        home: Scaffold(
          body: SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.lock_outline, size: 52),
                  const SizedBox(height: 20),
                  const Text('Protected data unavailable',
                      style:
                          TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                  const Text(
                    'Roadstr did not open or erase your saved locations. '
                    'Restart the device and try again. If the problem persists, '
                    'export the app data before reinstalling.',
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          ),
        ),
      );
}

/// Root widget. Rebuilds whenever the theme or locale changes.
///
/// [AppLocalizations.localizationsDelegates] wires in the generated ARB
/// translation delegates. [AppLocalizations.supportedLocales] lists all
/// language codes that have ARB files under `lib/l10n/`.
class RoadstrApp extends StatelessWidget {
  const RoadstrApp({super.key});
  @override
  Widget build(BuildContext context) {
    final themeProvider = context.watch<ThemeProvider>();
    final localeProvider = context.watch<LocaleProvider>();
    final isDark = themeProvider.effective.isDark;
    return MaterialApp(
      title: 'Roadstr',
      debugShowCheckedModeBanner: false,
      theme: themeProvider.effectiveThemeData,
      builder: (ctx, child) => AnnotatedRegion<SystemUiOverlayStyle>(
        value: SystemUiOverlayStyle(
          statusBarColor: Colors.transparent,
          statusBarIconBrightness: isDark ? Brightness.light : Brightness.dark,
          systemNavigationBarColor: Colors.transparent,
          systemNavigationBarIconBrightness:
              isDark ? Brightness.light : Brightness.dark,
        ),
        child: child!,
      ),
      // null locale means "use the device locale"; set when the user overrides.
      locale: localeProvider.locale,
      // Replace the stock GlobalMaterialLocalizations.delegate with our
      // fallback-aware wrapper.  Some EU languages (Irish 'ga', Maltese 'mt')
      // are not yet in flutter_localizations; without the wrapper selecting them
      // crashes AppBar with "NoMaterialLocalizationsFound".
      localizationsDelegates: [
        AppLocalizations.delegate,
        const _FallbackMaterialDelegate(),
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      home: const _DisclaimerGate(),
    );
  }
}

// ── Material localizations fallback ──────────────────────────────────────────

/// A [LocalizationsDelegate] for [MaterialLocalizations] that accepts every
/// locale but falls back to English when [GlobalMaterialLocalizations] does
/// not natively support the requested locale.
///
/// This is needed for EU official languages that Roadstr supports (Irish `ga`,
/// Maltese `mt`) but that are not yet in the `flutter_localizations` package.
/// Without this wrapper, [AppBar] and other Material widgets throw
/// "NoMaterialLocalizationsFound" for those locales.
///
/// Users still get Roadstr's full custom translations ([AppLocalizations])
/// for those languages; only internal Material widget strings (e.g. "Back"
/// button tooltip, date-picker labels) fall back to English until Flutter
/// adds official support.
class _FallbackMaterialDelegate
    extends LocalizationsDelegate<MaterialLocalizations> {
  const _FallbackMaterialDelegate();

  @override
  bool isSupported(Locale locale) => true; // Accept every locale.

  @override
  Future<MaterialLocalizations> load(Locale locale) {
    // If GlobalMaterialLocalizations supports the locale, use it directly.
    // Otherwise fall back to English so Material widgets always have strings.
    final effective = GlobalMaterialLocalizations.delegate.isSupported(locale)
        ? locale
        : const Locale('en');
    return GlobalMaterialLocalizations.delegate.load(effective);
  }

  @override
  bool shouldReload(_FallbackMaterialDelegate old) => false;
}

// ── First-launch disclaimer gate ─────────────────────────────────────────────

/// Routes users through [OnboardingScreen] until they have accepted the current
/// privacy disclosure. The versioned flag deliberately supersedes legacy
/// disclaimer flags because the old text understated third-party location
/// sharing and cannot count as informed acceptance of the corrected wording.
///
/// Key logic:
///   • `privacy_disclosure_v2 == true` → show map
///   • Otherwise → show [OnboardingScreen]
///
/// The legacy flags are retained for downgrade compatibility but never bypass
/// the corrected disclosure.
class _DisclaimerGate extends StatefulWidget {
  const _DisclaimerGate();
  @override
  State<_DisclaimerGate> createState() => _DisclaimerGateState();
}

class _DisclaimerGateState extends State<_DisclaimerGate> {
  late bool _done;

  @override
  void initState() {
    super.initState();
    final box = Hive.box('settings');
    _done = box.get('privacy_disclosure_v2', defaultValue: false) as bool;
  }

  void _completeOnboarding() async {
    final box = Hive.box('settings');
    await box.putAll({
      'disclaimer_accepted': true,
      'onboarding_v1': true,
      'privacy_disclosure_v2': true,
    });
    if (mounted) setState(() => _done = true);
  }

  @override
  Widget build(BuildContext context) => _done
      ? const _VoiceLanguageNoticeGate()
      : OnboardingScreen(onComplete: _completeOnboarding);
}

// ── First-launch voice-language notice ───────────────────────────────────────

/// Shows a one-time dialog if the device's system language has no voice
/// guidance support, then always renders [MapScreen]. The flag is stored in
/// Hive so the dialog never appears again after being dismissed once.
class _VoiceLanguageNoticeGate extends StatefulWidget {
  const _VoiceLanguageNoticeGate();
  @override
  State<_VoiceLanguageNoticeGate> createState() =>
      _VoiceLanguageNoticeGateState();
}

class _VoiceLanguageNoticeGateState extends State<_VoiceLanguageNoticeGate> {
  @override
  void initState() {
    super.initState();
    final shown = Hive.box('settings')
        .get('voice_unsupported_notice_shown', defaultValue: false) as bool;
    if (shown) return;
    final systemLang =
        WidgetsBinding.instance.platformDispatcher.locale.languageCode;
    if (kokoroSupportedLanguages.contains(systemLang)) return;
    WidgetsBinding.instance.addPostFrameCallback((_) => _showNotice());
  }

  void _showNotice() {
    if (!mounted) return;
    Hive.box('settings').put('voice_unsupported_notice_shown', true);
    final l = AppLocalizations.of(context);
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(l.voiceUnsupportedTitle,
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
        content: Text(l.voiceUnsupportedBody,
            style: const TextStyle(fontSize: 13, height: 1.5)),
        actions: [
          FilledButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(l.ok),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) => const MapScreen();
}
