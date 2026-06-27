// Theme definitions for Roadstr.
//
// Roadstr uses two accent colours with cultural significance:
//   - Nostr purple (#8B5CF6): the canonical Nostr protocol colour,
//     signalling the app's decentralised social identity.
//   - Bitcoin orange (#F7931A): the official Bitcoin brand colour,
//     reinforcing the Lightning Network payment integration.
//
// Currently only light themes are implemented. Dark mode is intentionally
// deferred — the map tile layer (OpenStreetMap) does not yet have a first-class
// dark variant that matches the app's design language.
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

/// Available theme identifiers. Only light themes exist for now.
enum AppThemeId { lightNostr, lightBitcoin }

extension AppThemeIdExt on AppThemeId {
  String localizedLabel(AppLocalizations l) {
    switch (this) {
      case AppThemeId.lightNostr:   return l.themeLightNostr;
      case AppThemeId.lightBitcoin: return l.themeLightBitcoin;
    }
  }
  Color get accent {
    switch (this) {
      case AppThemeId.lightNostr:   return kNostrPurple;
      case AppThemeId.lightBitcoin: return kBitcoinOrange;
    }
  }
  bool get isDark => false;
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
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      colorScheme: ColorScheme.light(
        primary: accent, secondary: accent,
        surface: Colors.white, onSurface: const Color(0xFF1A1A2E),
      ),
      scaffoldBackgroundColor: const Color(0xFFF5F5F5),
      cardColor: Colors.white,
      dividerColor: const Color(0xFFE0E0E0),
      appBarTheme: const AppBarTheme(
        backgroundColor: Colors.white,
        foregroundColor: Color(0xFF1A1A2E), elevation: 0,
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.resolveWith((s) =>
            s.contains(WidgetState.selected) ? accent : const Color(0xFF9E9E9E)),
        trackColor: WidgetStateProperty.resolveWith((s) =>
            s.contains(WidgetState.selected)
                ? accent.withOpacity(0.4) : const Color(0xFFE0E0E0)),
      ),
      extensions: [RoadstrColors(
        accent: accent, accentSoft: accent.withOpacity(0.12),
        surface1: const Color(0xFFF5F5F5), surface2: Colors.white,
        surface3: const Color(0xFFF0F0F0), border: const Color(0xFFE0E0E0),
        textPrimary: const Color(0xFF1A1A2E), textSecondary: const Color(0xFF757575),
        isDark: false,
        mapTile: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
        mapTileSubs: null,
        mapTileAttrib: '© OpenStreetMap contributors',
      )],
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
  /// OpenStreetMap tile URL template, e.g. `https://tile.openstreetmap.org/{z}/{x}/{y}.png`.
  final String mapTile;
  /// Optional subdomain list for tile CDN load balancing (e.g. `['a','b','c']`).
  final String? mapTileSubs;
  final String mapTileAttrib;

  const RoadstrColors({
    required this.accent, required this.accentSoft,
    required this.surface1, required this.surface2, required this.surface3,
    required this.border, required this.textPrimary, required this.textSecondary,
    required this.isDark, required this.mapTile,
    this.mapTileSubs, required this.mapTileAttrib,
  });

  @override
  RoadstrColors copyWith({
    Color? accent, Color? accentSoft, Color? surface1, Color? surface2,
    Color? surface3, Color? border, Color? textPrimary, Color? textSecondary,
    bool? isDark, String? mapTile, String? mapTileSubs, String? mapTileAttrib,
  }) => RoadstrColors(
    accent: accent ?? this.accent, accentSoft: accentSoft ?? this.accentSoft,
    surface1: surface1 ?? this.surface1, surface2: surface2 ?? this.surface2,
    surface3: surface3 ?? this.surface3, border: border ?? this.border,
    textPrimary: textPrimary ?? this.textPrimary,
    textSecondary: textSecondary ?? this.textSecondary,
    isDark: isDark ?? this.isDark, mapTile: mapTile ?? this.mapTile,
    mapTileSubs: mapTileSubs ?? this.mapTileSubs,
    mapTileAttrib: mapTileAttrib ?? this.mapTileAttrib,
  );

  @override RoadstrColors lerp(RoadstrColors? other, double t) => this;
  static RoadstrColors of(BuildContext context) =>
      Theme.of(context).extension<RoadstrColors>()!;
}
