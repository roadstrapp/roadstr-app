import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_navigation_strings.dart';

void main() {
  final presentation = File(
    'android/app/src/main/kotlin/app/roadstr/feature/navigation/'
    'NativeNavigationHudPresentation.kt',
  );
  final maneuver = File(
    'android/app/src/main/kotlin/app/roadstr/feature/navigation/'
    'NativeManeuverSymbol.kt',
  );
  final hud = File(
    'android/app/src/main/kotlin/app/roadstr/feature/navigation/'
    'NativeNavigationHud.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 Flutter HUD translations generate current Android resources',
      () {
    final generated = buildAndroidNavigationResources();

    expect(generated, hasLength(27));
    expect(
      generated.keys.where((path) => path.contains('/values/')),
      hasLength(1),
    );
    for (final entry in generated.entries) {
      final resource = File(entry.key);
      expect(resource.existsSync(), isTrue, reason: entry.key);
      expect(resource.readAsStringSync(), entry.value, reason: entry.key);
      final strings = RegExp(
        r'<string name="([^"]+)">([\s\S]*?)</string>',
      ).allMatches(entry.value).toList();
      expect(strings, hasLength(15), reason: entry.key);
      expect(strings.map((match) => match.group(1)).toSet(), hasLength(15));
      expect(_value(strings, 'native_nav_then'), contains(r'%1$s'));
      expect(_value(strings, 'native_nav_eta'), contains(r'%1$s'));
      expect(_value(strings, 'native_nav_duration_min'), contains(r'%1$d'));
      expect(
        _value(strings, 'native_nav_duration_hour_min'),
        allOf(contains(r'%1$d'), contains(r'%2$d')),
      );
      expect(_value(strings, 'native_nav_arrived'), isNotEmpty);
      expect(_value(strings, 'native_nav_close'), isNotEmpty);
    }
  });

  test('native HUD presentation preserves Flutter maneuver and summary rules',
      () {
    final dartHud = File('lib/widgets/nav/nav_hud.dart').readAsStringSync();
    final dartManeuver =
        File('lib/widgets/nav/maneuver_symbol.dart').readAsStringSync();
    final dartSpeedometer =
        File('lib/widgets/speedometer_widget.dart').readAsStringSync();
    final kotlin = presentation.readAsStringSync();

    expect(dartHud, contains('step.distanceM > 2000'));
    expect(dartHud, contains('distToNextM > 0 ? distToNextM'));
    expect(dartHud, contains('remainingSecs > 0 ? remainingSecs'));
    expect(kotlin, contains('currentStep.distanceM > 2_000'));
    expect(kotlin, contains('input.distanceToManeuverM.takeIf { it > 0 }'));
    expect(kotlin, contains('input.remainingSeconds.takeIf { it > 0 }'));

    for (final kind in <String>[
      'forkLeft',
      'mergeRight',
      'rampLeft',
      'exitRight',
      'roundabout',
      'arrive',
      'ferry',
    ]) {
      expect(dartManeuver, contains('ManeuverVisualKind.$kind'));
    }
    expect(kotlin, contains('RoutingResponseProtocol.MAX_ROUNDABOUT_ARMS'));
    for (final style in <String>[
      'classic',
      'digital',
      'analog',
      'sport',
      'minimal',
    ]) {
      expect(dartSpeedometer, contains(style));
      expect(kotlin.toLowerCase(), contains('("$style")'));
    }
  });

  test('Compose HUD is adaptive accessible and uses deterministic vectors', () {
    final source = hud.readAsStringSync();
    final vector = maneuver.readAsStringSync();

    expect(source, contains('val landscape = maxWidth > maxHeight'));
    expect(source, contains('statusBarsPadding()'));
    expect(source, contains('navigationBarsPadding()'));
    expect(source, contains('paneTitle = instruction'));
    expect(source, contains('liveRegion = LiveRegionMode.Polite'));
    expect(source, contains('.sizeIn(minWidth = 48.dp, minHeight = 48.dp)'));
    expect(source, contains('NativeSpeedometerStyle.Sport'));
    expect(source, contains('fun NativeNavigationArrivalBanner('));
    expect(source, isNot(contains('NativeLocationService')));
    expect(source, isNot(contains('NativeRoutingService')));
    expect(source, isNot(contains('Intent(')));

    expect(vector, contains('Path().apply'));
    expect(vector, contains('NativeManeuverKind.Roundabout'));
    expect(vector, contains('for (index in 1 until arms)'));
    expect(vector, isNot(contains('ImageBitmap')));
    expect(vector, isNot(contains('painterResource')));
  });

  test('private shell drives HUD through GPS-fed value-only navigation', () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativeNavigationHudSession()'));
    expect(source, contains('NativeActiveNavigationSession('));
    expect(source, contains('NativeNavigationHud('));
    expect(source, contains('activeNavigationSession.start('));
    expect(source, contains('activeNavigationSession.submitFix('));
    expect(source, contains('activeNavigationSession.stop('));
    expect(source, contains('activeNavigationSession.toggleVoice('));
    expect(source, contains('activeNavigationSession.completeReroute'));
    expect(source, contains('activeNavigationSession.failReroute'));
    expect(source, contains('NativeNavigationArrivalBanner('));
    expect(source, contains('journeyCoordinator?.reroute('));
    expect(source, contains('.selectedNavigationRoute('));
    expect(source, contains('routePlanningSession.beginNavigation('));
    expect(source, contains('onOpenSettings = {}'));
    expect(source, isNot(contains('NativeLocationService')));
    expect(source, isNot(contains('NativeRoutingService')));
    expect(source, isNot(contains("Hive.box('settings')")));
  });
}

String? _value(List<RegExpMatch> values, String name) =>
    values.singleWhere((match) => match.group(1) == name).group(2);
