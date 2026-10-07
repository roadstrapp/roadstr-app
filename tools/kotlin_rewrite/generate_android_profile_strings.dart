import 'dart:convert';
import 'dart:io';

const _sourceDirectory = 'lib/l10n';
const _resourceDirectory = 'android/app/src/main/res';
const _outputName = 'native_profile_strings.xml';

const _resourceKeys = <String, String>{
  'native_profile_title': 'profileTitle',
  'native_profile_visibility_title': 'profileVisibilityTitle',
  'native_profile_visibility_description': 'profileVisibilityDesc',
  'native_profile_visibility_clear': 'profileVisibilityClear',
  'native_profile_visibility_pseudonymous': 'profileVisibilityPseudonymous',
  'native_profile_nostrich': 'nostrichLabel',
  'native_profile_hidden_notice': 'profileHiddenNotice',
  'native_profile_not_connected': 'notConnected',
  'native_profile_login_title': 'loginWithNostrTitle',
  'native_profile_amber_title': 'amberNip55Title',
  'native_profile_amber_description': 'amberLoginDescription',
  'native_profile_connected_amber': 'connectedViaAmber',
  'native_profile_public_key': 'publicKeyLabel',
  'native_profile_npub_copied': 'npubCopiedToClipboard',
  'native_profile_logout': 'logoutButton',
  'native_profile_identity_info': 'nostrIdentityInfo',
  'native_profile_waiting_amber': 'waitingForAmberResponse',
  'native_profile_reports': 'myReports',
  'native_profile_no_reports': 'noReportsYet',
  'native_profile_zap_balance': 'zapBalance',
  'native_profile_zap_subtitle': 'satoshiFromReports',
  'native_profile_reputation_high': 'reputationHigh',
  'native_profile_reputation_medium': 'reputationMedium',
  'native_profile_reputation_low': 'reputationLow',
  'native_profile_reputation': 'reputationLabel',
  'native_profile_close': 'close',
  'native_profile_loading': 'loadingLabel',
};

const _placeholders = <String, List<(String, String)>>{
  'reputationLabel': [('level', 's')],
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

Map<String, String> buildAndroidProfileResources() {
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
  for (final entry in buildAndroidProfileResources().entries) {
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
