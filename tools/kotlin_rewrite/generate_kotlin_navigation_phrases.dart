import 'dart:io';

import 'package:roadstr/services/nav_phrases.dart';

const outputPath =
    'android/app/src/main/kotlin/app/roadstr/core/navigation/NavigationPhrases.kt';

String buildKotlinNavigationPhrases() {
  final output = StringBuffer()
    ..writeln('package app.roadstr.core.navigation')
    ..writeln()
    ..writeln(
        '/** Generated from the production Dart navigation phrase table. */')
    ..writeln('internal object NavigationPhrases {')
    ..writeln(
        '    private val values: Map<String, Map<String, String>> = mapOf(');
  for (final language in navPhrases.entries) {
    output.writeln('        ${_quoted(language.key)} to mapOf(');
    for (final phrase in language.value.entries) {
      output.writeln(
        '            ${_quoted(phrase.key)} to ${_quoted(phrase.value)},',
      );
    }
    output.writeln('        ),');
  }
  output
    ..writeln('    )')
    ..writeln()
    ..writeln('    fun phrase(languageCode: String, key: String): String =')
    ..writeln('        values[languageCode]?.get(key) ?:')
    ..writeln('            values.getValue("en")[key].orEmpty()')
    ..writeln('}');
  return output.toString();
}

String _quoted(String value) {
  final escaped = value
      .replaceAll(r'\', r'\\')
      .replaceAll('"', r'\"')
      .replaceAll(r'$', r'\$')
      .replaceAll('\n', r'\n')
      .replaceAll('\r', r'\r')
      .replaceAll('\t', r'\t');
  return '"$escaped"';
}

void main(List<String> arguments) {
  final generated = buildKotlinNavigationPhrases();
  final output = File(outputPath);
  if (arguments.contains('--check')) {
    if (!output.existsSync() || output.readAsStringSync() != generated) {
      stderr.writeln('$outputPath is stale; regenerate it.');
      exitCode = 1;
    }
    return;
  }
  output.parent.createSync(recursive: true);
  output.writeAsStringSync(generated);
}
