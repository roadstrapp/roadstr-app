import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final panel = File(
    'android/app/src/main/kotlin/app/roadstr/feature/journey/'
    'NativeJourneyPanel.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );
  final service = File(
    'android/app/src/main/kotlin/app/roadstr/service/nostr/'
    'NativeNip58Service.kt',
  );
  final strings = File(
    'android/app/src/main/res/values/native_journey_strings.xml',
  );

  test('achievement catalogue uses distinct vector marks instead of emoji', () {
    final source = panel.readAsStringSync();
    for (final key in <String>[
      'ignition',
      'street_rookie',
      'detour_hunter',
      'wanderlust',
      'wayfinder',
      'road_rebel',
      'asphalt_nomad',
      'horizon_chaser',
      'long_hauler',
      'borderless',
      'road_warrior',
      'atlas_runner',
      'continental_drifter',
      'planet_roamer',
      'earthshaker',
      'roadstr_myth',
      'impossible_road',
    ]) {
      expect(source, contains('"$key" ->'), reason: key);
    }
    expect(source, contains('Canvas(modifier)'));
    expect(source, isNot(contains('emoji:')));
  });

  test('public badges require configured issuer, verified definition and user action', () {
    final ui = panel.readAsStringSync();
    final orchestration = shell.readAsStringSync();
    final protocol = service.readAsStringSync();
    final resources = strings.readAsStringSync();

    expect(resources, contains('name="roadstr_badge_issuer_pubkey"'));
    expect(resources, contains('translatable="false"></string>'));
    expect(ui, contains('native_journey_public_badge_show'));
    expect(orchestration, contains('onShowPublicBadge = showPublicBadge@'));
    expect(orchestration, contains('service.findAward('));
    expect(orchestration, contains('service.addToProfile(award)'));
    expect(protocol, contains('if (!hasValidDefinition(reference)) return null'));
    expect(protocol, contains('definition?.reference == reference'));
  });
}
