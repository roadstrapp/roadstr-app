import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/migration/legacy_migration_headless.dart';

import '../tools/kotlin_rewrite/legacy_snapshot_fixture.dart';

const _protocolFixturePath =
    'android/app/src/test/resources/parity/legacy_bridge_protocol.tsv';
const _rawFixturePath =
    'android/app/src/test/resources/parity/legacy_settings_hive_v1.b64';
const _envelopeFixturePath =
    'android/app/src/test/resources/parity/legacy_snapshot_v1.b64';
const _secret = 'must-not-appear-in-headless-errors';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('channel contract matches the shared fixture and announces readiness',
      () async {
    final contract = _readProtocolFixture();
    expect(
      contract,
      {
        'protocol_version':
            LegacyMigrationChannelContract.protocolVersion.toString(),
        'channel': LegacyMigrationChannelContract.channelName,
        'ready_method': LegacyMigrationChannelContract.readyMethod,
        'read_method': LegacyMigrationChannelContract.readMethod,
        'entrypoint_library': LegacyMigrationChannelContract.entrypointLibrary,
        'entrypoint_function':
            LegacyMigrationChannelContract.entrypointFunction,
        'failure_code': LegacyMigrationChannelContract.failureCode,
      },
    );

    const channel = MethodChannel(LegacyMigrationChannelContract.channelName);
    final outboundCalls = <MethodCall>[];
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(channel, (call) async {
      outboundCalls.add(call);
      return null;
    });
    final server = LegacyMigrationHeadlessServer(
      handler: LegacyMigrationHeadlessHandler(
        getDocumentsDirectory: () async => Directory.systemTemp,
        readSecureValues: () async => const {},
      ),
      channel: channel,
    );
    try {
      await server.start();
      expect(outboundCalls, hasLength(1));
      expect(
        outboundCalls.single.method,
        LegacyMigrationChannelContract.readyMethod,
      );
      expect(
        outboundCalls.single.arguments,
        LegacyMigrationChannelContract.protocolVersion,
      );
    } finally {
      server.stop();
      messenger.setMockMethodCallHandler(channel, null);
    }
  });

  test('one-shot handler returns the exact envelope without source mutation',
      () async {
    final directory = await _createLegacyDocuments();
    try {
      final before = await _snapshotFiles(directory);
      final handler = LegacyMigrationHeadlessHandler(
        getDocumentsDirectory: () async => directory,
        readSecureValues: () async => buildSyntheticLegacySecureValues(),
      );

      final result = await handler.handle(const MethodCall(
        LegacyMigrationChannelContract.readMethod,
        LegacyMigrationChannelContract.protocolVersion,
      ));
      expect(result, isA<Uint8List>());
      expect(
        base64Encode(result! as Uint8List),
        File(_envelopeFixturePath).readAsStringSync().trim(),
      );
      expect(await _snapshotFiles(directory), before);

      await _expectNeutralFailure(handler.handle(const MethodCall(
        LegacyMigrationChannelContract.readMethod,
        LegacyMigrationChannelContract.protocolVersion,
      )));
    } finally {
      await directory.delete(recursive: true);
    }
  });

  test('handler returns null only for a fully absent legacy state', () async {
    final directory =
        await Directory.systemTemp.createTemp('roadstr-headless-empty-');
    try {
      final handler = LegacyMigrationHeadlessHandler(
        getDocumentsDirectory: () async => directory,
        readSecureValues: () async => const {},
      );
      expect(
        await handler.handle(const MethodCall(
          LegacyMigrationChannelContract.readMethod,
          LegacyMigrationChannelContract.protocolVersion,
        )),
        isNull,
      );
    } finally {
      await directory.delete(recursive: true);
    }
  });

  test('handler rejects protocol drift and redacts provider failures',
      () async {
    final handler = LegacyMigrationHeadlessHandler(
      getDocumentsDirectory: () async => Directory.systemTemp,
      readSecureValues: () async => throw StateError(_secret),
    );

    await _expectNeutralFailure(handler.handle(const MethodCall(
      LegacyMigrationChannelContract.readMethod,
      LegacyMigrationChannelContract.protocolVersion + 1,
    )));
    await _expectNeutralFailure(handler.handle(const MethodCall(
      LegacyMigrationChannelContract.readMethod,
      LegacyMigrationChannelContract.protocolVersion,
    )));
    await expectLater(
      handler.handle(const MethodCall('unexpectedMethod')),
      throwsA(isA<MissingPluginException>()),
    );
  });
}

Map<String, String> _readProtocolFixture() => {
      for (final line in File(_protocolFixturePath).readAsLinesSync())
        line.split('\t').first: line.split('\t').last,
    };

Future<Directory> _createLegacyDocuments() async {
  final directory =
      await Directory.systemTemp.createTemp('roadstr-headless-source-');
  await File('${directory.path}/settings.hive').writeAsBytes(
    base64Decode(File(_rawFixturePath).readAsStringSync().trim()),
    flush: true,
  );
  for (final entry in syntheticLegacyAssetContents.entries) {
    final file = File('${directory.path}/${entry.key}');
    await file.parent.create(recursive: true);
    await file.writeAsBytes(entry.value, flush: true);
  }
  return directory;
}

Future<Map<String, List<int>>> _snapshotFiles(Directory directory) async {
  final files = await directory
      .list(recursive: true, followLinks: false)
      .where((entity) => entity is File)
      .cast<File>()
      .toList();
  files.sort((left, right) => left.path.compareTo(right.path));
  return {
    for (final file in files)
      file.path.substring(directory.path.length + 1): await file.readAsBytes(),
  };
}

Future<void> _expectNeutralFailure(Future<Object?> operation) => expectLater(
      operation,
      throwsA(
        isA<PlatformException>()
            .having(
              (error) => error.code,
              'code',
              LegacyMigrationChannelContract.failureCode,
            )
            .having(
              (error) => error.toString(),
              'error',
              isNot(contains(_secret)),
            )
            .having((error) => error.details, 'details', isNull),
      ),
    );
