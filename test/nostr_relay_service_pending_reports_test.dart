import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hive/hive.dart';
import 'package:roadstr/services/nostr_relay_service.dart';

/// Covers the offline-report queue in isolation from the network: everything
/// [NostrRelayService.flushPendingReports] does before it would ever touch a
/// relay is deterministic and Hive-backed, so it belongs in a fast unit test
/// rather than behind a live WebSocket. What actually publishing a queued
/// report looks like is exercised by hand in the field, the same way every
/// other relay-touching path in this service is.
void main() {
  setUpAll(() async {
    Hive.init(
        (await Directory.systemTemp.createTemp('roadstr-pending-reports'))
            .path);
    await Hive.openBox('settings');
  });
  tearDownAll(Hive.close);
  setUp(() => Hive.box('settings').clear());

  int nowS() => DateTime.now().millisecondsSinceEpoch ~/ 1000;

  test('a queued report round-trips through Hive unchanged', () {
    final svc = NostrRelayService();
    final now = nowS();
    final event = {
      'id': 'abc123',
      'kind': 1315,
      'tags': [
        ['expiration', '${now + 3600}']
      ],
    };
    svc.debugQueuePendingReport(event, now + 3600);

    final pending = svc.debugPendingReports;
    expect(pending, hasLength(1));
    expect(pending.single['event'], event);
    expect(pending.single['expiresAt'], now + 3600);

    // Stored as its own JSON-encoded string per entry — same convention as
    // the 'favorites' list — not a nested map: Hive's box erases the
    // generic type of anything nested inside a stored List, so a bare
    // Map<String, dynamic> would read back as Map<dynamic, dynamic> and
    // risk the whole queue over one bad cast.
    final raw = Hive.box('settings').get('pending_road_reports') as List;
    expect(raw, everyElement(isA<String>()));
  });

  test('queueing twice keeps both entries, oldest first', () {
    final svc = NostrRelayService();
    final now = nowS();
    svc.debugQueuePendingReport({'id': 'first'}, now + 100);
    svc.debugQueuePendingReport({'id': 'second'}, now + 200);

    final pending = svc.debugPendingReports;
    expect(pending, hasLength(2));
    expect(pending[0]['event'], {'id': 'first'});
    expect(pending[1]['event'], {'id': 'second'});
  });

  test(
      'flushPendingReports drops a report whose TTL elapsed before a relay '
      'was reachable, without ever trying to publish it', () async {
    final svc = NostrRelayService();
    final now = nowS();
    // Expired one second ago: the exact "no signal for hours" case this
    // queue exists for, and exactly the report that must NOT go out late —
    // a "traffic jam" published as fresh when it is really hours old would
    // mislead whoever sees it.
    svc.debugQueuePendingReport({'id': 'stale'}, now - 1);

    await svc.flushPendingReports();

    expect(svc.debugPendingReports, isEmpty);
  });

  test('flushPendingReports treats a report expiring at this exact second as '
      'already stale', () async {
    final svc = NostrRelayService();
    final now = nowS();
    svc.debugQueuePendingReport({'id': 'boundary'}, now);

    await svc.flushPendingReports();

    expect(svc.debugPendingReports, isEmpty);
  });

  test('a corrupted queue entry is skipped rather than breaking every read',
      () {
    Hive.box('settings').put('pending_road_reports', <String>[
      'not valid json{',
      '{"event": {"id": "ok"}, "expiresAt": ${nowS() + 100}}',
    ]);
    final svc = NostrRelayService();

    final pending = svc.debugPendingReports;

    expect(pending, hasLength(1));
    expect(pending.single['event'], {'id': 'ok'});
  });

  test('an empty queue is a no-op — flushPendingReports never touches Hive '
      'when there is nothing to send', () async {
    final svc = NostrRelayService();
    await svc.flushPendingReports();
    expect(Hive.box('settings').containsKey('pending_road_reports'), isFalse);
  });
}
