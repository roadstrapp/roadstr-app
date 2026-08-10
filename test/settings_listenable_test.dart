import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:roadstr/utils/settings_listenable.dart';

/// A navigation session died mid-route because of this. Every `setState` on
/// the map — one per GPS fix — rebuilt a `ValueListenableBuilder` whose
/// listenable was constructed inline, so Hive opened a fresh box subscription
/// and dropped the previous one. `cancel()` is asynchronous and `listen()` is
/// not, so under a steady stream of fixes the subscriptions outran the
/// cancellations until the app ran out of room.
void main() {
  setUpAll(() async {
    Hive.init((await Directory.systemTemp.createTemp('roadstr-listenable')).path);
    await Hive.openBox('settings');
  });
  tearDownAll(Hive.close);
  setUp(SettingsListenable.reset);

  test('the same keys always yield the very same object', () {
    // Identity is the whole point: ValueListenableBuilder only re-subscribes
    // when the object changes, so a stable one means a stable subscription.
    final a = SettingsListenable.forKeys(const ['cursorStyle']);
    final b = SettingsListenable.forKeys(const ['cursorStyle']);
    expect(identical(a, b), isTrue);
  });

  test('a thousand rebuilds do not create a thousand listenables', () {
    final seen = <ValueListenable<Box>>{};
    for (var i = 0; i < 1000; i++) {
      seen.add(SettingsListenable.forKeys(const ['cursorStyle']));
    }
    expect(seen, hasLength(1),
        reason: 'one per key set, however often build() runs');
  });

  test('different key sets stay separate', () {
    final cursor = SettingsListenable.forKeys(const ['cursorStyle']);
    final activity = SettingsListenable.forKeys(const ['activity_abc']);
    expect(identical(cursor, activity), isFalse);
    expect(identical(activity, SettingsListenable.forKeys(const ['activity_abc'])),
        isTrue);
  });

  test('an empty key set is handled like any other', () {
    final a = SettingsListenable.forKeys(const []);
    expect(identical(a, SettingsListenable.forKeys(const [])), isTrue);
  });

  test('it still notifies when the watched key changes', () async {
    final box = Hive.box('settings');
    final listenable = SettingsListenable.forKeys(const ['cursorStyle']);
    var notified = 0;
    void onChange() => notified++;
    listenable.addListener(onChange);
    addTearDown(() => listenable.removeListener(onChange));

    await box.put('cursorStyle', 'racing');
    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(notified, greaterThan(0), reason: 'caching must not mute updates');

    await box.put('somethingElse', 1);
    final afterUnrelated = notified;
    await Future<void>.delayed(const Duration(milliseconds: 50));
    expect(notified, afterUnrelated,
        reason: 'and must keep filtering by key');
  });
}
