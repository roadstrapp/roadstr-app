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
}
