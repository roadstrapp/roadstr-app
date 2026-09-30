import 'dart:convert';
import 'dart:io';

const _sourceDirectory = 'lib/l10n';
const _resourceDirectory = 'android/app/src/main/res';
const _outputName = 'native_route_planning_strings.xml';

const _resourceKeys = <String, String>{
  'native_route_choose': 'chooseRoute',
  'native_route_cancel': 'cancel',
  'native_route_start': 'startNavigation',
  'native_route_fastest': 'fastestRoute',
  'native_route_calculate': 'calculateRoute',
  'native_route_mode_car': 'modeCar',
  'native_route_mode_bike': 'modeBike',
  'native_route_mode_walk': 'modeWalk',
  'native_route_mode_transit': 'transportModeTransit',
  'native_route_from_hint': 'plannerFromHint',
  'native_route_to_hint': 'plannerToHint',
  'native_route_stop_hint': 'plannerStopHint',
  'native_route_add_stop': 'plannerAddStop',
  'native_route_my_location': 'myLocation',
  'native_route_loading': 'loadingLabel',
  'native_route_depart_eta': 'departEta',
  'native_route_conditions': 'conditionsOnRoute',
  'native_route_avoid_highways_tolls': 'avoidHighwaysAndTolls',
  'native_route_unavoidable_section': 'avoidanceUnavoidableSection',
  'native_route_avoid_unpaved': 'avoidUnpavedRoads',
  'native_route_duration_min': 'durationMin',
  'native_route_duration_hour_min': 'durationHourMin',
};

const _placeholders = <String, List<(String, String)>>{
  'departEta': [('dep', 's'), ('arr', 's')],
  'durationMin': [('m', 'd')],
  'durationHourMin': [('h', 'd'), ('m', 'd')],
};

String _resourceFolder(String language) =>
    language == 'en' ? 'values' : 'values-$language';

String _androidText(String source, String sourceKey) {
  var value = source;
  final placeholders = _placeholders[sourceKey] ?? const [];
  for (var index = 0; index < placeholders.length; index++) {
    value = value.replaceAll(
      '{${placeholders[index].$1}}',
      '@@VALUE${index + 1}@@',
    );
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
      .replaceAll('\t', r'\t');
  for (var index = 0; index < placeholders.length; index++) {
    value = value.replaceAll(
      '@@VALUE${index + 1}@@',
      '%${index + 1}\$${placeholders[index].$2}',
    );
  }
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

Map<String, String> buildAndroidRoutePlanningResources() {
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
  for (final entry in buildAndroidRoutePlanningResources().entries) {
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
