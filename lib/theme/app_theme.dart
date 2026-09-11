// Theme definitions for Roadstr.
//
// Roadstr uses two accent colours with cultural significance:
//   - Nostr purple (#8B5CF6): the canonical Nostr protocol colour,
//     signalling the app's decentralised social identity.
//   - Bitcoin orange (#F7931A): the official Bitcoin brand colour,
//     reinforcing the Lightning Network payment integration.
//
// Light and dark variants exist for both accents; the dark themes use the
// same OSM raster tiles as light mode, recoloured by a filter rather than
// fetched from a separate dark-tile service (see the RoadstrColors.mapTile
// fields below, and MapScreen._darkTileBuilder for the filter itself).
//
// The RoadstrColors ThemeExtension pattern is used instead of raw Theme
// because it provides strongly-typed semantic colours (e.g. surface1,
// textSecondary) that are safer to refactor than Theme.of(context).colorScheme
// dot-chains. Widgets obtain these colours via RoadstrColors.of(context).
import 'package:flutter/material.dart';
import '../l10n/app_localizations.dart';

/// Nostr protocol brand colour — used as the primary accent for the default theme.
const kNostrPurple = Color(0xFF8B5CF6);

/// Bitcoin / Lightning Network brand colour — used as the accent for the Bitcoin theme.
const kBitcoinOrange = Color(0xFFF7931A);

/// Available theme identifiers.
/// Appended, never reordered: [AppThemeIdExt.fromIndex] reads a stored
/// ordinal, so inserting in the middle would silently change the theme of
/// everyone who already picked one.
enum AppThemeId {
  lightNostr,
  lightBitcoin,
  darkNostr,
  darkBitcoin,
}

extension AppThemeIdExt on AppThemeId {
  String localizedLabel(AppLocalizations l) {
    switch (this) {
      case AppThemeId.lightNostr:
        return l.themeLightNostr;
      case AppThemeId.lightBitcoin:
        return l.themeLightBitcoin;
      case AppThemeId.darkNostr:
        return l.themeDarkNostr;
      case AppThemeId.darkBitcoin:
        return l.themeDarkBitcoin;
    }
  }

  Color get accent {
    switch (this) {
      case AppThemeId.lightNostr:
      case AppThemeId.darkNostr:
        return kNostrPurple;
      case AppThemeId.lightBitcoin:
      case AppThemeId.darkBitcoin:
        return kBitcoinOrange;
    }
  }

  bool get isDark =>
      this == AppThemeId.darkNostr || this == AppThemeId.darkBitcoin;

  int get index2 => AppThemeId.values.indexOf(this);
  static AppThemeId fromIndex(int i) => switch (i) {
        0 || 4 => AppThemeId.lightNostr,
        1 || 5 => AppThemeId.lightBitcoin,
        2 || 6 => AppThemeId.darkNostr,
        3 || 7 => AppThemeId.darkBitcoin,
        _ => AppThemeId.lightNostr,
      };
}

/// Factory for [ThemeData] instances, parameterised by [AppThemeId].
///
/// Both themes share the same structural layout; only the accent colour differs.
/// The [RoadstrColors] extension is attached to the theme so widgets can access
/// semantic colours through `RoadstrColors.of(context)` without knowing which
/// theme is active.
class AppTheme {
  static ThemeData build(AppThemeId id) {
    final accent = id.accent;
    final dark = id.isDark;
    if (dark) {
      return _premiumTheme(
          ThemeData(
            useMaterial3: true,
            brightness: Brightness.dark,
            colorScheme: ColorScheme.dark(
              primary: accent,
              secondary: accent,
              surface: const Color(0xFF1A1A2E),
              onSurface: const Color(0xFFEEEEF8),
            ),
            scaffoldBackgroundColor: const Color(0xFF0D0D1A),
            cardColor: const Color(0xFF1A1A2E),
            dividerColor: const Color(0xFF2A2A40),
            appBarTheme: const AppBarTheme(
              backgroundColor: Color(0xFF1A1A2E),
              foregroundColor: Color(0xFFEEEEF8),
              elevation: 0,
            ),
            switchTheme: SwitchThemeData(
              thumbColor: WidgetStateProperty.resolveWith((s) =>
                  s.contains(WidgetState.selected)
                      ? accent
                      : const Color(0xFF555570)),
              trackColor: WidgetStateProperty.resolveWith((s) =>
                  s.contains(WidgetState.selected)
                      ? accent.withValues(alpha: 0.4)
                      : const Color(0xFF2A2A40)),
            ),
            extensions: [
              RoadstrColors(
                accent: accent,
                accentSoft: accent.withValues(alpha: 0.18),
                surface1: const Color(0xFF0D0D1A),
                surface2: const Color(0xFF1A1A2E),
                surface3: const Color(0xFF22223A),
                border: const Color(0xFF2A2A40),
                textPrimary: const Color(0xFFEEEEF8),
                textSecondary: const Color(0xFF8888A8),
                isDark: true,
                // The same standard OSM raster tiles light mode uses, not a
                // separate dark-tile service. CARTO's anonymous dark_all
                // endpoint — used here until this release — silently started
                // serving an "API KEY REQUIRED" watermark instead of tiles once
                // some unpublished usage threshold was crossed: a third party
                // could revoke free access at any time with zero warning, which
                // is exactly the kind of dependency this app avoids elsewhere.
                // The dark look now comes from inverting these same tiles (see
                // MapScreen._darkTileBuilder) instead of trusting another
                // service to keep rendering them for free.
                mapTile: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                mapTileSubs: null,
                mapTileAttrib: '© OpenStreetMap contributors',
              )
            ],
          ),
          accent: accent,
          dark: true);
    }
    return _premiumTheme(
        ThemeData(
          useMaterial3: true,
          brightness: Brightness.light,
          colorScheme: ColorScheme.light(
            primary: accent,
            secondary: accent,
            surface: Colors.white,
            onSurface: const Color(0xFF1A1A2E),
          ),
          scaffoldBackgroundColor: const Color(0xFFF5F5F5),
          cardColor: Colors.white,
          dividerColor: const Color(0xFFE0E0E0),
          appBarTheme: const AppBarTheme(
            backgroundColor: Colors.white,
            foregroundColor: Color(0xFF1A1A2E),
            elevation: 0,
          ),
          switchTheme: SwitchThemeData(
            thumbColor: WidgetStateProperty.resolveWith((s) =>
                s.contains(WidgetState.selected)
                    ? accent
                    : const Color(0xFF9E9E9E)),
            trackColor: WidgetStateProperty.resolveWith((s) =>
                s.contains(WidgetState.selected)
                    ? accent.withValues(alpha: 0.4)
                    : const Color(0xFFE0E0E0)),
          ),
          extensions: [
            RoadstrColors(
              accent: accent,
              accentSoft: accent.withValues(alpha: 0.12),
              surface1: const Color(0xFFF5F5F5),
              surface2: Colors.white,
              surface3: const Color(0xFFF0F0F0),
              border: const Color(0xFFE0E0E0),
              textPrimary: const Color(0xFF1A1A2E),
              textSecondary: const Color(0xFF757575),
              isDark: false,
              mapTile: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
              mapTileSubs: null,
              mapTileAttrib: '© OpenStreetMap contributors',
            )
          ],
        ),
        accent: accent,
        dark: false);
  }

  /// Applies Roadstr's component language to every palette variant. Keeping
  /// this separate from the colour definitions means the violet and Bitcoin
  /// families retain their identity while controls, typography and motion
  /// always feel like the same product.
  static ThemeData _premiumTheme(ThemeData base,
      {required Color accent, required bool dark}) {
    final surface = base.cardColor;
    final surfaceHigh = Color.lerp(surface,
        dark ? Colors.white : const Color(0xFF171225), dark ? 0.055 : 0.035)!;
    final outline = accent.withValues(alpha: dark ? 0.24 : 0.18);
    final textPrimary =
        dark ? const Color(0xFFF4F1FF) : const Color(0xFF171225);
    final textSecondary =
        dark ? const Color(0xFFA39DB8) : const Color(0xFF706A80);

    final textTheme = base.textTheme.copyWith(
      headlineSmall: base.textTheme.headlineSmall?.copyWith(
          color: textPrimary, fontWeight: FontWeight.w800, letterSpacing: -0.6),
      titleLarge: base.textTheme.titleLarge?.copyWith(
          color: textPrimary, fontWeight: FontWeight.w800, letterSpacing: -0.4),
      titleMedium: base.textTheme.titleMedium?.copyWith(
          color: textPrimary, fontWeight: FontWeight.w700, letterSpacing: -0.2),
      // Keep inherited metrics unchanged: navigation HUD readouts merge their
      // explicit styles with these defaults and must not move by a pixel.
      bodyLarge: base.textTheme.bodyLarge?.copyWith(color: textPrimary),
      bodyMedium: base.textTheme.bodyMedium?.copyWith(color: textSecondary),
      labelLarge: base.textTheme.labelLarge
          ?.copyWith(fontWeight: FontWeight.w700, letterSpacing: 0.1),
    );

    return base.copyWith(
      colorScheme: base.colorScheme.copyWith(
        primary: accent,
        secondary: accent,
        primaryContainer: accent.withValues(alpha: dark ? 0.20 : 0.12),
        onPrimaryContainer: accent,
        surfaceContainer: surface,
        surfaceContainerHigh: surfaceHigh,
        outline: outline,
        outlineVariant: outline.withValues(alpha: 0.65),
      ),
      textTheme: textTheme,
      appBarTheme: AppBarTheme(
        backgroundColor: base.scaffoldBackgroundColor,
        foregroundColor: textPrimary,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        toolbarHeight: 66,
        titleSpacing: 20,
        titleTextStyle: textTheme.titleLarge?.copyWith(fontSize: 21),
        iconTheme: IconThemeData(color: textPrimary, size: 22),
      ),
      cardTheme: CardThemeData(
        color: surface,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: BorderSide(color: outline),
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: surface,
        surfaceTintColor: Colors.transparent,
        elevation: 20,
        shadowColor: Colors.black.withValues(alpha: dark ? 0.50 : 0.18),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(26)),
        titleTextStyle: textTheme.titleLarge?.copyWith(fontSize: 20),
        contentTextStyle: textTheme.bodyMedium,
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: surface,
        modalBackgroundColor: surface,
        surfaceTintColor: Colors.transparent,
        elevation: 24,
        modalElevation: 24,
        shadowColor: Colors.black.withValues(alpha: dark ? 0.55 : 0.18),
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
        ),
        showDragHandle: false,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: surfaceHigh,
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 15),
        hintStyle: TextStyle(color: textSecondary.withValues(alpha: 0.82)),
        labelStyle: TextStyle(color: textSecondary),
        prefixIconColor: textSecondary,
        suffixIconColor: textSecondary,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: outline),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: outline),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: accent, width: 1.5),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: Color(0xFFEF4444)),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: ButtonStyle(
          minimumSize: const WidgetStatePropertyAll(Size(48, 50)),
          padding: const WidgetStatePropertyAll(
              EdgeInsets.symmetric(horizontal: 20, vertical: 13)),
          backgroundColor: WidgetStateProperty.resolveWith((states) =>
              states.contains(WidgetState.disabled)
                  ? accent.withValues(alpha: 0.30)
                  : accent),
          foregroundColor: const WidgetStatePropertyAll(Colors.white),
          overlayColor:
              WidgetStatePropertyAll(Colors.white.withValues(alpha: 0.12)),
          elevation: const WidgetStatePropertyAll(0),
          shape: WidgetStatePropertyAll(
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))),
          textStyle: WidgetStatePropertyAll(
              textTheme.labelLarge?.copyWith(fontSize: 14)),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: ButtonStyle(
          minimumSize: const WidgetStatePropertyAll(Size(48, 50)),
          padding: const WidgetStatePropertyAll(
              EdgeInsets.symmetric(horizontal: 18, vertical: 13)),
          foregroundColor: WidgetStatePropertyAll(accent),
          side: WidgetStatePropertyAll(BorderSide(color: outline)),
          shape: WidgetStatePropertyAll(
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))),
          textStyle: WidgetStatePropertyAll(textTheme.labelLarge),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: ButtonStyle(
          foregroundColor: WidgetStatePropertyAll(accent),
          shape: WidgetStatePropertyAll(
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(13))),
          textStyle: WidgetStatePropertyAll(textTheme.labelLarge),
        ),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: ButtonStyle(
          foregroundColor: WidgetStatePropertyAll(textSecondary),
          overlayColor: WidgetStatePropertyAll(accent.withValues(alpha: 0.10)),
          shape: const WidgetStatePropertyAll(CircleBorder()),
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: surfaceHigh,
        selectedColor: accent.withValues(alpha: dark ? 0.22 : 0.14),
        disabledColor: surfaceHigh.withValues(alpha: 0.5),
        side: BorderSide(color: outline),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        labelStyle: TextStyle(color: textPrimary, fontWeight: FontWeight.w600),
        secondaryLabelStyle:
            TextStyle(color: accent, fontWeight: FontWeight.w700),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      ),
      listTileTheme: ListTileThemeData(
        iconColor: accent,
        textColor: textPrimary,
        subtitleTextStyle: TextStyle(color: textSecondary, fontSize: 12.5),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 3),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      dividerTheme: DividerThemeData(
        color: outline.withValues(alpha: 0.65),
        thickness: 0.7,
        space: 1,
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor:
            dark ? const Color(0xFF242033) : const Color(0xFF201A2B),
        contentTextStyle: const TextStyle(color: Colors.white),
        behavior: SnackBarBehavior.floating,
        elevation: 10,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: accent,
        linearTrackColor: accent.withValues(alpha: 0.12),
        circularTrackColor: accent.withValues(alpha: 0.12),
      ),
    );
  }
}

/// A [ThemeExtension] that exposes Roadstr-specific semantic colours and map
/// tile configuration in a type-safe, theme-aware way.
///
/// Usage in widgets:
/// ```dart
/// final c = RoadstrColors.of(context);
/// Container(color: c.surface2, ...)
/// ```
///
/// The `mapTile`, `mapTileSubs`, and `mapTileAttrib` fields are included here
/// (rather than in a separate config) so that dark themes can switch to a
/// different tile set (e.g. a dark OSM style) purely by changing the theme.
class RoadstrColors extends ThemeExtension<RoadstrColors> {
  final Color accent, accentSoft, surface1, surface2, surface3;
  final Color border, textPrimary, textSecondary;
  final bool isDark;

  /// Optional panel treatment retained for backwards-compatible custom theme
  /// extensions. The four built-in themes use the shared surface system.
  final Gradient? panelGradient;

  /// OpenStreetMap tile URL template, e.g. `https://tile.openstreetmap.org/{z}/{x}/{y}.png`.
  final String mapTile;

  /// Optional subdomain list for tile CDN load balancing (e.g. `['a','b','c']`).
  final String? mapTileSubs;
  final String mapTileAttrib;

  const RoadstrColors({
    required this.accent,
    required this.accentSoft,
    required this.surface1,
    required this.surface2,
    required this.surface3,
    required this.border,
    required this.textPrimary,
    required this.textSecondary,
    required this.isDark,
    required this.mapTile,
    this.mapTileSubs,
    required this.mapTileAttrib,
    this.panelGradient,
  });

  @override
  RoadstrColors copyWith({
    Color? accent,
    Color? accentSoft,
    Color? surface1,
    Color? surface2,
    Color? surface3,
    Color? border,
    Color? textPrimary,
    Color? textSecondary,
    bool? isDark,
    String? mapTile,
    String? mapTileSubs,
    String? mapTileAttrib,
    Gradient? panelGradient,
  }) =>
      RoadstrColors(
        accent: accent ?? this.accent,
        accentSoft: accentSoft ?? this.accentSoft,
        surface1: surface1 ?? this.surface1,
        surface2: surface2 ?? this.surface2,
        surface3: surface3 ?? this.surface3,
        border: border ?? this.border,
        textPrimary: textPrimary ?? this.textPrimary,
        textSecondary: textSecondary ?? this.textSecondary,
        isDark: isDark ?? this.isDark,
        mapTile: mapTile ?? this.mapTile,
        mapTileSubs: mapTileSubs ?? this.mapTileSubs,
        mapTileAttrib: mapTileAttrib ?? this.mapTileAttrib,
        panelGradient: panelGradient ?? this.panelGradient,
      );

  /// Blends towards [other] over a theme change.
  ///
  /// MaterialApp cross-fades between themes over ~200 ms. Returning `this`
  /// (as this used to) froze every Roadstr-coloured surface on the OLD theme
  /// for the whole animation while Material's own colours faded, then snapped
  /// the rest at the end — a visible two-stage flash at every sunset/sunrise
  /// switch.
  ///
  /// The discrete fields cannot be blended: half a tile URL is not a URL. They
  /// switch at the midpoint, which is the same convention `ThemeData.lerp`
  /// uses for `brightness`, so the tile source changes when the colours are
  /// already halfway across rather than after everything else has settled.
  @override
  RoadstrColors lerp(RoadstrColors? other, double t) {
    if (other == null || identical(other, this)) return this;
    final target = t < 0.5 ? this : other;
    return RoadstrColors(
      accent: Color.lerp(accent, other.accent, t)!,
      accentSoft: Color.lerp(accentSoft, other.accentSoft, t)!,
      surface1: Color.lerp(surface1, other.surface1, t)!,
      surface2: Color.lerp(surface2, other.surface2, t)!,
      surface3: Color.lerp(surface3, other.surface3, t)!,
      border: Color.lerp(border, other.border, t)!,
      textPrimary: Color.lerp(textPrimary, other.textPrimary, t)!,
      textSecondary: Color.lerp(textSecondary, other.textSecondary, t)!,
      isDark: target.isDark,
      mapTile: target.mapTile,
      mapTileSubs: target.mapTileSubs,
      mapTileAttrib: target.mapTileAttrib,
      // Gradient.lerp copes with a null on either side, so a switch between a
      // flat and a modern theme fades rather than popping.
      panelGradient: Gradient.lerp(panelGradient, other.panelGradient, t),
    );
  }

  /// Solid accent fill with a soft highlight in the top-left corner.
  ///
  /// Used by the manoeuvre tile and the map controls so they read as one
  /// family of raised, lit objects rather than as flat swatches. The highlight
  /// is a light source, not a colour ramp: it stays in the corner and fades
  /// out well before the middle, which is why the stops are bunched early.
  ///
  /// Anything drawn on top of this must be light — see [onAccent].
  Gradient get accentGloss => LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [
          Color.lerp(accent, Colors.white, 0.42)!,
          Color.lerp(accent, Colors.white, 0.12)!,
          accent,
        ],
        stops: const [0.0, 0.28, 0.75],
      );

  /// Hairline that catches the light along a panel's edge.
  ///
  /// Accent-tinted rather than grey: a neutral outline reads as a box drawn
  /// around the panel, while a tint of the panel's own colour reads as the
  /// edge of a lit surface. Kept under half opacity — at full strength it
  /// becomes a border, which is the plasticky look this is avoiding.
  Color get panelEdge => accent.withValues(alpha: isDark ? 0.28 : 0.20);

  // Map chrome remains dark and translucent in every palette so it reads
  // consistently over both pale and dark tiles. Theme identity comes from
  // [accent], not from flooding every surface with purple or orange.
  Color get mapGlassLight => const Color(0xD91A1822);
  Color get mapGlassMedium => const Color(0xE31A1824);
  Color get mapGlassStrong => const Color(0xF0181620);
  Color get mapGlassBorder => Colors.white.withValues(alpha: 0.13);
  Color get mapTextPrimary => const Color(0xFFF8F7FC);
  Color get mapTextSecondary => const Color(0xFFAAA6B8);
  Color get mapTextMuted => const Color(0xFF777384);
  Color get accentGlow => Color.lerp(accent, Colors.white, 0.10)!;
  Color get routePrimary => accent;
  Color get routeGlow => accent.withValues(alpha: 0.22);
  Color get mapOverlayDark => const Color(0xE6111018);

  List<BoxShadow> mapGlassShadow({bool strong = false}) {
    return [
      BoxShadow(
        color: Colors.black.withValues(alpha: strong ? 0.36 : 0.27),
        blurRadius: strong ? 30 : 22,
        spreadRadius: -7,
        offset: const Offset(0, 10),
      ),
      BoxShadow(
        color: accent.withValues(alpha: strong ? 0.11 : 0.065),
        blurRadius: strong ? 26 : 18,
        spreadRadius: -10,
      ),
    ];
  }

  /// Soft, wide, low-opacity drop shadow.
  ///
  /// Large blur with little opacity lifts a panel off the map without the hard
  /// grey band a tight shadow produces — the panel appears to float rather
  /// than to be stuck on.
  List<BoxShadow> get panelShadow => [
        BoxShadow(
          color: Colors.black.withValues(alpha: isDark ? 0.38 : 0.13),
          blurRadius: 24,
          spreadRadius: -4,
          offset: const Offset(0, 6),
        ),
      ];

  /// Low-contrast ambient colour used behind ordinary (non-map) screens.
  /// It gives the accent a presence without tinting every card or reducing
  /// text contrast.
  Gradient get screenGradient => LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [
          Color.lerp(surface1, accent, isDark ? 0.13 : 0.07)!,
          surface1,
          Color.lerp(surface1, accent, isDark ? 0.06 : 0.025)!,
        ],
        stops: const [0, 0.46, 1],
      );

  /// A subtler sheen than [accentGloss] for cards that must stay neutral.
  Gradient get surfaceSheen => LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [
          Color.lerp(surface2, accent, isDark ? 0.10 : 0.045)!,
          surface2,
          Color.lerp(surface2, accent, isDark ? 0.04 : 0.02)!,
        ],
      );

  List<BoxShadow> get cardShadow => [
        BoxShadow(
          color: Colors.black.withValues(alpha: isDark ? 0.24 : 0.075),
          blurRadius: 18,
          spreadRadius: -5,
          offset: const Offset(0, 7),
        ),
      ];

  BoxDecoration premiumCard({double radius = 20, Color? edgeColor}) =>
      BoxDecoration(
        gradient: surfaceSheen,
        borderRadius: BorderRadius.circular(radius),
        border: Border.all(color: edgeColor ?? panelEdge, width: 0.9),
        boxShadow: [
          ...cardShadow,
          BoxShadow(
            color: accent.withValues(alpha: isDark ? 0.055 : 0.025),
            blurRadius: 24,
            spreadRadius: -12,
          ),
        ],
      );

  /// Corner radius shared by the raised panels.
  static const double panelRadius = 22.0;

  /// Foreground colour for content sitting on [accentGloss].
  Color get onAccent => Colors.white;

  static RoadstrColors of(BuildContext context) =>
      Theme.of(context).extension<RoadstrColors>()!;
}

/// Ambient backdrop shared by list/detail screens. It is intentionally a
/// paint-only wrapper: scroll state, semantics and screen logic stay owned by
/// the child exactly as before.
class RoadstrScreenBackground extends StatelessWidget {
  final Widget child;
  const RoadstrScreenBackground({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    final colors = RoadstrColors.of(context);
    return Stack(
      fit: StackFit.expand,
      children: [
        RepaintBoundary(
          child: DecoratedBox(
            decoration: BoxDecoration(gradient: colors.screenGradient),
            child: CustomPaint(painter: _RoadstrAmbientPainter(colors)),
          ),
        ),
        child,
      ],
    );
  }
}

class _RoadstrAmbientPainter extends CustomPainter {
  final RoadstrColors colors;
  const _RoadstrAmbientPainter(this.colors);

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final radius = size.shortestSide * 0.52;
    final topCenter = Offset(size.width * 0.92, size.height * 0.03);
    final bottomCenter = Offset(size.width * 0.08, size.height * 0.92);
    final opacity = colors.isDark ? 0.12 : 0.065;
    final paint = Paint()
      ..shader = RadialGradient(colors: [
        colors.accent.withValues(alpha: opacity),
        colors.accent.withValues(alpha: 0),
      ]).createShader(Rect.fromCircle(center: topCenter, radius: radius));
    canvas.drawCircle(topCenter, radius, paint);
    paint.shader = RadialGradient(colors: [
      colors.accent.withValues(alpha: opacity * 0.55),
      colors.accent.withValues(alpha: 0),
    ]).createShader(
        Rect.fromCircle(center: bottomCenter, radius: radius * 0.8));
    canvas.drawCircle(bottomCenter, radius * 0.8, paint);
  }

  @override
  bool shouldRepaint(_RoadstrAmbientPainter oldDelegate) =>
      oldDelegate.colors.accent != colors.accent ||
      oldDelegate.colors.isDark != colors.isDark;
}
