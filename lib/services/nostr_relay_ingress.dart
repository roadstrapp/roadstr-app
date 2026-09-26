/// Cheap, stateful admission policy for relay `EVENT` envelopes.
///
/// Relays are untrusted and their REQ filters are only hints. This boundary
/// decides whether an event belongs to a local subscription and enforces the
/// per-subscription message budget before callers spend CPU on canonical-ID
/// hashing and Schnorr verification. A `verify` verdict never means the event
/// is trusted; it means the caller may now perform cryptographic verification.
enum NostrIngressVerdict { ignore, rejectKind, verify, limitReached }

enum NostrIngressRoute {
  roadEvent,
  roadUpdate,
  confirmation,
  ownConfirmation,
  zapReceipt,
  userReport,
  userUpdate,
  userVote,
  editRequest,
  profileVisibility,
  profileMetadata,
}

class NostrIngressRule {
  NostrIngressRule({
    required this.name,
    required this.subscriptionId,
    Map<int, NostrIngressRoute> routes = const {},
    this.fallbackRoute,
    this.maxEvents,
  }) : routes = Map.unmodifiable(routes) {
    if (name.isEmpty) {
      throw ArgumentError.value(name, 'name', 'must not be empty');
    }
    if (routes.isEmpty && fallbackRoute == null) {
      throw ArgumentError('An ingress rule must expose at least one route');
    }
    if (maxEvents != null && maxEvents! <= 0) {
      throw ArgumentError.value(maxEvents, 'maxEvents', 'must be positive');
    }
  }

  final String name;
  final String subscriptionId;
  final Map<int, NostrIngressRoute> routes;

  /// Route used when the legacy caller did not locally constrain event kind.
  ///
  /// This is deliberately explicit so a future hardening change can remove a
  /// fallback without silently changing the parity fixture.
  final NostrIngressRoute? fallbackRoute;
  final int? maxEvents;
}

class NostrIngressDecision {
  const NostrIngressDecision({
    required this.verdict,
    this.route,
    this.ruleName,
    this.observedEvents = 0,
  });

  final NostrIngressVerdict verdict;
  final NostrIngressRoute? route;
  final String? ruleName;
  final int observedEvents;

  bool get shouldVerify => verdict == NostrIngressVerdict.verify;
  bool get limitReached => verdict == NostrIngressVerdict.limitReached;
}

class NostrRelayIngress {
  NostrRelayIngress(Iterable<NostrIngressRule> rules)
      : _rules = List.unmodifiable(rules) {
    final names = <String>{};
    for (final rule in _rules) {
      if (!names.add(rule.name)) {
        throw ArgumentError.value(rule.name, 'rules', 'duplicate rule name');
      }
    }
  }

  final List<NostrIngressRule> _rules;
  final Map<String, int> _counts = {};

  /// Inspects one already shape-checked relay event.
  ///
  /// If subscription ids collide, the first matching rule owns the budget,
  /// while the first matching kind route across all matching rules wins. This
  /// preserves the current Dart dispatch order even for extremely unlikely
  /// random subscription-id collisions.
  NostrIngressDecision inspect({
    required String subscriptionId,
    required Object? claimedKind,
  }) {
    final matching = <NostrIngressRule>[
      for (final rule in _rules)
        if (rule.subscriptionId == subscriptionId) rule,
    ];
    if (matching.isEmpty) {
      return const NostrIngressDecision(
        verdict: NostrIngressVerdict.ignore,
      );
    }

    final budgetRule = matching.first;
    final observed = (_counts[budgetRule.name] ?? 0) + 1;
    _counts[budgetRule.name] = observed;
    final maximum = budgetRule.maxEvents;
    if (maximum != null && observed > maximum) {
      return NostrIngressDecision(
        verdict: NostrIngressVerdict.limitReached,
        ruleName: budgetRule.name,
        observedEvents: observed,
      );
    }

    NostrIngressRoute? route;
    if (claimedKind is int) {
      for (final rule in matching) {
        route = rule.routes[claimedKind];
        if (route != null) break;
      }
    }
    if (route == null) {
      for (final rule in matching) {
        route = rule.fallbackRoute;
        if (route != null) break;
      }
    }
    if (route == null) {
      return NostrIngressDecision(
        verdict: NostrIngressVerdict.rejectKind,
        ruleName: budgetRule.name,
        observedEvents: observed,
      );
    }
    return NostrIngressDecision(
      verdict: NostrIngressVerdict.verify,
      route: route,
      ruleName: budgetRule.name,
      observedEvents: observed,
    );
  }

  int observedFor(String ruleName) => _counts[ruleName] ?? 0;
}
