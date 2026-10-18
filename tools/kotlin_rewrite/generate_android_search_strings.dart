import 'dart:convert';
import 'dart:io';

const _sourceDirectory = 'lib/l10n';
const _resourceDirectory = 'android/app/src/main/res';
const _outputName = 'native_search_strings.xml';
const _featureStrings = 'tools/kotlin_rewrite/native_feature_strings.json';

const _resourceKeys = <String, String>{
  'native_search_hint': 'searchHint',
  'native_search_close': 'close',
  'native_search_history': 'history',
  'native_search_clear_history': 'clearHistory',
  'native_search_favorites': 'sectionFavorites',
  'native_search_nearby': 'nearbyTitle',
  'native_search_nearby_needs_gps': 'nearbyNeedsGps',
  'native_search_nearby_empty': 'nearbyNothingFound',
  'native_search_nearby_fuel': 'nearbyFuel',
  'native_search_nearby_restaurant': 'nearbyRestaurant',
  'native_search_nearby_supermarket': 'nearbySupermarket',
  'native_search_nearby_atm': 'nearbyAtm',
  'native_search_nearby_pharmacy': 'nearbyPharmacy',
  'native_search_nearby_hospital': 'nearbyHospital',
  'native_search_nearby_police': 'nearbyPolice',
  'native_search_nearby_post_office': 'nearbyPostOffice',
  'native_search_nearby_parking': 'nearbyParking',
  'native_search_nearby_hotel': 'nearbyHotel',
  'native_search_nearby_charging': 'nearbyCharging',
  'native_explore_area': 'exploreArea',
  'native_explore_categories': 'exploreCategories',
  'native_explore_all': 'exploreAll',
  'native_explore_food': 'exploreFood',
  'native_explore_culture': 'exploreCulture',
  'native_explore_shopping': 'exploreShopping',
  'native_explore_outdoor': 'exploreOutdoor',
  'native_explore_services': 'exploreServices',
  'native_explore_transport': 'exploreTransport',
  'native_explore_health': 'exploreHealth',
  'native_explore_sport': 'exploreSport',
  'native_explore_lodging': 'exploreLodging',
};

String _resourceFolder(String language) =>
    language == 'en' ? 'values' : 'values-$language';

String _androidText(String source, String sourceKey) {
  if (RegExp(r'\{[^}]+\}').hasMatch(source)) {
    throw FormatException('Unsupported placeholder in $sourceKey: $source');
  }
  return source
      .replaceAll(r'\', r'\\')
      .replaceAll('%', '%%')
      .replaceAll("'", r"\'")
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll('\n', r'\n')
      .replaceAll('\r', r'\r')
      .replaceAll('\t', r'\t');
}

String _buildResource(
  String language,
  Map<String, dynamic> arb,
  Map<String, dynamic> fallback,
) {
  final output = StringBuffer()
    ..writeln('<?xml version="1.0" encoding="utf-8"?>')
    ..writeln(
      '<!-- Generated from lib/l10n/app_$language.arb. Do not edit. -->',
    )
    ..writeln('<resources>');
  for (final entry in _resourceKeys.entries) {
    final source = arb[entry.value] ?? fallback[entry.value];
    if (source is! String || source.isEmpty) {
      throw FormatException('Missing ${entry.value} in app_$language.arb');
    }
    output.writeln(
      '    <string name="${entry.key}">${_androidText(source, entry.value)}</string>',
    );
  }
  output.writeln('</resources>');
  return output.toString();
}

Map<String, String> buildAndroidSearchResources() {
  final sources = Directory(_sourceDirectory)
      .listSync()
      .whereType<File>()
      .where((file) => RegExp(r'app_[a-z]{2}\.arb$').hasMatch(file.path))
      .toList()
    ..sort((left, right) => left.path.compareTo(right.path));
  if (sources.length != 27) {
    throw StateError('Expected 27 ARB locale files, found ${sources.length}');
  }
  final fallbackDecoded = jsonDecode(
    File('$_sourceDirectory/app_en.arb').readAsStringSync(),
  );
  if (fallbackDecoded is! Map<String, dynamic>) {
    throw const FormatException('app_en.arb is not a JSON object');
  }
  final featureDecoded = jsonDecode(File(_featureStrings).readAsStringSync());
  if (featureDecoded is! Map<String, dynamic> ||
      featureDecoded['en'] is! Map<String, dynamic>) {
    throw const FormatException('Invalid native feature string catalogue');
  }
  final fallback = <String, dynamic>{
    ...fallbackDecoded,
    ...(featureDecoded['en'] as Map<String, dynamic>),
  };

  final outputs = <String, String>{};
  for (final source in sources) {
    final match = RegExp(r'app_([a-z]{2})\.arb$').firstMatch(source.path)!;
    final language = match.group(1)!;
    final decoded = jsonDecode(source.readAsStringSync());
    if (decoded is! Map<String, dynamic>) {
      throw FormatException('${source.path} is not a JSON object');
    }
    final localizedFeatures = featureDecoded[language];
    final localized = <String, dynamic>{
      ...decoded,
      if (localizedFeatures is Map<String, dynamic>) ...localizedFeatures,
    };
    final path =
        '$_resourceDirectory/${_resourceFolder(language)}/$_outputName';
    outputs[path] = _buildResource(language, localized, fallback);
  }
  return outputs;
}

void main(List<String> arguments) {
  final check = arguments.contains('--check');
  var stale = false;
  for (final entry in buildAndroidSearchResources().entries) {
    final output = File(entry.key);
    if (check) {
      if (!output.existsSync() || output.readAsStringSync() != entry.value) {
        stderr.writeln('${entry.key} is stale; regenerate it.');
        stale = true;
      }
      continue;
    }
    output.parent.createSync(recursive: true);
    output.writeAsStringSync(entry.value);
  }
  if (stale) exitCode = 1;
}
