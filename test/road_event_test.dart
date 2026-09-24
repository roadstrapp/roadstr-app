import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/models/road_event.dart';

void main() {
  String repeated(String value, int count) => List.filled(count, value).join();

  Map<String, dynamic> event({
    List<List<String>>? tags,
    int? createdAt,
    String content = 'clear lane',
  }) =>
      {
        'id': repeated('a', 64),
        'pubkey': repeated('b', 64),
        'kind': 1315,
        'created_at': createdAt ??
            DateTime.now().millisecondsSinceEpoch ~/
                Duration.millisecondsPerSecond,
        'content': content,
        'tags': tags ??
            [
              ['lat', '41.9028'],
              ['lon', '12.4964'],
              ['t', 'hazard'],
            ],
      };

  test('accepts one valid coordinate and category tag', () {
    final parsed = RoadEvent.fromNostr(event());

    expect(parsed, isNotNull);
    expect(parsed!.category, RoadCategory.hazard);
    expect(parsed.position.latitude, 41.9028);
    expect(parsed.position.longitude, 12.4964);
  });

  test('rejects duplicate security-sensitive tags', () {
    final parsed = RoadEvent.fromNostr(event(tags: [
      ['lat', '41.9028'],
      ['lat', '0'],
      ['lon', '12.4964'],
      ['t', 'hazard'],
    ]));

    expect(parsed, isNull);
  });

  test('rejects invalid coordinates and far-future timestamps', () {
    expect(
      RoadEvent.fromNostr(event(tags: [
        ['lat', '91'],
        ['lon', '12.4964'],
        ['t', 'hazard'],
      ])),
      isNull,
    );

    final now =
        DateTime.now().millisecondsSinceEpoch ~/ Duration.millisecondsPerSecond;
    expect(RoadEvent.fromNostr(event(createdAt: now + 301)), isNull);
  });

  test('rejects expired events and bounds untrusted comments', () {
    final now =
        DateTime.now().millisecondsSinceEpoch ~/ Duration.millisecondsPerSecond;
    expect(
      RoadEvent.fromNostr(event(createdAt: now - 5 * 3600)),
      isNull,
    );

    final parsed = RoadEvent.fromNostr(event(content: repeated('x', 800)));
    expect(parsed, isNotNull);
    expect(parsed!.comment.length, 501);
    expect(parsed.comment.endsWith('…'), isTrue);
  });

  group('RoadCategory wire keys', () {
    test('every category survives a round trip through its own key', () {
      // A typo or a collision in nostrKey does not fail loudly: fromKey quietly
      // falls back to "other", so a report would just come back as the wrong
      // thing on every other client. This is the thing that catches it.
      for (final c in RoadCategory.values) {
        expect(RoadCategory.fromKey(c.nostrKey), c,
            reason: '${c.name} -> "${c.nostrKey}" did not come back as itself');
      }
    });

    test('no two categories share a key', () {
      final keys = RoadCategory.values.map((c) => c.nostrKey).toList();
      expect(keys.toSet(), hasLength(keys.length));
    });

    test('an unknown key still degrades to "other" instead of failing', () {
      // Also what an older client does with a category it predates — such as
      // police_station on anything before it existed.
      expect(RoadCategory.fromKey('something_from_the_future'),
          RoadCategory.other);
    });
  });

  group('police station', () {
    test('is its own category, not a patrol', () {
      expect(RoadCategory.policeStation.nostrKey, 'police_station');
      expect(RoadCategory.policeStation, isNot(RoadCategory.police));
      expect(RoadCategory.policeStation.emoji, isNot(RoadCategory.police.emoji));
    });

    test('lasts as long as a speed camera, unlike a patrol', () {
      // A building does not move. Treating it like the four-hour patrol would
      // have it vanish from the map before most people had passed it.
      expect(RoadCategory.policeStation.ttlSeconds,
          RoadCategory.speedCamera.ttlSeconds);
      expect(RoadCategory.policeStation.ttlSeconds,
          greaterThan(RoadCategory.police.ttlSeconds));
    });

    test('is still shown hours after a patrol report would have expired', () {
      final fiveHoursAgo = DateTime.now().millisecondsSinceEpoch ~/
              Duration.millisecondsPerSecond -
          5 * 3600;
      List<List<String>> tagsFor(String key) => [
            ['lat', '41.9028'],
            ['lon', '12.4964'],
            ['t', key],
          ];

      expect(
          RoadEvent.fromNostr(
              event(tags: tagsFor('police'), createdAt: fiveHoursAgo)),
          isNull);
      final station = RoadEvent.fromNostr(
          event(tags: tagsFor('police_station'), createdAt: fiveHoursAgo));
      expect(station, isNotNull);
      expect(station!.category, RoadCategory.policeStation);
    });
  });
}
