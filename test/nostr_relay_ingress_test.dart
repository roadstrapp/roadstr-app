import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:roadstr/services/nostr_relay_ingress.dart';

import '../tools/kotlin_rewrite/generate_nostr_ingress_fixture.dart' as fixture;

void main() {
  test('committed ingress transcript matches the production policy', () {
    final generated = fixture.buildNostrIngressFixture();
    expect(File(fixture.outputPath).readAsStringSync(), generated);
    expect(
      generated
          .split('\n')
          .where((line) => line.isNotEmpty && !line.startsWith('#')),
      hasLength(60),
    );
  });

  test('recognized hostile events consume budget before kind filtering', () {
    final ingress = NostrRelayIngress([
      NostrIngressRule(
        name: 'bounded',
        subscriptionId: 'sub',
        routes: const {1315: NostrIngressRoute.roadEvent},
        maxEvents: 2,
      ),
    ]);

    final unknown = ingress.inspect(
      subscriptionId: 'other',
      claimedKind: 1315,
    );
    expect(unknown.verdict, NostrIngressVerdict.ignore);
    expect(ingress.observedFor('bounded'), 0);

    final wrongKind = ingress.inspect(
      subscriptionId: 'sub',
      claimedKind: 1,
    );
    expect(wrongKind.verdict, NostrIngressVerdict.rejectKind);
    expect(wrongKind.observedEvents, 1);

    final exactBoundary = ingress.inspect(
      subscriptionId: 'sub',
      claimedKind: 1315,
    );
    expect(exactBoundary.verdict, NostrIngressVerdict.verify);
    expect(exactBoundary.route, NostrIngressRoute.roadEvent);

    final exhausted = ingress.inspect(
      subscriptionId: 'sub',
      claimedKind: 1315,
    );
    expect(exhausted.verdict, NostrIngressVerdict.limitReached);
    expect(exhausted.route, isNull);
  });

  test('subscription collisions preserve rule and route precedence', () {
    final ingress = NostrRelayIngress([
      NostrIngressRule(
        name: 'first-budget',
        subscriptionId: 'same',
        routes: const {1315: NostrIngressRoute.roadEvent},
        maxEvents: 2,
      ),
      NostrIngressRule(
        name: 'second-route',
        subscriptionId: 'same',
        routes: const {1316: NostrIngressRoute.confirmation},
        maxEvents: 1,
      ),
    ]);

    final decision = ingress.inspect(
      subscriptionId: 'same',
      claimedKind: 1316,
    );
    expect(decision.verdict, NostrIngressVerdict.verify);
    expect(decision.route, NostrIngressRoute.confirmation);
    expect(decision.ruleName, 'first-budget');
    expect(ingress.observedFor('first-budget'), 1);
    expect(ingress.observedFor('second-route'), 0);

    final fallbackCollision = NostrRelayIngress([
      NostrIngressRule(
        name: 'fallback',
        subscriptionId: 'same',
        fallbackRoute: NostrIngressRoute.profileMetadata,
      ),
      NostrIngressRule(
        name: 'exact',
        subscriptionId: 'same',
        routes: const {1315: NostrIngressRoute.roadEvent},
      ),
    ]);
    expect(
      fallbackCollision
          .inspect(subscriptionId: 'same', claimedKind: 1315)
          .route,
      NostrIngressRoute.roadEvent,
    );
  });

  test('rules validate configuration and isolate caller-owned route maps', () {
    expect(
      () => NostrIngressRule(name: '', subscriptionId: 'sub'),
      throwsArgumentError,
    );
    expect(
      () => NostrIngressRule(name: 'empty', subscriptionId: 'sub'),
      throwsArgumentError,
    );
    expect(
      () => NostrIngressRule(
        name: 'zero',
        subscriptionId: 'sub',
        fallbackRoute: NostrIngressRoute.profileMetadata,
        maxEvents: 0,
      ),
      throwsArgumentError,
    );
    expect(
      () => NostrRelayIngress([
        NostrIngressRule(
          name: 'same',
          subscriptionId: 'one',
          fallbackRoute: NostrIngressRoute.profileMetadata,
        ),
        NostrIngressRule(
          name: 'same',
          subscriptionId: 'two',
          fallbackRoute: NostrIngressRoute.profileMetadata,
        ),
      ]),
      throwsArgumentError,
    );

    final mutable = <int, NostrIngressRoute>{
      1315: NostrIngressRoute.roadEvent,
    };
    final ingress = NostrRelayIngress([
      NostrIngressRule(
        name: 'isolated',
        subscriptionId: 'sub',
        routes: mutable,
      ),
    ]);
    mutable[1315] = NostrIngressRoute.roadUpdate;
    expect(
      ingress.inspect(subscriptionId: 'sub', claimedKind: 1315).route,
      NostrIngressRoute.roadEvent,
    );
  });
}
