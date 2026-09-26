import 'dart:io';

import 'package:roadstr/services/nostr_relay_ingress.dart';

const outputPath = 'android/app/src/test/resources/parity/nostr_ingress_v1.tsv';

typedef _Expected = ({
  String verdict,
  String route,
  String rule,
  int observed,
});

typedef _Step = ({
  String scenario,
  String name,
  String subscription,
  String kind,
  _Expected expected,
});

_Expected _expected(
  String verdict, {
  String route = '-',
  String rule = '-',
  int observed = 0,
}) =>
    (verdict: verdict, route: route, rule: rule, observed: observed);

Object? _kind(String encoded) {
  if (encoded == 'null') return null;
  final separator = encoded.indexOf(':');
  if (separator < 0) throw StateError('Invalid fixture kind: $encoded');
  final type = encoded.substring(0, separator);
  final value = encoded.substring(separator + 1);
  return switch (type) {
    'int' => int.parse(value),
    'string' => value,
    _ => throw StateError('Unknown fixture kind type: $type'),
  };
}

NostrRelayIngress _policy(String scenario) => switch (scenario) {
      'live' => NostrRelayIngress([
          NostrIngressRule(
            name: 'events',
            subscriptionId: 'events',
            routes: const {
              1315: NostrIngressRoute.roadEvent,
              1317: NostrIngressRoute.roadUpdate,
            },
          ),
          NostrIngressRule(
            name: 'confirmations',
            subscriptionId: 'confirmations',
            routes: const {1316: NostrIngressRoute.confirmation},
          ),
          NostrIngressRule(
            name: 'mine',
            subscriptionId: 'mine',
            routes: const {1316: NostrIngressRoute.ownConfirmation},
          ),
          NostrIngressRule(
            name: 'zaps',
            subscriptionId: 'zaps',
            routes: const {9735: NostrIngressRoute.zapReceipt},
          ),
        ]),
      'live-collision' => NostrRelayIngress([
          NostrIngressRule(
            name: 'events',
            subscriptionId: 'same',
            routes: const {
              1315: NostrIngressRoute.roadEvent,
              1317: NostrIngressRoute.roadUpdate,
            },
          ),
          NostrIngressRule(
            name: 'confirmations',
            subscriptionId: 'same',
            routes: const {1316: NostrIngressRoute.confirmation},
          ),
          NostrIngressRule(
            name: 'mine',
            subscriptionId: 'same',
            routes: const {1316: NostrIngressRoute.ownConfirmation},
          ),
          NostrIngressRule(
            name: 'zaps',
            subscriptionId: 'same',
            routes: const {9735: NostrIngressRoute.zapReceipt},
          ),
        ]),
      'history' => NostrRelayIngress([
          NostrIngressRule(
            name: 'reports',
            subscriptionId: 'events',
            routes: const {
              1315: NostrIngressRoute.userReport,
              1317: NostrIngressRoute.userUpdate,
            },
            maxEvents: 2,
          ),
          NostrIngressRule(
            name: 'votes',
            subscriptionId: 'votes',
            routes: const {1316: NostrIngressRoute.userVote},
            maxEvents: 1,
          ),
        ]),
      'history-collision' => NostrRelayIngress([
          NostrIngressRule(
            name: 'reports',
            subscriptionId: 'same',
            routes: const {
              1315: NostrIngressRoute.userReport,
              1317: NostrIngressRoute.userUpdate,
            },
            maxEvents: 2,
          ),
          NostrIngressRule(
            name: 'votes',
            subscriptionId: 'same',
            routes: const {1316: NostrIngressRoute.userVote},
            maxEvents: 1,
          ),
        ]),
      'edit' => NostrRelayIngress([
          NostrIngressRule(
            name: 'edit',
            subscriptionId: 'edit',
            routes: const {1318: NostrIngressRoute.editRequest},
            maxEvents: 2,
          ),
        ]),
      'fallback' => NostrRelayIngress([
          NostrIngressRule(
            name: 'profile',
            subscriptionId: 'profile',
            fallbackRoute: NostrIngressRoute.profileMetadata,
            maxEvents: 2,
          ),
        ]),
      'visibility' => NostrRelayIngress([
          NostrIngressRule(
            name: 'visibility',
            subscriptionId: 'visibility',
            routes: const {30078: NostrIngressRoute.profileVisibility},
            maxEvents: 2,
          ),
        ]),
      'one-off' => NostrRelayIngress([
          NostrIngressRule(
            name: 'favorite-snapshot',
            subscriptionId: 'favorites',
            routes: const {30078: NostrIngressRoute.favoriteSnapshot},
            maxEvents: 4,
          ),
          NostrIngressRule(
            name: 'lightning-address',
            subscriptionId: 'lightning',
            fallbackRoute: NostrIngressRoute.lightningAddress,
            maxEvents: 10,
          ),
          NostrIngressRule(
            name: 'nwc-response',
            subscriptionId: 'nwc',
            routes: const {23195: NostrIngressRoute.nwcResponse},
          ),
          NostrIngressRule(
            name: 'zap-receipts',
            subscriptionId: 'receipts',
            routes: const {9735: NostrIngressRoute.zapReceiptQuery},
            maxEvents: 2,
          ),
        ]),
      _ => throw StateError('Unknown fixture scenario: $scenario'),
    };

List<_Step> _steps() => [
      (
        scenario: 'live',
        name: 'unknown-subscription',
        subscription: 'other',
        kind: 'int:1315',
        expected: _expected('ignore'),
      ),
      (
        scenario: 'live',
        name: 'road-event',
        subscription: 'events',
        kind: 'int:1315',
        expected: _expected('verify',
            route: 'roadEvent', rule: 'events', observed: 1),
      ),
      (
        scenario: 'live',
        name: 'road-update',
        subscription: 'events',
        kind: 'int:1317',
        expected: _expected('verify',
            route: 'roadUpdate', rule: 'events', observed: 2),
      ),
      (
        scenario: 'live',
        name: 'wrong-kind-consumes-budget',
        subscription: 'events',
        kind: 'int:1316',
        expected: _expected('rejectKind', rule: 'events', observed: 3),
      ),
      (
        scenario: 'live',
        name: 'confirmation',
        subscription: 'confirmations',
        kind: 'int:1316',
        expected: _expected('verify',
            route: 'confirmation', rule: 'confirmations', observed: 1),
      ),
      (
        scenario: 'live',
        name: 'own-confirmation',
        subscription: 'mine',
        kind: 'int:1316',
        expected: _expected('verify',
            route: 'ownConfirmation', rule: 'mine', observed: 1),
      ),
      (
        scenario: 'live',
        name: 'zap-receipt',
        subscription: 'zaps',
        kind: 'int:9735',
        expected:
            _expected('verify', route: 'zapReceipt', rule: 'zaps', observed: 1),
      ),
      (
        scenario: 'live',
        name: 'string-kind',
        subscription: 'events',
        kind: 'string:1315',
        expected: _expected('rejectKind', rule: 'events', observed: 4),
      ),
      (
        scenario: 'live',
        name: 'missing-kind',
        subscription: 'events',
        kind: 'null',
        expected: _expected('rejectKind', rule: 'events', observed: 5),
      ),
      (
        scenario: 'live',
        name: 'wrong-zap-kind',
        subscription: 'zaps',
        kind: 'int:1315',
        expected: _expected('rejectKind', rule: 'zaps', observed: 2),
      ),
      (
        scenario: 'live-collision',
        name: 'collision-confirmation-first',
        subscription: 'same',
        kind: 'int:1316',
        expected: _expected('verify',
            route: 'confirmation', rule: 'events', observed: 1),
      ),
      (
        scenario: 'live-collision',
        name: 'collision-zap',
        subscription: 'same',
        kind: 'int:9735',
        expected: _expected('verify',
            route: 'zapReceipt', rule: 'events', observed: 2),
      ),
      (
        scenario: 'live-collision',
        name: 'collision-road',
        subscription: 'same',
        kind: 'int:1315',
        expected: _expected('verify',
            route: 'roadEvent', rule: 'events', observed: 3),
      ),
      (
        scenario: 'live-collision',
        name: 'collision-update',
        subscription: 'same',
        kind: 'int:1317',
        expected: _expected('verify',
            route: 'roadUpdate', rule: 'events', observed: 4),
      ),
      (
        scenario: 'live-collision',
        name: 'collision-wrong-kind',
        subscription: 'same',
        kind: 'int:999',
        expected: _expected('rejectKind', rule: 'events', observed: 5),
      ),
      (
        scenario: 'history',
        name: 'history-unknown',
        subscription: 'other',
        kind: 'int:1315',
        expected: _expected('ignore'),
      ),
      (
        scenario: 'history',
        name: 'history-wrong-kind',
        subscription: 'events',
        kind: 'int:999',
        expected: _expected('rejectKind', rule: 'reports', observed: 1),
      ),
      (
        scenario: 'history',
        name: 'history-exact-budget',
        subscription: 'events',
        kind: 'int:1315',
        expected: _expected('verify',
            route: 'userReport', rule: 'reports', observed: 2),
      ),
      (
        scenario: 'history',
        name: 'history-over-budget',
        subscription: 'events',
        kind: 'int:1317',
        expected: _expected('limitReached', rule: 'reports', observed: 3),
      ),
      (
        scenario: 'history',
        name: 'history-remains-exhausted',
        subscription: 'events',
        kind: 'int:1315',
        expected: _expected('limitReached', rule: 'reports', observed: 4),
      ),
      (
        scenario: 'history',
        name: 'votes-independent-budget',
        subscription: 'votes',
        kind: 'int:1316',
        expected:
            _expected('verify', route: 'userVote', rule: 'votes', observed: 1),
      ),
      (
        scenario: 'history',
        name: 'limit-precedes-kind',
        subscription: 'votes',
        kind: 'int:999',
        expected: _expected('limitReached', rule: 'votes', observed: 2),
      ),
      (
        scenario: 'history-collision',
        name: 'history-collision-vote-route',
        subscription: 'same',
        kind: 'int:1316',
        expected: _expected('verify',
            route: 'userVote', rule: 'reports', observed: 1),
      ),
      (
        scenario: 'history-collision',
        name: 'history-collision-report-route',
        subscription: 'same',
        kind: 'int:1315',
        expected: _expected('verify',
            route: 'userReport', rule: 'reports', observed: 2),
      ),
      (
        scenario: 'history-collision',
        name: 'history-collision-first-budget-wins',
        subscription: 'same',
        kind: 'int:1317',
        expected: _expected('limitReached', rule: 'reports', observed: 3),
      ),
      (
        scenario: 'history-collision',
        name: 'history-collision-unknown',
        subscription: 'other',
        kind: 'int:1316',
        expected: _expected('ignore'),
      ),
      (
        scenario: 'edit',
        name: 'edit-wrong-kind',
        subscription: 'edit',
        kind: 'int:1317',
        expected: _expected('rejectKind', rule: 'edit', observed: 1),
      ),
      (
        scenario: 'edit',
        name: 'edit-exact-budget',
        subscription: 'edit',
        kind: 'int:1318',
        expected: _expected('verify',
            route: 'editRequest', rule: 'edit', observed: 2),
      ),
      (
        scenario: 'edit',
        name: 'edit-over-budget',
        subscription: 'edit',
        kind: 'int:1318',
        expected: _expected('limitReached', rule: 'edit', observed: 3),
      ),
      (
        scenario: 'fallback',
        name: 'fallback-string-kind',
        subscription: 'profile',
        kind: 'string:0',
        expected: _expected('verify',
            route: 'profileMetadata', rule: 'profile', observed: 1),
      ),
      (
        scenario: 'fallback',
        name: 'fallback-missing-kind',
        subscription: 'profile',
        kind: 'null',
        expected: _expected('verify',
            route: 'profileMetadata', rule: 'profile', observed: 2),
      ),
      (
        scenario: 'fallback',
        name: 'fallback-over-budget',
        subscription: 'profile',
        kind: 'int:0',
        expected: _expected('limitReached', rule: 'profile', observed: 3),
      ),
      (
        scenario: 'visibility',
        name: 'visibility-event',
        subscription: 'visibility',
        kind: 'int:30078',
        expected: _expected('verify',
            route: 'profileVisibility', rule: 'visibility', observed: 1),
      ),
      (
        scenario: 'visibility',
        name: 'visibility-wrong-kind',
        subscription: 'visibility',
        kind: 'int:0',
        expected: _expected('rejectKind', rule: 'visibility', observed: 2),
      ),
      (
        scenario: 'visibility',
        name: 'visibility-limit-precedes-kind',
        subscription: 'visibility',
        kind: 'int:0',
        expected: _expected('limitReached', rule: 'visibility', observed: 3),
      ),
      (
        scenario: 'one-off',
        name: 'one-off-unknown-subscription',
        subscription: 'other',
        kind: 'int:30078',
        expected: _expected('ignore'),
      ),
      (
        scenario: 'one-off',
        name: 'favorite-snapshot',
        subscription: 'favorites',
        kind: 'int:30078',
        expected: _expected('verify',
            route: 'favoriteSnapshot', rule: 'favorite-snapshot', observed: 1),
      ),
      (
        scenario: 'one-off',
        name: 'favorite-wrong-kind',
        subscription: 'favorites',
        kind: 'int:0',
        expected:
            _expected('rejectKind', rule: 'favorite-snapshot', observed: 2),
      ),
      (
        scenario: 'one-off',
        name: 'favorite-string-kind',
        subscription: 'favorites',
        kind: 'string:30078',
        expected:
            _expected('rejectKind', rule: 'favorite-snapshot', observed: 3),
      ),
      (
        scenario: 'one-off',
        name: 'favorite-exact-budget',
        subscription: 'favorites',
        kind: 'int:30078',
        expected: _expected('verify',
            route: 'favoriteSnapshot', rule: 'favorite-snapshot', observed: 4),
      ),
      (
        scenario: 'one-off',
        name: 'favorite-over-budget',
        subscription: 'favorites',
        kind: 'int:30078',
        expected:
            _expected('limitReached', rule: 'favorite-snapshot', observed: 5),
      ),
      (
        scenario: 'one-off',
        name: 'lightning-kind-zero',
        subscription: 'lightning',
        kind: 'int:0',
        expected: _expected('verify',
            route: 'lightningAddress', rule: 'lightning-address', observed: 1),
      ),
      (
        scenario: 'one-off',
        name: 'lightning-fallback-wrong-kind',
        subscription: 'lightning',
        kind: 'int:1',
        expected: _expected('verify',
            route: 'lightningAddress', rule: 'lightning-address', observed: 2),
      ),
      (
        scenario: 'one-off',
        name: 'lightning-fallback-string-kind',
        subscription: 'lightning',
        kind: 'string:0',
        expected: _expected('verify',
            route: 'lightningAddress', rule: 'lightning-address', observed: 3),
      ),
      (
        scenario: 'one-off',
        name: 'lightning-fallback-missing-kind',
        subscription: 'lightning',
        kind: 'null',
        expected: _expected('verify',
            route: 'lightningAddress', rule: 'lightning-address', observed: 4),
      ),
      (
        scenario: 'one-off',
        name: 'lightning-budget-five',
        subscription: 'lightning',
        kind: 'int:0',
        expected: _expected('verify',
            route: 'lightningAddress', rule: 'lightning-address', observed: 5),
      ),
      (
        scenario: 'one-off',
        name: 'lightning-budget-six',
        subscription: 'lightning',
        kind: 'int:0',
        expected: _expected('verify',
            route: 'lightningAddress', rule: 'lightning-address', observed: 6),
      ),
      (
        scenario: 'one-off',
        name: 'lightning-budget-seven',
        subscription: 'lightning',
        kind: 'int:0',
        expected: _expected('verify',
            route: 'lightningAddress', rule: 'lightning-address', observed: 7),
      ),
      (
        scenario: 'one-off',
        name: 'lightning-budget-eight',
        subscription: 'lightning',
        kind: 'int:0',
        expected: _expected('verify',
            route: 'lightningAddress', rule: 'lightning-address', observed: 8),
      ),
      (
        scenario: 'one-off',
        name: 'lightning-budget-nine',
        subscription: 'lightning',
        kind: 'int:0',
        expected: _expected('verify',
            route: 'lightningAddress', rule: 'lightning-address', observed: 9),
      ),
      (
        scenario: 'one-off',
        name: 'lightning-exact-budget',
        subscription: 'lightning',
        kind: 'int:0',
        expected: _expected('verify',
            route: 'lightningAddress', rule: 'lightning-address', observed: 10),
      ),
      (
        scenario: 'one-off',
        name: 'lightning-over-budget',
        subscription: 'lightning',
        kind: 'int:0',
        expected:
            _expected('limitReached', rule: 'lightning-address', observed: 11),
      ),
      (
        scenario: 'one-off',
        name: 'nwc-response',
        subscription: 'nwc',
        kind: 'int:23195',
        expected: _expected('verify',
            route: 'nwcResponse', rule: 'nwc-response', observed: 1),
      ),
      (
        scenario: 'one-off',
        name: 'nwc-wrong-kind',
        subscription: 'nwc',
        kind: 'int:23194',
        expected: _expected('rejectKind', rule: 'nwc-response', observed: 2),
      ),
      (
        scenario: 'one-off',
        name: 'nwc-string-kind',
        subscription: 'nwc',
        kind: 'string:23195',
        expected: _expected('rejectKind', rule: 'nwc-response', observed: 3),
      ),
      (
        scenario: 'one-off',
        name: 'nwc-remains-unbounded',
        subscription: 'nwc',
        kind: 'int:23195',
        expected: _expected('verify',
            route: 'nwcResponse', rule: 'nwc-response', observed: 4),
      ),
      (
        scenario: 'one-off',
        name: 'zap-receipt-query',
        subscription: 'receipts',
        kind: 'int:9735',
        expected: _expected('verify',
            route: 'zapReceiptQuery', rule: 'zap-receipts', observed: 1),
      ),
      (
        scenario: 'one-off',
        name: 'zap-receipt-wrong-kind',
        subscription: 'receipts',
        kind: 'int:9734',
        expected: _expected('rejectKind', rule: 'zap-receipts', observed: 2),
      ),
      (
        scenario: 'one-off',
        name: 'zap-receipt-over-budget',
        subscription: 'receipts',
        kind: 'int:9735',
        expected: _expected('limitReached', rule: 'zap-receipts', observed: 3),
      ),
      (
        scenario: 'one-off',
        name: 'zap-receipt-remains-exhausted',
        subscription: 'receipts',
        kind: 'int:9735',
        expected: _expected('limitReached', rule: 'zap-receipts', observed: 4),
      ),
    ];

String buildNostrIngressFixture() {
  final policies = <String, NostrRelayIngress>{};
  final lines = <String>[
    '# Roadstr Nostr relay ingress fixture v1.',
    '# scenario\tstep\tsubscription\tkind\tverdict\troute\trule\tobserved',
  ];
  for (final step in _steps()) {
    final policy = policies.putIfAbsent(
      step.scenario,
      () => _policy(step.scenario),
    );
    final actual = policy.inspect(
      subscriptionId: step.subscription,
      claimedKind: _kind(step.kind),
    );
    final actualExpected = (
      verdict: actual.verdict.name,
      route: actual.route?.name ?? '-',
      rule: actual.ruleName ?? '-',
      observed: actual.observedEvents,
    );
    if (actualExpected != step.expected) {
      throw StateError(
        'Ingress mismatch for ${step.scenario}/${step.name}: '
        '$actualExpected != ${step.expected}',
      );
    }
    lines.add([
      step.scenario,
      step.name,
      step.subscription,
      step.kind,
      step.expected.verdict,
      step.expected.route,
      step.expected.rule,
      step.expected.observed,
    ].join('\t'));
  }
  return '${lines.join('\n')}\n';
}

void main(List<String> arguments) {
  final generated = buildNostrIngressFixture();
  final output = File(outputPath);
  if (arguments.contains('--check')) {
    if (!output.existsSync() || output.readAsStringSync() != generated) {
      stderr.writeln('$outputPath is stale; regenerate it without --check.');
      exitCode = 1;
    }
    return;
  }
  output.writeAsStringSync(generated);
}
