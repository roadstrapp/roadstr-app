import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// The one secure-storage handle every part of the app uses.
///
/// flutter_secure_storage defaults to `resetOnError: true`: when any read hits
/// a decryption error, it deletes the data and retries. A Keystore that is
/// merely busy or not yet unlocked right after boot is enough to trigger it,
/// and the casualties are the Nostr private key, the NWC wallet secret, the
/// favourites-sync passphrase and the key that encrypts the settings box.
/// For that last one the damage is the worst: the deleted key is replaced by a
/// fresh one, and the encrypted favourites and history become unreadable for
/// good, which is exactly what `_openEncryptedSettingsBox` promises never to
/// allow.
///
/// With reset off, an error surfaces as an exception that callers already
/// treat as "storage unavailable"; the data is still there on the next launch.
/// `migrateWithBackup` keeps a copy while the plugin converts data to a new
/// algorithm, so a crash mid-conversion does not lose it either.
///
/// Every other option is the plugin's default, so existing installations keep
/// reading what they wrote.
const FlutterSecureStorage appSecureStorage = FlutterSecureStorage(
  aOptions: AndroidOptions(
    resetOnError: false,
    migrateWithBackup: true,
  ),
);
