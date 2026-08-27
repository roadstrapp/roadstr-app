import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/theme/app_theme.dart';

RoadstrColors colorsOf(AppThemeId id) =>
    AppTheme.build(id).extension<RoadstrColors>()!;

void main() {
  final light = colorsOf(AppThemeId.lightNostr);
  final dark = colorsOf(AppThemeId.darkNostr);

  group('day/night transition', () {
    // MaterialApp cross-fades themes over ~200 ms. This extension used to
    // return `this` for every t, so Roadstr's own surfaces stayed on the old
    // theme for the whole animation and then snapped — a two-stage flash at
    // every sunset switch, most visible mid-navigation.
    test('surface colours actually move between the two themes', () {
      final mid = light.lerp(dark, 0.5);
      expect(mid.surface2, isNot(light.surface2));
      expect(mid.surface2, isNot(dark.surface2));
      expect(mid.surface2, Color.lerp(light.surface2, dark.surface2, 0.5));
      expect(mid.textPrimary, isNot(light.textPrimary));
    });

    test('the endpoints are exactly the source themes', () {
      expect(light.lerp(dark, 0).surface2, light.surface2);
      expect(light.lerp(dark, 1).surface2, dark.surface2);
      expect(light.lerp(dark, 1).isDark, isTrue);
      expect(light.lerp(dark, 0).isDark, isFalse);
    });

    test('the tile source never changes across the transition', () {
      // Light and dark used to point at different hosts (plain OSM vs a
      // separately-hosted dark basemap), so this URL had to step exactly
      // once, at the midpoint, or the tile layer would reload mid-fade. That
      // second host is gone: dark mode now recolours the same OSM tile light
      // mode uses, so there is nothing left to step — the URL is constant for
      // every t, and a reload the day/night switch itself could ever trigger
      // is gone with it.
      for (var i = 0; i <= 20; i++) {
        expect(light.lerp(dark, i / 20).mapTile, light.mapTile);
      }
    });

    test('a null or identical target is a no-op', () {
      expect(light.lerp(null, 0.5), same(light));
      expect(light.lerp(light, 0.5), same(light));
    });

    test('the two themes use the exact same tile host', () {
      // Not a coincidence to protect: dark mode used to fetch tiles from
      // CARTO's free anonymous dark_all endpoint, which started serving an
      // "API KEY REQUIRED" watermark instead of tiles once some unpublished
      // usage threshold was crossed — a dependency that could (and did) break
      // with zero warning. Dark mode now reads the same reliable OSM tiles
      // light mode already depends on; this test is what stops a future
      // change from quietly reintroducing a second, independently-failing
      // tile source for dark mode.
      expect(Uri.parse(dark.mapTile).host, Uri.parse(light.mapTile).host);
    });
  });

  group('accent switch keeps brightness', () {
    test('lerping between two light themes never turns dark', () {
      final bitcoin = colorsOf(AppThemeId.lightBitcoin);
      for (var i = 0; i <= 10; i++) {
        final c = light.lerp(bitcoin, i / 10);
        expect(c.isDark, isFalse);
        expect(c.mapTile, light.mapTile); // same source, no tile reload
      }
    });
  });
}
