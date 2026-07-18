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
}
