import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/poi_search_service.dart';

void main() {
  test('extracts useful bounded POI information', () {
    final details = OsmPoiDetails.fromOsmTags({
      'amenity': 'restaurant',
      'name': 'Trattoria Test',
      'description': 'Cucina locale\ncon terrazza',
      'cuisine': 'italian;pizza',
      'operator': 'Cooperativa Test',
      'wheelchair': 'limited',
      'contact:phone': '+39 0123 456789',
      'contact:email': 'info@example.test',
      'contact:website': 'https://example.test/menu',
      'addr:street': 'Via Roma',
      'addr:housenumber': '7',
      'addr:postcode': '00100',
      'addr:city': 'Roma',
    });

    expect(details, isNotNull);
    expect(details!.category, 'Restaurant');
    expect(details.description, 'Cucina locale con terrazza');
    expect(details.cuisine, 'Italian, Pizza');
    expect(details.wheelchair, 'limited');
    expect(details.address, 'Via Roma 7, 00100 Roma');
    expect(details.website, Uri.parse('https://example.test/menu'));
  });

  test('rejects unsafe contact data and non-POI tag sets', () {
    expect(OsmPoiDetails.fromOsmTags({'name': 'Just a name'}), isNull);

    final details = OsmPoiDetails.fromOsmTags({
      'shop': 'books',
      'website': 'http://example.test/',
      'email': 'not an email',
      'wheelchair': 'sometimes',
    });
    expect(details, isNotNull);
    expect(details!.website, isNull);
    expect(details.email, isNull);
    expect(details.wheelchair, isNull);
  });

  test('prefers localized names and caps untrusted text', () {
    final longDescription = List.filled(700, 'x').join();
    final details = OsmPoiDetails.fromOsmTags(
      {
        'tourism': 'attraction',
        'name': 'English name',
        'name:it': 'Nome italiano',
        'description': longDescription,
      },
      languageCode: 'it',
    );

    expect(details!.name, 'Nome italiano');
    expect(details.description!.length, 501);
    expect(details.description!.endsWith('…'), isTrue);
  });

  test('extracts Bitcoin and Lightning payment support plus restricted access',
      () {
    final details = OsmPoiDetails.fromOsmTags({
      'shop': 'bakery',
      'payment:bitcoin': 'yes',
      'payment:lightning': 'yes',
      'access': 'customers',
    });

    expect(details!.acceptsBitcoin, isTrue);
    expect(details.acceptsLightning, isTrue);
    expect(details.access, 'customers');
  });

  test('extracts parking details only in parking context', () {
    final parking = OsmPoiDetails.fromOsmTags({
      'amenity': 'parking',
      'parking': 'underground',
      'fee': 'yes',
      'charge': '2 EUR/hour',
      'capacity': '240',
      'maxstay': '3 hours',
    });
    expect(parking!.kind, OsmPoiKind.parking);
    expect(parking.parkingType, 'underground');
    expect(parking.fee, 'yes');
    expect(parking.charge, '2 EUR/hour');
    expect(parking.capacity, 240);
    expect(parking.maxStay, '3 hours');

    final shop = OsmPoiDetails.fromOsmTags({
      'shop': 'mall',
      'fee': 'yes',
      'capacity': '999',
      'parking': 'surface',
    });
    expect(shop!.kind, OsmPoiKind.other);
    expect(shop.fee, isNull);
    expect(shop.capacity, isNull);
    expect(shop.parkingType, isNull);
  });

  test('extracts EV connector counts and output safely', () {
    final details = OsmPoiDetails.fromOsmTags({
      'amenity': 'charging_station',
      'socket:type2': '4',
      'socket:type2:output': '22 kW',
      'socket:chademo': 'yes',
      'socket:chademo:output': '50 kW',
      'socket:type2_combo': 'no',
      'capacity': '6',
      'fee': 'no',
    });

    expect(details!.kind, OsmPoiKind.chargingStation);
    expect(details.capacity, 6);
    expect(details.evConnectors, hasLength(2));
    expect(details.evConnectors.first.type, 'type2');
    expect(details.evConnectors.first.count, 4);
    expect(details.evConnectors.first.output, '22 kW');
    expect(details.evConnectors.last.type, 'chademo');
    expect(details.evConnectors.last.count, isNull);
  });

  test('extracts contextual fuel, lodging and food amenities', () {
    final fuel = OsmPoiDetails.fromOsmTags({
      'amenity': 'fuel',
      'fuel:diesel': 'yes',
      'fuel:octane_95': 'yes',
    });
    expect(fuel!.fuels, {'diesel', 'octane_95'});

    final hotel = OsmPoiDetails.fromOsmTags({
      'tourism': 'hotel',
      'stars': '4S',
    });
    expect(hotel!.kind, OsmPoiKind.lodging);
    expect(hotel.stars, '4S');

    final cafe = OsmPoiDetails.fromOsmTags({
      'amenity': 'cafe',
      'smoking': 'outside',
      'outdoor_seating': 'yes',
      'takeaway': 'only',
    });
    expect(cafe!.kind, OsmPoiKind.foodAndDrink);
    expect(cafe.smoking, 'outside');
    expect(cafe.outdoorSeating, 'yes');
    expect(cafe.takeaway, 'only');
  });

  test('rejects malformed counts, unknown access and unsupported socket data',
      () {
    final details = OsmPoiDetails.fromOsmTags({
      'amenity': 'charging_station',
      'access': 'definitely_not_a_real_value',
      'capacity': '-4',
      'socket:type2': 'many',
      'socket:chademo': '0',
      'socket:type2_combo': '000',
    });

    expect(details!.access, isNull);
    expect(details.capacity, isNull);
    expect(details.evConnectors, isEmpty);
  });
}
