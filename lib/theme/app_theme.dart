// Theme definitions for Roadstr.
//
// Roadstr uses two accent colours with cultural significance:
//   - Nostr purple (#8B5CF6): the canonical Nostr protocol colour,
//     signalling the app's decentralised social identity.
//   - Bitcoin orange (#F7931A): the official Bitcoin brand colour,
//     reinforcing the Lightning Network payment integration.
//
// Light and dark variants exist for both accents; the dark themes swap the
// map tiles to CARTO dark_all (see the RoadstrColors.mapTile fields below).
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
  modernNostr,
  modernBitcoin,
  modernDarkNostr,
  modernDarkBitcoin,
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
      case AppThemeId.modernNostr:
        return l.themeModernNostr;
      case AppThemeId.modernBitcoin:
        return l.themeModernBitcoin;
      case AppThemeId.modernDarkNostr:
        return l.themeModernDarkNostr;
      case AppThemeId.modernDarkBitcoin:
        return l.themeModernDarkBitcoin;
    }
  }

  Color get accent {
    switch (this) {
      case AppThemeId.lightNostr:
      case AppThemeId.darkNostr:
      case AppThemeId.modernNostr:
      case AppThemeId.modernDarkNostr:
        return kNostrPurple;
      case AppThemeId.lightBitcoin:
      case AppThemeId.darkBitcoin:
      case AppThemeId.modernBitcoin:
      case AppThemeId.modernDarkBitcoin:
        return kBitcoinOrange;
    }
  }

  bool get isDark =>
      this == AppThemeId.darkNostr ||
      this == AppThemeId.darkBitcoin ||
      this == AppThemeId.modernDarkNostr ||
      this == AppThemeId.modernDarkBitcoin;

  /// Whether this theme paints its panels with a gradient rather than a flat
  /// fill. Kept separate from [isDark] so a future dark modern variant is a
  /// one-line change rather than a second boolean threaded everywhere.
  bool get isModern =>
      this == AppThemeId.modernNostr ||
      this == AppThemeId.modernBitcoin ||
      this == AppThemeId.modernDarkNostr ||
      this == AppThemeId.modernDarkBitcoin;
  int get index2 => AppThemeId.values.indexOf(this);
  static AppThemeId fromIndex(int i) =>
      AppThemeId.values[i.clamp(0, AppThemeId.values.length - 1)];
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
    if (id.isModern) return _buildModern(accent, dark: dark);
    if (dark) {
      return ThemeData(
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
            mapTile:
                'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png',
            mapTileSubs: 'abcd',
            mapTileAttrib: '© OpenStreetMap contributors © CARTO',
          )
        ],
      );
    }
    return ThemeData(
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
    );
  }

  /// The "modern" variants: a light base washed with a diagonal gradient that
  /// runs from white at the top-left to the accent colour at the bottom-right.
  ///
  /// Panels stay light so text contrast is unaffected — the gradient is a wash
  /// behind them, not a recolour of them. That is why the flat surface colours
  /// below are still near-white: any widget that does not paint
  /// [RoadstrColors.panelGradient] keeps working and simply looks like the
  /// light theme, rather than ending up with dark text on a saturated field.
  static ThemeData _buildModern(Color accent, {required bool dark}) {
    // Tinted at both edges, plain through the middle. A diagonal wash put the
    // heaviest colour under one corner of the text and none under the other,
    // which read as weight rather than as style; a symmetric pair of edges
    // frames the panel instead of tilting it, and keeps the centre — where the
    // instruction actually sits — clear for contrast.
    //
    // Kept deliberately faint: this is a frame, not a fill.
    final base = dark ? const Color(0xFF10101C) : Colors.white;
    // The dark variant needs a touch more tint to register at all against a
    // near-black panel, where the same 0.22 simply disappears.
    final edge = Color.lerp(base, accent, dark ? 0.34 : 0.22)!;
    final gradient = LinearGradient(
      begin: Alignment.centerLeft,
      end: Alignment.centerRight,
      colors: [edge, base, base, edge],
      stops: const [0.0, 0.32, 0.68, 1.0],
    );

    if (dark) {
      return ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorScheme: ColorScheme.dark(
          primary: accent,
          secondary: accent,
          surface: const Color(0xFF15151F),
          onSurface: const Color(0xFFEDEDF7),
        ),
        scaffoldBackgroundColor: const Color(0xFF0A0A14),
        cardColor: const Color(0xFF15151F),
        dividerColor: const Color(0xFF262637),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFF15151F),
          foregroundColor: Color(0xFFEDEDF7),
          elevation: 0,
        ),
        switchTheme: SwitchThemeData(
          thumbColor: WidgetStateProperty.resolveWith((s) =>
              s.contains(WidgetState.selected)
                  ? accent
                  : const Color(0xFF55556E)),
          trackColor: WidgetStateProperty.resolveWith((s) =>
              s.contains(WidgetState.selected)
                  ? accent.withValues(alpha: 0.40)
                  : const Color(0xFF262637)),
        ),
        extensions: [
          RoadstrColors(
            accent: accent,
            accentSoft: accent.withValues(alpha: 0.20),
            surface1: const Color(0xFF0A0A14),
            surface2: const Color(0xFF15151F),
            surface3: const Color(0xFF1E1E2C),
            border: const Color(0xFF262637),
            textPrimary: const Color(0xFFEDEDF7),
            textSecondary: const Color(0xFF8C8CA8),
            isDark: true,
            mapTile:
                'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png',
            mapTileSubs: 'abcd',
            mapTileAttrib: '© OpenStreetMap contributors © CARTO',
            panelGradient: gradient,
          )
        ],
      );
    }

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      colorScheme: ColorScheme.light(
        primary: accent,
        secondary: accent,
        surface: Colors.white,
        onSurface: const Color(0xFF15151F),
      ),
      scaffoldBackgroundColor: const Color(0xFFF7F5FC),
      cardColor: Colors.white,
      dividerColor: const Color(0xFFE3DEF0),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.white,
        foregroundColor: Color(0xFF15151F),
        elevation: 0,
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith((s) =>
            s.contains(WidgetState.selected) ? accent : const Color(0xFFBDBDD0)),
        trackColor: WidgetStateProperty.resolveWith((s) =>
            s.contains(WidgetState.selected)
                ? accent.withValues(alpha: 0.35)
                : const Color(0xFFE3DEF0)),
      ),
      extensions: [
        RoadstrColors(
          accent: accent,
          accentSoft: accent.withValues(alpha: 0.14),
          surface1: Colors.white,
          surface2: const Color(0xFFFBFAFF),
          surface3: const Color(0xFFF2EEFB),
          border: const Color(0xFFE3DEF0),
          textPrimary: const Color(0xFF15151F),
          textSecondary: const Color(0xFF6B6B85),
          isDark: false,
          mapTile: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
          mapTileAttrib: '© OpenStreetMap contributors',
          panelGradient: gradient,
        )
      ],
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

  /// Diagonal wash painted behind panels in the "modern" themes, and null in
  /// the flat ones. Nullable rather than a flag so every existing theme keeps
  /// its exact appearance, and so a widget opts in by painting it when present
  /// instead of branching on which theme is active.
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

  /// Corner radius shared by the raised panels.
  static const double panelRadius = 22.0;

  /// Foreground colour for content sitting on [accentGloss].
  Color get onAccent => Colors.white;

  static RoadstrColors of(BuildContext context) =>
      Theme.of(context).extension<RoadstrColors>()!;
}
