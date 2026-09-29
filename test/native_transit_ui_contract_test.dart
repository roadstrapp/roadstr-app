import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_transit_strings.dart';

void main() {
  final presentation = File(
    'android/app/src/main/kotlin/app/roadstr/feature/transit/'
    'NativeTransitPresentation.kt',
  );
  final panel = File(
    'android/app/src/main/kotlin/app/roadstr/feature/transit/'
    'NativeTransitItinerariesPanel.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 Flutter transit translations generate current Android resources',
      () {
    final generated = buildAndroidTransitResources();

    expect(generated, hasLength(27));
    expect(generated.keys.where((path) => path.contains('/values/')),
        hasLength(1));
    for (final entry in generated.entries) {
      final resource = File(entry.key);
      expect(resource.existsSync(), isTrue, reason: entry.key);
      expect(resource.readAsStringSync(), entry.value, reason: entry.key);
      final strings = RegExp(
        r'<string name="([^"]+)">([\s\S]*?)</string>',
      ).allMatches(entry.value).toList();
      expect(strings, hasLength(15), reason: entry.key);
      expect(strings.map((match) => match.group(1)).toSet(), hasLength(15));
      expect(
        strings
            .singleWhere(
              (match) => match.group(1) == 'native_transit_transfers',
            )
            .group(2),
        contains(r'%1$d'),
      );
    }
  });

  test('native presentation keeps Flutter card formatting and work bounds', () {
    final dartCard =
        File('lib/widgets/transit_itinerary_widget.dart').readAsStringSync();
    final dartUnits = File('lib/utils/units.dart').readAsStringSync();
    final kotlin = presentation.readAsStringSync();

    expect(dartCard, contains("hours > 0 ? '\${hours}h \${minutes}m'"));
    expect(dartCard, contains('background.computeLuminance() > 0.5'));
    expect(dartUnits, contains('if (metres < 50)'));
    expect(dartUnits, contains('if (ft < 500)'));
    expect(kotlin, contains('fun formatDuration(seconds: Long)'));
    expect(kotlin,
        contains('fun formatDistance(meters: Double, imperial: Boolean)'));
    expect(kotlin, contains('luminance > 0.5'));
    expect(
      kotlin,
      contains('take(NativeTransitOverlayCompiler.MAX_ITINERARIES)'),
    );
  });

  test(
      'Compose transit panel exposes bounded states and accessibility semantics',
      () {
    final source = panel.readAsStringSync();

    for (final state in <String>[
      'NativeTransitUiStatus.Loading',
      'NativeTransitUiStatus.Ready',
      'NativeTransitUiStatus.Unavailable',
      'NativeTransitUiStatus.Failure',
    ]) {
      expect(source, contains(state));
    }
    expect(source, contains('.heightIn(max = 300.dp)'));
    expect(source, contains('navigationBarsPadding()'));
    expect(source, contains('paneTitle ='));
    expect(source, contains('contentDescription ='));
    expect(source, contains('this.selected = selected'));
    expect(source, isNot(contains('NativeTransitService')));
  });

  test('private shell packages the panel without activating Transitous', () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativeTransitJourneySession('));
    expect(source, contains('NativeTransitItinerariesPanel('));
    expect(source, contains('onRetry = null'));
    expect(source, isNot(contains('NativeTransitService')));
    expect(source, isNot(contains('.submitPlan(')));
    expect(source, isNot(contains('.begin(')));
  });
}
