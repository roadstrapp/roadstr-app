// Parser correctness for the common real-world opening_hours forms (sampled
// live from OSM). A wrong open/closed badge is worse than none, so the "unknown
// → show raw string" fallbacks are asserted too.
import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/opening_hours.dart';

void main() {
  // Monday 2024-01-01 is a Monday; use it as the reference week anchor.
  DateTime mon(int h, int m) => DateTime(2024, 1, 1, h, m); // Monday
  DateTime sat(int h, int m) => DateTime(2024, 1, 6, h, m); // Saturday
  DateTime sun(int h, int m) => DateTime(2024, 1, 7, h, m); // Sunday

  group('24/7', () {
    test('always open', () {
      expect(OpeningHours.evaluate('24/7', mon(3, 0)).state, OpenState.open);
      expect(OpeningHours.evaluate('24/7', sun(23, 59)).state, OpenState.open);
    });
  });

  group('common weekday+time', () {
    test('inside hours → open', () {
      final r = OpeningHours.evaluate('Mo-Fr 08:00-18:00', mon(10, 0));
      expect(r.state, OpenState.open);
      expect(r.nextChange, mon(18, 0)); // closes at 18:00
    });

    test('before opening → closed, opens today', () {
      final r = OpeningHours.evaluate('Mo-Fr 08:00-18:00', mon(7, 0));
      expect(r.state, OpenState.closed);
      expect(r.nextChange, mon(8, 0));
    });

    test('weekend excluded from Mo-Fr → closed, opens Monday', () {
      final r = OpeningHours.evaluate('Mo-Fr 08:00-18:00', sat(10, 0));
      expect(r.state, OpenState.closed);
      // Next Monday is 2024-01-08 08:00.
      expect(r.nextChange, DateTime(2024, 1, 8, 8, 0));
    });

    test('multiple spans (lunch break) → closed during the break', () {
      const oh = 'Mo-Fr 08:35-12:55, 14:10-16:15; Sa-Su off';
      expect(OpeningHours.evaluate(oh, mon(13, 0)).state, OpenState.closed);
      expect(OpeningHours.evaluate(oh, mon(9, 0)).state, OpenState.open);
      expect(OpeningHours.evaluate(oh, mon(15, 0)).state, OpenState.open);
    });

    test('holiday rule is unknown without a holiday calendar', () {
      const oh = 'Mo-Fr 08:20-19:05, Sa 08:20-12:35; Su,PH off';
      expect(OpeningHours.evaluate(oh, sat(9, 0)).state, OpenState.unknown);
    });

    test('day list Mo,We,Fr', () {
      const oh = 'Mo,We,Fr 09:00-17:00';
      expect(OpeningHours.evaluate(oh, mon(10, 0)).state, OpenState.open);
      // Tuesday 2024-01-02 → closed.
      expect(OpeningHours.evaluate(oh, DateTime(2024, 1, 2, 10, 0)).state,
          OpenState.closed);
    });
  });

  group('overnight spans', () {
    test('bar open past midnight', () {
      const oh = 'Mo-Sa 16:30-02:00, Su 15:00-01:00';
      // Monday 23:00 → open.
      expect(OpeningHours.evaluate(oh, mon(23, 0)).state, OpenState.open);
      // Tuesday 01:00 → still open (Monday's span bleeds into Tuesday).
      expect(OpeningHours.evaluate(oh, DateTime(2024, 1, 2, 1, 0)).state,
          OpenState.open);
      // Tuesday 03:00 → closed.
      expect(OpeningHours.evaluate(oh, DateTime(2024, 1, 2, 3, 0)).state,
          OpenState.closed);
    });
  });

  group('unknown → caller shows raw string', () {
    test('month-scoped', () {
      expect(
          OpeningHours.evaluate(
                  'Nov-Mar: 07:00-18:00; Apr-Oct: 07:00-19:00', mon(10, 0))
              .state,
          OpenState.unknown);
    });
    test('comment', () {
      expect(
          OpeningHours.evaluate(
                  'Mo-Fr 08:00-18:00 "by appointment"', mon(10, 0))
              .state,
          OpenState.unknown);
    });
    test('astronomical', () {
      expect(OpeningHours.evaluate('Mo-Su sunrise-sunset', mon(10, 0)).state,
          OpenState.unknown);
    });
    test('empty', () {
      expect(OpeningHours.evaluate('', mon(10, 0)).state, OpenState.unknown);
    });
    test('unsupported suffix is not partially accepted', () {
      expect(
          OpeningHours.evaluate('Mo-Fr 08:00-18:00 unknown', mon(10, 0)).state,
          OpenState.unknown);
    });
    test('invalid 24-hour time is unknown', () {
      expect(OpeningHours.evaluate('Mo-Fr 08:00-24:30', mon(10, 0)).state,
          OpenState.unknown);
      expect(OpeningHours.evaluate('Mo-Fr 24:00-01:00', mon(0, 30)).state,
          OpenState.unknown);
    });
  });

  test('overlapping intervals are merged before calculating close time', () {
    final result =
        OpeningHours.evaluate('Mo 08:00-12:00,10:00-18:00', mon(11, 0));
    expect(result.state, OpenState.open);
    expect(result.nextChange, mon(18, 0));
  });
}
