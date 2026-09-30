import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_road_event_strings.dart';

void main() {
  final presentation = File(
    'android/app/src/main/kotlin/app/roadstr/feature/report/'
    'NativeRoadEventPresentation.kt',
  );
  final panels = File(
    'android/app/src/main/kotlin/app/roadstr/feature/report/'
    'NativeRoadEventPanels.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 road-event resource sets are current and complete', () {
    final generated = buildAndroidRoadEventResources();

    expect(generated, hasLength(27));
    expect(
      generated.keys.where((path) => path.contains('/values/')),
      hasLength(1),
    );
    for (final entry in generated.entries) {
      final resource = File(entry.key);
      expect(resource.existsSync(), isTrue, reason: entry.key);
      expect(resource.readAsStringSync(), entry.value, reason: entry.key);
      final strings = RegExp(
        r'<string name="([^"]+)">([\s\S]*?)</string>',
      ).allMatches(entry.value).toList();
      expect(strings, hasLength(39), reason: entry.key);
      expect(strings.map((match) => match.group(1)).toSet(), hasLength(39));
    }
  });

  test('native projection preserves Flutter category and report bounds', () {
    final model = File('lib/models/road_event.dart').readAsStringSync();
    final flutter = File(
      'lib/widgets/sheets/road_event_sheets.dart',
    ).readAsStringSync();
    final kotlin = presentation.readAsStringSync();

    expect(model, contains('enum RoadCategory'));
    expect(model, contains('if (comment.length > 500)'));
    expect(flutter, contains('children: RoadCategory.values.map((cat)'));
    expect(flutter, contains('maxLength: 200'));
    expect(flutter, contains('raw <= 0 || raw > 300'));
    expect(flutter, contains('(raw * 1.60934).round()'));
    expect(kotlin, contains('const val MAX_RECEIVED_COMMENT = 500'));
    expect(kotlin, contains('const val MAX_REPORT_COMMENT = 200'));
    expect(panels.readAsStringSync(), contains('RoadCategoryWire.entries'));
    expect(kotlin, contains('(it * 1.60934).roundToInt()'));
    expect(
      kotlin,
      contains('safeClientExpiration(nowSeconds, category.ttlSeconds)'),
    );
  });

  test('Compose panels remain bounded accessible and side-effect free', () {
    final source = panels.readAsStringSync();
    final state = presentation.readAsStringSync();

    expect(source, contains('heightIn(max = maxHeight * 0.84f)'));
    expect(source, contains('verticalScroll(rememberScrollState())'));
    expect(source, contains('navigationBarsPadding()'));
    expect(source, contains('paneTitle = title'));
    expect(source, contains('Role.RadioButton'));
    expect(source, contains('.sizeIn(minWidth = 48.dp, minHeight = 48.dp)'));
    expect(source, contains('onVote: (Long, Boolean) -> Unit'));
    expect(source, contains('onZap: (Long) -> Unit'));
    expect(source, isNot(contains('NostrRelayService')));
    expect(source, isNot(contains('ZapService')));
    expect(source, isNot(contains('FlutterSecureStorage')));
    expect(source, isNot(contains('Intent(')));
    expect(
      state,
      contains('with no signer, relay, wallet, GPS or persistence adapter'),
    );
  });

  test('private shell packages road events without opening or publishing', () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativeRoadEventSession()'));
    expect(source, contains('NativeRoadEventPanels('));
    expect(source, contains('onSubmit = {}'));
    expect(source, contains('onVote = { _, _ -> }'));
    expect(source, contains('onZap = {}'));
    expect(source, isNot(contains('roadEventSession.showDetail(')));
    expect(source, isNot(contains('roadEventSession.showComposer(')));
    expect(source, isNot(contains('roadEventSession.beginSubmission(')));
    expect(source, isNot(contains('NostrRelayService')));
    expect(source, isNot(contains('Amberflutter')));
  });
}
