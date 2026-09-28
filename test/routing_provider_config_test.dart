import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/routing_orchestration_protocol.dart';
import 'package:roadstr/services/routing_provider_config.dart';

import '../tools/kotlin_rewrite/generate_routing_provider_config_fixture.dart'
    as fixture;

void main() {
  test('shared routing provider configuration fixture is current', () {
    expect(
      File(fixture.outputPath).readAsStringSync(),
      fixture.buildRoutingProviderConfigFixture(),
    );
  });

  test('MapLibre OSRM fast-path opens no credential store', () async {
    final result = await RoutingProviderConfigResolver.resolveStored(
      providerKey: 'osrm',
      deferCredentialReadForOsrm: true,
      readSecureApiKey: () => throw StateError('secure read must be skipped'),
      readLegacyApiKey: () => throw StateError('legacy read must be skipped'),
      writeSecureApiKey: (_) => throw StateError('write must be skipped'),
      deleteLegacyApiKey: () => throw StateError('delete must be skipped'),
      readGraphHopperServer: () =>
          throw StateError('server read must be skipped'),
    );

    expect(result.provider, RoutingProvider.osrm);
    expect(result.apiKey, isNull);
    expect(result.graphHopperServer, isNull);
    expect(result.readsCredentials, isFalse);
  });

  test('an existing secure key bypasses legacy storage', () async {
    final calls = <String>[];
    final result = await RoutingProviderConfigResolver.resolveStored(
      providerKey: 'graphhopper_public',
      deferCredentialReadForOsrm: true,
      readSecureApiKey: () async {
        calls.add('read-secure');
        return ' secure-key ';
      },
      readLegacyApiKey: () {
        calls.add('read-legacy');
        return 'legacy-key';
      },
      writeSecureApiKey: (value) async => calls.add('write:$value'),
      deleteLegacyApiKey: () async => calls.add('delete-legacy'),
      readGraphHopperServer: () {
        calls.add('read-server');
        return '';
      },
    );

    expect(calls, ['read-secure', 'read-server']);
    expect(result.provider, RoutingProvider.graphHopper);
    expect(result.apiKey, 'secure-key');
    expect(result.legacyApiKeyMigration, isNull);
  });

  test('legacy key migration is ordered and deletes only after secure write',
      () async {
    final calls = <String>[];
    final result = await RoutingProviderConfigResolver.resolveStored(
      providerKey: 'openroute',
      deferCredentialReadForOsrm: false,
      readSecureApiKey: () async {
        calls.add('read-secure');
        return null;
      },
      readLegacyApiKey: () {
        calls.add('read-legacy');
        return '  legacy-key  ';
      },
      writeSecureApiKey: (value) async => calls.add('write:$value'),
      deleteLegacyApiKey: () async => calls.add('delete-legacy'),
      readGraphHopperServer: () {
        calls.add('read-server');
        return '  https://stale.example/route  ';
      },
    );

    expect(calls, [
      'read-secure',
      'read-legacy',
      'write:legacy-key',
      'delete-legacy',
      'read-server',
    ]);
    expect(result.provider, RoutingProvider.openRoute);
    expect(result.apiKey, 'legacy-key');
    expect(result.graphHopperServer, 'https://stale.example/route');
    expect(result.legacyApiKeyMigration, 'legacy-key');
  });

  test('a whitespace secure value suppresses legacy migration before trim',
      () async {
    final calls = <String>[];
    final result = await RoutingProviderConfigResolver.resolveStored(
      providerKey: 'graphhopper_public',
      deferCredentialReadForOsrm: false,
      readSecureApiKey: () async {
        calls.add('read-secure');
        return '   ';
      },
      readLegacyApiKey: () {
        calls.add('read-legacy');
        return 'legacy-key';
      },
      writeSecureApiKey: (value) async => calls.add('write:$value'),
      deleteLegacyApiKey: () async => calls.add('delete-legacy'),
      readGraphHopperServer: () {
        calls.add('read-server');
        return '';
      },
    );

    expect(calls, ['read-secure', 'read-server']);
    expect(result.provider, RoutingProvider.osrm);
    expect(
      result.issue,
      RoutingProviderConfigurationIssue.graphHopperApiKeyMissing,
    );
  });

  test('failed secure migration never deletes the recoverable legacy key',
      () async {
    final calls = <String>[];
    await expectLater(
      RoutingProviderConfigResolver.resolveStored(
        providerKey: 'openroute',
        deferCredentialReadForOsrm: false,
        readSecureApiKey: () async {
          calls.add('read-secure');
          return '';
        },
        readLegacyApiKey: () {
          calls.add('read-legacy');
          return 'legacy-key';
        },
        writeSecureApiKey: (value) async {
          calls.add('write:$value');
          throw StateError('keystore unavailable');
        },
        deleteLegacyApiKey: () async => calls.add('delete-legacy'),
        readGraphHopperServer: () {
          calls.add('read-server');
          return '';
        },
      ),
      throwsStateError,
    );
    expect(calls, ['read-secure', 'read-legacy', 'write:legacy-key']);
  });
}
