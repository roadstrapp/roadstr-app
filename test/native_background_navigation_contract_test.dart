import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

// A trip has to go on with the screen off. These checks pin how: the trip's sessions and the work that
// moves it along live in a host that has no view, the GPS and the voice are held while a trip lasts, the
// real app keeps one runtime for the whole process, and a foreground service holds the process.

const _main = 'android/app/src/main/kotlin/app/roadstr';
const _roadTest = 'native-android/app/src/main/kotlin/app/roadstr/roadtest';

String _read(String path) => File(path).readAsStringSync();

void main() {
  test('the host owns the trip sessions and the work that moves a trip along',
      () {
    final host = _read('$_main/feature/navigation/NativeNavigationHost.kt');

    expect(
        host,
        contains(
            'val session = NativeActiveNavigationSession(hudSession, routeSession)'));
    for (final duty in [
      'gps.collect',
      'session.submitFix(',
      'voice?.announceManeuver(',
      'voice?.announceArrival()',
      'session.completeReroute(',
      'session.failReroute(',
      'platform.showInstruction(',
      'platform.guidanceStarted()',
      'platform.guidanceStopped()',
    ]) {
      expect(host, contains(duty), reason: duty);
    }
    // No view, no composition and no Android UI class in the host.
    for (final forbidden in [
      'import androidx.compose',
      'import android.app',
      'import android.view',
      'LocalContext',
      'LaunchedEffect',
      'collectAsState',
    ]) {
      expect(host, isNot(contains(forbidden)), reason: forbidden);
    }
  });

  test('the shell leaves a trip to the host when it has one', () {
    final shell = _read('$_main/feature/home/NativeRoadstrShell.kt');

    expect(shell, contains('navigationHost: NativeNavigationHost? = null'));
    expect(shell, contains('navigationHost?.session ?: remember'));
    expect(shell, contains('navigationHost?.routeSession ?: remember'));
    expect(shell, contains('navigationHost?.hudSession ?: remember'));
    // Each piece of work that the host now does is skipped by the shell, so nothing runs twice.
    expect(shell, contains('if (navigating && navigationHost == null)'));
    expect(
        shell, contains('if (navigationHost != null) return@LaunchedEffect'));
    expect(
        shell,
        contains(
            'if (navigationHost == null) voiceGateway?.announceArrival()'));
    expect(
        shell,
        contains(
            'routePlanningSession.synchronizeNavigationRevision(activeNavigationState.revision)'));
  });

  test(
      'the GPS and the voice are held while a trip lasts and released afterwards',
      () {
    final controller = _read('$_roadTest/NativeRoadTestLocationController.kt');
    final runtime = _read('$_roadTest/NativeNavigationRuntime.kt');
    final activity = _read('$_roadTest/NativeRoadTestActivity.kt');

    expect(controller, contains('fun holdForNavigation(hold: Boolean)'));
    expect(controller, contains('val wanted = visible || held'));
    expect(runtime, contains('location.holdForNavigation(true)'));
    expect(runtime, contains('location.holdForNavigation(false)'));
    expect(runtime, contains('if (!host.navigating) voice.stop()'));
    expect(runtime, contains('if (!visible) voice.stop()'));
    // The Activity no longer stops the voice or the GPS on its own.
    final onStop = activity.substring(activity.indexOf('override fun onStop()'),
        activity.indexOf('override fun onTrimMemory'));
    expect(onStop, isNot(contains('voiceGateway.stop()')));
    expect(onStop, isNot(contains('locationController.onHostStop()')));
    expect(activity, contains('releaseRuntime(runtime)'));
  });

  test(
      'the real app keeps one runtime for the whole process, and releases it only between trips',
      () {
    final holder = _read('$_main/startup/NativeGuidanceRuntime.kt');
    final app = _read('$_main/startup/NativeAppActivity.kt');

    expect(holder, contains('internal object NativeGuidanceRuntimeHolder'));
    expect(holder, contains('if (runtime.navigating) return@synchronized'));
    expect(
        app, contains('override fun obtainRuntime(): NativeNavigationRuntime'));
    expect(
        app,
        contains(
            'NativeGuidanceRuntimeHolder.obtain(applicationContext, storeNames)'));
    expect(app, contains('NativeGuidanceRuntimeHolder.release(runtime)'));
  });

  test(
      'a foreground service holds the process during a trip and its notification opens the app',
      () {
    final platform = _read('$_main/startup/NativeGuidanceRuntime.kt');
    final service =
        _read('$_main/service/navigation/NativeNavigationForegroundService.kt');
    final manifest = _read('android/app/src/main/AndroidManifest.xml');

    expect(platform, contains('ContextCompat.startForegroundService('));
    expect(platform, contains('appContext.stopService('));
    expect(platform, contains('NativeNavigationNotificationCommand.Update('));
    expect(platform, contains('NativeNavigationNotificationCommand.Reset'));
    expect(service, contains('.setContentIntent(contentIntent())'));
    expect(service, contains('getLaunchIntentForPackage(packageName)'));
    expect(manifest, contains('android:foregroundServiceType="location"'));
    expect(
        manifest, contains('android.permission.FOREGROUND_SERVICE_LOCATION'));
    expect(manifest, contains('android.permission.POST_NOTIFICATIONS'));
  });

  test('the notification permission is asked once, when the first trip starts',
      () {
    final app = _read('$_main/startup/NativeAppActivity.kt');

    expect(app, contains('Manifest.permission.POST_NOTIFICATIONS'));
    expect(
        app,
        contains(
            'if (notificationsAsked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return'));
    expect(
        app,
        contains(
            '.map { it.active }.distinctUntilChanged().filter { it }.collect { askForNotifications() }'));
  });
}
