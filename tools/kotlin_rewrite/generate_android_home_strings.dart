import 'dart:convert';
import 'dart:io';

const _sourceDirectory = 'lib/l10n';
const _resourceDirectory = 'android/app/src/main/res';
const _outputName = 'native_home_strings.xml';

const homeResourceKeys = <String, String>{
  'native_home_notifications': 'bottomBarNotifications',
  'native_home_profile': 'bottomBarProfile',
  'native_home_menu': 'bottomBarMenu',
  'native_home_ready': 'homeReadyToGo',
  'native_home_navigate': 'homeNavigate',
  'native_home_parking': 'homeParking',
  'native_home_activity': 'homeActivity',
  'native_home_events': 'homeEvents',
  'native_home_saved_places': 'homeSavedPlaces',
  'native_home_my_location': 'myLocation',
};

String _resourceFolder(String language) =>
    language == 'en' ? 'values' : 'values-$language';

String _androidText(String source) => source
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

String _buildResource(
  String language,
  Map<String, dynamic> arb,
  Map<String, dynamic> english,
) {
  final output = StringBuffer()
    ..writeln('<?xml version="1.0" encoding="utf-8"?>')
    ..writeln(
      '<!-- Generated from lib/l10n/app_$language.arb. Do not edit. -->',
    )
    ..writeln('<resources>');
  for (final entry in homeResourceKeys.entries) {
    final source = arb[entry.value] ?? english[entry.value];
    if (source is! String || source.isEmpty) {
      throw FormatException(
        'Missing ${entry.value} in app_$language.arb and English fallback',
      );
    }
    if (RegExp(r'\{[^}]+\}').hasMatch(source)) {
      throw FormatException(
        'Unsupported placeholder in ${entry.value}: $source',
      );
    }
    output.writeln(
      '    <string name="${entry.key}">${_androidText(source)}</string>',
    );
  }
  output.writeln('</resources>');
  return output.toString();
}

Map<String, String> buildAndroidHomeResources() {
  final sources = Directory(_sourceDirectory)
      .listSync()
      .whereType<File>()
      .where((file) => RegExp(r'app_[a-z]{2}\.arb$').hasMatch(file.path))
      .toList()
    ..sort((left, right) => left.path.compareTo(right.path));
  if (sources.length != 27) {
    throw StateError('Expected 27 ARB locale files, found ${sources.length}');
  }
  final english =
      jsonDecode(File('$_sourceDirectory/app_en.arb').readAsStringSync());
  if (english is! Map<String, dynamic>) {
    throw const FormatException('app_en.arb is not a JSON object');
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
    outputs[path] = _buildResource(language, decoded, english);
  }
  return outputs;
}

void main(List<String> arguments) {
  final check = arguments.contains('--check');
  var stale = false;
  for (final entry in buildAndroidHomeResources().entries) {
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
