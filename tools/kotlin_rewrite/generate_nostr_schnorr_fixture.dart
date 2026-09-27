import 'dart:convert';
import 'dart:io';

import 'package:nostr_tools/nostr_tools.dart';
import 'package:roadstr/services/nostr_protocol_codec.dart';
import 'package:roadstr/services/nostr_schnorr.dart';

const outputPath = 'android/app/src/test/resources/parity/nostr_schnorr_v1.tsv';

// Official BIP-340 vectors, retrieved from bitcoin/bips bip-0340 on
// 2026-09-27. Upstream offers these under BSD-2-Clause, MIT or CC0-1.0.
// https://github.com/bitcoin/bips/blob/master/bip-0340/test-vectors.csv
const _officialVectors =
    r'''index,secret key,public key,aux_rand,message,signature,verification result,comment
0,0000000000000000000000000000000000000000000000000000000000000003,F9308A019258C31049344F85F89D5229B531C845836F99B08601F113BCE036F9,0000000000000000000000000000000000000000000000000000000000000000,0000000000000000000000000000000000000000000000000000000000000000,E907831F80848D1069A5371B402410364BDF1C5F8307B0084C55F1CE2DCA821525F66A4A85EA8B71E482A74F382D2CE5EBEEE8FDB2172F477DF4900D310536C0,TRUE,
1,B7E151628AED2A6ABF7158809CF4F3C762E7160F38B4DA56A784D9045190CFEF,DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659,0000000000000000000000000000000000000000000000000000000000000001,243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89,6896BD60EEAE296DB48A229FF71DFE071BDE413E6D43F917DC8DCF8C78DE33418906D11AC976ABCCB20B091292BFF4EA897EFCB639EA871CFA95F6DE339E4B0A,TRUE,
2,C90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B14E5C9,DD308AFEC5777E13121FA72B9CC1B7CC0139715309B086C960E18FD969774EB8,C87AA53824B4D7AE2EB035A2B5BBBCCC080E76CDC6D1692C4B0B62D798E6D906,7E2D58D8B3BCDF1ABADEC7829054F90DDA9805AAB56C77333024B9D0A508B75C,5831AAEED7B44BB74E5EAB94BA9D4294C49BCF2A60728D8B4C200F50DD313C1BAB745879A5AD954A72C45A91C3A51D3C7ADEA98D82F8481E0E1E03674A6F3FB7,TRUE,
3,0B432B2677937381AEF05BB02A66ECD012773062CF3FA2549E44F58ED2401710,25D1DFF95105F5253C4022F628A996AD3A0D95FBF21D468A1B33F8C160D8F517,FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF,FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF,7EB0509757E246F19449885651611CB965ECC1A187DD51B64FDA1EDC9637D5EC97582B9CB13DB3933705B32BA982AF5AF25FD78881EBB32771FC5922EFC66EA3,TRUE,test fails if msg is reduced modulo p or n
4,,D69C3509BB99E412E68B0FE8544E72837DFA30746D8BE2AA65975F29D22DC7B9,,4DF3C3F68FCC83B27E9D42C90431A72499F17875C81A599B566C9889B9696703,00000000000000000000003B78CE563F89A0ED9414F5AA28AD0D96D6795F9C6376AFB1548AF603B3EB45C9F8207DEE1060CB71C04E80F593060B07D28308D7F4,TRUE,
5,,EEFDEA4CDB677750A420FEE807EACF21EB9898AE79B9768766E4FAA04A2D4A34,,243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89,6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E17776969E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B,FALSE,public key not on the curve
6,,DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659,,243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89,FFF97BD5755EEEA420453A14355235D382F6472F8568A18B2F057A14602975563CC27944640AC607CD107AE10923D9EF7A73C643E166BE5EBEAFA34B1AC553E2,FALSE,has_even_y(R) is false
7,,DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659,,243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89,1FA62E331EDBC21C394792D2AB1100A7B432B013DF3F6FF4F99FCB33E0E1515F28890B3EDB6E7189B630448B515CE4F8622A954CFE545735AAEA5134FCCDB2BD,FALSE,negated message
8,,DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659,,243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89,6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E177769961764B3AA9B2FFCB6EF947B6887A226E8D7C93E00C5ED0C1834FF0D0C2E6DA6,FALSE,negated s value
9,,DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659,,243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89,0000000000000000000000000000000000000000000000000000000000000000123DDA8328AF9C23A94C1FEECFD123BA4FB73476F0D594DCB65C6425BD186051,FALSE,sG - eP is infinite
10,,DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659,,243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89,00000000000000000000000000000000000000000000000000000000000000017615FBAF5AE28864013C099742DEADB4DBA87F11AC6754F93780D5A1837CF197,FALSE,sG - eP is infinite
11,,DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659,,243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89,4A298DACAE57395A15D0795DDBFD1DCB564DA82B0F269BC70A74F8220429BA1D69E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B,FALSE,sig r is not an X coordinate
12,,DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659,,243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89,FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F69E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B,FALSE,r equals field size
13,,DFF1D77F2A671C5F36183726DB2341BE58FEAE1DA2DECED843240F7B502BA659,,243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89,6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E177769FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141,FALSE,s equals curve order
14,,FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC30,,243F6A8885A308D313198A2E03707344A4093822299F31D0082EFA98EC4E6C89,6CFF5C3BA86C69EA4B7376F31A9BCB4F74C1976089B2D9963DA2E5543E17776969E89B4C5564D00349106B8497785DD7D1D713A8AE82B32FA79D5F7FC407D39B,FALSE,public key exceeds field size
15,0340034003400340034003400340034003400340034003400340034003400340,778CAA53B4393AC467774D09497A87224BF9FAB6F6E68B23086497324D6FD117,0000000000000000000000000000000000000000000000000000000000000000,,71535DB165ECD9FBBC046E5FFAEA61186BB6AD436732FCCC25291A55895464CF6069CE26BF03466228F19A3A62DB8A649F2D560FAC652827D1AF0574E427AB63,TRUE,message of size 0
16,0340034003400340034003400340034003400340034003400340034003400340,778CAA53B4393AC467774D09497A87224BF9FAB6F6E68B23086497324D6FD117,0000000000000000000000000000000000000000000000000000000000000000,11,08A20A0AFEF64124649232E0693C583AB1B9934AE63B4C3511F3AE1134C6A303EA3173BFEA6683BD101FA5AA5DBC1996FE7CACFC5A577D33EC14564CEC2BACBF,TRUE,message of size 1
17,0340034003400340034003400340034003400340034003400340034003400340,778CAA53B4393AC467774D09497A87224BF9FAB6F6E68B23086497324D6FD117,0000000000000000000000000000000000000000000000000000000000000000,0102030405060708090A0B0C0D0E0F1011,5130F39A4059B43BC7CAC09A19ECE52B5D8699D1A71E3C52DA9AFDB6B50AC370C4A482B77BF960F8681540E25B6771ECE1E5A37FD80E5A51897C5566A97EA5A5,TRUE,message of size 17
18,0340034003400340034003400340034003400340034003400340034003400340,778CAA53B4393AC467774D09497A87224BF9FAB6F6E68B23086497324D6FD117,0000000000000000000000000000000000000000000000000000000000000000,99999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999,403B12B0D8555A344175EA7EC746566303321E5DBFA8BE6F091635163ECA79A8585ED3E3170807E7C03B720FC54C7B23897FCBA0E9D0B4A06894CFD249F22367,TRUE,message of size 100''';

String _field(String value) => value.isEmpty ? '-' : value.toLowerCase();

String _repeat(String value, int count) => List.filled(count, value).join();

String _text64(String value) =>
    base64Url.encode(utf8.encode(value)).replaceAll('=', '');

bool _legacyVerify(String publicKey, String message, String signature) {
  try {
    return Bip340Util.verify(publicKey, message, signature);
  } catch (_) {
    return false;
  }
}

String buildNostrSchnorrFixture() {
  final lines = <String>[
    '# Roadstr Nostr BIP-340 fixture v1.',
    '# Includes the 19 official bitcoin/bips vectors and deterministic NIP-01 events.',
    '# Empty official messages and absent fields use the - sentinel.',
    '# Generated by tools/kotlin_rewrite/generate_nostr_schnorr_fixture.dart.',
  ];

  final officialLines = const LineSplitter().convert(_officialVectors);
  for (final line in officialLines.skip(1)) {
    final fields = line.split(',');
    if (fields.length < 8) {
      throw StateError('Malformed embedded BIP-340 vector');
    }
    final index = fields[0];
    final secret = fields[1];
    final publicKey = fields[2];
    final auxiliaryRandom = fields[3];
    final message = fields[4];
    final signature = fields[5];
    final expected = fields[6] == 'TRUE';
    final comment = fields.sublist(7).join(',');

    final actual = _legacyVerify(publicKey, message, signature);
    if (actual != expected) {
      throw StateError('nostr_tools disagrees with official vector $index');
    }
    if (secret.isNotEmpty) {
      if (Bip340Util.getPublicKey(secret).toLowerCase() !=
          publicKey.toLowerCase()) {
        throw StateError('public-key derivation disagrees for vector $index');
      }
      if (Bip340Util.sign(secret, message, auxiliaryRandom).toLowerCase() !=
          signature.toLowerCase()) {
        throw StateError('signing disagrees for vector $index');
      }
    }
    lines.add([
      'bip340',
      index,
      _field(secret),
      publicKey.toLowerCase(),
      _field(auxiliaryRandom),
      _field(message),
      signature.toLowerCase(),
      '$expected',
      comment.isEmpty ? '-' : _text64(comment),
    ].join('\t'));
  }

  final privateKeys = [
    '${_repeat('00', 31)}01',
    '${_repeat('00', 31)}02',
    '${_repeat('00', 31)}03',
    'b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfef',
    'c90fdaa22168c234c4c6628b80dc1cd129024e088a67cc74020bbea63b14e5c9',
    '0340034003400340034003400340034003400340034003400340034003400340',
  ];
  final auxiliaryValues = List.generate(
    privateKeys.length,
    (index) => (index + 1).toRadixString(16).padLeft(64, '0'),
  );
  final publicKeys = privateKeys.map(NostrSchnorr.publicKey).toList();
  final drafts = <String, NostrEventDraft>{
    'report': RoadstrNostrEvents.report(
      pubkey: publicKeys[0],
      createdAt: 1700000000,
      latitude: 41.9028,
      longitude: 12.4964,
      category: 'hazard',
      expiresAt: 1700014400,
      content: 'ostacolo — corsia destra 🛣️',
    ),
    'vote': RoadstrNostrEvents.vote(
      pubkey: publicKeys[1],
      createdAt: 1700000001,
      eventId: _repeat('22', 32),
      stillThere: true,
    ),
    'update': RoadstrNostrEvents.update(
      ownerPubkey: publicKeys[2],
      createdAt: 1700000002,
      eventId: _repeat('33', 32),
      speedLimit: 90,
      latitude: -33.8688,
      longitude: 151.2093,
      content: 'limite verificato',
      requestId: _repeat('44', 32),
    ),
    'metadata': NostrEventDraft(
      pubkey: publicKeys[3],
      createdAt: 1700000003,
      kind: 0,
      tags: const [],
      content: jsonEncode({'name': 'Roadstr café', 'lud16': 'road@str.test'}),
    ),
    'nwc': NostrEventDraft(
      pubkey: publicKeys[4],
      createdAt: 1700000004,
      kind: 23194,
      tags: [
        ['p', _repeat('55', 32)],
      ],
      content: 'legacy-nip04-ciphertext?iv=test',
    ),
    'favorites': NostrEventDraft(
      pubkey: publicKeys[5],
      createdAt: 1700000005,
      kind: 30078,
      tags: const [
        ['d', 'roadstr-favorites'],
        ['client', 'roadstr'],
      ],
      content: 'Ag==',
    ),
  };
  final eventApi = EventApi();
  var eventIndex = 0;
  for (final entry in drafts.entries) {
    final privateKey = privateKeys[eventIndex];
    final auxiliaryRandom = auxiliaryValues[eventIndex];
    final draft = entry.value;
    final signature = NostrSchnorr.signHashWithAux(
      privateKey,
      draft.id,
      auxiliaryRandom,
    );
    final oracle = Event(
      id: draft.id,
      pubkey: draft.pubkey,
      created_at: draft.createdAt,
      kind: draft.kind,
      tags: draft.tags.map((tag) => tag.toList()).toList(),
      content: draft.content,
      sig: signature,
    );
    if (!eventApi.verifySignature(oracle) ||
        !NostrSchnorr.verifyHash(draft.pubkey, draft.id, signature)) {
      throw StateError('NIP-01 event signature failed for ${entry.key}');
    }
    lines.add([
      'nostr',
      entry.key,
      privateKey,
      auxiliaryRandom,
      draft.pubkey,
      draft.id,
      signature,
      _text64(draft.canonicalJson),
    ].join('\t'));
    eventIndex++;
  }

  final invalidPrivateKeys = <String, String>{
    'zero': _repeat('00', 32),
    'order': 'fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141',
    'above-order': _repeat('ff', 32),
    'short': '01',
    'non-hex': _repeat('gg', 32),
  };
  for (final entry in invalidPrivateKeys.entries) {
    try {
      NostrSchnorr.publicKey(entry.value);
      throw StateError('invalid private key accepted: ${entry.key}');
    } on ArgumentError {
      lines.add(['public_reject', entry.key, entry.value].join('\t'));
    }
  }

  final validPrivate = privateKeys.first;
  final validHash = drafts.values.first.id;
  final validAux = auxiliaryValues.first;
  final signingRejects = <(String, String, String, String)>[
    ('zero-private', _repeat('00', 32), validHash, validAux),
    ('short-hash', validPrivate, '00', validAux),
    ('non-hex-hash', validPrivate, _repeat('gg', 32), validAux),
    ('short-aux', validPrivate, validHash, '00'),
  ];
  for (final row in signingRejects) {
    try {
      NostrSchnorr.signHashWithAux(row.$2, row.$3, row.$4);
      throw StateError('invalid signing input accepted: ${row.$1}');
    } on ArgumentError {
      lines.add(['sign_reject', row.$1, row.$2, row.$3, row.$4].join('\t'));
    }
  }

  final validPublic = drafts.values.first.pubkey;
  final validSignature = NostrSchnorr.signHashWithAux(
    validPrivate,
    validHash,
    validAux,
  );
  final verificationCases = <(String, String, String, String, bool)>[
    ('valid', validPublic, validHash, validSignature, true),
    (
      'upper-case',
      validPublic.toUpperCase(),
      validHash.toUpperCase(),
      validSignature.toUpperCase(),
      true
    ),
    (
      'tampered-hash',
      validPublic,
      'ff${validHash.substring(2)}',
      validSignature,
      false
    ),
    (
      'tampered-signature',
      validPublic,
      validHash,
      'ff${validSignature.substring(2)}',
      false
    ),
    ('short-public', '00', validHash, validSignature, false),
    ('non-hex-public', _repeat('gg', 32), validHash, validSignature, false),
    ('short-hash', validPublic, '00', validSignature, false),
    ('short-signature', validPublic, validHash, '00', false),
    ('non-hex-signature', validPublic, validHash, _repeat('gg', 64), false),
  ];
  for (final row in verificationCases) {
    final actual = NostrSchnorr.verifyHash(row.$2, row.$3, row.$4);
    if (actual != row.$5) {
      throw StateError('strict verification disagrees for ${row.$1}');
    }
    lines.add([
      'verify_hash',
      row.$1,
      row.$2,
      row.$3,
      row.$4,
      '${row.$5}',
    ].join('\t'));
  }

  return '${lines.join('\n')}\n';
}

void main(List<String> arguments) {
  final generated = buildNostrSchnorrFixture();
  final output = File(outputPath);
  if (arguments.contains('--check')) {
    if (!output.existsSync() || output.readAsStringSync() != generated) {
      stderr.writeln('$outputPath is stale; regenerate it without --check.');
      exitCode = 1;
    }
    return;
  }
  output.writeAsStringSync(generated);
}
