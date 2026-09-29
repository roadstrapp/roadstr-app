import 'dart:convert';
import 'dart:io';

const _sourceDirectory = 'lib/l10n';
const _resourceDirectory = 'android/app/src/main/res';
const _outputName = 'native_navigation_strings.xml';

const _resourceKeys = <String, String>{
  'native_nav_now': 'now',
  'native_nav_then': 'thenManeuver',
  'native_nav_arrival_ahead': 'arrivalAhead',
  'native_nav_arrival_left': 'arrivalAheadLeft',
  'native_nav_arrival_right': 'arrivalAheadRight',
  'native_nav_eta': 'etaArrivalLabel',
  'native_nav_duration_min': 'durationMin',
  'native_nav_duration_hour_min': 'durationHourMin',
  'native_nav_exit': 'navExitTitle',
  'native_nav_settings': 'openSettings',
  'native_nav_voice': 'voiceGuidance',
  'native_nav_speed_limit': 'speedLimitHint',
  'native_nav_altitude': 'showAltitude',
};

const _placeholders = <String, List<(String, String)>>{
  'thenManeuver': [('instruction', 's')],
  'etaArrivalLabel': [('time', 's')],
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

Map<String, String> buildAndroidNavigationResources() {
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
  for (final entry in buildAndroidNavigationResources().entries) {
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
