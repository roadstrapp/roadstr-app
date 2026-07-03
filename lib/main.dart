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
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:provider/provider.dart';
import 'l10n/app_localizations.dart';
import 'providers/locale_provider.dart';
import 'theme/theme_provider.dart';
import 'theme/app_theme.dart';
import 'screens/map_screen.dart';
import 'services/kokoro/kokoro_voices.dart';

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
          systemNavigationBarIconBrightness: isDark ? Brightness.light : Brightness.dark,
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

/// Shows [_DisclaimerScreen] on the very first app launch; once the user
/// accepts, the flag is stored in Hive so the screen never appears again.
class _DisclaimerGate extends StatefulWidget {
  const _DisclaimerGate();
  @override
  State<_DisclaimerGate> createState() => _DisclaimerGateState();
}

class _DisclaimerGateState extends State<_DisclaimerGate> {
  late bool _accepted;

  @override
  void initState() {
    super.initState();
    _accepted = Hive.box('settings')
        .get('disclaimer_accepted', defaultValue: false) as bool;
  }

  void _accept() {
    Hive.box('settings').put('disclaimer_accepted', true);
    setState(() => _accepted = true);
  }

  @override
  Widget build(BuildContext context) =>
      _accepted ? const _VoiceLanguageNoticeGate() : _DisclaimerScreen(onAccept: _accept);
}

// ── First-launch voice-language notice ───────────────────────────────────────

/// Shows a one-time dialog if the device's system language has no voice
/// guidance support, then always renders [MapScreen]. The flag is stored in
/// Hive so the dialog never appears again after being dismissed once.
class _VoiceLanguageNoticeGate extends StatefulWidget {
  const _VoiceLanguageNoticeGate();
  @override
  State<_VoiceLanguageNoticeGate> createState() => _VoiceLanguageNoticeGateState();
}

class _VoiceLanguageNoticeGateState extends State<_VoiceLanguageNoticeGate> {
  @override
  void initState() {
    super.initState();
    final shown = Hive.box('settings')
        .get('voice_unsupported_notice_shown', defaultValue: false) as bool;
    if (shown) return;
    final systemLang = WidgetsBinding.instance.platformDispatcher.locale.languageCode;
    if (kokoroSupportedLanguages.contains(systemLang)) return;
    WidgetsBinding.instance.addPostFrameCallback((_) => _showNotice());
  }

  void _showNotice() {
    if (!mounted) return;
    Hive.box('settings').put('voice_unsupported_notice_shown', true);
    final l = AppLocalizations.of(context)!;
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

class _DisclaimerScreen extends StatelessWidget {
  final VoidCallback onAccept;
  const _DisclaimerScreen({required this.onAccept});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(24, 32, 24, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Logo / app name
              Row(children: [
                const Icon(Icons.navigation_rounded,
                    color: Color(0xFF7C3AED), size: 32),
                const SizedBox(width: 12),
                Text('Roadstr',
                    style: theme.textTheme.headlineMedium?.copyWith(
                        fontWeight: FontWeight.w800,
                        color: const Color(0xFF7C3AED))),
              ]),
              const SizedBox(height: 32),
              Text('Avviso importante',
                  style: theme.textTheme.titleLarge
                      ?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 16),
              Expanded(
                child: SingleChildScrollView(
                  child: Text(
                    _disclaimerText,
                    style: theme.textTheme.bodyMedium
                        ?.copyWith(height: 1.6, fontSize: 14),
                  ),
                ),
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  style: FilledButton.styleFrom(
                      backgroundColor: const Color(0xFF7C3AED),
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14))),
                  onPressed: onAccept,
                  child: const Text('Ho letto e accetto',
                      style: TextStyle(
                          fontSize: 16, fontWeight: FontWeight.w700)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  static const _disclaimerText = '''
Roadstr è un'applicazione di navigazione sperimentale basata su dati OpenStreetMap e sul protocollo Nostr. Utilizzando questa app l'utente accetta integralmente le seguenti condizioni:

🚗  SICUREZZA STRADALE
Il guidatore è tenuto a mantenere sempre gli occhi sulla strada. Non guardare il telefono durante la guida. Assicurare il dispositivo in un supporto approvato e visibile senza distogliere l'attenzione dalla strada.

⚠️  LIMITAZIONE DI RESPONSABILITÀ
Roadstr viene fornita "così com'è", senza garanzie di accuratezza, completezza o idoneità a scopi specifici. Gli sviluppatori declinano qualsiasi responsabilità per danni derivanti dall'uso dell'applicazione, inclusi ma non limitati a: incidenti stradali, sanzioni amministrative, danni a cose o persone.

🚫  ZONE A TRAFFICO LIMITATO (ZTL)
La navigazione si basa sui dati OpenStreetMap che potrebbero non essere aggiornati riguardo a ZTL, corsie preferenziali e restrizioni locali. L'utente è responsabile di verificare autonomamente l'accessibilità del percorso suggerito nelle zone a traffico limitato prima di percorrerlo. Gli sviluppatori non rispondono di eventuali sanzioni ricevute.

📍  ACCURATEZZA
Il tracciamento GPS può risultare impreciso. Le indicazioni stradali sono a titolo orientativo. Rispettare sempre la segnaletica stradale verticale e orizzontale, che ha sempre la precedenza sulle indicazioni dell'app.

🔒  PRIVACY
Nessun dato di posizione viene trasmesso a server esterni. Il calcolo del percorso avviene tramite servizi terzi (OSRM, GraphHopper, OpenRouteService) ai quali vengono inviate esclusivamente le coordinate di partenza e arrivo.

Utilizzando Roadstr l'utente si assume la piena e totale responsabilità dell'utilizzo dell'applicazione e di qualsiasi conseguenza derivante dall'uso della stessa.
  ''';
}
