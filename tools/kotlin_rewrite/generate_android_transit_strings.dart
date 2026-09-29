import 'dart:convert';
import 'dart:io';

const _sourceDirectory = 'lib/l10n';
const _resourceDirectory = 'android/app/src/main/res';
const _outputName = 'native_transit_strings.xml';

const _resourceKeys = <String, String>{
  'native_cancel': 'cancel',
  'native_mode_car': 'modeCar',
  'native_mode_bike': 'modeBike',
  'native_mode_walk': 'modeWalk',
  'native_transport_mode_walk': 'transportModeWalk',
  'native_transport_mode_transit': 'transportModeTransit',
  'native_transit_no_service_here': 'transitNoServiceHere',
  'native_transit_request_failed': 'transitRequestFailed',
  'native_transit_retry': 'transitRetry',
  'native_transit_walk_leg': 'transitWalkLeg',
  'native_transit_your_location': 'transitYourLocation',
  'native_transit_destination': 'transitDestination',
  'native_transit_scheduled_times': 'transitScheduledTimes',
  'native_transit_direct': 'transitDirect',
  'native_transit_transfers': 'transitTransfers',
};

String _resourceFolder(String language) =>
    language == 'en' ? 'values' : 'values-$language';

String _androidText(String source, String sourceKey) {
  var value = source;
  if (sourceKey == 'transitTransfers') {
    value = value.replaceAll('{count}', '@@COUNT@@');
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
      .replaceAll('@@COUNT@@', '%1\$d');
  return value;
}

String _buildResource(String language, Map<String, dynamic> arb) {
  final output = StringBuffer()
    ..writeln('<?xml version="1.0" encoding="utf-8"?>')
    ..writeln(
        '<!-- Generated from lib/l10n/app_$language.arb. Do not edit. -->')
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

Map<String, String> buildAndroidTransitResources() {
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
  for (final entry in buildAndroidTransitResources().entries) {
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
