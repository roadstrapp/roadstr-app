import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// The Kotlin app logs in through Amber or a remote signer (bunker, NIP-46) and never holds the account's
/// private key. These checks keep a private-key login from creeping back in unnoticed.
void main() {
  final production = Directory('android/app/src/main/kotlin')
      .listSync(recursive: true)
      .whereType<File>()
      .where((file) => file.path.endsWith('.kt'))
      .toList();
  final roadTest = Directory('native-android/app/src/main/kotlin')
      .listSync(recursive: true)
      .whereType<File>()
      .where((file) => file.path.endsWith('.kt'))
      .toList();
  String all(List<File> files) => files.map((f) => f.readAsStringSync()).join('\n');

  test('there is no private-key login and no signer that holds a key', () {
    final source = '${all(production)}\n${all(roadTest)}';

    expect(source, isNot(contains('loginNsec')));
    expect(source, isNot(contains('class NativeLocalKeySigner')));
    expect(source, isNot(contains('NativeProfileIdentityFlavor.Nsec')));
    expect(source, isNot(contains('onNsecLogin')));
  });

  test('the only identity kinds are Amber and bunker', () {
    final presentation = File(
      'android/app/src/main/kotlin/app/roadstr/feature/profile/NativeProfilePresentation.kt',
    ).readAsStringSync();
    final enumBody = RegExp(r'enum class NativeProfileIdentityFlavor[^{]*\{([\s\S]*?)companion')
        .firstMatch(presentation)!
        .group(1)!;

    expect(enumBody, contains('Amber("amber")'));
    expect(enumBody, contains('Bunker("bunker")'));
    expect(enumBody, isNot(contains('Nsec')));
  });

  test('a private key of an earlier build is erased, never written', () {
    final gateway = File(
      'native-android/app/src/main/kotlin/app/roadstr/roadtest/NativeRoadTestIdentityGateway.kt',
    ).readAsStringSync();

    expect(gateway, contains('dropPrivateKeyLogin()'));
    expect(gateway, isNot(contains('putString(PRIVATE_KEY')));
    expect(gateway, isNot(contains('fun privateKeyHex')));
  });

  test('the import never carries a private key over', () {
    final import = File(
      'android/app/src/main/kotlin/app/roadstr/startup/NativeProfileImport.kt',
    ).readAsStringSync();
    final targets = File(
      'android/app/src/main/kotlin/app/roadstr/startup/AndroidProfileImportTargets.kt',
    ).readAsStringSync();

    expect(import, isNot(contains('privateKeyHex')));
    expect(import, contains('loginDropped'));
    expect(targets, isNot(contains('privateKeyHex')));
    expect(targets, contains('noteLoginReset()'));
  });

  test('the bunker link is only taken with secure relays and the client key stays encrypted', () {
    final protocol = File(
      'android/app/src/main/kotlin/app/roadstr/core/protocol/nostr/Nip46.kt',
    ).readAsStringSync();
    final gateway = File(
      'native-android/app/src/main/kotlin/app/roadstr/roadtest/NativeRoadTestIdentityGateway.kt',
    ).readAsStringSync();

    expect(protocol, contains('startsWith("wss://")'));
    expect(gateway, contains('putString(BUNKER_CLIENT_KEY, encrypt(clientKey))'));
  });
}
