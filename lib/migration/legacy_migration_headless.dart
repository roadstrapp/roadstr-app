import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:path_provider/path_provider.dart';

import 'legacy_migration_bridge_reader.dart';
import 'legacy_secure_storage_source.dart';
import 'legacy_snapshot_envelope.dart';

abstract final class LegacyMigrationChannelContract {
  static const int protocolVersion = 1;
  static const String channelName = 'app.roadstr/legacy_migration';
  static const String readyMethod = 'bridgeReady';
  static const String readMethod = 'readLegacySnapshot';
  static const String entrypointLibrary = 'package:roadstr/main.dart';
  static const String entrypointFunction = 'legacyMigrationHeadlessMain';
  static const String failureCode = 'legacy_snapshot_unavailable';
}

typedef LegacyDocumentsDirectory = Future<Directory> Function();

/// One-shot channel handler for the isolated migration engine.
///
/// Every failure crosses the platform boundary as the same value-free error.
/// A second read is rejected so one engine can never observe two different
/// storage states.
class LegacyMigrationHeadlessHandler {
  LegacyMigrationHeadlessHandler({
    required LegacyDocumentsDirectory getDocumentsDirectory,
    required Future<Map<String, String>> Function() readSecureValues,
    LegacyMigrationBridgeReader reader = const LegacyMigrationBridgeReader(),
  })  : _getDocumentsDirectory = getDocumentsDirectory,
        _readSecureValues = readSecureValues,
        _reader = reader;

  factory LegacyMigrationHeadlessHandler.plugin() {
    final secureStorage = LegacySecureStorageSource.plugin();
    return LegacyMigrationHeadlessHandler(
      getDocumentsDirectory: getApplicationDocumentsDirectory,
      readSecureValues: secureStorage.readAll,
    );
  }

  final LegacyDocumentsDirectory _getDocumentsDirectory;
  final Future<Map<String, String>> Function() _readSecureValues;
  final LegacyMigrationBridgeReader _reader;
  bool _readStarted = false;

  Future<Object?> handle(MethodCall call) async {
    if (call.method != LegacyMigrationChannelContract.readMethod) {
      throw MissingPluginException('Unsupported legacy migration method');
    }
    if (call.arguments != LegacyMigrationChannelContract.protocolVersion ||
        _readStarted) {
      throw _neutralFailure();
    }
    _readStarted = true;

    try {
      final encoded = await _reader.readEncoded(
        documentsDirectory: await _getDocumentsDirectory(),
        readSecureValues: _readSecureValues,
      );
      if (encoded != null &&
          encoded.length > LegacyEnvelopeLimits.maxEnvelopeBytes) {
        throw const LegacyMigrationBridgeException(
          'Legacy snapshot exceeds the transport limit',
        );
      }
      return encoded == null ? null : Uint8List.fromList(encoded);
    } catch (_) {
      throw _neutralFailure();
    }
  }

  PlatformException _neutralFailure() => PlatformException(
        code: LegacyMigrationChannelContract.failureCode,
        message: 'Legacy migration snapshot is unavailable',
      );
}

/// Installs the Dart side before announcing readiness to the native launcher.
class LegacyMigrationHeadlessServer {
  LegacyMigrationHeadlessServer({
    required LegacyMigrationHeadlessHandler handler,
    MethodChannel channel = const MethodChannel(
      LegacyMigrationChannelContract.channelName,
    ),
  })  : _handler = handler,
        _channel = channel;

  final LegacyMigrationHeadlessHandler _handler;
  final MethodChannel _channel;
  bool _started = false;

  Future<void> start() async {
    if (_started) throw StateError('Legacy migration bridge already started');
    _started = true;
    _channel.setMethodCallHandler(_handler.handle);
    try {
      await _channel.invokeMethod<void>(
        LegacyMigrationChannelContract.readyMethod,
        LegacyMigrationChannelContract.protocolVersion,
      );
    } catch (_) {
      _channel.setMethodCallHandler(null);
      _started = false;
      rethrow;
    }
  }

  void stop() {
    _channel.setMethodCallHandler(null);
    _started = false;
  }
}

Future<void> startLegacyMigrationHeadlessServer() async {
  WidgetsFlutterBinding.ensureInitialized();
  await LegacyMigrationHeadlessServer(
    handler: LegacyMigrationHeadlessHandler.plugin(),
  ).start();
}
