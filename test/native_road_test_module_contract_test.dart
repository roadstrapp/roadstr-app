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
    expect(
      RegExp(r'ACCESS_(?:COARSE|FINE)_LOCATION[\s\S]*?tools:node="remove"')
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
      isNot(contains('"app/roadstr/migration/**"')),
    );
    expect(
      appBuild.readAsStringSync(),
      isNot(contains('"app/roadstr/service/**"')),
    );
  });

  test('road-test launcher enters Compose directly', () {
    final source = activity.readAsStringSync();

    expect(source, contains('class NativeRoadTestActivity : ComponentActivity()'));
    expect(source, contains('setContent {'));
    expect(
      source,
      contains('NativeRoadstrShell(mode = NativeShellMode.RoadTest)'),
    );
    expect(source, isNot(contains('FlutterActivity')));
    expect(source, isNot(contains('FlutterEngine')));
    expect(source, isNot(contains('MethodChannel')));
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
