import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_route_planning_strings.dart';

void main() {
  final presentation = File(
    'android/app/src/main/kotlin/app/roadstr/feature/route/'
    'NativeRoutePlanningPresentation.kt',
  );
  final panel = File(
    'android/app/src/main/kotlin/app/roadstr/feature/route/'
    'NativeRoutePlanningPanel.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 route-planning resource sets are current and complete', () {
    final generated = buildAndroidRoutePlanningResources();

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
      expect(strings, hasLength(22), reason: entry.key);
      expect(strings.map((match) => match.group(1)).toSet(), hasLength(22));
      expect(_value(strings, 'native_route_depart_eta'), contains(r'%1$s'));
      expect(_value(strings, 'native_route_depart_eta'), contains(r'%2$s'));
      expect(_value(strings, 'native_route_duration_min'), contains(r'%1$d'));
    }
  });

  test('native presenter locks the Flutter route-choice vocabulary and bounds',
      () {
    final flutter =
        File('lib/widgets/route/route_panels.dart').readAsStringSync();
    final native = presentation.readAsStringSync();

    expect(flutter, contains('class RoutePlannerBar'));
    expect(flutter, contains('class RoutePreviewPanel'));
    expect(flutter, contains('class RouteAlternativesPanel'));
    expect(flutter, contains('route.isOffRoadAvoidance'));
    expect(flutter, contains('route.isHighwayAndTollAvoidance'));
    expect(native, contains('const val MAX_STOPS = 5'));
    expect(native, contains('MAX_ROUTE_ALTERNATIVES'));
    expect(native, contains('RoutingRouteAvoidance.OffRoadAvoided'));
    expect(native, contains('RoutingRouteAvoidance.HighwayAndTollFree'));
    expect(native, contains('RoutingRouteAvoidance.MinimizedHighwaysAndTolls'));
    expect(
        native, contains('conditions: List<NativeRouteConditionPresentation>'));
  });

  test('Compose planner keeps bounded accessible controls and no live owners',
      () {
    final source = panel.readAsStringSync();

    expect(source, contains('OutlinedTextField'));
    expect(source, contains('LazyRow'));
    expect(source, contains('navigationBarsPadding()'));
    expect(source, contains('paneTitle ='));
    expect(source, contains('Role.RadioButton'));
    expect(source, contains('minHeight = 48.dp'));
    expect(source, contains('NativeRoutePlanningSession.MAX_STOPS'));
    expect(source, isNot(contains('RoutingService')));
    expect(source, isNot(contains('LocationManager')));
    expect(source, isNot(contains('WeatherService')));
    expect(source, isNot(contains('Hive')));
  });

  test('private shell feeds planning through the provider-neutral coordinator',
      () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativeRoutePlanningSession('));
    expect(source, contains('NativeRoutePlanningPanel('));
    expect(source, contains('journeyCoordinator?.calculateRoute('));
    expect(source, contains('journeyCoordinator?.cancelRoute()'));
    expect(source, contains('routePlanningSession.useMyLocation('));
    expect(source, contains('.selectedNavigationRoute('));
    expect(source, contains('routePlanningSession.beginNavigation('));
    expect(source, contains('activeNavigationSession.start('));
    expect(source, isNot(contains('NativeRoutingService')));
  });
}

String? _value(List<RegExpMatch> values, String name) =>
    values.singleWhere((match) => match.group(1) == name).group(2);
