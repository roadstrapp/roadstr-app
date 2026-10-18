import 'dart:convert';
import 'dart:io';

const _catalogue = 'tools/kotlin_rewrite/native_speech_translations.json';
const _resourceDirectory = 'android/app/src/main/res';
const _outputName = 'native_speech_strings.xml';

/// Resource name suffix of every translated string, in the order they are written.
const speechStringKeys = <String>[
  'section',
  'title',
  'desc',
  'checking',
  'not_downloaded',
  'downloading',
  'ready',
  'unavailable',
  'download',
  'cancel',
  'delete',
  'privacy',
  'streets_privacy',
  'problem_network',
  'problem_storage',
  'problem_integrity',
  'problem_engine',
  'listening',
  'transcribing',
  'done',
  'stop',
  'nothing_heard',
];

/// The only string with a format argument: the size of the model in megabytes.
const speechFormattedKey = 'download';

String _resourceFolder(String language) =>
    language == 'en' ? 'values' : 'values-$language';

String _androidText(String source, String key) {
  final percents = '%'.allMatches(source).length;
  final expected = key == speechFormattedKey ? 1 : 0;
  if (percents != expected ||
      (expected == 1 && !source.contains(r'%1$d'))) {
    throw FormatException('Unexpected format specifier in $key: $source');
  }
  return source
      .replaceAll(r'\', r'\\')
      .replaceAll("'", r"\'")
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', r'\"');
}

String _buildResource(
  String language,
  Map<String, dynamic> strings,
  String attribution,
) {
  final output = StringBuffer()
    ..writeln('<?xml version="1.0" encoding="utf-8"?>')
    ..writeln(
      '<!-- Generated from tools/kotlin_rewrite/native_speech_translations.json. Do not edit. -->',
    )
    ..writeln('<resources>');
  for (final key in speechStringKeys) {
    final source = strings[key];
    if (source is! String || source.isEmpty) {
      throw FormatException('Missing $key for $language');
    }
    output.writeln(
      '    <string name="native_speech_$key">${_androidText(source, key)}</string>',
    );
  }
  if (language == 'en') {
    output
      ..writeln('    <!-- Licence text, deliberately not translated. -->')
      ..writeln(
        '    <string name="native_speech_attribution" translatable="false">${_androidText(attribution, 'attribution')}</string>',
      );
  }
  output.writeln('</resources>');
  return output.toString();
}

/// Every output file with its content, keyed by path.
Map<String, String> buildAndroidSpeechResources() {
  final decoded = jsonDecode(File(_catalogue).readAsStringSync());
  if (decoded is! Map<String, dynamic> || decoded['attribution'] is! String) {
    throw const FormatException('Invalid speech string catalogue');
  }
  final languages = decoded.keys.where((key) => key != 'attribution').toList();
  if (languages.length != 27) {
    throw StateError('Expected 27 languages, found ${languages.length}');
  }
  final outputs = <String, String>{};
  for (final language in languages) {
    final strings = decoded[language];
    if (strings is! Map<String, dynamic>) {
      throw FormatException('$language is not an object');
    }
    final extra = strings.keys.toSet().difference(speechStringKeys.toSet());
    if (extra.isNotEmpty) {
      throw FormatException('Unknown keys for $language: $extra');
    }
    final path =
        '$_resourceDirectory/${_resourceFolder(language)}/$_outputName';
    outputs[path] = _buildResource(
      language,
      strings,
      decoded['attribution'] as String,
    );
  }
  return outputs;
}

void main(List<String> arguments) {
  final check = arguments.contains('--check');
  var stale = false;
  for (final entry in buildAndroidSpeechResources().entries) {
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
