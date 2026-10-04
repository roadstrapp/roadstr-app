import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/nostr_pending_report_queue.dart';
import 'package:roadstr/services/nostr_protocol_codec.dart';

import '../tools/kotlin_rewrite/generate_nostr_queue_fixture.dart' as fixture;

const queueFixturePath =
    'android/app/src/test/resources/parity/nostr_pending_queue_v1.tsv';
const nostrFixturePath =
    'android/app/src/test/resources/parity/nostr_protocol_v1.tsv';

String unb64(String value) {
  final padded = value.padRight((value.length + 3) ~/ 4 * 4, '=');
  return utf8.decode(base64Url.decode(padded));
}

List<String> csv(String value) => value == '-' ? const [] : value.split(',');

void main() {
  late List<List<String>> rows;

  setUpAll(() {
    rows = File(queueFixturePath)
        .readAsLinesSync()
        .where((line) => line.isNotEmpty && !line.startsWith('#'))
        .map((line) => line.split('\t'))
        .toList();
  });

  test('committed queue fixture is current', () async {
    expect(
      File(queueFixturePath).readAsStringSync(),
      await fixture.buildQueueFixture(),
    );
  });

  test('Dart queue policy reproduces every shared flush transcript', () async {
    for (final fields in rows.where((row) => row[0] == 'flush')) {
      final specs = fields[3] == '-'
          ? <List<String>>[]
          : fields[3].split(';').map((value) => value.split(',')).toList();
      final byId = {for (final spec in specs) spec[0]: spec};
      final result = await flushPendingRoadReports(
        pending: specs
            .map((spec) => PendingRoadReportStorage.entry(
                  {'id': spec[0]},
                  int.parse(spec[1]),
                ))
            .toList(),
        now: int.parse(fields[2]),
        verify: (event) => bool.parse(byId[event['id']]![2]),
        publish: (event) async {
          final outcome = byId[event['id']]![3];
          if (outcome == 'failure') throw Exception('relay rejected');
          if (outcome == 'throws') throw StateError('transport failed');
        },
      );

      expect(result.attemptedIds, csv(fields[4]), reason: fields[1]);
      expect(
        result.remaining
            .map((entry) => (entry['event'] as Map)['id'] as String),
        csv(fields[5]),
        reason: fields[1],
      );
      expect(
        result.decisions.map(
          (decision) => '${decision.id}=${decision.disposition.name}',
        ),
        csv(fields[6]),
        reason: fields[1],
      );
    }
  });

  test('storage JSON remains byte-compatible with signed event maps', () {
    final fields = rows.singleWhere((row) => row[0] == 'storage');
    final nostrRows = File(nostrFixturePath)
        .readAsLinesSync()
        .where((line) => line.isNotEmpty && !line.startsWith('#'))
        .map((line) => line.split('\t'))
        .toList();
    final report = nostrRows.singleWhere(
      (row) => row[0] == 'report' && row[1] == fields[2],
    );
    final draft = RoadstrNostrEvents.report(
      pubkey: report[2],
      createdAt: int.parse(report[3]),
      latitude: double.parse(report[4]),
      longitude: double.parse(report[5]),
      category: report[6],
      expiresAt: int.parse(report[7]),
      speedLimit: int.parse(report[8]),
      content: unb64(report[9]),
    );

    final encoded = PendingRoadReportStorage.encode([
      PendingRoadReportStorage.entry(
        draft.toJson(signature: fields[3]),
        int.parse(fields[4]),
      ),
    ]);

    expect(encoded, hasLength(1));
    expect(encoded.single, unb64(fields[5]));
  });

  test('storage decoder isolates corrupt and non-string Hive entries', () {
    final valid = jsonEncode({
      'event': {'id': 'valid'},
      'expiresAt': 1234,
    });

    final decoded = PendingRoadReportStorage.decode([
      'not json{',
      42,
      valid,
      jsonEncode(['wrong top-level shape']),
    ]);

    expect(decoded, hasLength(1));
    expect((decoded.single['event'] as Map)['id'], 'valid');
    expect(() => PendingRoadReportStorage.decode('not a Hive list'),
        throwsA(isA<TypeError>()));
  });

  test('flush is sequential and uses one captured clock', () async {
    final entries = [
      PendingRoadReportStorage.entry({'id': 'first'}, 101),
      PendingRoadReportStorage.entry({'id': 'second'}, 101),
    ];
    var active = 0;
    var maxActive = 0;
    final order = <String>[];

    final result = await flushPendingRoadReports(
      pending: entries,
      now: 100,
      verify: (_) => true,
      publish: (event) async {
        active++;
        if (active > maxActive) maxActive = active;
        order.add(event['id'] as String);
        await Future<void>.delayed(Duration.zero);
        active--;
      },
    );

    expect(maxActive, 1);
    expect(order, ['first', 'second']);
    expect(result.remaining, isEmpty);
    expect(() => result.remaining.add(entries.first), throwsUnsupportedError);
  });

  test('missing expiration and event are discarded before publish', () async {
    var verifies = 0;
    var publishes = 0;
    final result = await flushPendingRoadReports(
      pending: [
        {
          'event': {'id': 'missing-expiration'}
        },
        {'expiresAt': 200},
      ],
      now: 100,
      verify: (_) {
        verifies++;
        return true;
      },
      publish: (_) async => publishes++,
    );

    expect(verifies, 0);
    expect(publishes, 0);
    expect(result.remaining, isEmpty);
    expect(
      result.decisions.map((decision) => decision.disposition),
      [PendingReportDisposition.expired, PendingReportDisposition.invalid],
    );
  });

  test('a report queued while the flush awaited the relay survives the '
      'final write', () {
    Map<String, dynamic> entry(String id) => {
          'event': {'id': id},
          'expiresAt': 500,
        };
    final snapshot = [entry('published'), entry('retry')];
    // Hive as it stands when the flush finishes: the snapshot, plus one
    // report the driver filed while the relay was being awaited.
    final current = [...snapshot, entry('filed-during-flush')];

    final persisted = pendingReportsAfterFlush(
      snapshot: snapshot,
      current: current,
      remaining: [entry('retry')],
    );

    expect(
      persisted.map((e) => (e['event'] as Map)['id']),
      ['retry', 'filed-during-flush'],
    );
  });
}
