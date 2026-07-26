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

    test('the tile source switches exactly once, at the midpoint', () {
      // Half a URL is not a URL: the discrete fields must step, not blend,
      // and must step only once so the tile layer reloads at most once.
      final seen = <String>[];
      for (var i = 0; i <= 20; i++) {
        final url = light.lerp(dark, i / 20).mapTile;
        if (seen.isEmpty || seen.last != url) seen.add(url);
      }
      expect(seen, [light.mapTile, dark.mapTile]);
    });

    test('isDark steps with the tile source, never out of step with it', () {
      for (var i = 0; i <= 20; i++) {
        final c = light.lerp(dark, i / 20);
        expect(c.isDark, c.mapTile == dark.mapTile,
            reason: 'at t=${i / 20} the dark flag and the tile source '
                'disagreed, which would filter light tiles or vice versa');
      }
    });

    test('a null or identical target is a no-op', () {
      expect(light.lerp(null, 0.5), same(light));
      expect(light.lerp(light, 0.5), same(light));
    });

    test('the two themes really do use different tile hosts', () {
      // If this ever stops being true the reload-on-switch problem disappears
      // and the tileDisplay workaround in map_screen can be revisited.
      expect(Uri.parse(light.mapTile).host,
          isNot(Uri.parse(dark.mapTile).host));
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
