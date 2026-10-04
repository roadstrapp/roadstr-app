import 'dart:convert';

enum PendingReportDisposition {
  expired,
  invalid,
  published,
  retry,
}

class PendingReportDecision {
  const PendingReportDecision({
    required this.id,
    required this.disposition,
  });

  final String id;
  final PendingReportDisposition disposition;
}

class PendingReportFlushResult {
  PendingReportFlushResult({
    required List<Map<String, dynamic>> remaining,
    required List<PendingReportDecision> decisions,
    required List<String> attemptedIds,
  })  : remaining = List.unmodifiable(remaining),
        decisions = List.unmodifiable(decisions),
        attemptedIds = List.unmodifiable(attemptedIds);

  final List<Map<String, dynamic>> remaining;
  final List<PendingReportDecision> decisions;
  final List<String> attemptedIds;
}

/// Exact Hive-facing representation of `pending_road_reports`.
///
/// Hive stores a list of JSON strings, not nested maps. A malformed string is
/// discarded independently so it cannot make every queued report unreadable.
abstract final class PendingRoadReportStorage {
  static List<Map<String, dynamic>> decode(Object? stored) => (stored as List)
      .whereType<String>()
      .map((value) {
        try {
          return jsonDecode(value) as Map<String, dynamic>;
        } catch (_) {
          return null;
        }
      })
      .whereType<Map<String, dynamic>>()
      .toList();

  static List<String> encode(Iterable<Map<String, dynamic>> entries) =>
      entries.map(jsonEncode).toList(growable: false);

  static Map<String, dynamic> entry(
    Map<String, dynamic> event,
    int expiresAt,
  ) =>
      {
        'event': event,
        'expiresAt': expiresAt,
      };
}

/// Runs one sequential offline-report flush without touching Hive or sockets.
///
/// The caller persists [PendingReportFlushResult.remaining] once, after the
/// whole pass. This mirrors the current crash behavior: if the process dies
/// before that final write, an already-published event may be retried with the
/// same Nostr ID, but no unpublished entry is lost by a partial queue commit.
Future<PendingReportFlushResult> flushPendingRoadReports({
  required Iterable<Map<String, dynamic>> pending,
  required int now,
  required bool Function(Map<String, dynamic> event) verify,
  required Future<void> Function(Map<String, dynamic> event) publish,
}) async {
  final remaining = <Map<String, dynamic>>[];
  final decisions = <PendingReportDecision>[];
  final attemptedIds = <String>[];

  for (final entry in pending) {
    final id = _debugId(entry);
    final expiresAt = entry['expiresAt'] as int? ?? 0;
    if (expiresAt <= now) {
      decisions.add(PendingReportDecision(
        id: id,
        disposition: PendingReportDisposition.expired,
      ));
      continue;
    }
    final event = (entry['event'] as Map?)?.cast<String, dynamic>();
    if (event == null || !verify(event)) {
      decisions.add(PendingReportDecision(
        id: id,
        disposition: PendingReportDisposition.invalid,
      ));
      continue;
    }
    attemptedIds.add(id);
    try {
      await publish(event);
      decisions.add(PendingReportDecision(
        id: id,
        disposition: PendingReportDisposition.published,
      ));
    } catch (_) {
      remaining.add(entry);
      decisions.add(PendingReportDecision(
        id: id,
        disposition: PendingReportDisposition.retry,
      ));
    }
  }

  return PendingReportFlushResult(
    remaining: remaining,
    decisions: decisions,
    attemptedIds: attemptedIds,
  );
}

/// The queue to persist after a flush of [snapshot].
///
/// The flush awaits the relay for every entry, and a report filed during that
/// wait is appended to Hive behind the snapshot's back. Writing only
/// [remaining] would silently drop it, so every stored entry the snapshot did
/// not contain is carried over.
List<Map<String, dynamic>> pendingReportsAfterFlush({
  required Iterable<Map<String, dynamic>> snapshot,
  required Iterable<Map<String, dynamic>> current,
  required List<Map<String, dynamic>> remaining,
}) {
  final flushed = {for (final entry in snapshot) _debugId(entry)};
  return [
    ...remaining,
    for (final entry in current)
      if (!flushed.contains(_debugId(entry))) entry,
  ];
}

String _debugId(Map<String, dynamic> entry) {
  final event = entry['event'];
  if (event is Map && event['id'] is String) return event['id'] as String;
  return '<invalid>';
}
