import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

const _auditScript = 'tools/kotlin_rewrite/audit_android_release.sh';
const _releaseCertificate =
    'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';

void main() {
  test('release source contract is valid without reading signing material', () {
    final result = Process.runSync(_auditScript, ['--source-only']);

    expect(result.exitCode, 0, reason: '${result.stderr}');
    expect(
      result.stdout,
      contains('release source contract valid: app.roadstr 1.0.0+2060'),
    );
  });

  test('official release recipe is guarded and includes every ABI artifact',
      () {
    final script = File('build_release.sh').readAsStringSync();

    expect(
      RegExp(r'^\s*--split-per-abi(?:\s|\\|$)', multiLine: true)
          .hasMatch(script),
      isFalse,
      reason: 'Flutter ABI offsets must not change the declared versionCode',
    );
    expect(
      script,
      contains('audit_android_release.sh --source-only --require-upgrade'),
    );
    expect(script, contains('--mode official'));
    expect(script, contains('--expected-cert-sha256'));
    expect(script, contains('build/release-manifest.tsv'));
    for (final artifact in const [
      'app-release.apk',
      'app-arm64-v8a-release.apk',
      'app-armeabi-v7a-release.apk',
      'app-x86_64-release.apk',
    ]) {
      expect(script, contains(artifact), reason: '$artifact is not audited');
    }
  });

  test('official artifact audit records identity ABI hashes and certificate',
      () async {
    final fixture = await _ReleaseAuditFixture.create();
    try {
      final manifest = File('${fixture.root.path}/release-manifest.tsv');
      final result = fixture.run([
        '--artifacts',
        fixture.artifacts.path,
        '--mode',
        'official',
        '--expected-cert-sha256',
        _releaseCertificate.toLowerCase(),
        '--output',
        manifest.path,
      ]);

      expect(result.exitCode, 0, reason: '${result.stderr}');
      final contents = manifest.readAsStringSync();
      expect(contents, contains('application_id\tapp.roadstr'));
      expect(contents, contains('version_name\t1.0.0'));
      expect(contents, contains('version_code\t2060'));
      expect(
        RegExp(r'^artifact\t(?!variant).+$', multiLine: true)
            .allMatches(contents),
        hasLength(4),
      );
      expect(
        contents,
        contains('artifact\tuniversal\tapp-release.apk\t'),
      );
      expect(contents, contains('arm64-v8a,armeabi-v7a,x86_64\tsigned'));
      expect(contents, contains('\tsigned\t$_releaseCertificate'));
      expect(
        RegExp(r'\t[0-9a-f]{64}\t').allMatches(contents),
        hasLength(4),
      );
    } finally {
      await fixture.root.delete(recursive: true);
    }
  });

  test('official artifact audit requires the expected certificate', () async {
    final fixture = await _ReleaseAuditFixture.create();
    try {
      final result = fixture.run([
        '--artifacts',
        fixture.artifacts.path,
        '--mode',
        'official',
      ]);

      expect(result.exitCode, isNot(0));
      expect(
        result.stderr,
        contains('official mode requires a 64-hex expected certificate'),
      );
    } finally {
      await fixture.root.delete(recursive: true);
    }
  });

  test('artifact audit fails closed on an ABI mismatch', () async {
    final fixture = await _ReleaseAuditFixture.create();
    try {
      final result = fixture.run(
        [
          '--artifacts',
          fixture.artifacts.path,
          '--mode',
          'official',
          '--expected-cert-sha256',
          _releaseCertificate,
        ],
        extraEnvironment: const {'ROADSTR_FAKE_BAD_ABI': '1'},
      );

      expect(result.exitCode, isNot(0));
      expect(
          result.stderr, contains('ABI mismatch in app-arm64-v8a-release.apk'));
    } finally {
      await fixture.root.delete(recursive: true);
    }
  });
}

final class _ReleaseAuditFixture {
  _ReleaseAuditFixture(this.root, this.artifacts, this.tools);

  final Directory root;
  final Directory artifacts;
  final Directory tools;

  static Future<_ReleaseAuditFixture> create() async {
    final root =
        await Directory.systemTemp.createTemp('roadstr-release-audit-');
    final artifacts = Directory('${root.path}/artifacts')..createSync();
    final tools = Directory('${root.path}/tools')..createSync();

    for (final artifact in const [
      'app-release.apk',
      'app-arm64-v8a-release.apk',
      'app-armeabi-v7a-release.apk',
      'app-x86_64-release.apk',
    ]) {
      File('${artifacts.path}/$artifact')
          .writeAsStringSync('fixture:$artifact');
    }

    final aapt = File('${tools.path}/aapt')
      ..writeAsStringSync(r'''#!/usr/bin/env bash
set -euo pipefail
apk="${3##*/}"
case "$apk" in
  app-release.apk) abis="arm64-v8a armeabi-v7a x86_64" ;;
  app-arm64-v8a-release.apk) abis="arm64-v8a" ;;
  app-armeabi-v7a-release.apk) abis="armeabi-v7a" ;;
  app-x86_64-release.apk) abis="x86_64" ;;
  *) exit 2 ;;
esac
if [ "${ROADSTR_FAKE_BAD_ABI:-}" = 1 ] && [ "$apk" = app-arm64-v8a-release.apk ]; then
  abis="x86_64"
fi
printf "package: name='app.roadstr' versionCode='2060' versionName='1.0.0'\n"
printf "application-label:'Roadstr'\n"
printf 'native-code:'
for abi in $abis; do printf " '%s'" "$abi"; done
printf '\n'
''');
    final apksigner = File('${tools.path}/apksigner')
      ..writeAsStringSync('''#!/usr/bin/env bash
set -euo pipefail
case "\$*" in
  *--print-certs*) printf 'Signer #1 certificate SHA-256 digest: $_releaseCertificate\\n' ;;
esac
''');
    final chmod = Process.runSync('chmod', ['+x', aapt.path, apksigner.path]);
    if (chmod.exitCode != 0) {
      throw StateError('Could not prepare release test tools: ${chmod.stderr}');
    }
    return _ReleaseAuditFixture(root, artifacts, tools);
  }

  ProcessResult run(
    List<String> arguments, {
    Map<String, String> extraEnvironment = const {},
  }) {
    return Process.runSync(
      _auditScript,
      arguments,
      environment: {
        ...Platform.environment,
        'PATH': '${tools.path}:${Platform.environment['PATH'] ?? ''}',
        ...extraEnvironment,
      },
    );
  }
}
