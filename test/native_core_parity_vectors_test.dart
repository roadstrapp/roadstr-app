import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/bolt11_invoice.dart';
import 'package:roadstr/services/opening_hours.dart';
import 'package:roadstr/services/sun_calc.dart';
import 'package:roadstr/utils/fuzzy_match.dart';
import 'package:roadstr/utils/polyline.dart';

/// Locks committed values to the Flutter oracle and the Kotlin rewrite.
/// The Kotlin suite reads this exact same TSV resource.
void main() {
  test('Flutter and Kotlin share deterministic core vectors', () {
    final file = File('android/app/src/test/resources/parity/core_vectors.tsv');
    expect(file.existsSync(), isTrue);
    var executed = 0;
    for (final line in file.readAsLinesSync()) {
      if (line.trim().isEmpty || line.startsWith('#')) continue;
      final fields = line.split('\t');
      final id = fields[1];
      switch (fields[0]) {
        case 'sun':
          final result = SunCalc.sunTimes(
            double.parse(fields[2]),
            double.parse(fields[3]),
            // The vector is a calendar date in UTC. Making that explicit
            // avoids host-timezone drift in SunCalc's day-of-year contract.
            DateTime.parse('${fields[4]}T00:00:00Z'),
          );
          expect(result.rise?.millisecondsSinceEpoch, int.parse(fields[5]),
              reason: id);
          expect(result.set?.millisecondsSinceEpoch, int.parse(fields[6]),
              reason: id);
        case 'fuzzy':
          expect(FuzzyMatch.normalize(fields[2]), fields[3], reason: id);
        case 'opening':
          final result = OpeningHours.evaluate(
            fields[2],
            DateTime.parse(fields[3]),
          );
          expect(result.state.name, fields[4], reason: id);
          final expectedChange =
              fields[5] == '-' ? null : DateTime.parse(fields[5]);
          expect(result.nextChange, expectedChange, reason: id);
        case 'polyline':
          final points = decodePolyline(
            fields[2],
            precision: int.parse(fields[3]),
          );
          expect(points, hasLength(int.parse(fields[4])), reason: id);
          expect(points.first.latitude, closeTo(double.parse(fields[5]), 1e-7),
              reason: id);
          expect(points.first.longitude, closeTo(double.parse(fields[6]), 1e-7),
              reason: id);
          expect(points.last.latitude, closeTo(double.parse(fields[7]), 1e-7),
              reason: id);
          expect(points.last.longitude, closeTo(double.parse(fields[8]), 1e-7),
              reason: id);
        case 'bolt11':
          final invoice = Bolt11Invoice.tryParse(fields[2]);
          expect(invoice, isNotNull, reason: id);
          expect(invoice!.amountMsat, int.parse(fields[3]), reason: id);
          expect(invoice.createdAt, int.parse(fields[4]), reason: id);
          expect(invoice.expirySeconds, int.parse(fields[5]), reason: id);
          expect(invoice.preimageMatches(fields[6]), isTrue, reason: id);
          expect(invoice.descriptionMatches(fields[7]), isTrue, reason: id);
        default:
          fail('Unknown parity vector kind: ${fields[0]}');
      }
      executed++;
    }
    expect(executed, greaterThanOrEqualTo(10));
  });
}
