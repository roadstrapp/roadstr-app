import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/native_navigation_fix_feed.dart';

void main() {
  test('contract and decoder preserve a complete normalized fix', () {
    expect(NativeNavigationFixContract.channelName,
        'app.roadstr/native_navigation_fixes');

    final fix = NativeNavigationFix.fromEvent(<Object?, Object?>{
      'latitude': 45,
      'longitude': 9.0,
      'speedKmh': 36.0,
      'accuracy': 4.5,
      'heading': null,
      'altitude': 120.0,
      'timestampMillis': 1700000000000,
    });

    expect(fix.latitude, 45.0);
    expect(fix.longitude, 9.0);
    expect(fix.speedKmh, 36.0);
    expect(fix.accuracy, 4.5);
    expect(fix.heading, isNull);
    expect(fix.altitude, 120.0);
    expect(
        fix.timestamp,
        DateTime.fromMillisecondsSinceEpoch(
          1700000000000,
          isUtc: true,
        ));
    expect(fix.isReliable, isTrue);
  });

  test('decoder rejects malformed or unsafe fix payloads', () {
    final valid = <Object?, Object?>{
      'latitude': 45.0,
      'longitude': 9.0,
      'speedKmh': 0.0,
      'accuracy': double.infinity,
      'heading': 90.0,
      'altitude': 0.0,
      'timestampMillis': 1700000000000,
    };

    expect(NativeNavigationFix.fromEvent(valid).isReliable, isFalse);
    expect(
      () => NativeNavigationFix.fromEvent({...valid, 'latitude': 91.0}),
      throwsFormatException,
    );
    expect(
      () => NativeNavigationFix.fromEvent({...valid, 'speedKmh': -1.0}),
      throwsFormatException,
    );
    expect(
      () => NativeNavigationFix.fromEvent({...valid, 'altitude': double.nan}),
      throwsFormatException,
    );
    expect(
      () => NativeNavigationFix.fromEvent(const <Object?, Object?>{}),
      throwsFormatException,
    );
  });

  test('disabled feed never subscribes to the platform source', () async {
    var listenCount = 0;
    final controller = StreamController<NativeNavigationFix>.broadcast(
      onListen: () => listenCount++,
    );
    final feed = NativeNavigationFixFeed(
      source: _FakeFixSource(controller.stream),
    );

    feed.start();
    await Future<void>.delayed(Duration.zero);

    expect(listenCount, 0);
    expect(feed.receivedFixCount, 0);
    await feed.dispose();
    await controller.close();
  });

  test('enabled feed is idempotent and keeps receiving after a stream error',
      () async {
    var listenCount = 0;
    final controller = StreamController<NativeNavigationFix>.broadcast(
      onListen: () => listenCount++,
    );
    final feed = NativeNavigationFixFeed(
      source: _FakeFixSource(controller.stream),
      enabled: true,
    );
    final first = NativeNavigationFix(
      latitude: 45.0,
      longitude: 9.0,
      speedKmh: 10.0,
      accuracy: 5.0,
      heading: 90.0,
      altitude: 100.0,
      timestamp: DateTime.fromMillisecondsSinceEpoch(1700000000000),
    );
    final second = NativeNavigationFix(
      latitude: 45.1,
      longitude: 9.1,
      speedKmh: 12.0,
      accuracy: 6.0,
      heading: 91.0,
      altitude: 101.0,
      timestamp: DateTime.fromMillisecondsSinceEpoch(1700000000500),
    );

    feed.start();
    feed.start();
    controller.add(first);
    controller.addError(StateError('detached engine'));
    controller.add(second);
    await Future<void>.delayed(Duration.zero);

    expect(listenCount, 1);
    expect(feed.receivedFixCount, 2);
    expect(feed.streamErrorCount, 1);
    expect(feed.latestFix, same(second));

    await feed.dispose();
    controller.add(first);
    await Future<void>.delayed(Duration.zero);
    expect(feed.latestFix, isNull);
    expect(feed.receivedFixCount, 2);
    await controller.close();
  });
}

class _FakeFixSource implements NativeNavigationFixSource {
  const _FakeFixSource(this.fixes);

  @override
  final Stream<NativeNavigationFix> fixes;
}
