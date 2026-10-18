import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:amberflutter/models.dart';
import 'amberflutter_method_channel.dart';

abstract class AmberflutterPlatform extends PlatformInterface {
  /// Constructs a AmberflutterPlatform.
  AmberflutterPlatform() : super(token: _token);

  static final Object _token = Object();

  static AmberflutterPlatform _instance = MethodChannelAmberflutter();

  /// The default instance of [AmberflutterPlatform] to use.
  ///
  /// Defaults to [MethodChannelAmberflutter].
  static AmberflutterPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [AmberflutterPlatform] when
  /// they register themselves.
  static set instance(AmberflutterPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<bool> isAppInstalled() {
    throw UnimplementedError('isAppInstalled() has not been implemented.');
  }

  Future<Map<dynamic, dynamic>> getPublicKey({List<Permission>? permissions}) {
    throw UnimplementedError('getPublicKey() has not been implemented.');
  }

  Future<Map<dynamic, dynamic>> signEvent(
    String currentUser,
    String eventJson,
    String? id,
  ) {
    throw UnimplementedError('signEvent() has not been implemented.');
  }

  Future<Map<dynamic, dynamic>> nip04Encrypt(
    String plaintext,
    String currentUser,
    String pubKey,
    String? id,
  ) {
    throw UnimplementedError('nip04Encrypt() has not been implemented.');
  }

  Future<Map<dynamic, dynamic>> nip04Decrypt(
    String ciphertext,
    String currentUser,
    String pubKey,
    String? id,
  ) {
    throw UnimplementedError('nip04Decrypt() has not been implemented.');
  }

  Future<Map<dynamic, dynamic>> nip44Encrypt(
    String plaintext,
    String currentUser,
    String pubKey,
    String? id,
  ) {
    throw UnimplementedError('nip44Encrypt() has not been implemented.');
  }

  Future<Map<dynamic, dynamic>> nip44Decrypt(
    String ciphertext,
    String currentUser,
    String pubKey,
    String? id,
  ) {
    throw UnimplementedError('nip44Decrypt() has not been implemented.');
  }

  Future<Map<dynamic, dynamic>> decryptZapEvent(
    String eventJson,
    String currentUser,
    String? id,
  ) {
    throw UnimplementedError('decryptZapEvent() has not been implemented.');
  }
}
