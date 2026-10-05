import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

const _root = 'android/app/src/main/kotlin/app/roadstr';
const _gecko = 'native-android/app/src/gecko/kotlin/app/roadstr/roadtest';
const _locales = [
  'bg', 'cs', 'da', 'de', 'el', 'en', 'es', 'et', 'fi', 'fr', 'ga', 'hr', //
  'hu', 'it', 'ja', 'lt', 'lv', 'mt', 'nl', 'pl', 'pt', 'ro', 'ru', 'sk',
  'sl', 'sv', 'zh',
];

String _read(String path) => File(path).readAsStringSync();

/// The code without block comments and whole-line comments, so an explanation of why
/// something is not called is not mistaken for a call.
String _code(String source) => source
    .replaceAll(RegExp(r'/\*[\s\S]*?\*/'), '')
    .split('\n')
    .where((line) => !line.trimLeft().startsWith('//'))
    .join('\n');

void main() {
  test('every address the in-app browser opens goes through one policy', () {
    final policy = _read('$_root/core/web/WebNavigationPolicy.kt');
    for (final scheme in ['"https"', '"http"', '"tel"', '"mailto"', '"geo"']) {
      expect(policy, contains(scheme), reason: scheme);
    }
    // Anything else, including intent:, javascript:, data:, file:, content: and custom schemes, is refused.
    expect(policy, contains('else -> block(BlockReason.UNSAFE_SCHEME)'));
    expect(policy, contains('if (isLocalHost(host)) return block(BlockReason.LOCAL_HOST)'));
    expect(policy, contains('const val MAX_REDIRECTS = 10'));
    // A page can only ask: phone, mail and map links are confirmed, never started by the page.
    expect(policy, contains('NavigationDecision.Confirm(ExternalAction.Dial(number))'));

    final delegates = _read('$_gecko/GeckoPolicyDelegates.kt');
    expect(delegates, contains('WebNavigationPolicy.decide(request.uri)'));
    expect(delegates, contains('redirects.allowNext()'));
    expect(delegates, isNot(contains('startActivity')));
    final host = _read('$_gecko/GeckoWebBrowserHost.kt');
    expect(host, contains('WebNavigationPolicy.decide(url)'));
  });

  test('the browser gives a page no capability and keeps nothing', () {
    final profile = _read('$_gecko/GeckoPrivacyProfile.kt');
    for (final setting in [
      '.usePrivateMode(true)',
      '.safeBrowsing(ContentBlocking.SafeBrowsing.NONE)',
      '.allowInsecureConnections(GeckoRuntimeSettings.HTTPS_ONLY)',
      '.aboutConfigEnabled(false)',
      '.loginAutofillEnabled(false)',
      '.remoteDebuggingEnabled(false)',
      '.globalPrivacyControlEnabled(true)',
      '.trustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_DISABLED)',
      'ContentBlocking.AntiTracking.STRICT',
      'ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS',
    ]) {
      expect(profile, contains(setting), reason: setting);
    }
    // No crash uploader, no experiments, no sign-in manager, no history store.
    for (final absent in ['crashHandler(', 'experimentDelegate(', 'setHistoryDelegate', 'setAutofillDelegate']) {
      for (final file in Directory(_gecko).listSync().whereType<File>()) {
        expect(file.readAsStringSync(), isNot(contains(absent)), reason: '${file.path} $absent');
      }
    }
    final delegates = _read('$_gecko/GeckoPolicyDelegates.kt');
    expect(delegates, contains('callback.reject()'));
    expect(delegates, contains('VALUE_DENY'));
    expect(delegates, contains('prompt.confirm(AllowOrDeny.DENY)'));
    expect(_read('$_gecko/GeckoWebBrowserHost.kt'), contains('runtime.clearAllData()'));
  });

  test('the engine is created on first use and never shut down in the process', () {
    final holder = _code(_read('$_gecko/GeckoRuntimeHolder.kt'));
    expect(holder, contains('runtime ?: create()'));
    expect(holder, isNot(contains('.shutdown()')));
    expect(holder, isNot(contains('warmUp')));
  });

  test('the in-app browser is an opt-in build variant and the default build is unchanged', () {
    final gradle = _read('native-android/app/build.gradle.kts');
    expect(gradle, contains('providers.gradleProperty("geckoview").orNull == "true"'));
    // The default lines are untouched; the variant overrides them in its own block.
    expect(gradle, contains('minSdk = 24'));
    expect(gradle, contains('compileSdk = 36'));
    expect(gradle, contains('kotlin.directories.add("src/system/kotlin")'));
    final variant = gradle.substring(gradle.indexOf('if (geckoView) {\n    android {'));
    expect(variant, contains('minSdk = 26'));
    expect(variant, contains('compileSdk = 37'));
    expect(variant, contains('abiFilters += "arm64-v8a"'));
    expect(variant, contains('applicationId = "app.roadstr.roadtest.gecko"'));
    expect(variant, contains('kotlin.directories.add("src/gecko/kotlin")'));
    expect(gradle, contains('exclude(group = "com.google.android.gms")'));
    // The variant needs a newer Gradle than the pinned one, so it has its own wrapper and script.
    expect(_read('native-android/gradle/wrapper/gradle-wrapper.properties'), contains('gradle-9.1.0-all.zip'));
    final variantWrapper = _read('native-android/gradle/wrapper-geckoview/gradle-wrapper.properties');
    expect(variantWrapper, contains('gradle-9.3.1-all.zip'));
    expect(variantWrapper, contains('distributionSha256Sum=17f277867f6914d61b1aa02efab1ba7bb439ad652ca485cd8ca6842fccec6e43'));
    expect(_read('native-android/gradlew-geckoview'), contains('-Pgeckoview=true'));
    expect(_read('native-android/gradle.properties'), contains('geckoview=false'));
    final settings = _read('native-android/settings.gradle.kts');
    expect(settings, contains('useVersion("9.1.0")'));
    expect(settings, contains('if (geckoViewVariant)'));
    expect(settings, contains('includeGroup("org.mozilla.geckoview")'));
    // The default build opens pages in the user's browser through the same interface.
    final system = _read('native-android/app/src/system/kotlin/app/roadstr/roadtest/WebBrowserHostFactory.kt');
    expect(system, contains('SystemBrowserHost(openExternal)'));
    expect(system, isNot(contains('geckoview')));
  });

  test('the shell opens web results through the host and draws the in-app page', () {
    final shell = _read('$_root/feature/home/NativeRoadstrShell.kt');
    expect(shell, contains('if (webBrowser != null) webBrowser.open(url) else onOpenExternal(url)'));
    expect(shell, contains('NativeWebBrowserScreen('));
    expect(shell, contains('browserState.open -> webBrowser?.close()'));
    final activity = _read('native-android/app/src/main/kotlin/app/roadstr/roadtest/NativeRoadTestActivity.kt');
    expect(activity, contains('WebBrowserHostFactory.create(this, ::openExternal)'));
    expect(activity, contains('webBrowser.onHostStop()'));
  });

  test('every browser string is translated into all 27 languages', () {
    final generator = _read('tools/kotlin_rewrite/generate_android_nostr_strings.dart');
    final keys = RegExp(r"'(native_browser_[a-z_]+)':").allMatches(generator).map((m) => m.group(1)!).toSet();
    expect(keys.length, greaterThanOrEqualTo(17));
    for (final locale in _locales) {
      final dir = locale == 'en' ? 'values' : 'values-$locale';
      final xml = _read('android/app/src/main/res/$dir/native_nostr_strings.xml');
      for (final key in keys) {
        expect(xml, contains('name="$key"'), reason: '$dir $key');
      }
    }
  });

  test('the browser engine preferences are the private ones', () {
    final prefs = _read('$_root/core/web/BrowserPrivacyPrefs.kt');
    for (final line in [
      '"security.webauth.webauthn" to false',
      '"geo.enabled" to false',
      '"media.peerconnection.enabled" to false',
      '"toolkit.telemetry.enabled" to false',
      '"browser.safebrowsing.malware.enabled" to false',
      '"dom.security.https_only_mode" to true',
    ]) {
      expect(prefs, contains(line), reason: line);
    }
  });

  group('page place extraction', () {
    const extension = 'native-android/app/src/gecko/assets/web/place-extractor';

    test('the bundled extension can only read the page and tell Roadstr', () {
      final manifest = _read('$extension/manifest.json');
      expect(manifest, contains('"permissions": ["nativeMessaging"]'));
      expect(manifest, contains('"matches": ["https://*/*"]'));
      expect(manifest, contains('"all_frames": false'));
      for (final broad in ['<all_urls>', 'http://', '"tabs"', '"webRequest"', '"cookies"', '"storage"', '"downloads"']) {
        expect(manifest, isNot(contains(broad)), reason: broad);
      }
      for (final file in ['extractor.js', 'background.js']) {
        final script = _code(_read('$extension/$file'));
        for (final forbidden in ['eval(', 'Function(', 'fetch(', 'XMLHttpRequest', 'WebSocket', 'innerHTML', 'document.write', 'importScripts', 'localStorage']) {
          expect(script, isNot(contains(forbidden)), reason: '$file $forbidden');
        }
      }
      // The background script drops anything that did not come from the extension's own content script.
      expect(_read('$extension/background.js'), contains('sender.id !== browser.runtime.id'));
      expect(_read('$extension/background.js'), contains('sendNativeMessage("roadstrExtractor"'));
    });

    test('the extension reads exactly the meta tags the Kotlin schema accepts', () {
      final js = _read('$extension/extractor.js');
      final jsKeys = RegExp(r'"((?:og|place):[a-z_:\-]+)"').allMatches(js.substring(js.indexOf('META_KEYS'), js.indexOf('];'))).map((m) => m.group(1)!).toSet();
      final kotlin = _read('$_root/core/discovery/structured/WebPageMessage.kt');
      final block = kotlin.substring(kotlin.indexOf('val metaKeys'), kotlin.indexOf(')', kotlin.indexOf('val metaKeys')));
      final kotlinKeys = RegExp(r'"((?:og|place):[a-z_:\-]+)"').allMatches(block).map((m) => m.group(1)!).toSet();
      expect(jsKeys, isNotEmpty);
      expect(jsKeys, kotlinKeys);
      expect(js, contains('var MAX_BLOCKS = 8;'));
      expect(js, contains('var MAX_CHARS = 65536;'));
      expect(kotlin, contains('const val MAX_BYTES = 64 * 1024'));
      expect(_read('$_root/core/discovery/structured/JsonLdPlaceParser.kt'), contains('const val MAX_BLOCKS = 8'));
      expect(_read('$_root/core/discovery/structured/JsonLdPlaceParser.kt'), contains('const val MAX_BLOCK_CHARS = 65_536'));
    });

    test('the extension is shipped only in the variant and the host trusts only the page on screen', () {
      expect(_read('native-android/app/build.gradle.kts'), contains('assets.directories.add("src/gecko/assets")'));
      final host = _read('$_gecko/GeckoWebBrowserHost.kt');
      expect(host, contains('WebPageMessageSchema.parse(raw) ?: return'));
      expect(host, contains('HostMatching.registrableDomain(page.url.host) != HostMatching.registrableDomain(current.host)'));
      expect(host, contains('pagePlace = if (loading) null else current.pagePlace'));
      final extractor = _read('$_gecko/GeckoPagePlaceExtractor.kt');
      expect(extractor, contains('ensureBuiltIn(LOCATION, ID)'));
      expect(extractor, contains('setAllowedInPrivateBrowsing(extension, true)'));
      expect(extractor, isNot(contains('ALLOW_CONTENT_MESSAGING')));
    });

    test('a page can never put a pin on the map or move the destination without a confirmation', () {
      final matcher = _read('$_root/core/discovery/resolve/PageMatcher.kt');
      expect(matcher, contains('sources = setOf(PlaceSource.WEBSITE)'));
      expect(matcher, contains('if (GeoMath.distanceMeters(context.center, position) > reach) return null'));
      final host = _read('$_gecko/GeckoWebBrowserHost.kt');
      expect(host, contains('ExternalAction.UseAsDestination(place.latitude, place.longitude)'));
      // Navigating from a page goes through the same confirmation dialog as any map link.
      final screen = _read('$_root/feature/web/NativeWebBrowserScreen.kt');
      expect(screen, contains('ConfirmActionDialog('));
    });
  });
}

