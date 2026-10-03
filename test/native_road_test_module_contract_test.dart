import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final settings = File('native-android/settings.gradle.kts');
  final appBuild = File('native-android/app/build.gradle.kts');
  final manifest = File(
    'native-android/app/src/main/AndroidManifest.xml',
  );
  final activity = File(
    'native-android/app/src/main/kotlin/app/roadstr/roadtest/'
    'NativeRoadTestActivity.kt',
  );
  final locationController = File(
    'native-android/app/src/main/kotlin/app/roadstr/roadtest/'
    'NativeRoadTestLocationController.kt',
  );
  final journeyGateway = File(
    'native-android/app/src/main/kotlin/app/roadstr/roadtest/'
    'NativeRoadTestJourneyGateway.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );
  final aospSource = File(
    'android/app/src/main/kotlin/app/roadstr/service/location/'
    'AndroidLocationManagerSource.kt',
  );

  test('road-test build is a separate native Android application', () {
    final build = appBuild.readAsStringSync();
    final xml = manifest.readAsStringSync();
    final wrapper = File(
      'native-android/gradle/wrapper/gradle-wrapper.properties',
    );

    expect(File('native-android/gradlew').existsSync(), isTrue);
    expect(
      File('native-android/gradle/wrapper/gradle-wrapper.jar').existsSync(),
      isTrue,
    );
    final wrapperProperties = wrapper.readAsStringSync();
    expect(wrapperProperties, contains('gradle-9.1.0-all.zip'));
    expect(wrapperProperties, contains('distributionSha256Sum='));
    expect(build, contains('applicationId = "app.roadstr.roadtest"'));
    expect(build, contains('namespace = "app.roadstr"'));
    expect(build, contains('compileSdk = 36'));
    expect(build, contains('minSdk = 24'));
    expect(build, contains('compose = true'));
    expect(xml, contains('android:name=".roadtest.NativeRoadTestActivity"'));
    expect(xml, contains('android.intent.action.MAIN'));
    expect(xml, contains('android.intent.category.LAUNCHER'));
    expect(xml, contains('android:allowBackup="false"'));
    expect(xml, contains('android:usesCleartextTraffic="false"'));
    expect(xml, contains('android.permission.INTERNET'));
    expect(xml, contains('android.permission.ACCESS_COARSE_LOCATION'));
    expect(xml, contains('android.permission.ACCESS_FINE_LOCATION'));
    expect(xml, isNot(contains('android.permission.ACCESS_BACKGROUND_LOCATION')));
    expect(xml, contains('android.hardware.location.gps'));
    expect(
      RegExp(r'android\.hardware\.location(?:\.gps)?[\s\S]*?required="false"')
          .allMatches(xml),
      hasLength(2),
    );
  });

  test('road-test Gradle graph has no Flutter runtime or plugin', () {
    final graph = '${settings.readAsStringSync()}\n${appBuild.readAsStringSync()}';

    expect(graph, isNot(contains('dev.flutter')));
    expect(graph, isNot(contains('io.flutter')));
    expect(graph, isNot(contains('flutter-gradle-plugin')));
    expect(graph, isNot(contains('flutterEmbedding')));
    expect(
      appBuild.readAsStringSync(),
      contains('"../../android/app/src/main/kotlin/app/roadstr/core"'),
    );
    expect(
      appBuild.readAsStringSync(),
      contains('"../../android/app/src/main/kotlin/app/roadstr/feature"'),
    );
    expect(
      appBuild.readAsStringSync(),
      contains(
        '"../../android/app/src/main/kotlin/app/roadstr/service/location"',
      ),
    );
    for (final service in ['network', 'routing', 'search']) {
      expect(
        appBuild.readAsStringSync(),
        contains(
          '"../../android/app/src/main/kotlin/app/roadstr/service/$service"',
        ),
      );
    }
    expect(
      appBuild.readAsStringSync(),
      isNot(contains('"app/roadstr/migration/**"')),
    );
    expect(
      appBuild.readAsStringSync(),
      isNot(contains('app/roadstr/service/navigation')),
    );
  });

  test('road-test launcher enters Compose directly', () {
    final source = activity.readAsStringSync();

    expect(source, contains('class NativeRoadTestActivity : ComponentActivity()'));
    expect(source, contains('setContent {'));
    expect(source, contains('NativeRoadstrShell('));
    expect(source, contains('mode = NativeShellMode.RoadTest'));
    expect(source, contains('journeyGateway = journeyGateway'));
    expect(source, isNot(contains('FlutterActivity')));
    expect(source, isNot(contains('FlutterEngine')));
    expect(source, isNot(contains('MethodChannel')));
  });

  test('road-test journey gateway admits only bounded public OSM routing', () {
    final source = journeyGateway.readAsStringSync();

    expect(source, contains('NativeBoundedHttpClient()'));
    expect(source, contains('NativeSearchService(transport)'));
    expect(source, contains('NativeRoutingService(transport)'));
    expect(source, contains('providerKey = "osrm"'));
    expect(source, contains('deferCredentialReadForOsrm = true'));
    expect(source, contains('mode != NativeRouteTransportMode.Transit'));
    expect(source, isNot(contains('apiKey = "')));
    expect(source, isNot(contains('Log.')));
  });

  test('road-test GPS owner drives the native cursor and camera in foreground', () {
    final activitySource = activity.readAsStringSync();
    final controllerSource = locationController.readAsStringSync();
    final shellSource = shell.readAsStringSync();
    final aospSourceCode = aospSource.readAsStringSync();

    expect(
      activitySource,
      contains('ActivityResultContracts.RequestMultiplePermissions()'),
    );
    expect(activitySource, contains('locationController.onHostStart()'));
    expect(activitySource, contains('locationController.onHostStop()'));
    expect(activitySource, contains('Settings.ACTION_LOCATION_SOURCE_SETTINGS'));
    expect(controllerSource, contains('AndroidLocationManagerSource(context)'));
    expect(controllerSource, contains('NativeLocationService('));
    expect(controllerSource, contains('service.lastKnown()'));
    expect(controllerSource, contains('service.stop()'));
    expect(controllerSource, isNot(contains('FusedLocationProvider')));
    expect(controllerSource, isNot(contains('GoogleApi')));
    expect(controllerSource, isNot(contains('Log.')));
    expect(aospSourceCode, contains('catch (_: IllegalArgumentException)'));
    expect(shellSource, contains('cursorSession.submitPosition'));
    expect(shellSource, contains('cameraSession.submitFix'));
    expect(shellSource, contains('cameraSession.recenter'));
  });

  test('ordinary Roadstr launcher remains unchanged', () {
    final ordinary = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();
    final launcher = RegExp(
      r'<activity\s+android:name="\.MainActivity"[\s\S]*?</activity>',
    ).firstMatch(ordinary);

    expect(launcher, isNotNull);
    expect(launcher!.group(0), contains('android.intent.action.MAIN'));
    expect(launcher.group(0), contains('android.intent.category.LAUNCHER'));
    expect(ordinary, isNot(contains('NativeRoadTestActivity')));
  });
}
