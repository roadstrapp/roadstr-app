import 'dart:convert';
import 'dart:io';

const _sourceDirectory = 'lib/l10n';
const _resourceDirectory = 'android/app/src/main/res';
const _outputName = 'native_place_strings.xml';

const _resourceKeys = <String, String>{
  'native_place_loading': 'loadingInfo',
  'native_place_cancel': 'cancel',
  'native_place_navigate': 'navigateHere',
  'native_place_read_wikipedia': 'readOnWikipedia',
  'native_place_search_engine': 'searchOnEngine',
  'native_place_osm_details': 'poiDetailsFromOsm',
  'native_place_category': 'poiCategory',
  'native_place_operator': 'poiOperator',
  'native_place_cuisine': 'poiCuisine',
  'native_place_wheelchair_yes': 'poiWheelchairYes',
  'native_place_wheelchair_limited': 'poiWheelchairLimited',
  'native_place_wheelchair_no': 'poiWheelchairNo',
  'native_place_contact': 'poiContact',
  'native_place_address': 'poiAddress',
  'native_place_website': 'poiWebsite',
  'native_place_access_private': 'poiAccessPrivate',
  'native_place_access_customers': 'poiAccessCustomers',
  'native_place_access_permit': 'poiAccessPermit',
  'native_place_access_no': 'poiAccessNo',
  'native_place_access_destination': 'poiAccessDestination',
  'native_place_lightning': 'poiLightningAccepted',
  'native_place_bitcoin': 'poiBitcoinAccepted',
  'native_place_parking': 'poiParkingDetails',
  'native_place_parking_surface': 'poiParkingSurface',
  'native_place_parking_underground': 'poiParkingUnderground',
  'native_place_parking_multi_storey': 'poiParkingMultiStorey',
  'native_place_parking_street_side': 'poiParkingStreetSide',
  'native_place_parking_lane': 'poiParkingLane',
  'native_place_parking_rooftop': 'poiParkingRooftop',
  'native_place_fee': 'poiFee',
  'native_place_free': 'poiFree',
  'native_place_paid': 'poiPaid',
  'native_place_capacity': 'poiCapacity',
  'native_place_max_stay': 'poiMaxStay',
  'native_place_price': 'poiPrice',
  'native_place_charging': 'poiChargingDetails',
  'native_place_connector_type2': 'poiConnectorType2',
  'native_place_connector_chademo': 'poiConnectorChademo',
  'native_place_connector_ccs': 'poiConnectorCcs',
  'native_place_diesel': 'poiDiesel',
  'native_place_petrol95': 'poiPetrol95',
  'native_place_smoking_allowed': 'poiSmokingAllowed',
  'native_place_smoking_outside': 'poiSmokingOutside',
  'native_place_smoking_areas': 'poiSmokingAreas',
  'native_place_smoke_free': 'poiSmokeFree',
  'native_place_outdoor_seating': 'poiOutdoorSeating',
  'native_place_takeaway': 'poiTakeaway',
  'native_place_takeaway_only': 'poiTakeawayOnly',
  'native_place_open_now': 'poiOpenNow',
  'native_place_closed_now': 'poiClosedNow',
  'native_place_opens_at': 'poiOpensAt',
  'native_place_closes_at': 'poiClosesAt',
};

const _placeholders = <String, String>{
  'searchOnEngine': 'engine',
  'poiOpensAt': 'when',
  'poiClosesAt': 'when',
};

String _resourceFolder(String language) =>
    language == 'en' ? 'values' : 'values-$language';

String _androidText(String source, String sourceKey) {
  var value = source;
  final placeholder = _placeholders[sourceKey];
  if (placeholder != null) {
    value = value.replaceAll('{$placeholder}', '@@VALUE@@');
  }
  if (RegExp(r'\{[^}]+\}').hasMatch(value)) {
    throw FormatException('Unsupported placeholder in $sourceKey: $source');
  }
  value = value
      .replaceAll(r'\', r'\\')
      .replaceAll('%', '%%')
      .replaceAll("'", r"\'")
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll('\n', r'\n')
      .replaceAll('\r', r'\r')
      .replaceAll('\t', r'\t')
      .replaceAll('@@VALUE@@', r'%1$s');
  return value;
}

String _buildResource(String language, Map<String, dynamic> arb) {
  final output = StringBuffer()
    ..writeln('<?xml version="1.0" encoding="utf-8"?>')
    ..writeln(
      '<!-- Generated from lib/l10n/app_$language.arb. Do not edit. -->',
    )
    ..writeln('<resources>');
  for (final entry in _resourceKeys.entries) {
    final source = arb[entry.value];
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

Map<String, String> buildAndroidPlaceResources() {
  final sources = Directory(_sourceDirectory)
      .listSync()
      .whereType<File>()
      .where((file) => RegExp(r'app_[a-z]{2}\.arb$').hasMatch(file.path))
      .toList()
    ..sort((left, right) => left.path.compareTo(right.path));
  if (sources.length != 27) {
    throw StateError('Expected 27 ARB locale files, found ${sources.length}');
  }

  final outputs = <String, String>{};
  for (final source in sources) {
    final match = RegExp(r'app_([a-z]{2})\.arb$').firstMatch(source.path)!;
    final language = match.group(1)!;
    final decoded = jsonDecode(source.readAsStringSync());
    if (decoded is! Map<String, dynamic>) {
      throw FormatException('${source.path} is not a JSON object');
    }
    final path =
        '$_resourceDirectory/${_resourceFolder(language)}/$_outputName';
    outputs[path] = _buildResource(language, decoded);
  }
  return outputs;
}

void main(List<String> arguments) {
  final check = arguments.contains('--check');
  var stale = false;
  for (final entry in buildAndroidPlaceResources().entries) {
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
