import 'package:flutter_secure_storage/flutter_secure_storage.dart';

typedef LegacySecureReadAll = Future<Map<String, String>> Function();

class LegacySecureStorageReadException implements Exception {
  const LegacySecureStorageReadException(this.message);

  final String message;

  @override
  String toString() => 'LegacySecureStorageReadException: $message';
}

/// Narrow production adapter around the exact plugin API used by the legacy
/// app.
///
/// `resetOnError` is deliberately false: the package's Dart default is true
/// and may delete protected values after a decryption error. Algorithm changes
/// remain enabled because supported older installations may need them, but use
/// the plugin's backup-protected migration path. This candidate must still be
/// exercised on controlled signed installations before startup wiring.
class LegacySecureStorageSource {
  LegacySecureStorageSource({required LegacySecureReadAll readAll})
      : _readAll = readAll;

  factory LegacySecureStorageSource.plugin() => LegacySecureStorageSource(
        readAll: _pluginStorage.readAll,
      );

  static const AndroidOptions androidOptions = AndroidOptions(
    resetOnError: false,
    migrateOnAlgorithmChange: true,
    migrateWithBackup: true,
  );

  static const FlutterSecureStorage _pluginStorage = FlutterSecureStorage(
    aOptions: androidOptions,
  );

  final LegacySecureReadAll _readAll;

  Future<Map<String, String>> readAll() async {
    try {
      final values = await _readAll();
      return Map.unmodifiable(values);
    } catch (_) {
      throw const LegacySecureStorageReadException(
        'Legacy secure storage is unavailable',
      );
    }
  }
}
