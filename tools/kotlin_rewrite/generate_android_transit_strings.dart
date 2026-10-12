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
  'native_transit_departures': 'transitDepartures',
  'native_transit_updated_at': 'transitUpdatedAt',
  'native_transit_no_departures': 'transitNoDepartures',
  'native_transit_cancelled': 'transitCancelled',
  'native_transit_nav_approach_stop': 'transitNavApproachStop',
  'native_transit_nav_waiting_to_board': 'transitNavWaitingToBoard',
  'native_transit_nav_on_board': 'transitNavOnBoard',
  'native_transit_nav_approaching_alight': 'transitNavApproachingAlight',
  'native_transit_nav_transfer_walk': 'transitNavTransferWalk',
  'native_transit_nav_waiting_connection': 'transitNavWaitingConnection',
  'native_transit_nav_final_walk': 'transitNavFinalWalk',
  'native_transit_nav_arrived': 'transitNavArrived',
  'native_transit_nav_suspended': 'transitNavSuspended',
  'native_transit_nav_data_unavailable': 'transitNavDataUnavailable',
  'native_transit_nav_missed': 'transitNavMissed',
  'native_transit_nav_direction': 'transitNavDirection',
  'native_transit_nav_next_stop': 'transitNavNextStop',
  'native_transit_nav_alight_at': 'transitNavAlightAt',
  'native_transit_nav_stops_remaining': 'transitNavStopsRemaining',
  'native_transit_nav_expected_at': 'transitNavExpectedAt',
  'native_transit_nav_platform': 'transitNavPlatform',
  'native_transit_nav_next_line': 'transitNavNextLine',
  'native_transit_connection_feasible': 'transitConnectionFeasible',
  'native_transit_connection_at_risk': 'transitConnectionAtRisk',
  'native_transit_connection_missed': 'transitConnectionMissed',
  'native_transit_connection_unknown': 'transitConnectionUnknown',
  'native_transit_nav_boarded': 'transitNavBoarded',
  'native_transit_nav_alighted': 'transitNavAlighted',
  'native_transit_nav_reached_stop': 'transitNavReachedStop',
  'native_transit_nav_missed_action': 'transitNavMissedAction',
  'native_transit_nav_verify': 'transitNavVerify',
  'native_transit_nav_end': 'transitNavEnd',
  'native_transit_voice_approach_stop': 'transitVoiceApproachStop',
  'native_transit_voice_prepare_board': 'transitVoicePrepareBoard',
  'native_transit_voice_take_line': 'transitVoiceTakeLine',
  'native_transit_voice_alight': 'transitVoiceAlight',
  'native_transit_voice_connection': 'transitVoiceConnection',
  'native_transit_voice_continue_walk': 'transitVoiceContinueWalk',
  'native_transit_voice_arrived': 'transitVoiceArrived',
  'native_saved_transit_title': 'savedTransitTitle',
  'native_saved_transit_empty': 'savedTransitEmpty',
  'native_saved_transit_save': 'savedTransitSave',
  'native_saved_transit_original': 'savedTransitOriginal',
  'native_saved_transit_updated': 'savedTransitUpdated',
  'native_saved_transit_needs_verification': 'savedTransitNeedsVerification',
  'native_saved_transit_unavailable': 'savedTransitUnavailable',
  'native_saved_transit_offline_warning': 'savedTransitOfflineWarning',
  'native_saved_transit_verify': 'savedTransitVerify',
  'native_saved_transit_view': 'savedTransitView',
  'native_saved_transit_delete': 'savedTransitDelete',
  'native_saved_transit_differences': 'savedTransitDifferences',
  'native_saved_transit_open_list': 'savedTransitOpenList',
};

String _resourceFolder(String language) =>
    language == 'en' ? 'values' : 'values-$language';

String _androidText(String source, String sourceKey) {
  var value = source;
  final placeholders = RegExp(r'\{[^}]+\}').allMatches(value).toList();
  for (var index = 0; index < placeholders.length; index += 1) {
    value = value.replaceFirst(placeholders[index].group(0)!, '@@ARG${index + 1}@@');
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
      .replaceAllMapped(
        RegExp(r'@@ARG(\d+)@@'),
        (match) => '%${match.group(1)}\$s',
      );
  if (sourceKey == 'transitTransfers' || sourceKey == 'transitNavStopsRemaining' ||
      sourceKey == 'savedTransitDifferences') {
    value = value.replaceFirst(r'%1$s', r'%1$d');
  }
  return value;
}

String _buildResource(
  String language,
  Map<String, dynamic> arb,
  Map<String, dynamic> english,
) {
  final output = StringBuffer()
    ..writeln('<?xml version="1.0" encoding="utf-8"?>')
    ..writeln(
        '<!-- Generated from lib/l10n/app_$language.arb. Do not edit. -->')
    ..writeln('<resources>');
  for (final entry in _resourceKeys.entries) {
    // Newly introduced native-only strings fall back to English until the
    // corresponding Flutter catalogue receives a reviewed translation.
    final source = arb[entry.value] ?? english[entry.value];
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
  final englishFile = sources.singleWhere((file) => file.path.endsWith('app_en.arb'));
  final english = jsonDecode(englishFile.readAsStringSync()) as Map<String, dynamic>;
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
