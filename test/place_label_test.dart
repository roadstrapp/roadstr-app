import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/routing_service.dart';

/// [RoutingService.shortLabelFrom] builds the name shown in the search history.
/// The bug it exists for: Nominatim formats European addresses house-number
/// first, so the obvious `display_name.split(',').first` turned every history
/// entry into a bare "12" next to a pin.
void main() {
  test('street address keeps the street name, not the house number', () {
    final label = RoutingService.shortLabelFrom(
      '12, Via Roberto Ricci, Torino, Piemonte, 10121, Italia',
      {
        'house_number': '12',
        'road': 'Via Roberto Ricci',
        'city': 'Torino',
        'country': 'Italia',
      },
    );
    expect(label, 'Via Roberto Ricci 12, Torino');
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
      'Ospedale Santa Maria delle Croci, Viale Randi, Torino, Italia',
      {'road': 'Viale Randi', 'city': 'Torino'},
      name: 'Ospedale Santa Maria delle Croci',
    );
    expect(label, 'Ospedale Santa Maria delle Croci, Torino');
  });

  test('POI whose name equals the town is not repeated', () {
    final label = RoutingService.shortLabelFrom(
      'Torino, Piemonte, Italia',
      {'city': 'Torino'},
      name: 'Torino',
    );
    expect(label, 'Torino');
  });

  test('no street and no name: first non-numeric component', () {
    final label = RoutingService.shortLabelFrom(
      '5, Piazza del Popolo, Torino, Italia',
      {'city': 'Torino'},
    );
    expect(label, 'Piazza del Popolo, Torino');
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
      {'road': long, 'city': 'Torino'},
    );
    expect(label.length, lessThan(120));
    expect(label, endsWith(', Torino'));

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
