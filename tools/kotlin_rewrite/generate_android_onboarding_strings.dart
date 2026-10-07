import 'dart:convert';
import 'dart:io';

import 'native_recovery_translations.dart';

const _sourceDirectory = 'lib/l10n';
const _resourceDirectory = 'android/app/src/main/res';
const _outputName = 'native_onboarding_strings.xml';

const onboardingResourceKeys = <String, String>{
  'native_onboarding_app_subtitle': 'onboardingAppSubtitle',
  'native_onboarding_welcome_title': 'onboardingWelcomeTitle',
  'native_onboarding_welcome_body': 'onboardingWelcomeBody',
  'native_onboarding_feature_navigation': 'onboardingFeatureNav',
  'native_onboarding_feature_nostr': 'onboardingFeatureNostr',
  'native_onboarding_feature_lightning': 'onboardingFeatureLightning',
  'native_onboarding_feature_voice': 'onboardingFeatureVoice',
  'native_onboarding_feature_privacy': 'onboardingFeaturePrivacy',
  'native_onboarding_get_started': 'onboardingGetStarted',
  'native_onboarding_vpn_notice': 'onboardingVpnNotice',
  'native_onboarding_identity_title': 'onboardingNostrTitle',
  'native_onboarding_identity_subtitle': 'onboardingNostrSubtitle',
  'native_onboarding_identity_connected': 'onboardingNostrConnected',
  'native_onboarding_amber_title': 'onboardingAmberTitle',
  'native_onboarding_amber_subtitle': 'onboardingAmberSubtitle',
  'native_onboarding_amber_secure_hint': 'amberSecureMethodHint',
  'native_onboarding_login': 'loginButton',
  'native_onboarding_favorites_sync_notice': 'onboardingFavoritesSyncNotice',
  'native_onboarding_profile_visibility_title': 'profileVisibilityTitle',
  'native_onboarding_profile_visibility_description': 'profileVisibilityDesc',
  'native_onboarding_profile_visibility_clear': 'profileVisibilityClear',
  'native_onboarding_profile_visibility_pseudonymous':
      'profileVisibilityPseudonymous',
  'native_onboarding_profile_visibility_notice': 'profileVisibilityOnboarding',
  'native_onboarding_skip': 'onboardingSkip',
  'native_onboarding_continue': 'onboardingContinue',
  'native_onboarding_setup_title': 'onboardingSetupTitle',
  'native_onboarding_setup_subtitle': 'onboardingSetupSubtitle',
  'native_onboarding_location_title': 'onboardingLocationTitle',
  'native_onboarding_location_granted': 'onboardingLocationGranted',
  'native_onboarding_location_required': 'onboardingLocationRequired',
  'native_onboarding_grant': 'onboardingGrantButton',
  'native_onboarding_graphene_title': 'onboardingGrapheneTitle',
  'native_onboarding_graphene_body': 'onboardingGrapheneBody',
  'native_onboarding_voice_title': 'onboardingVoiceTitle',
  'native_onboarding_voice_ready': 'onboardingVoiceReady',
  'native_onboarding_voice_downloading': 'onboardingVoiceDownloading',
  'native_onboarding_voice_not_downloaded': 'onboardingVoiceNotDownloaded',
  'native_onboarding_voice_checking': 'onboardingVoiceChecking',
  'native_onboarding_download': 'onboardingDownloadButton',
  'native_onboarding_voice_later': 'onboardingVoiceLaterHint',
  'native_onboarding_ready_title': 'onboardingReadyTitle',
  'native_onboarding_ready_body': 'onboardingReadyBody',
  'native_onboarding_lets_go': 'onboardingLetsGo',
  'native_onboarding_disclosure_title': 'disclaimerTitle',
  'native_onboarding_disclosure_body': 'disclaimerBody',
  'native_onboarding_disclosure_accept': 'disclaimerAccept',
  'native_onboarding_loading': 'loadingLabel',
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
  for (final entry in onboardingResourceKeys.entries) {
    final source = arb[entry.value] ?? english[entry.value];
    if (source is! String || source.isEmpty) {
      throw FormatException(
          'Missing ${entry.value} in app_$language.arb and English fallback');
    }
    if (RegExp(r'\{[^}]+\}').hasMatch(source)) {
      throw FormatException(
          'Unsupported placeholder in ${entry.value}: $source');
    }
    output.writeln(
      '    <string name="${entry.key}">${_androidText(source)}</string>',
    );
  }
  // The startup screens are not in the Flutter ARB files; their 27 translations live in
  // native_recovery_translations.dart and every language must have all of them.
  final recovery = nativeRecoveryTranslations[language];
  if (recovery == null || recovery.length != nativeRecoveryKeys.length) {
    throw FormatException('Missing startup screen strings for $language');
  }
  for (var index = 0; index < nativeRecoveryKeys.length; index++) {
    output.writeln(
      '    <string name="${nativeRecoveryKeys[index]}">'
      '${_androidText(recovery[index])}</string>',
    );
  }
  output.writeln('</resources>');
  return output.toString();
}

Map<String, String> buildAndroidOnboardingResources() {
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
  for (final entry in buildAndroidOnboardingResources().entries) {
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
