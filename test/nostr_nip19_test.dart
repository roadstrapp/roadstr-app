import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/nostr_nip19.dart';

import '../tools/kotlin_rewrite/generate_nostr_nip19_fixture.dart' as fixture;

const fixturePath = 'android/app/src/test/resources/parity/nostr_nip19_v1.tsv';

void main() {
  late List<List<String>> rows;

  setUpAll(() {
    rows = File(fixturePath)
        .readAsLinesSync()
        .where((line) => line.isNotEmpty && !line.startsWith('#'))
        .map((line) => line.split('\t'))
        .toList();
  });

  test('committed NIP-19 fixture is current and oracle-checked', () async {
    expect(
      File(fixturePath).readAsStringSync(),
      await fixture.buildNip19Fixture(),
    );
  });

  test('Dart codec reproduces every shared encode and decode case', () {
    expect(rows, hasLength(35));
    for (final fields in rows) {
      switch (fields[0]) {
        case 'encode':
          final hex = fields[3] == '-' ? '' : fields[3];
          String encode() => fields[2] == 'npub'
              ? NostrNip19.encodePublicKey(hex)
              : NostrNip19.encodePrivateKey(hex);
          if (fields[4] == 'reject') {
            expect(encode, throwsFormatException, reason: fields[1]);
          } else {
            expect(encode(), fields[4], reason: fields[1]);
          }
        case 'decode':
          if (fields[3] == 'reject') {
            expect(
              () => NostrNip19.decode(fields[2]),
              throwsFormatException,
              reason: fields[1],
            );
          } else {
            final decoded = NostrNip19.decode(fields[2]);
            expect(decoded.kind.hrp, fields[3], reason: fields[1]);
            expect(decoded.hex, fields[4], reason: fields[1]);
          }
      }
    }
  });

  test('typed decoders reject the other key type', () {
    final npubRow = rows.singleWhere((row) => row[1] == 'zero-npub');
    final nsecRow = rows.singleWhere((row) => row[1] == 'zero-nsec');

    expect(NostrNip19.decodePublicKey(npubRow[4]), '00' * 32);
    expect(NostrNip19.decodePrivateKey(nsecRow[4]), '00' * 32);
    expect(
      () => NostrNip19.decodePrivateKey(npubRow[4]),
      throwsFormatException,
    );
    expect(
      () => NostrNip19.decodePublicKey(nsecRow[4]),
      throwsFormatException,
    );
  });

  test('decode errors never echo possible secret input', () {
    const candidateSecret = 'nsec1this-must-never-appear-in-an-error';

    try {
      NostrNip19.decodePrivateKey(candidateSecret);
      fail('invalid nsec unexpectedly decoded');
    } on FormatException catch (error) {
      expect(error.toString(), isNot(contains(candidateSecret)));
    }
  });
}
