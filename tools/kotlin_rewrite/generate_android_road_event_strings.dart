import 'dart:convert';
import 'dart:io';

const _sourceDirectory = 'lib/l10n';
const _resourceDirectory = 'android/app/src/main/res';
const _outputName = 'native_road_event_strings.xml';

const _resourceKeys = <String, String>{
  'native_road_event_close': 'close',
  'native_road_event_cancel': 'cancel',
  'native_road_event_category_police': 'categoryPolice',
  'native_road_event_category_police_station': 'categoryPoliceStation',
  'native_road_event_category_speed_camera': 'categorySpeedCamera',
  'native_road_event_category_traffic_jam': 'categoryTrafficJam',
  'native_road_event_category_accident': 'categoryAccident',
  'native_road_event_category_road_closure': 'categoryRoadClosure',
  'native_road_event_category_construction': 'categoryConstruction',
  'native_road_event_category_hazard': 'categoryHazard',
  'native_road_event_category_road_condition': 'categoryRoadCondition',
  'native_road_event_category_pothole': 'categoryPothole',
  'native_road_event_category_fog': 'categoryFog',
  'native_road_event_category_ice': 'categoryIce',
  'native_road_event_category_animal': 'categoryAnimal',
  'native_road_event_category_other': 'categoryOther',
  'native_road_event_minutes_ago': 'minutesAgo',
  'native_road_event_hours_ago': 'hoursAgo',
  'native_road_event_days_ago': 'daysAgo',
  'native_road_event_reported_speed': 'reportedSpeedLimit',
  'native_road_event_edit_speed': 'editSpeedLimit',
  'native_road_event_request_speed': 'requestSpeedLimit',
  'native_road_event_accept_edit': 'acceptEditRequest',
  'native_road_event_pending_edits': 'pendingEditRequests',
  'native_road_event_nostrich': 'nostrichLabel',
  'native_road_event_still_there': 'stillThere',
  'native_road_event_not_there': 'notThereAnymore',
  'native_road_event_login_confirm': 'loginToConfirm',
  'native_road_event_zap': 'zapSendSats',
  'native_road_event_report_title': 'reportAnEvent',
  'native_road_event_report_speed': 'reportSpeedLimitHint',
  'native_road_event_optional_comment': 'optionalComment',
  'native_road_event_publish': 'publish',
  'native_road_event_publishing': 'publishing',
  'native_road_event_confirmed': 'confirmedLabel',
  'native_road_event_removed': 'removedLabel',
};

const _placeholders = <String, List<(String, String)>>{
  'minutesAgo': [('count', 'd')],
  'hoursAgo': [('count', 'd')],
  'daysAgo': [('count', 'd')],
};

const _privacyEnglish = <String, String>{
  'native_road_event_privacy_title': 'Public report',
  'native_road_event_privacy_body':
      'This report publishes its exact position, time, content and your public '
          'key to Nostr relays. It is pseudonymous, not anonymous, can be linked '
          'to your other reports, and relay deletion cannot be guaranteed.',
  'native_road_event_understand': 'I understand',
};

const _privacyItalian = <String, String>{
  'native_road_event_privacy_title': 'Report pubblico',
  'native_road_event_privacy_body':
      'Il report pubblicherà sui relay Nostr posizione esatta, orario, '
          'contenuto e chiave pubblica. È pseudonimo, non anonimo, può essere '
          'collegato agli altri tuoi report e la cancellazione dai relay non può '
          'essere garantita.',
  'native_road_event_understand': 'Ho capito',
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
      '<!-- Generated from Flutter road-event oracles. Do not edit. -->',
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
  final privacy = language == 'it' ? _privacyItalian : _privacyEnglish;
  for (final entry in privacy.entries) {
    output.writeln(
      '    <string name="${entry.key}">${_androidText(entry.value, entry.key)}</string>',
    );
  }
  output.writeln('</resources>');
  return output.toString();
}

Map<String, String> buildAndroidRoadEventResources() {
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
  for (final entry in buildAndroidRoadEventResources().entries) {
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
