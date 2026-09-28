import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/native_navigation_bridge.dart';
import 'package:roadstr/services/native_navigation_ownership.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel(NativeNavigationBridgeContract.channelName);
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
  });

  test('disabled ownership never crosses the platform boundary', () async {
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return true;
    });
    final owner = NativeNavigationOwnership();

    expect(await owner.start(), isFalse);
    await owner.stop();

    expect(owner.ownsForegroundGps, isFalse);
    expect(calls, isEmpty);
  });

  test('enabled ownership is idempotent across duplicate starts and stops',
      () async {
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return call.method == NativeNavigationBridgeContract.startForegroundGps
          ? true
          : null;
    });
    final owner = NativeNavigationOwnership(
      bridge: NativeNavigationBridge(channel: channel),
      enabled: true,
    );

    expect(await owner.start(), isTrue);
    expect(await owner.start(), isTrue);
    await owner.stop();
    await owner.stop();

    expect(
      calls.map((call) => call.method),
      [
        NativeNavigationBridgeContract.startForegroundGps,
        NativeNavigationBridgeContract.stopForegroundGps,
      ],
    );
    expect(owner.ownsForegroundGps, isFalse);
  });

  test('start failure does not claim ownership and can be retried', () async {
    var attempts = 0;
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == NativeNavigationBridgeContract.startForegroundGps) {
        attempts++;
        if (attempts == 1) {
          throw PlatformException(
            code: NativeNavigationBridgeContract.startFailedCode,
          );
        }
        return true;
      }
      return null;
    });
    final owner = NativeNavigationOwnership(
      bridge: NativeNavigationBridge(channel: channel),
      enabled: true,
    );

    await expectLater(owner.start(), throwsA(isA<PlatformException>()));
    expect(owner.ownsForegroundGps, isFalse);
    expect(await owner.start(), isTrue);
    expect(attempts, 2);
  });

  test('concurrent start and stop calls remain ordered', () async {
    final calls = <MethodCall>[];
    final startDone = Completer<void>();
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      if (call.method == NativeNavigationBridgeContract.startForegroundGps) {
        await startDone.future;
        return true;
      }
      return null;
    });
    final owner = NativeNavigationOwnership(
      bridge: NativeNavigationBridge(channel: channel),
      enabled: true,
    );

    final starting = owner.start();
    final stopping = owner.stop();
    await Future<void>.delayed(Duration.zero);
    expect(calls.map((call) => call.method),
        [NativeNavigationBridgeContract.startForegroundGps]);

    startDone.complete();
    expect(await starting, isTrue);
    await stopping;
    expect(
      calls.map((call) => call.method),
      [
        NativeNavigationBridgeContract.startForegroundGps,
        NativeNavigationBridgeContract.stopForegroundGps,
      ],
    );
    expect(owner.ownsForegroundGps, isFalse);
  });

  test('dispose stops an owned service and permanently closes the owner',
      () async {
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return call.method == NativeNavigationBridgeContract.startForegroundGps
          ? true
          : null;
    });
    final owner = NativeNavigationOwnership(
      bridge: NativeNavigationBridge(channel: channel),
      enabled: true,
    );

    await owner.start();
    await owner.dispose();
    expect(await owner.start(), isFalse);
    await owner.dispose();

    expect(
      calls.map((call) => call.method),
      [
        NativeNavigationBridgeContract.startForegroundGps,
        NativeNavigationBridgeContract.stopForegroundGps,
      ],
    );
  });
}
