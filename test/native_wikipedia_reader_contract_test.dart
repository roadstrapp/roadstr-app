import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tools/kotlin_rewrite/generate_android_wikipedia_strings.dart';

void main() {
  final presentation = File(
    'android/app/src/main/kotlin/app/roadstr/feature/wikipedia/'
    'NativeWikipediaPresentation.kt',
  );
  final reader = File(
    'android/app/src/main/kotlin/app/roadstr/feature/wikipedia/'
    'NativeWikipediaReader.kt',
  );
  final shell = File(
    'android/app/src/main/kotlin/app/roadstr/feature/home/'
    'NativeRoadstrShell.kt',
  );

  test('all 27 Wikipedia translations generate current Android resources', () {
    final generated = buildAndroidWikipediaResources();

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
      expect(strings, hasLength(4), reason: entry.key);
      expect(strings.map((match) => match.group(1)).toSet(), hasLength(4));
    }
  });

  test('native URI policy mirrors the Flutter Wikipedia article boundary', () {
    final flutter = File(
      'lib/screens/wikipedia_webview_screen.dart',
    ).readAsStringSync();
    final kotlin = presentation.readAsStringSync();

    for (final token in <String>[
      "uri.scheme == 'https'",
      'uri.userInfo.isEmpty',
      '!uri.hasPort',
      "path.startsWith('/wiki/')",
      "RegExp(r'^[a-z0-9-]{1,24}\$')",
    ]) {
      expect(flutter, contains(token));
    }
    expect(kotlin, contains('uri.scheme.equals("https", ignoreCase = true)'));
    expect(kotlin, contains('uri.userInfo == null && uri.port == -1'));
    expect(kotlin, contains('path.startsWith("/wiki/")'));
    expect(kotlin, contains('Regex("^[a-z0-9-]{1,24}\$")'));
    expect(kotlin, contains('isMainFrame && parseAllowedArticle(url) != null'));
  });

  test('native WebView is restricted and clears ephemeral browser state', () {
    final source = reader.readAsStringSync();

    expect(source, contains('settings.javaScriptEnabled = false'));
    expect(source, contains('settings.allowFileAccess = false'));
    expect(source, contains('settings.allowContentAccess = false'));
    expect(source, contains('settings.mixedContentMode'));
    expect(source, contains('request.isForMainFrame'));
    expect(source, contains('handler.cancel()'));
    expect(source, contains('request.deny()'));
    expect(source, contains('filePathCallback.onReceiveValue(emptyArray())'));
    expect(source, contains('removeAllCookies(null)'));
    expect(source, contains('webView.destroy()'));
    expect(source, isNot(contains('addJavascriptInterface')));
    expect(source, isNot(contains('Intent(')));
  });

  test('private shell packages the reader without opening it or the browser',
      () {
    final source = shell.readAsStringSync();

    expect(source, contains('NativeWikipediaSession()'));
    expect(source, contains('NativeWikipediaReader('));
    expect(source, contains('onOpenExternal = {}'));
    expect(source, isNot(contains('wikipediaSession.open(')));
    expect(source, isNot(contains('Intent(')));
    expect(source, isNot(contains('startActivity(')));
  });
}
