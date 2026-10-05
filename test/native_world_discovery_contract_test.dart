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

/// The code without block comments and whole-line comments, so an example address in a
/// KDoc is not mistaken for a place the app talks to.
String _code(String source) => source
    .replaceAll(RegExp(r'/\*[\s\S]*?\*/'), '')
    .split('\n')
    .where((line) => !line.trimLeft().startsWith('//'))
    .join('\n');

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
      // Lexicons are words; the web package and the host list have their own, stricter tests below.
      if (file.path.contains('/lexicon/') ||
          file.path.contains('/discovery/web/') ||
          file.path.endsWith('/resolve/HostMatching.kt')) {
        continue;
      }
      final source = file.readAsStringSync();
      expect(source.toLowerCase(), isNot(contains('google')), reason: file.path);
      for (final match in hosts.allMatches(_code(source))) {
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

  test('web search names Google only to keep it out, and has no address of its own', () {
    final hosts = RegExp(r'https?://([A-Za-z0-9.\-]+)');
    for (final file in _kotlinFiles('core/discovery/web')) {
      final name = file.path.split('/').last;
      final source = file.readAsStringSync();
      final mentionsGoogle = source.toLowerCase().contains('google');
      expect(
        mentionsGoogle,
        const {'SearchSourcePolicy.kt', 'WebDiscovery.kt'}.contains(name),
        reason: '$name may only mention Google where it is blocked',
      );
      // No instance, public list or engine address is built in.
      expect(hosts.allMatches(_code(source)), isEmpty, reason: name);
    }
    final policy = _read('$_root/core/discovery/web/SearchSourcePolicy.kt');
    expect(policy, contains('val DEFAULT_BLOCKED = listOf("google", "startpage")'));
  });

  test('linking results to places never trusts a listing site and talks only to Nominatim', () {
    final hostMatching = _read('$_root/core/discovery/resolve/HostMatching.kt');
    // Google is named here only as a listing site that identifies no business.
    for (final label in ['facebook', 'tripadvisor', 'yelp', 'google']) {
      expect(hostMatching, contains('"$label"'), reason: label);
    }
    expect(RegExp(r'https?://').hasMatch(_code(hostMatching)), isFalse);
    final hosts = RegExp(r'https?://([A-Za-z0-9.\-]+)');
    for (final file in _kotlinFiles('core/discovery/resolve')) {
      for (final match in hosts.allMatches(_code(file.readAsStringSync()))) {
        expect(match.group(1), 'nominatim.openstreetmap.org', reason: file.path);
      }
    }
    final resolver = _read('$_root/core/discovery/resolve/PlaceEntityResolver.kt');
    expect(resolver, contains('const val MAX_LOOKUPS = 3'));
    final evidence = _read('$_root/core/discovery/resolve/Evidence.kt');
    expect(evidence, contains('const val LINK_THRESHOLD = 0.8'));
    expect(evidence, contains('const val CANDIDATE_THRESHOLD = 0.5'));
    // Lookups go through the shared pacer and carry a name taken from a page title, not typed text.
    final lookup = _read('$_root/service/discovery/NativePlaceLookup.kt');
    expect(lookup, contains('pacer.paced'));
    expect(_read('native-android/app/src/main/kotlin/app/roadstr/roadtest/NativeRoadTestJourneyGateway.kt'),
        contains('NativePlaceLookup(transport, nominatimPacer)'));
  });

  test('web requests carry words and a language, never a position', () {
    for (final name in ['SearxngRequests', 'SearxngResponse', 'SearxngEndpoint', 'WebDiscovery']) {
      final source = _read('$_root/core/discovery/web/$name.kt').toLowerCase();
      for (final word in ['latitude', 'longitude', 'geopoint']) {
        expect(source, isNot(contains(word)), reason: '$name $word');
      }
    }
    for (final path in ['service/discovery/NativeSearxngProvider.kt', 'service/discovery/NativeLanHttpTransport.kt']) {
      final source = _read('$_root/$path').toLowerCase();
      expect(source, isNot(contains('latitude')), reason: path);
      expect(source, isNot(contains('longitude')), reason: path);
    }
    // The town name is the only trace of "near me", and it comes from a rounded cell.
    final locality = _read('$_root/service/discovery/CoarseLocality.kt');
    expect(locality, contains('AreaGeocoding.localityCell(point)'));
  });

  test('web search is off by default and its values never print the address', () {
    final settings = _read('$_root/core/discovery/web/WebDiscovery.kt');
    expect(settings, contains('val mode: WebDiscoveryMode = WebDiscoveryMode.OFF'));
    expect(settings, contains('override fun toString(): String = "WebDiscoverySettings(mode=\$mode)"'));
    expect(
      _read('$_root/core/discovery/web/SearxngEndpoint.kt'),
      contains('override fun toString(): String = "SearxngEndpoint(cleartext=\$cleartext)"'),
    );
  });

  test('plain http is only reachable for a local network the user marked as their own', () {
    final endpoint = _read('$_root/core/discovery/web/SearxngEndpoint.kt');
    expect(endpoint, contains('LOCAL_HTTP_NOT_CONFIRMED'));
    expect(endpoint, contains('!ownInstanceConfirmed -> reject(EndpointRejection.LOCAL_HTTP_NOT_CONFIRMED)'));
    expect(endpoint, contains('!isLocalNetwork(host) -> reject(EndpointRejection.NOT_HTTPS)'));
  });

  test('the web settings screen and its shell wiring exist and are stored encrypted', () {
    final panel = _read('$_root/feature/settings/NativeWebSearchPanel.kt');
    expect(panel, contains('fun NativeWebSearchPanel('));
    final shell = _read('$_root/feature/home/NativeRoadstrShell.kt');
    expect(shell, contains('NativeSettingsUiAction.OpenWebSearch -> webPanelVisible = true'));
    expect(shell, contains('webSettings = { webSettings }'));
    final activity = _read('native-android/app/src/main/kotlin/app/roadstr/roadtest/NativeRoadTestActivity.kt');
    expect(activity, contains('keyAlias = "app.roadstr.roadtest.web-search.v1"'));
    expect(activity, contains('WebDiscoverySettingsCodec.encode(settings)'));
    // The Flutter-compatible settings store stays closed; web search has its own model.
    final store = _read('$_root/feature/settings/NativeSettingsPresentation.kt');
    expect(store.toLowerCase(), isNot(contains('searxng')));
  });

  test('every web settings string is translated into all 27 languages', () {
    final keys = RegExp(r"'(native_websearch_[a-z_]+|native_search_web_[a-z_]+|native_settings_web_results(?:_desc)?)':")
        .allMatches(_read('tools/kotlin_rewrite/generate_android_nostr_strings.dart'))
        .map((match) => match.group(1)!)
        .toSet();
    expect(keys, contains('native_search_web_consent_town'));
    expect(keys.length, greaterThanOrEqualTo(40));
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
