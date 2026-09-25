import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:nostr_tools/nostr_tools.dart';
import 'package:roadstr/models/road_event.dart';
import 'package:roadstr/services/nostr_event_verify.dart';
import 'package:roadstr/services/nostr_protocol_codec.dart';
import 'package:roadstr/services/nostr_relay_service.dart';

import '../tools/kotlin_rewrite/generate_nostr_protocol_fixture.dart'
    as fixture;

const fixturePath =
    'android/app/src/test/resources/parity/nostr_protocol_v1.tsv';

String unb64(String value) {
  final padded = value.padRight((value.length + 3) ~/ 4 * 4, '=');
  return utf8.decode(base64Url.decode(padded));
}

List<List<String>> decodeTags(String value) =>
    value.split(',').map((tag) => tag.split('.').map(unb64).toList()).toList();

void expectDraft(List<String> fields, NostrEventDraft draft, int suffixAt) {
  expect(draft.canonicalJson, unb64(fields[suffixAt]), reason: fields[1]);
  expect(draft.id, fields[suffixAt + 1], reason: fields[1]);
}

void main() {
  late List<List<String>> rows;

  setUpAll(() {
    rows = File(fixturePath)
        .readAsLinesSync()
        .where((line) => line.isNotEmpty && !line.startsWith('#'))
        .map((line) => line.split('\t'))
        .toList();
  });

  test('committed fixture is current and cross-checked with nostr_tools', () {
    expect(File(fixturePath).readAsStringSync(), fixture.buildFixture());
  });

  test('fixture locks every RoadCategory key and TTL', () {
    final expected = {
      for (final category in RoadCategory.values)
        category.name: (category.nostrKey, category.ttlSeconds),
    };
    final actual = {
      for (final row in rows.where((row) => row[0] == 'category'))
        row[1]: (row[2], int.parse(row[3])),
    };

    expect(actual, expected);
  });

  test('production Amber builders match every shared Roadstr vector', () {
    for (final fields in rows) {
      switch (fields[0]) {
        case 'report':
          final category = RoadCategory.values.singleWhere(
            (category) => category.nostrKey == fields[6],
          );
          final json = NostrRelayService.buildKind1315Map(
            position: LatLng(double.parse(fields[4]), double.parse(fields[5])),
            category: category,
            comment: unb64(fields[9]),
            pubKeyHex: fields[2],
            now: int.parse(fields[3]),
            expires: int.parse(fields[7]),
            speedLimit: fields[8] == '-' ? null : int.parse(fields[8]),
          );
          expectDraft(fields, nostrEventDraftFromJson(json), 10);
          expect(json['id'], fields[11], reason: fields[1]);
          expect(json['sig'], isEmpty, reason: fields[1]);
        case 'vote':
          final json = NostrRelayService.buildKind1316Map(
            eventId: fields[4],
            stillThere: bool.parse(fields[5]),
            pubKeyHex: fields[2],
            now: int.parse(fields[3]),
          );
          expectDraft(fields, nostrEventDraftFromJson(json), 6);
        case 'update':
          final json = NostrRelayService.buildKind1317Map(
            eventId: fields[4],
            ownerPubKeyHex: fields[2],
            speedLimit: int.parse(fields[5]),
            position: LatLng(double.parse(fields[6]), double.parse(fields[7])),
            comment: unb64(fields[9]),
            requestId: fields[8] == '-' ? null : fields[8],
            now: int.parse(fields[3]),
          );
          expectDraft(fields, nostrEventDraftFromJson(json), 10);
        case 'edit':
          final json = NostrRelayService.buildKind1318Map(
            eventId: fields[5],
            requesterPubKeyHex: fields[2],
            ownerPubKeyHex: fields[3],
            speedLimit: int.parse(fields[6]),
            position: LatLng(double.parse(fields[7]), double.parse(fields[8])),
            now: int.parse(fields[4]),
          );
          expectDraft(fields, nostrEventDraftFromJson(json), 9);
        case 'profile':
          final json = NostrRelayService.buildProfileVisibilityMap(
            pubKeyHex: fields[2],
            isPublic: bool.parse(fields[4]),
            now: int.parse(fields[3]),
          );
          expectDraft(fields, nostrEventDraftFromJson(json), 5);
      }
    }
  });

  test('generic vector locks JSON escaping and event-id bytes', () {
    final fields = rows.singleWhere((row) => row[0] == 'event');
    final draft = NostrEventDraft(
      pubkey: fields[2],
      createdAt: int.parse(fields[3]),
      kind: int.parse(fields[4]),
      tags: decodeTags(fields[5]),
      content: unb64(fields[6]),
    );

    expectDraft(fields, draft, 7);
  });

  test('relay EVENT, REQ and CLOSE messages match exact wire bytes', () {
    final published = rows.singleWhere(
      (row) => row[0] == 'report' && row[1] == 'report-speedCamera',
    );
    final draft = RoadstrNostrEvents.report(
      pubkey: published[2],
      createdAt: int.parse(published[3]),
      latitude: double.parse(published[4]),
      longitude: double.parse(published[5]),
      category: published[6],
      expiresAt: int.parse(published[7]),
      content: unb64(published[9]),
      speedLimit: int.parse(published[8]),
    );
    final publish = rows.singleWhere((row) => row[0] == 'wire-publish');
    expect(
      NostrRelayWire.encode(NostrRelayWire.publish(
        draft.toJson(signature: publish[3]),
      )),
      unb64(publish[4]),
    );

    final area = rows.singleWhere((row) => row[0] == 'wire-area');
    expect(
      NostrRelayWire.encode(NostrRelayWire.areaRequest(
        subscriptionId: area[2],
        geohashes: area[4].split(','),
        now: int.parse(area[3]),
      )),
      unb64(area[5]),
    );

    final confirmations =
        rows.singleWhere((row) => row[0] == 'wire-confirmations');
    expect(
      NostrRelayWire.encode(NostrRelayWire.confirmationRequest(
        subscriptionId: confirmations[2],
        eventIds: confirmations[4].split(','),
        now: int.parse(confirmations[3]),
      )),
      unb64(confirmations[5]),
    );

    final close = rows.singleWhere((row) => row[0] == 'wire-close');
    expect(
      NostrRelayWire.encode(NostrRelayWire.close(close[2])),
      unb64(close[3]),
    );
  });

  test('drafts and relay filters defensively copy caller-owned lists', () {
    final tags = <List<String>>[
      ['t', 'hazard'],
    ];
    final draft = NostrEventDraft(
      pubkey: List.filled(64, '1').join(),
      createdAt: 1700000000,
      kind: 1,
      tags: tags,
      content: '',
    );
    final geohashes = ['sr2y'];
    final request = NostrRelayWire.areaRequest(
      subscriptionId: 'sub',
      geohashes: geohashes,
      now: 1700000000,
    );

    tags.single[1] = 'changed';
    geohashes[0] = 'changed';

    expect(draft.tags.single, ['t', 'hazard']);
    expect((request[2] as Map)['#g'], ['sr2y']);
    expect(() => draft.tags.add(['x']), throwsUnsupportedError);
    expect(() => draft.tags.single.add('x'), throwsUnsupportedError);
  });

  test('Roadstr update builders reject out-of-range speed limits', () {
    NostrEventDraft build(int speed) => RoadstrNostrEvents.update(
          ownerPubkey: List.filled(64, '3').join(),
          createdAt: 1700000000,
          eventId: List.filled(64, '2').join(),
          speedLimit: speed,
          latitude: 0,
          longitude: 0,
          content: '',
        );

    expect(() => build(4), throwsFormatException);
    expect(() => build(301), throwsFormatException);
    expect(build(5).tags.last, ['maxspeed', '5']);
    expect(build(300).tags.last, ['maxspeed', '300']);
  });

  test('production verifier accepts the shared hash and rejects tampering', () {
    final signed = EventApi().finishEvent(
      Event(
        kind: 1316,
        tags: [
          ['e', List.filled(64, '2').join()],
          ['status', 'still_there'],
        ],
        content: '',
        created_at: 1700000000,
      ),
      List.filled(64, '1').join(),
    );
    final valid = signed.toJson();

    expect(verifyEventJson(valid), isTrue);
    expect(verifyEventJson({...valid, 'content': 'tampered'}), isFalse);
    expect(verifyEventJson({...valid, 'id': List.filled(64, '0').join()}),
        isFalse);
    expect(verifyEventJson({...valid, 'sig': 'not-a-signature'}), isFalse);
    expect(verifyEventJson(const {}), isFalse);
  });
}
