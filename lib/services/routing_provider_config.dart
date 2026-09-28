import 'routing_orchestration_protocol.dart';

enum RoutingProviderConfigurationIssue {
  none,
  graphHopperServerMissing,
  graphHopperApiKeyMissing,
  openRouteApiKeyMissing,
}

class RoutingProviderConfiguration {
  const RoutingProviderConfiguration({
    required this.provider,
    required this.apiKey,
    required this.graphHopperServer,
    required this.issue,
    required this.readsCredentials,
    required this.legacyApiKeyMigration,
  });

  final RoutingProvider provider;
  final String? apiKey;
  final String? graphHopperServer;
  final RoutingProviderConfigurationIssue issue;

  /// Whether resolving this setting requires opening secure storage.
  final bool readsCredentials;

  /// Trimmed legacy key to persist securely, or null when no migration runs.
  final String? legacyApiKeyMigration;
}

/// Pure provider/key/server resolution used by both Flutter map engines.
///
/// This deliberately preserves two shipped execution modes. The MapLibre
/// screen skips secure storage entirely for the default OSRM provider, while
/// the legacy raster screen still reads it. Encoding that difference makes it
/// visible and testable without silently changing migration timing during the
/// native rewrite.
class RoutingProviderConfigProtocol {
  const RoutingProviderConfigProtocol._();

  static RoutingProviderConfiguration resolve({
    required String providerKey,
    required String? secureApiKey,
    required String legacyApiKey,
    required String graphHopperServer,
    required bool deferCredentialReadForOsrm,
  }) {
    if (deferCredentialReadForOsrm && providerKey == 'osrm') {
      return const RoutingProviderConfiguration(
        provider: RoutingProvider.osrm,
        apiKey: null,
        graphHopperServer: null,
        issue: RoutingProviderConfigurationIssue.none,
        readsCredentials: false,
        legacyApiKeyMigration: null,
      );
    }

    final secure = secureApiKey ?? '';
    final legacyMigration =
        secure.isEmpty ? _trimmedOrNull(legacyApiKey) : null;
    final apiKey = _trimmedOrNull(legacyMigration ?? secure);
    final server = _trimmedOrNull(graphHopperServer);

    final RoutingProvider provider;
    final RoutingProviderConfigurationIssue issue;
    switch (providerKey) {
      case 'graphhopper':
        if (server != null) {
          provider = RoutingProvider.graphHopper;
          issue = RoutingProviderConfigurationIssue.none;
        } else {
          provider = RoutingProvider.osrm;
          issue = RoutingProviderConfigurationIssue.graphHopperServerMissing;
        }
      case 'graphhopper_public':
        if (apiKey != null) {
          provider = RoutingProvider.graphHopper;
          issue = RoutingProviderConfigurationIssue.none;
        } else {
          provider = RoutingProvider.osrm;
          issue = RoutingProviderConfigurationIssue.graphHopperApiKeyMissing;
        }
      case 'openroute':
        if (apiKey != null) {
          provider = RoutingProvider.openRoute;
          issue = RoutingProviderConfigurationIssue.none;
        } else {
          provider = RoutingProvider.osrm;
          issue = RoutingProviderConfigurationIssue.openRouteApiKeyMissing;
        }
      default:
        provider = RoutingProvider.osrm;
        issue = RoutingProviderConfigurationIssue.none;
    }

    return RoutingProviderConfiguration(
      provider: provider,
      apiKey: apiKey,
      graphHopperServer: server,
      issue: issue,
      readsCredentials: true,
      legacyApiKeyMigration: legacyMigration,
    );
  }

  static String? _trimmedOrNull(String value) {
    final trimmed = value.trim();
    return trimmed.isEmpty ? null : trimmed;
  }
}

/// Side-effect adapter for the existing Hive + Secure Storage layout.
///
/// Callbacks keep the storage policy independently testable and make the
/// ordering explicit: secure read, optional legacy read/write/delete, then
/// GraphHopper server read. A failed secure write never deletes the legacy key.
class RoutingProviderConfigResolver {
  const RoutingProviderConfigResolver._();

  static Future<RoutingProviderConfiguration> resolveStored({
    required String providerKey,
    required bool deferCredentialReadForOsrm,
    required Future<String?> Function() readSecureApiKey,
    required String Function() readLegacyApiKey,
    required Future<void> Function(String value) writeSecureApiKey,
    required Future<void> Function() deleteLegacyApiKey,
    required String Function() readGraphHopperServer,
  }) async {
    if (deferCredentialReadForOsrm && providerKey == 'osrm') {
      return RoutingProviderConfigProtocol.resolve(
        providerKey: providerKey,
        secureApiKey: null,
        legacyApiKey: '',
        graphHopperServer: '',
        deferCredentialReadForOsrm: true,
      );
    }

    final secureApiKey = await readSecureApiKey();
    final secureValue = secureApiKey ?? '';
    final legacyApiKey = secureValue.isEmpty ? readLegacyApiKey() : '';
    final preliminary = RoutingProviderConfigProtocol.resolve(
      providerKey: providerKey,
      secureApiKey: secureApiKey,
      legacyApiKey: legacyApiKey,
      graphHopperServer: '',
      deferCredentialReadForOsrm: false,
    );
    final migration = preliminary.legacyApiKeyMigration;
    if (migration != null) {
      await writeSecureApiKey(migration);
      await deleteLegacyApiKey();
    }

    return RoutingProviderConfigProtocol.resolve(
      providerKey: providerKey,
      secureApiKey: secureApiKey,
      legacyApiKey: legacyApiKey,
      graphHopperServer: readGraphHopperServer(),
      deferCredentialReadForOsrm: false,
    );
  }
}
