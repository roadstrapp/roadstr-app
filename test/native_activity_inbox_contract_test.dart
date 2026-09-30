import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_activity_strings.dart';

void main() {
  final presentation = File(
    'android/app/src/main/kotlin/app/roadstr/feature/activity/'
    'NativeActivityInboxPresentation.kt',
  );
  final panel = File(
    'android/app/src/main/kotlin/app/roadstr/feature/activity/'
    'NativeActivityInboxPanel.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 activity resource sets are current and complete', () {
    final generated = buildAndroidActivityResources();

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
      expect(strings, hasLength(12), reason: entry.key);
      expect(strings.map((match) => match.group(1)).toSet(), hasLength(12));
      expect(_value(strings, 'native_activity_zap_body'), contains(r'%1$d'));
      expect(
        _value(strings, 'native_activity_confirmed_body'),
        contains(r'%1$s'),
      );
      expect(
        _value(strings, 'native_activity_denied_body'),
        contains(r'%1$s'),
      );
    }
  });

  test('native protocol preserves Flutter inbox and cursor contracts', () {
    final model =
        File('lib/models/activity_notification.dart').readAsStringSync();
    final service = File(
      'lib/services/activity_notification_service.dart',
    ).readAsStringSync();
    final relay =
        File('lib/services/nostr_relay_service.dart').readAsStringSync();
    final native = presentation.readAsStringSync();

    expect(model,
        contains('enum ActivityNotificationType { zap, confirmed, denied }'));
    expect(model, contains("'createdAt': createdAt"));
    expect(model, contains("'isRead': isRead"));
    expect(service, contains('static const _maxEntries = 100'));
    expect(service, contains("'activity_inbox_\$pubkey'"));
    expect(
        service, contains('items.any((item) => item.id == notification.id)'));
    expect(service, contains('b.createdAt.compareTo(a.createdAt)'));
    expect(relay, contains("_activityCursorKey('zap', pubKeyHex)"));
    expect(relay, contains("_activityCursorKey('confirmation', pubKeyHex)"));
    expect(native, contains('const val MAX_ENTRIES = 100'));
    expect(native, contains('activity_inbox_'));
    expect(native, contains('activity_\${kind.wireName}_cursor_\$pubkey'));
    expect(native, contains('sortedByDescending'));
    expect(native, contains('it.id == normalized.id'));
  });

  test('Compose inbox remains bounded accessible and deliberately silent', () {
    final source = panel.readAsStringSync();
    final state = presentation.readAsStringSync();

    expect(source, contains('LazyColumn'));
    expect(source, contains('heightIn(max = maxHeight * 0.9f)'));
    expect(source, contains('navigationBarsPadding()'));
    expect(source, contains('paneTitle = title'));
    expect(source, contains('.sizeIn(minWidth = 48.dp, minHeight = 48.dp)'));
    expect(source, contains('onViewed(snapshot.revision)'));
    expect(source, isNot(contains('NotificationManager')));
    expect(source, isNot(contains('MediaPlayer')));
    expect(source, isNot(contains('NostrRelayService')));
    expect(source, isNot(contains('Hive')));
    expect(
      state,
      contains('with no Hive, relay, socket or notification owner'),
    );
  });

  test('private shell packages the inbox without opening or feeding it', () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativeActivityInboxSession()'));
    expect(source, contains('NativeActivityInboxPanel('));
    expect(source, contains('activityInboxSession.markAllRead(revision)'));
    expect(source, isNot(contains('activityInboxSession.show(')));
    expect(source, isNot(contains('activityInboxSession.showLoggedOut(')));
    expect(source, isNot(contains('activityInboxSession.record(')));
    expect(source, isNot(contains('enableActivityNotifications(')));
    expect(source, isNot(contains('NotificationManager')));
  });
}

String? _value(List<RegExpMatch> values, String name) =>
    values.singleWhere((match) => match.group(1) == name).group(2);
