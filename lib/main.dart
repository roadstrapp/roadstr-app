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
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:provider/provider.dart';
import 'l10n/app_localizations.dart';
import 'providers/locale_provider.dart';
import 'theme/theme_provider.dart';
import 'screens/map_screen.dart';

Future<void> main() async {
  // Step 1: Required before any async platform-channel calls.
  WidgetsFlutterBinding.ensureInitialized();

  // Step 2: Hive must be ready before providers try to read persisted settings.
  await Hive.initFlutter();
  await Hive.openBox('settings');

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

/// Root widget. Rebuilds whenever the theme or locale changes.
///
/// [AppLocalizations.localizationsDelegates] wires in the generated ARB
/// translation delegates. [AppLocalizations.supportedLocales] lists all
/// language codes that have ARB files under `lib/l10n/`.
class RoadstrApp extends StatelessWidget {
  const RoadstrApp({super.key});
  @override
  Widget build(BuildContext context) {
    final themeProvider  = context.watch<ThemeProvider>();
    final localeProvider = context.watch<LocaleProvider>();
    return MaterialApp(
      title: 'Roadstr',
      debugShowCheckedModeBanner: false,
      theme: themeProvider.themeData,
      // null locale means "use the device locale"; set when the user overrides.
      locale: localeProvider.locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: const MapScreen(),
    );
  }
}
