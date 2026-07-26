import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/routing_service.dart';

/// [RoutingService.shortLabelFrom] builds the name shown in the search history.
/// The bug it exists for: Nominatim formats European addresses house-number
/// first, so the obvious `display_name.split(',').first` turned every history
/// entry into a bare "12" next to a pin.
void main() {
  test('street address keeps the street name, not the house number', () {
    final label = RoutingService.shortLabelFrom(
      '12, Via Attilio Monti, Ravenna, Emilia-Romagna, 48122, Italia',
      {
        'house_number': '12',
        'road': 'Via Attilio Monti',
        'city': 'Ravenna',
        'country': 'Italia',
      },
    );
    expect(label, 'Via Attilio Monti 12, Ravenna');
  });

  test('street without a house number', () {
    final label = RoutingService.shortLabelFrom(
      'Via Roma, Milano, Italia',
      {'road': 'Via Roma', 'city': 'Milano'},
    );
    expect(label, 'Via Roma, Milano');
  });

  test('a named POI wins over its street', () {
    final label = RoutingService.shortLabelFrom(
      'Ospedale Santa Maria delle Croci, Viale Randi, Ravenna, Italia',
      {'road': 'Viale Randi', 'city': 'Ravenna'},
      name: 'Ospedale Santa Maria delle Croci',
    );
    expect(label, 'Ospedale Santa Maria delle Croci, Ravenna');
  });

  test('POI whose name equals the town is not repeated', () {
    final label = RoutingService.shortLabelFrom(
      'Ravenna, Emilia-Romagna, Italia',
      {'city': 'Ravenna'},
      name: 'Ravenna',
    );
    expect(label, 'Ravenna');
  });

  test('no street and no name: first non-numeric component', () {
    final label = RoutingService.shortLabelFrom(
      '5, Piazza del Popolo, Ravenna, Italia',
      {'city': 'Ravenna'},
    );
    expect(label, 'Piazza del Popolo, Ravenna');
  });

  test('empty address block never throws', () {
    expect(RoutingService.shortLabelFrom('', const {}), '');
  });

  test('bounds and sanitises untrusted geocoder text', () {
    // The address block is third-party data that gets persisted in the history
    // box and rendered in one-line tiles.
    final long = 'A' * 500;
    final label = RoutingService.shortLabelFrom(
      'x',
      {'road': long, 'city': 'Ravenna'},
    );
    expect(label.length, lessThan(120));
    expect(label, endsWith(', Ravenna'));

    final controls = RoutingService.shortLabelFrom(
      'x',
      {'road': 'Via\u0000Roma\u001f', 'city': 'Milano'},
    );
    expect(controls, 'Via Roma, Milano');
  });

  test('non-string address components are ignored, not crashed on', () {
    expect(
      RoutingService.shortLabelFrom('Somewhere', {'road': 42, 'city': null}),
      'Somewhere',
    );
  });
}
