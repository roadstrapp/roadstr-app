import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

const _root = 'android/app/src/main/kotlin/app/roadstr';
const _locales = [
  'bg', 'cs', 'da', 'de', 'el', 'en', 'es', 'et', 'fi', 'fr', 'ga', 'hr', //
  'hu', 'it', 'ja', 'lt', 'lv', 'mt', 'nl', 'pl', 'pt', 'ro', 'ru', 'sk',
  'sl', 'sv', 'zh',
];

List<File> _kotlinFiles(String dir) => Directory('$_root/$dir')
    .listSync(recursive: true)
    .whereType<File>()
    .where((file) => file.path.endsWith('.kt'))
    .toList();

String _read(String path) => File(path).readAsStringSync();

void main() {
  final discoverySources = [
    ..._kotlinFiles('core/discovery'),
    ..._kotlinFiles('service/discovery'),
    ..._kotlinFiles('feature/discovery'),
  ];

  test('discovery code never logs, since it handles what the user typed', () {
    for (final file in discoverySources) {
      final source = file.readAsStringSync();
      expect(source, isNot(contains('Log.')), reason: file.path);
      expect(source, isNot(contains('println(')), reason: file.path);
      expect(source, isNot(contains('Timber')), reason: file.path);
    }
  });

  test('discovery talks only to OpenStreetMap services, never to Google', () {
    final hosts = RegExp(r'https?://([A-Za-z0-9.\-]+)');
    const allowed = {
      'nominatim.openstreetmap.org',
      'github.com',
    };
    for (final file in discoverySources) {
      if (file.path.contains('/lexicon/')) continue;
      final source = file.readAsStringSync();
      expect(source.toLowerCase(), isNot(contains('google')), reason: file.path);
      for (final match in hosts.allMatches(source)) {
        expect(allowed, contains(match.group(1)), reason: '${file.path} ${match.group(0)}');
      }
    }
  });

  test('values that carry a position or typed text print neither', () {
    for (final entry in {
      'core/discovery/RoadstrPlace.kt': 'override fun toString(): String = "RoadstrPlace(id=\$id)"',
      'core/discovery/NaturalPlaceQuery.kt': 'override fun toString(): String = "NaturalPlaceQuery(intent=\$intent)"',
      'core/discovery/DiscoveryOutcome.kt': 'override fun toString(): String = "DiscoveryRequest(intent=\${query.intent})"',
      'core/discovery/AreaGeocoding.kt': 'override fun toString(): String = "GeocodedArea(osm=\$osm)"',
    }.entries) {
      expect(_read('$_root/${entry.key}'), contains(entry.value), reason: entry.key);
    }
  });

  test('tags are filtered through a whitelist and queries are built from the catalogue', () {
    final policy = _read('$_root/core/discovery/RoadstrPlace.kt');
    expect(policy, contains('object PlaceTagPolicy'));
    expect(policy, isNot(contains('"fixme"')));
    final query = _read('$_root/core/discovery/OverpassDiscoveryQuery.kt');
    expect(query, isNot(contains('rawText')));
    expect(query, isNot(contains('residualTerms')));
    expect(query, contains('PlaceCategory'));
  });

  test('there is one vocabulary per language and every language is registered', () {
    final registry = _read('$_root/core/discovery/lexicon/LexiconRegistry.kt');
    for (final locale in _locales) {
      final name = 'Lexicon${locale[0].toUpperCase()}${locale[1]}';
      expect(File('$_root/core/discovery/lexicon/$name.kt').existsSync(), isTrue, reason: name);
      expect(registry, contains('"$locale" to $name.TEXT'), reason: locale);
    }
  });

  test('every search notice is translated into all 27 languages', () {
    const keys = [
      'native_search_notice_few_tagged',
      'native_search_notice_widened',
      'native_search_notice_route_unsupported',
      'native_search_notice_area_fallback',
      'native_search_notice_open_hours_unknown',
    ];
    for (final locale in _locales) {
      final dir = locale == 'en' ? 'values' : 'values-$locale';
      final xml = _read('android/app/src/main/res/$dir/native_nostr_strings.xml');
      for (final key in keys) {
        expect(xml, contains('name="$key"'), reason: '$dir $key');
      }
    }
  });

  test('the road-test build compiles the discovery service', () {
    expect(
      _read('native-android/app/build.gradle.kts'),
      contains('app/roadstr/service/discovery'),
    );
  });

  test('the shell pins discovery results and opens them like any place', () {
    final shell = _read('$_root/feature/home/NativeRoadstrShell.kt');
    expect(shell, contains('DiscoveryPresentation.pins(discovery.places)'));
    expect(shell, contains('is NativeMapInteraction.PlaceMarkerTap'));
    expect(shell, contains('showDiscoveryPlace'));
  });
}
