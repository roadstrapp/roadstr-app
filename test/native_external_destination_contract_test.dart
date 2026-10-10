import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('native launcher accepts Tankful JSON on cold and singleTop delivery', () {
    final activity = File(
      'native-android/app/src/main/kotlin/app/roadstr/roadtest/NativeRoadTestActivity.kt',
    ).readAsStringSync();
    final shell = File(
      'android/app/src/main/kotlin/app/roadstr/feature/home/NativeRoadstrShell.kt',
    ).readAsStringSync();
    final productionManifest =
        File('android/app/src/main/AndroidManifest.xml').readAsStringSync();

    expect(activity, contains('acceptExternalDestination(intent)'));
    expect(activity, contains('override fun onNewIntent(intent: Intent)'));
    expect(activity, contains('intent.getStringExtra(Intent.EXTRA_TEXT)'));
    expect(activity, contains('externalDestinationInbox.requests.collectAsStateWithLifecycle()'));
    expect(shell, contains('coordinator.selectDestinationAndCalculate('));
    expect(shell, contains('coordinator.selectDestination(destination.label, point, null, myLocationLabel)'));
    expect(shell, contains('onExternalDestinationConsumed(request.revision)'));

    // Tankful resolves the launcher component explicitly. Do not enlarge the public surface by
    // registering Roadstr as an implicit system-wide share target.
    expect(productionManifest, isNot(contains('android.intent.action.SEND')));
  });
}
