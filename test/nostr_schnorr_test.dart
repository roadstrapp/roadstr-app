import 'package:flutter_test/flutter_test.dart';

import 'package:roadstr/services/nostr_event_verify.dart';
import 'package:roadstr/services/nostr_protocol_codec.dart';
import 'package:roadstr/services/nostr_schnorr.dart';

void main() {
  const privateKey =
      '0000000000000000000000000000000000000000000000000000000000000003';
  const publicKey =
      'f9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9';
  const hash =
      '0000000000000000000000000000000000000000000000000000000000000000';
  const auxiliaryRandom =
      '0000000000000000000000000000000000000000000000000000000000000000';
  const officialSignature =
      'e907831f80848d1069a5371b402410364bdf1c5f8307b0084c55f1ce2dca8215'
      '25f66a4a85ea8b71e482a74f382d2ce5ebeee8fdb2172f477df4900d310536c0';

  test('derivation and deterministic signing reproduce BIP-340 vector zero',
      () {
    expect(NostrSchnorr.publicKey(privateKey), publicKey);
    expect(
      NostrSchnorr.signHashWithAux(privateKey, hash, auxiliaryRandom),
      officialSignature,
    );
    expect(
      NostrSchnorr.verifyHash(publicKey, hash, officialSignature),
      isTrue,
    );
  });

  test('production signing uses fresh auxiliary randomness', () {
    final first = NostrSchnorr.signHash(privateKey, hash);
    final second = NostrSchnorr.signHash(privateKey, hash);

    expect(first, isNot(second));
    expect(NostrSchnorr.verifyHash(publicKey, hash, first), isTrue);
    expect(NostrSchnorr.verifyHash(publicKey, hash, second), isTrue);
  });

  test('strict verifier returns false for malformed and tampered inputs', () {
    expect(NostrSchnorr.verifyHash('00', hash, officialSignature), isFalse);
    expect(
        NostrSchnorr.verifyHash(publicKey, '00', officialSignature), isFalse);
    expect(NostrSchnorr.verifyHash(publicKey, hash, '00'), isFalse);
    expect(
      NostrSchnorr.verifyHash(
        publicKey,
        'ff${hash.substring(2)}',
        officialSignature,
      ),
      isFalse,
    );
  });

  test('private scalar and deterministic inputs are validated', () {
    expect(
      () => NostrSchnorr.publicKey(List.filled(32, '00').join()),
      throwsArgumentError,
    );
    expect(
      () => NostrSchnorr.publicKey(
        'fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141',
      ),
      throwsArgumentError,
    );
    expect(
      () => NostrSchnorr.signHashWithAux(privateKey, '00', auxiliaryRandom),
      throwsArgumentError,
    );
  });

  test('production event gate verifies canonical id and signature together',
      () {
    final draft = NostrEventDraft(
      pubkey: publicKey,
      createdAt: 1700000000,
      kind: 1,
      tags: const [
        ['client', 'roadstr'],
      ],
      content: 'firma Nostr 🛣️',
    );
    final signature = NostrSchnorr.signHashWithAux(
      privateKey,
      draft.id,
      auxiliaryRandom,
    );
    final event = draft.toJson(signature: signature);

    expect(verifyEventJson(event), isTrue);
    expect(verifyEventJson({...event, 'content': 'tampered'}), isFalse);
    expect(
      verifyEventJson({...event, 'sig': List.filled(64, '00').join()}),
      isFalse,
    );
    expect(
      verifyEventJson({...event, 'id': List.filled(32, '00').join()}),
      isFalse,
    );
  });
}
