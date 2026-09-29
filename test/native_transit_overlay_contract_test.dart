import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final flutterMap = File('lib/screens/maplibre_map_screen.dart');
  final flutterModes = File('lib/models/transit_mode.dart');
  final flutterItinerary = File('lib/models/transit_itinerary.dart');
  final flutterService = File('lib/services/transit_service.dart');
  final flutterPolyline = File('lib/utils/polyline.dart');
  final fixture = File('test/fixtures/transit_plan_berlin.json');
  final protocol = File(
    'android/app/src/main/kotlin/app/roadstr/core/network/'
    'TransitProtocol.kt',
  );
  final nativeService = File(
    'android/app/src/main/kotlin/app/roadstr/service/transit/'
    'NativeTransitService.kt',
  );
  final overlay = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeTransitOverlay.kt',
  );
  final renderer = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapTransitRenderer.kt',
  );
  final session = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeTransitOverlaySession.kt',
  );
  final host = File(
    'android/app/src/main/kotlin/app/roadstr/feature/map/'
    'NativeMapLibreHost.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('native transit styling retains the Flutter per-leg oracle', () {
    final dart = flutterMap.readAsStringSync();
    final nativeOverlay = overlay.readAsStringSync();
    final nativeRenderer = renderer.readAsStringSync();

    expect(dart, contains('leg.routeColor ?? c.accent'));
    expect(dart, contains('c.textSecondary.withValues(alpha: 0.7)'));
    expect(dart, contains('routeWidthPx(leg.mode.isTransit ? 7 : 4)'));
    expect(nativeOverlay, contains('TRANSIT_LOGICAL_WIDTH = 7.0'));
    expect(nativeOverlay, contains('STREET_LOGICAL_WIDTH = 4.0'));
    expect(nativeRenderer, contains('const val STREET_ALPHA = 0.7'));
    expect(nativeRenderer,
        contains('Expression.toColor(Expression.get(COLOR_PROPERTY))'));
    expect(nativeRenderer, contains('Property.LINE_CAP_ROUND'));
  });

  test(
      'native transit mode catalogue stays aligned with worldwide Flutter modes',
      () {
    final dart = flutterModes.readAsStringSync();
    final native = protocol.readAsStringSync();

    for (final wireName in <String>[
      'WALK',
      'BIKE',
      'CAR',
      'TRAM',
      'SUBWAY',
      'METRO',
      'SUBURBAN',
      'REGIONAL_RAIL',
      'REGIONAL_FAST_RAIL',
      'LONG_DISTANCE',
      'HIGHSPEED_RAIL',
      'NIGHT_RAIL',
      'RAIL',
      'BUS',
      'COACH',
      'FERRY',
      'AIRPLANE',
      'FUNICULAR',
      'AERIAL_LIFT',
      'ODM',
      'OTHER',
    ]) {
      expect(dart, contains("('$wireName')"));
      expect(native, contains('("$wireName",'));
    }
    expect(dart,
        contains('TransitMode.walk || TransitMode.bike || TransitMode.car'));
    expect(native, contains('Walk("WALK", false)'));
    expect(native, contains('Other("OTHER", true)'));
    expect(
      overlay.readAsStringSync(),
      contains('typealias NativeTransitMode = TransitMode'),
    );
  });

  test('private shell owns a bounded dormant transit renderer', () {
    final nativeSession = session.readAsStringSync();
    final nativeRenderer = renderer.readAsStringSync();
    final hostSource = host.readAsStringSync();
    final shellSource = shell.readAsStringSync();

    expect(nativeSession, contains('revision <= this.revision'));
    expect(nativeSession, contains('MAX_TRANSIT_POINTS'));
    expect(nativeRenderer, contains('roadstr-transit-source'));
    expect(nativeRenderer, contains('roadstr-transit-legs'));
    expect(hostSource, contains('transitRenderer.attach(loadedStyle)'));
    expect(
      hostSource.indexOf('transitRenderer.attach(loadedStyle)'),
      lessThan(hostSource.indexOf('routeRenderer.attach(loadedStyle)')),
    );
    expect(shellSource, contains('NativeTransitOverlaySession'));
    expect(shellSource, contains('transitOverlay = transitState'));
    expect(shellSource, isNot(contains('TransitService')));
  });

  test('native Transitous request keeps Flutter endpoint and budgets', () {
    final dart = flutterService.readAsStringSync();
    final kotlin = protocol.readAsStringSync();
    final service = nativeService.readAsStringSync();

    expect(dart, contains("'https://api.transitous.org/api/v1/plan'"));
    expect(dart, contains('static const _itineraryCount = 3'));
    expect(dart, contains('static const _maxAccessWalkSeconds = 1800'));
    expect(dart, contains('maxBytes: NetworkLimits.transitPlan'));
    expect(dart, contains('timeout: NetworkTimeouts.transit'));
    expect(kotlin, contains('REQUESTED_ITINERARIES = 3'));
    expect(kotlin, contains('MAX_ACCESS_WALK_SECONDS = 1_800'));
    expect(service, contains('NetworkTimeoutBudget.Transit.milliseconds'));
    expect(service, contains('NetworkResponseLimit.TransitPlan.bytes'));
  });

  test('native parser is locked to the real Berlin and polyline oracles', () {
    final dartModel = flutterItinerary.readAsStringSync();
    final dartPolyline = flutterPolyline.readAsStringSync();
    final kotlin = protocol.readAsStringSync();

    expect(fixture.existsSync(), isTrue);
    expect(fixture.lengthSync(), greaterThan(40000));
    expect(dartModel, contains("precision: _asInt(json['precision']) ?? 5"));
    expect(dartModel,
        contains("if (name == null || name == 'START' || name == 'END')"));
    expect(dartPolyline, contains('if (shift > 30) return null'));
    expect(kotlin,
        contains('fun decodePolyline(encoded: String, precision: Int)'));
    expect(kotlin, contains('if (shift > 30) return null'));
    expect(kotlin, contains('filterNot(TransitItinerary::isWalkOnly)'));
    expect(kotlin, contains('.sortedBy(TransitItinerary::durationSeconds)'));
  });

  test('native transit execution remains cancellable private and dormant', () {
    final service = nativeService.readAsStringSync();
    final nativeOverlay = overlay.readAsStringSync();
    final nativeSession = session.readAsStringSync();
    final shellSource = shell.readAsStringSync();

    expect(service, contains('NativeTransitHttpTransport'));
    expect(service, contains('RetryPolicy.Interactive'));
    expect(service, contains('catch (cancelled: CancellationException)'));
    expect(service, contains('throw cancelled'));
    expect(service, isNot(contains('println(')));
    expect(nativeOverlay, contains('object NativeTransitOverlayProjection'));
    expect(nativeSession, contains('fun submitPlan('));
    expect(shellSource, contains('NativeTransitOverlaySession'));
    expect(shellSource, isNot(contains('NativeTransitService')));
  });
}
