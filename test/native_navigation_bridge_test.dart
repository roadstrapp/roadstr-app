import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/native_navigation_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel(NativeNavigationBridgeContract.channelName);
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
  });

  test('contract mirrors native opt-in commands and runtime state', () {
    expect(NativeNavigationBridgeContract.channelName,
        'app.roadstr/native_navigation');
    expect(NativeNavigationBridgeContract.startForegroundGps,
        'startForegroundGps');
    expect(
        NativeNavigationBridgeContract.stopForegroundGps, 'stopForegroundGps');
    expect(NativeNavigationBridgeContract.isForegroundGpsRunning,
        'isForegroundGpsRunning');
    expect(NativeNavigationBridgeContract.updateNavigationNotification,
        'updateNavigationNotification');
    expect(NativeNavigationBridgeContract.resetNavigationNotification,
        'resetNavigationNotification');
    expect(NativeNavigationBridgeContract.permissionDeniedCode,
        'permission_denied');
    expect(NativeNavigationBridgeContract.startFailedCode, 'start_failed');
    expect(NativeNavigationBridgeContract.invalidArgumentsCode,
        'invalid_arguments');
  });

  test('runtime query returns the native foreground GPS state', () async {
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return true;
    });
    const bridge = NativeNavigationBridge(channel: channel);

    expect(await bridge.isForegroundGpsRunning(), isTrue);
    expect(calls, hasLength(1));
    expect(calls.single.method,
        NativeNavigationBridgeContract.isForegroundGpsRunning);
    expect(calls.single.arguments, isNull);
  });

  test('start and stop send explicit calls without arguments', () async {
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return call.method == NativeNavigationBridgeContract.startForegroundGps;
    });
    const bridge = NativeNavigationBridge(channel: channel);

    expect(await bridge.startForegroundGps(), isTrue);
    await bridge.stopForegroundGps();

    expect(calls, hasLength(2));
    expect(calls[0].method, NativeNavigationBridgeContract.startForegroundGps);
    expect(calls[0].arguments, isNull);
    expect(calls[1].method, NativeNavigationBridgeContract.stopForegroundGps);
    expect(calls[1].arguments, isNull);
  });

  test('notification mirror sends bounded text and an explicit reset',
      () async {
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return true;
    });
    const bridge = NativeNavigationBridge(channel: channel);

    expect(
      await bridge.updateNavigationNotification(
        instruction: 'Turn right',
        distance: '200 m',
      ),
      isTrue,
    );
    expect(await bridge.resetNavigationNotification(), isTrue);

    expect(calls, hasLength(2));
    expect(calls.first.method,
        NativeNavigationBridgeContract.updateNavigationNotification);
    expect(
      calls.first.arguments,
      <String, String>{'instruction': 'Turn right', 'distance': '200 m'},
    );
    expect(calls.last.method,
        NativeNavigationBridgeContract.resetNavigationNotification);
    expect(calls.last.arguments, isNull);
  });

  test('native permission failures cross the boundary unchanged', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      throw PlatformException(
        code: NativeNavigationBridgeContract.permissionDeniedCode,
        message:
            'Location permission must be granted before starting native GPS',
      );
    });
    const bridge = NativeNavigationBridge(channel: channel);

    await expectLater(
      bridge.startForegroundGps(),
      throwsA(
        isA<PlatformException>().having(
          (error) => error.code,
          'code',
          NativeNavigationBridgeContract.permissionDeniedCode,
        ),
      ),
    );
  });
}
