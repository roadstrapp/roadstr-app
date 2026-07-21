// Nostr relay service for Roadstr — handles the full NIP-01 WebSocket lifecycle,
// road-event (kind 1315/1316) publish and subscription, geohash-based filtering,
// profile (kind 0) fetching, and NWC (NIP-47) payment via ZapService.
//
// Architecture overview:
//   - One persistent WebSocket is maintained to the "primary" relay; on error or
//     close, the service rotates to the next relay in _relays and reconnects.
//   - Road events are published to ALL relays in _publishRelays for redundancy.
//   - Received events are accumulated in _events (keyed by event ID) and never
//     fully cleared on re-subscribe — TTL expiry is handled in a background timer.
import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart' show debugPrint;
import 'package:hive/hive.dart';
import 'package:latlong2/latlong.dart';
import 'package:nostr_tools/nostr_tools.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../models/activity_notification.dart';
import '../models/road_event.dart';
import 'nostr_event_verify.dart';
import 'zap_service.dart';

// ── Nostr profile (kind 0) ────────────────────────────────────────────────────

/// Lightweight representation of a Nostr user profile decoded from a kind-0 event.
/// Only the fields relevant to Roadstr's UI are stored.
class NostrProfile {
  final String? name;
  final String? displayName;
  final String? picture;
  const NostrProfile({this.name, this.displayName, this.picture});

  /// Returns the best available display name: prefers [displayName], falls back
  /// to [name], and returns an empty string if neither is set.
  String get label => displayName?.isNotEmpty == true
      ? displayName!
      : name?.isNotEmpty == true
          ? name!
          : '';
}

/// Manages the Nostr WebSocket connection for receiving and publishing road events.
///
/// Protocol (NIP-01):
///   - Client sends `["REQ", subscriptionId, filter]` to subscribe.
///   - Relay streams `["EVENT", subscriptionId, eventJson]` messages.
///   - Relay sends `["EOSE", subscriptionId]` when historical events are exhausted.
///   - Client sends `["CLOSE", subscriptionId]` to unsubscribe.
///   - Client sends `["EVENT", eventJson]` to publish; relay responds `["OK", ...]`.
class NostrRelayService {
  final _eventApi = EventApi();
  static const _maxInboundMessageChars = 256 * 1024;
  static const _maxCachedEvents = 1000;

  /// Primary relays used for subscriptions (tried in round-robin order on
  /// failure). NB: relay.nostr.band was deliberately REMOVED — this is the
  /// persistent socket that carries the user's geohash area subscriptions
  /// (a coarse live-location beacon), and nostr.band feeds a public search
  /// indexer: the one place that trail must never land.
  static const _relays = [
    'wss://relay.damus.io',
    'wss://nos.lol',
  ];

  /// Additional relays used only for publishing (fire-and-forget) to maximise
  /// event propagation across the Nostr network.
  static const _publishRelays = [
    'wss://relay.damus.io',
    'wss://nos.lol',
  ];

  // ── state ─────────────────────────────────────────────────────────────────

  /// In-memory cache of received road events, keyed by event ID.
  /// Intentionally NOT cleared on re-subscribe: valid events remain visible on
  /// the map during the brief gap between sending CLOSE and receiving new EVENTs.
  /// Expired events are purged by the [_cleanupTimer] every 2 minutes.
  final _events = <String, RoadEvent>{};
  final _controller = StreamController<List<RoadEvent>>.broadcast();

  WebSocketChannel? _ws;
  StreamSubscription<dynamic>? _wsSub;
  Timer? _reconnectTimer;

  /// Debounce timer that delays sending the confirmation (kind-1316) REQ until
  /// all kind-1315 historical events have been received (after EOSE).
  Timer? _confTimer;
  Timer? _cleanupTimer;

  /// Index into [_relays] for round-robin failover.
  int _relayIdx = 0;
  bool _connected = false;
  bool _disposed = false;

  /// NIP-01 subscription IDs so we can send CLOSE before a new REQ.
  String _eventsSubId = '';
  String _confSubId = '';

  /// The geohash (precision 4) of the last subscribed area.
  /// Compared on each [subscribeArea] call to avoid redundant REQ messages.
  List<String> _lastGeohashes = [];

  /// IDs of kind-1315 events received in the current batch; used to build the
  /// follow-up kind-1316 confirmation REQ after EOSE.
  final _pendingIds = <String>{};

  /// "eventId:pubkey" pairs whose confirmation/denial has been counted.
  /// Enforces one vote per identity per event and makes re-fetched
  /// confirmations idempotent. Pruned together with expired events.
  final _countedVotes = <String>{};
  final _publishAcks = <String, Completer<bool>>{};

  // ── activity notifications (zaps + confirmations/denials of MY reports) ────
  // Separate from the geohash road-event stream above: this is about the
  // logged-in user's OWN activity, not nearby traffic, so it uses its own
  // subscriptions and its own dedupe/cursor bookkeeping.
  final _activityController =
      StreamController<ActivityNotification>.broadcast();
  String _zapSubId = '';
  String _myConfSubId = '';
  String? _myPubKeyForNotif;
  String? _myZapSigner;
  Set<String> _myEventIds = {};
  Map<String, RoadEvent> _myEventsById = {};
  int _lastZapNotifiedAt = 0;
  int _lastConfNotifiedAt = 0;
  // Session-only dedupe: a reconnect re-requests with `since` = the last
  // persisted cursor, which can redeliver the boundary event. Never persisted
  // — an occasional duplicate notification across app restarts is harmless,
  // unlike the unbounded memory growth of persisting every id forever.
  final _notifiedEventIds = <String>{};

  Box get _box => Hive.box('settings');

  String _activityCursorKey(String kind, String pubkey) =>
      'activity_${kind}_cursor_$pubkey';

  // ── public API ─────────────────────────────────────────────────────────────

  Stream<List<RoadEvent>> get stream => _controller.stream;

  /// Notifications about the logged-in user's own activity: zaps received,
  /// and confirmations/denials of their own road reports. Empty stream until
  /// [enableActivityNotifications] is called.
  Stream<ActivityNotification> get activityStream => _activityController.stream;

  /// Returns all non-expired events currently in the in-memory cache.
  List<RoadEvent> get currentEvents =>
      _events.values.where((e) => !e.isExpired).toList();

  /// Opens a WebSocket connection to the next relay in the round-robin list.
  ///
  /// If a previous subscription existed, it is replayed on the new connection
  /// so the caller does not need to call [subscribeArea] again after reconnect.
  Future<void> connect() async {
    if (_disposed) return;
    await _wsSub?.cancel();
    _ws?.sink.close().catchError((_) {});
    _connected = false;
    // Remove stale events every 2 minutes without waiting for a new GPS position.
    _cleanupTimer?.cancel();
    _cleanupTimer =
        Timer.periodic(const Duration(minutes: 2), (_) => _removeExpired());

    final url = _relays[_relayIdx % _relays.length];
    try {
      if (_disposed) return;
      _ws = WebSocketChannel.connect(Uri.parse(url));
      _wsSub = _ws!.stream.listen(
        _onMessage,
        onError: (_) => _scheduleReconnect(),
        onDone: _scheduleReconnect,
        cancelOnError: false,
      );
      _connected = true;
      // Replay the last area subscription if the app already had one active.
      if (_lastGeohashes.isNotEmpty) _sendEventsReq(_lastGeohashes);
      if (_myPubKeyForNotif != null) _sendMyNotificationReqs();
    } catch (_) {
      _scheduleReconnect();
    }
  }

  /// Enables live notifications for [pubKeyHex]'s own activity: zaps received
  /// and confirmations/denials of their road reports. Call once after login
  /// (Amber or nsec) — never for a logged-out/anonymous user. Best-effort:
  /// any resolution failure here just means notifications don't fire, never
  /// a crash or a blocked UI.
  Future<void> enableActivityNotifications(String pubKeyHex) async {
    if (_disposed || !_isHex32(pubKeyHex)) return;
    if (_myPubKeyForNotif != null && _myPubKeyForNotif != pubKeyHex) {
      disableActivityNotifications();
      _notifiedEventIds.clear();
    }
    _myPubKeyForNotif = pubKeyHex;
    // First-ever activation: seed cursors at "now" so years of past zaps and
    // confirmations don't all fire as notifications at once. Later launches
    // resume from the last event actually shown.
    final zapCursorKey = _activityCursorKey('zap', pubKeyHex);
    final storedZap = _box.get(zapCursorKey) as int?;
    _lastZapNotifiedAt = storedZap ?? _nowS();
    if (storedZap == null) _box.put(zapCursorKey, _lastZapNotifiedAt);
    final confCursorKey = _activityCursorKey('confirmation', pubKeyHex);
    final storedConf = _box.get(confCursorKey) as int?;
    _lastConfNotifiedAt = storedConf ?? _nowS();
    if (storedConf == null) _box.put(confCursorKey, _lastConfNotifiedAt);

    // Zap receipts are only trusted when signed by the exact LNURL provider
    // key this user's Lightning address advertises — same rule fetchBalance
    // uses. Resolved once per session; a transient failure here just means
    // zap notifications silently don't fire (confirmations still can).
    _myZapSigner = await ZapService.resolveZapSigner(pubKeyHex);

    // Seed "my own report" ids via a NIP-01 authors filter (kind-1315,
    // 30 days — the longest report TTL) so kind-1316 confirmations can be
    // matched without requiring a `p` tag on the confirming event (older
    // clients/versions of Roadstr never added one).
    final myEvents = await fetchUserEvents(pubKeyHex, limit: 200);
    _myEventsById = {for (final e in myEvents) e.id: e};
    _myEventIds = _myEventsById.keys.toSet();

    if (_connected) _sendMyNotificationReqs();
  }

  /// Disables activity notifications (e.g. on logout). Best-effort CLOSE of
  /// the live subscriptions; safe to call even if never enabled.
  void disableActivityNotifications() {
    if (_zapSubId.isNotEmpty) _send(['CLOSE', _zapSubId]);
    if (_myConfSubId.isNotEmpty) _send(['CLOSE', _myConfSubId]);
    _zapSubId = '';
    _myConfSubId = '';
    _myPubKeyForNotif = null;
    _myZapSigner = null;
    _myEventIds = {};
    _myEventsById = {};
  }

  void _sendMyNotificationReqs() {
    final pub = _myPubKeyForNotif;
    if (pub == null) return;
    _zapSubId = randomSubId();
    _send([
      'REQ',
      _zapSubId,
      {
        'kinds': [9735],
        '#p': [pub],
        'since': _lastZapNotifiedAt,
      }
    ]);
    if (_myEventIds.isNotEmpty) {
      _myConfSubId = randomSubId();
      _send([
        'REQ',
        _myConfSubId,
        {
          'kinds': [1316],
          '#e': _myEventIds.toList(),
          'since': _lastConfNotifiedAt,
        }
      ]);
    }
  }

  /// Subscribes to road events (kind 1315) within the geohash cell that contains
  /// [center].
  ///
  /// **Why ONLY geohash level 4 (~40 × 20 km)?**
  /// Every published event is tagged with its own g4, g5 AND g6 (see
  /// publishRoadEvent), and a same-g5 event necessarily shares our g4 (a
  /// geohash is a prefix code) — so filtering on g4 alone already matches
  /// every event the old [g4, g5] filter matched. Including g5 added ZERO
  /// recall while telling the relay where the user is to ~5 × 5 km on every
  /// cell crossing. This REQ is effectively a live location beacon; g4-only
  /// makes it 8× coarser (city-level) and re-fires ~8× less often. Do not
  /// "improve" precision here without re-reading this.
  ///
  /// **Why _events is NOT cleared here:**
  /// Clearing the map would cause all map markers to disappear briefly while the
  /// relay sends new EVENTs. Instead, valid events accumulate; the 2-minute
  /// cleanup timer removes expired ones.
  void subscribeArea(LatLng center) {
    if (_disposed) return;
    final g4 = _gh(center.latitude, center.longitude, 4);
    // Skip if the user is still inside the same geohash cell as before.
    if (_lastGeohashes.length == 1 && _lastGeohashes[0] == g4) return;
    _lastGeohashes = [g4];
    _pendingIds.clear();
    // Keep nearby markers during the relay round-trip, but discard areas from
    // earlier legs of a long journey so the map/cache cannot grow for 30 days.
    const distance = Distance();
    _events.removeWhere((_, event) =>
        distance.as(LengthUnit.Kilometer, center, event.position) > 100);
    _pruneVotes();
    if (_connected) _sendEventsReq(_lastGeohashes);
  }

  /// Publishes a kind-1315 road-event and immediately returns a local [RoadEvent]
  /// so the map marker appears without waiting for the relay echo.
  ///
  /// The event is also sent to all [_publishRelays] for redundancy (NIP-01).
  Future<RoadEvent> publishRoadEvent({
    required LatLng position,
    required RoadCategory category,
    required String comment,
    required String privKeyHex,
    required String pubKeyHex,
    int? speedLimit,
  }) async {
    _requireConnected();
    _validatePublishInput(position, comment, pubKeyHex);
    final now = _nowS();
    final expires = now + category.ttlSeconds;
    final signed = _eventApi.finishEvent(
      Event(
        pubkey: pubKeyHex,
        created_at: now,
        kind: 1315,
        tags: [
          ['lat', position.latitude.toStringAsFixed(7)],
          ['lon', position.longitude.toStringAsFixed(7)],
          ['g', _gh(position.latitude, position.longitude, 4)],
          ['g', _gh(position.latitude, position.longitude, 5)],
          ['g', _gh(position.latitude, position.longitude, 6)],
          ['t', category.nostrKey],
          ['expiration', '$expires'],
          if (speedLimit != null) ['maxspeed', '$speedLimit'],
        ],
        content: comment,
      ),
      privKeyHex,
    );
    if (!verifyEventJson(signed.toJson())) {
      throw const FormatException('The local Nostr key pair does not match');
    }
    await _publishEvent(signed.toJson());
    // Return the local event with the same ID the relay will assign.
    return RoadEvent(
      id: signed.id,
      pubkey: pubKeyHex,
      category: category,
      position: position,
      comment: comment,
      createdAt: now,
      expiresAt: expires,
      speedLimit: speedLimit,
    );
  }

  /// Publishes a kind-1316 confirmation or denial for an existing road event.
  ///
  /// Kind 1316 is a Roadstr-specific event that references a kind-1315 event via
  /// the `["e", eventId]` tag and carries a `status` tag of either
  /// `"still_there"` or `"no_longer_there"`. The relay service accumulates these
  /// counts in the [RoadEvent.confirmations] / [RoadEvent.denials] fields.
  Future<void> confirmRoadEvent({
    required String eventId,
    required bool stillThere,
    required String privKeyHex,
    required String pubKeyHex,
  }) async {
    _requireConnected();
    if (!_isHex32(eventId) || !_isHex32(pubKeyHex)) {
      throw const FormatException('Invalid Nostr confirmation fields');
    }
    final signed = _eventApi.finishEvent(
      Event(
        pubkey: pubKeyHex,
        created_at: _nowS(),
        kind: 1316,
        tags: [
          ['e', eventId],
          ['status', stillThere ? 'still_there' : 'no_longer_there'],
        ],
        content: '',
      ),
      privKeyHex,
    );
    if (!verifyEventJson(signed.toJson())) {
      throw const FormatException('The local Nostr key pair does not match');
    }
    await _publishEvent(signed.toJson());
  }

  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _connected = false;
    _reconnectTimer?.cancel();
    _confTimer?.cancel();
    _cleanupTimer?.cancel();
    _wsSub?.cancel();
    _ws?.sink.close().catchError((_) {});
    for (final ack in _publishAcks.values) {
      if (!ack.isCompleted) ack.complete(false);
    }
    _publishAcks.clear();
    _controller.close();
    _activityController.close();
  }

  /// Removes expired events from the cache and pushes an updated list downstream.
  void _removeExpired() {
    if (_disposed) return;
    final before = _events.length;
    _events.removeWhere((_, ev) => ev.isExpired);
    if (_events.length != before) {
      // Drop vote records for events that no longer exist.
      _pruneVotes();
      if (!_controller.isClosed) _controller.add(currentEvents);
    }
  }

  // ── internals ─────────────────────────────────────────────────────────────

  void _requireConnected() {
    if (!_connected || _ws == null) {
      throw Exception('Not connected to Nostr relay');
    }
  }

  /// Increments [_relayIdx] so the next [connect] call uses a different relay.
  void _scheduleReconnect() {
    if (_disposed) return;
    _connected = false;
    _reconnectTimer?.cancel();
    _relayIdx++;
    _reconnectTimer = Timer(const Duration(seconds: 5), connect);
  }

  void _send(dynamic msg) {
    if (_disposed) return;
    try {
      _ws?.sink.add(jsonEncode(msg));
    } catch (_) {}
  }

  /// Publishes to the persistent relay and all secondary relays concurrently.
  /// Success means at least one relay returned a positive NIP-01 `OK`; merely
  /// writing bytes to a socket is not reported to the UI as a publication.
  Future<void> _publishEvent(Map<String, dynamic> eventJson) async {
    final id = eventJson['id'] as String;
    final primaryAck = Completer<bool>();
    _publishAcks[id] = primaryAck;
    _send(['EVENT', eventJson]);

    final attempts = <Future<bool>>[
      primaryAck.future.timeout(
        const Duration(seconds: 5),
        onTimeout: () => false,
      ),
    ];
    for (final url in _publishRelays) {
      if (url == _relays[_relayIdx % _relays.length]) {
        continue;
      }
      attempts.add(_publishToRelay(url, eventJson));
    }
    try {
      final accepted = (await Future.wait(attempts)).any((ok) => ok);
      if (!accepted) throw Exception('All Nostr relays rejected or timed out');
    } finally {
      _publishAcks.remove(id);
    }
  }

  Future<bool> _publishToRelay(
      String url, Map<String, dynamic> eventJson) async {
    WebSocketChannel? ws;
    StreamSubscription<dynamic>? subscription;
    try {
      ws = WebSocketChannel.connect(Uri.parse(url));
      final ack = Completer<bool>();
      subscription = ws.stream.listen(
        (raw) {
          if (ack.isCompleted ||
              raw is! String ||
              raw.length > _maxInboundMessageChars) {
            return;
          }
          try {
            final msg = jsonDecode(raw) as List;
            if (msg.length >= 3 &&
                msg[0] == 'OK' &&
                msg[1] == eventJson['id']) {
              ack.complete(msg[2] == true);
            }
          } catch (_) {}
        },
        onError: (_) {
          if (!ack.isCompleted) ack.complete(false);
        },
        onDone: () {
          if (!ack.isCompleted) ack.complete(false);
        },
      );
      await ws.ready.timeout(const Duration(seconds: 5));
      ws.sink.add(jsonEncode(['EVENT', eventJson]));
      return await ack.future.timeout(
        const Duration(seconds: 5),
        onTimeout: () => false,
      );
    } catch (_) {
      return false;
    } finally {
      await subscription?.cancel();
      ws?.sink.close().catchError((_) {});
    }
  }

  /// Sends a NIP-01 REQ for kind-1315 events tagged with [geohashes].
  /// Closes any previous events subscription first to avoid duplicate messages.
  void _sendEventsReq(List<String> geohashes) {
    if (_eventsSubId.isNotEmpty) _send(['CLOSE', _eventsSubId]);
    _eventsSubId = randomSubId();
    _send([
      'REQ',
      _eventsSubId,
      {
        'kinds': [1315],
        '#g': geohashes,
        // Speed-camera reports have the longest category TTL (30 days).
        'since': _nowS() - 30 * 86400,
        'limit': 500,
      }
    ]);
  }

  /// Sends a NIP-01 REQ for kind-1316 confirmation/denial events that reference
  /// the given [ids]. Called after EOSE for the events subscription.
  void _sendConfReq(List<String> ids) {
    if (ids.isEmpty) return;
    if (_confSubId.isNotEmpty) _send(['CLOSE', _confSubId]);
    _confSubId = randomSubId();
    _send([
      'REQ',
      _confSubId,
      {
        'kinds': [1316],
        '#e': ids,
        'since': _nowS() - 30 * 86400,
        'limit': 1000,
      }
    ]);
  }

  /// Dispatches incoming NIP-01 relay messages to the appropriate handler.
  ///
  /// Message types handled:
  /// - `OK`    — logs relay rejections (accepted == false).
  /// - `NOTICE`— logs relay informational messages.
  /// - `EVENT` — routes kind-1315 to [_handleRoadEvent] and kind-1316 to
  ///             [_handleConfirmation].
  /// - `EOSE`  — after receiving all historical events for the events sub,
  ///             schedules a confirmation REQ with a 300 ms debounce so that
  ///             back-to-back EOSE signals don't trigger multiple conf REQs.
  void _onMessage(dynamic raw) {
    try {
      if (_disposed || raw is! String || raw.length > _maxInboundMessageChars) {
        return;
      }
      final msg = jsonDecode(raw) as List;
      if (msg.isEmpty) return;
      switch (msg[0] as String) {
        case 'OK':
          // Format: ['OK', event_id, accepted, reason]
          if (msg.length >= 3 && msg[1] is String) {
            final ack = _publishAcks[msg[1]];
            if (ack != null && !ack.isCompleted) ack.complete(msg[2] == true);
          }
          if (msg.length >= 3 && msg[2] == false) {
            debugPrint(
                '[Nostr] Relay rejected event ${msg[1]}: ${msg.length > 3 ? msg[3] : ""}');
          }
        case 'NOTICE':
          debugPrint(
              '[Nostr] NOTICE from relay: ${msg.length > 1 ? msg[1] : ""}');
        case 'EVENT':
          if (msg.length < 3) return;
          final json = (msg[2] as Map).cast<String, dynamic>();
          // Relays are untrusted: drop events whose id doesn't match the
          // canonical hash or whose Schnorr signature is invalid. Without
          // this check a malicious relay could fabricate road events or
          // confirmations attributed to any pubkey.
          if (!_verifyEvent(json)) return;
          switch (json['kind'] as int) {
            case 1315:
              if (msg[1] != _eventsSubId) return;
              _handleRoadEvent(json);
            case 1316:
              if (msg[1] == _confSubId) {
                _handleConfirmation(json);
              } else if (msg[1] == _myConfSubId) {
                _handleMyConfirmation(json);
              }
            case 9735:
              if (msg[1] != _zapSubId) return;
              _handleZapReceipt(json);
          }
        case 'EOSE':
          if (msg[1] == _eventsSubId) {
            // All historical kind-1315 events received; now fetch their confirmations.
            // The 300 ms delay lets any in-flight EVENTs arrive before we build the
            // confirmation filter from [_pendingIds].
            _confTimer?.cancel();
            _confTimer = Timer(const Duration(milliseconds: 300), () {
              _sendConfReq(List.from(_pendingIds));
              _pendingIds.clear();
            });
          }
      }
    } catch (_) {}
  }

  /// Recomputes the event id from the canonical serialization and verifies
  /// the BIP-340 signature against the claimed pubkey. Any event that fails
  /// is discarded — relay data must never be taken at face value.
  bool _verifyEvent(Map<String, dynamic> json) => verifyEventJson(json);

  void _handleRoadEvent(Map<String, dynamic> json) {
    final event = RoadEvent.fromNostr(json);
    if (event == null) return;
    final geohashes = (json['tags'] as List)
        .whereType<List>()
        .where((tag) => tag.length >= 2 && tag[0] == 'g')
        .map((tag) => tag[1].toString())
        .toSet();
    if (!geohashes.contains(
            _gh(event.position.latitude, event.position.longitude, 4)) ||
        !geohashes.contains(
            _gh(event.position.latitude, event.position.longitude, 5)) ||
        !geohashes.contains(
            _gh(event.position.latitude, event.position.longitude, 6))) {
      return;
    }
    _events[event.id] = event;
    if (_events.length > _maxCachedEvents) {
      final oldest =
          _events.values.reduce((a, b) => a.createdAt <= b.createdAt ? a : b);
      _events.remove(oldest.id);
      _pruneVotes();
    }
    _pendingIds.add(event.id);
    if (!_controller.isClosed) _controller.add(currentEvents);
  }

  void _handleConfirmation(Map<String, dynamic> json) {
    final tags = (json['tags'] as List)
        .map((t) => List<String>.from(t as List))
        .toList();
    final targetIds = <String>[];
    final statuses = <String>[];
    for (final t in tags) {
      if (t.length < 2) continue;
      if (t[0] == 'e') targetIds.add(t[1]);
      if (t[0] == 'status') statuses.add(t[1]);
    }
    if (targetIds.length != 1 || statuses.length != 1) return;
    final targetId = targetIds.single;
    final status = statuses.single;
    if (status != 'still_there' && status != 'no_longer_there') return;
    final ev = _events[targetId];
    if (ev == null) return;
    // One vote per pubkey per event: prevents a single identity from
    // inflating counts, and prevents double-counting when confirmations are
    // re-fetched after every re-subscription.
    final voter = '$targetId:${json['pubkey']}';
    if (!_countedVotes.add(voter)) return;
    if (status == 'still_there') {
      ev.confirmations++;
    } else if (status == 'no_longer_there') {
      ev.denials++;
    }
    if (!_controller.isClosed) _controller.add(currentEvents);
  }

  /// Handles a kind-1316 confirmation/denial of one of the LOGGED-IN USER'S
  /// OWN reports (matched against [_myEventIds], independent of the nearby-
  /// events cache used by [_handleConfirmation]). Emits an [ActivityNotification]
  /// at most once per event id per session.
  void _handleMyConfirmation(Map<String, dynamic> json) {
    final tags = (json['tags'] as List)
        .map((t) => List<String>.from(t as List))
        .toList();
    String? targetId, status;
    for (final t in tags) {
      if (t.length < 2) continue;
      if (t[0] == 'e') targetId = t[1];
      if (t[0] == 'status') status = t[1];
    }
    if (targetId == null || status == null) return;
    if (status != 'still_there' && status != 'no_longer_there') return;
    final myEvent = _myEventsById[targetId];
    if (myEvent == null) return;
    // A self-confirmation of your own report (however it happened) is not
    // "someone confirmed you" and must never self-notify.
    if (json['pubkey'] == myEvent.pubkey) return;
    final id = json['id'] as String?;
    if (id == null || !_notifiedEventIds.add(id)) return;
    final createdAt = json['created_at'] as int? ?? 0;
    if (createdAt > _lastConfNotifiedAt) {
      _lastConfNotifiedAt = createdAt;
      final pub = _myPubKeyForNotif;
      if (pub != null) {
        _box.put(_activityCursorKey('confirmation', pub), createdAt);
      }
    }
    if (_activityController.isClosed) return;
    _activityController.add(status == 'still_there'
        ? ActivityNotification.confirmed(
            id: id, createdAt: createdAt, category: myEvent.category)
        : ActivityNotification.denied(
            id: id, createdAt: createdAt, category: myEvent.category));
  }

  /// Handles a kind-9735 NIP-57 zap receipt addressed to the logged-in user
  /// (`#p` filter). Trusts only receipts signed by the exact LNURL provider
  /// key resolved for this user — see [ZapService.verifiedReceiptAmount].
  void _handleZapReceipt(Map<String, dynamic> json) {
    final signer = _myZapSigner;
    final pub = _myPubKeyForNotif;
    if (signer == null || pub == null) return;
    final amountMsat = ZapService.verifiedReceiptAmount(
      json,
      recipientPub: pub,
      receiptSigner: signer,
    );
    if (amountMsat == null) return;
    final id = json['id'] as String?;
    if (id == null || !_notifiedEventIds.add(id)) return;
    final createdAt = json['created_at'] as int? ?? 0;
    if (createdAt > _lastZapNotifiedAt) {
      _lastZapNotifiedAt = createdAt;
      _box.put(_activityCursorKey('zap', pub), createdAt);
    }
    if (_activityController.isClosed) return;
    _activityController.add(ActivityNotification.zap(
        id: id, createdAt: createdAt, amountSat: amountMsat ~/ 1000));
  }

  void _pruneVotes() {
    _countedVotes.removeWhere((vote) {
      final separator = vote.indexOf(':');
      return separator <= 0 ||
          !_events.containsKey(vote.substring(0, separator));
    });
  }

  // ── Geohash encoder (pure Dart, no external dependencies) ───────────────

  /// Base-32 alphabet used by the geohash standard (Niemeyer, 2008).
  static const _gh32 = '0123456789bcdefghjkmnpqrstuvwxyz';

  /// Encodes [lat]/[lon] to a geohash string of [precision] characters.
  ///
  /// Algorithm: interleave longitude bits (even positions) and latitude bits
  /// (odd positions), then encode each group of 5 bits as a base-32 character.
  /// A precision-4 hash covers ~40 × 20 km; precision-5 covers ~5 × 5 km;
  /// precision-6 covers ~1.2 × 0.6 km.
  static String _gh(double lat, double lon, int precision) {
    var minLat = -90.0, maxLat = 90.0;
    var minLon = -180.0, maxLon = 180.0;
    // Start with longitude (even bit index = 0, 2, 4 …)
    var isLon = true, bits = 0, count = 0;
    final buf = StringBuffer();
    while (buf.length < precision) {
      if (isLon) {
        final mid = (minLon + maxLon) / 2;
        if (lon >= mid) {
          bits = (bits << 1) | 1;
          minLon = mid;
        } else {
          bits = bits << 1;
          maxLon = mid;
        }
      } else {
        final mid = (minLat + maxLat) / 2;
        if (lat >= mid) {
          bits = (bits << 1) | 1;
          minLat = mid;
        } else {
          bits = bits << 1;
          maxLat = mid;
        }
      }
      isLon = !isLon;
      // Every 5 bits form one base-32 character.
      if (++count == 5) {
        buf.write(_gh32[bits]);
        bits = 0;
        count = 0;
      }
    }
    return buf.toString();
  }

  static int _nowS() => DateTime.now().millisecondsSinceEpoch ~/ 1000;

  /// Builds a kind-1315 event map with its computed ID but **without a signature**.
  /// Used when Amber (NIP-55) is the active signing method — the unsigned event is
  /// passed to Amber via `startActivityForResult`, and Amber returns the signed JSON.
  static Map<String, dynamic> buildKind1315Map({
    required LatLng position,
    required RoadCategory category,
    required String comment,
    required String pubKeyHex,
    required int now,
    required int expires,
    int? speedLimit,
  }) {
    final event = Event(
      pubkey: pubKeyHex,
      created_at: now,
      kind: 1315,
      tags: [
        ['lat', position.latitude.toStringAsFixed(7)],
        ['lon', position.longitude.toStringAsFixed(7)],
        ['g', _gh(position.latitude, position.longitude, 4)],
        ['g', _gh(position.latitude, position.longitude, 5)],
        ['g', _gh(position.latitude, position.longitude, 6)],
        ['t', category.nostrKey],
        ['expiration', '$expires'],
        if (speedLimit != null) ['maxspeed', '$speedLimit'],
      ],
      content: comment,
    );
    event.id = EventApi().getEventHash(event);
    return event.toJson();
  }

  /// Builds an unsigned kind-1316 confirmation/denial map for external signing.
  /// The `status` tag carries either `"still_there"` or `"no_longer_there"`.
  static Map<String, dynamic> buildKind1316Map({
    required String eventId,
    required bool stillThere,
    required String pubKeyHex,
  }) {
    final event = Event(
      pubkey: pubKeyHex,
      created_at: _nowS(),
      kind: 1316,
      tags: [
        ['e', eventId],
        ['status', stillThere ? 'still_there' : 'no_longer_there'],
      ],
      content: '',
    );
    event.id = EventApi().getEventHash(event);
    return event.toJson();
  }

  /// Publishes an already-signed kind-1315 event (e.g. from Amber) and returns
  /// the corresponding local [RoadEvent] for immediate map display.
  Future<RoadEvent> publishRawRoadEvent({
    required Map<String, dynamic> eventJson,
    required RoadCategory category,
    required LatLng position,
    required String comment,
    required int now,
    required int expires,
    required String expectedPubKeyHex,
  }) async {
    _requireConnected();
    if (!verifyEventJson(eventJson) || eventJson['kind'] != 1315) {
      throw const FormatException('Signer returned an invalid road event');
    }
    final parsed = RoadEvent.fromNostr(eventJson);
    if (parsed == null ||
        parsed.category != category ||
        const Distance().as(LengthUnit.Meter, parsed.position, position) > 1 ||
        parsed.comment != comment ||
        parsed.pubkey != expectedPubKeyHex ||
        parsed.createdAt != now ||
        parsed.expiresAt != expires) {
      throw const FormatException('Signer changed the road report payload');
    }
    // Same redundancy as the nsec path (publishRoadEvent): all publish relays,
    // not just the primary — Amber users' reports must propagate equally well.
    await _publishEvent(eventJson);
    return RoadEvent(
      id: eventJson['id'] as String,
      pubkey: eventJson['pubkey'] as String,
      category: category,
      position: position,
      comment: comment,
      createdAt: now,
      expiresAt: expires,
      speedLimit: parsed.speedLimit,
    );
  }

  /// Publishes an externally-signed event only if the signer preserved every
  /// canonical field of the event that Roadstr asked it to sign.
  Future<void> publishRawEvent(
    Map<String, dynamic> eventJson, {
    required Map<String, dynamic> expectedUnsigned,
  }) async {
    _requireConnected();
    if (!verifyEventJson(eventJson)) {
      throw const FormatException('Signer returned an invalid Nostr event');
    }
    for (final field in const ['pubkey', 'created_at', 'kind', 'content']) {
      if (eventJson[field] != expectedUnsigned[field]) {
        throw const FormatException('Signer changed the Nostr event payload');
      }
    }
    if (jsonEncode(eventJson['tags']) != jsonEncode(expectedUnsigned['tags'])) {
      throw const FormatException('Signer changed the Nostr event tags');
    }
    await _publishEvent(eventJson);
  }

  static bool _isHex32(String value) =>
      RegExp(r'^[0-9a-fA-F]{64}$').hasMatch(value);

  static void _validatePublishInput(
      LatLng position, String comment, String pubKeyHex) {
    if (!position.latitude.isFinite ||
        !position.longitude.isFinite ||
        position.latitude < -90 ||
        position.latitude > 90 ||
        position.longitude < -180 ||
        position.longitude > 180 ||
        comment.length > 200 ||
        !_isHex32(pubKeyHex)) {
      throw const FormatException('Invalid road report fields');
    }
  }

  /// Fetches all kind-1315 road events published by [pubHex] from a public relay,
  /// then enriches each event with its kind-1316 confirmation/denial counts.
  ///
  /// Uses two sequential subscriptions:
  ///   1. Fetch events (`evSub`) — wait for EOSE.
  ///   2. Fetch confirmations (`confSub`) filtered by the collected event IDs.
  static Future<List<RoadEvent>> fetchUserEvents(String pubHex,
      {int limit = 100}) async {
    if (!_isHex32(pubHex)) return [];
    final safeLimit = limit.clamp(1, 500);
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse('wss://relay.damus.io'));
      final events = <RoadEvent>[];
      final eventIds = <String>{};
      // One vote per pubkey per event, and dedupe re-delivered events —
      // same anti-inflation rules the live stream enforces via _countedVotes.
      final countedVotes = <String>{};
      final completer = Completer<List<RoadEvent>>();
      final evSub = randomSubId();
      final confSub = randomSubId();
      var confirmationRequested = false;

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            if (raw is! String || raw.length > _maxInboundMessageChars) return;
            final msg = jsonDecode(raw) as List;
            if (msg[0] == 'EVENT') {
              final json = (msg[2] as Map).cast<String, dynamic>();
              // Same trust rule as the live subscription: verify id + signature
              // before using anything a relay sends.
              if (!verifyEventJson(json)) return;
              if (msg[1] == evSub && json['kind'] == 1315) {
                final ev = RoadEvent.fromNostr(json);
                if (ev != null && eventIds.add(ev.id)) {
                  events.add(ev);
                }
              } else if (msg[1] == confSub && json['kind'] == 1316) {
                final tags = (json['tags'] as List)
                    .map((t) => List<String>.from(t as List))
                    .toList();
                final targetIds = <String>[];
                final statuses = <String>[];
                for (final t in tags) {
                  if (t.length < 2) continue;
                  if (t[0] == 'e') targetIds.add(t[1]);
                  if (t[0] == 'status') statuses.add(t[1]);
                }
                if (targetIds.length != 1 || statuses.length != 1) return;
                final targetId = targetIds.single;
                final status = statuses.single;
                if (status != 'still_there' && status != 'no_longer_there') {
                  return;
                }
                final idx = events.indexWhere((e) => e.id == targetId);
                if (idx < 0) return;
                if (!countedVotes.add('$targetId:${json['pubkey']}')) return;
                if (status == 'still_there') {
                  events[idx].confirmations++;
                } else if (status == 'no_longer_there') {
                  events[idx].denials++;
                }
              }
            } else if (msg[0] == 'EOSE') {
              if (msg[1] == evSub) {
                if (confirmationRequested) return;
                confirmationRequested = true;
                if (eventIds.isEmpty) {
                  completer.complete(events);
                  return;
                }
                ws?.sink.add(jsonEncode([
                  'REQ',
                  confSub,
                  {
                    'kinds': [1316],
                    '#e': eventIds.toList(),
                    'limit': 500,
                  }
                ]));
              } else if (msg[1] == confSub) {
                if (!completer.isCompleted) completer.complete(events);
              }
            }
          } catch (_) {}
        },
        onError: (_) {
          if (!completer.isCompleted) completer.complete(events);
        },
        onDone: () {
          if (!completer.isCompleted) completer.complete(events);
        },
      );

      ws.sink.add(jsonEncode([
        'REQ',
        evSub,
        {
          'kinds': [1315],
          'authors': [pubHex],
          'since': _nowS() - 30 * 86400, // last 30 days
          'limit': safeLimit,
        }
      ]));

      return await completer.future.timeout(
        const Duration(seconds: 12),
        onTimeout: () => events,
      );
    } catch (_) {
      return [];
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }

  /// Fetches the NIP-01 kind-0 metadata event for [pubHex] from a public relay
  /// and returns a [NostrProfile] with name, displayName, and picture URL.
  /// Returns `null` if the relay has no profile or the request times out (6 s).
  /// Relays tried in order when fetching a Nostr kind-0 profile.
  /// Having multiple fallbacks increases the chance of finding the profile
  /// even when one relay is offline or doesn't have the user's metadata.
  /// nostr.band removed here too: a profile REQ carries authors=[npub], and
  /// handing "this npub is active right now at this IP" to a search-engine
  /// operator is not worth a third fallback for cosmetic data.
  static const _profileRelays = [
    'wss://relay.damus.io',
    'wss://nos.lol',
  ];

  /// Fetches a Nostr kind-0 (profile metadata) event for [pubHex].
  ///
  /// Tries [_profileRelays] in sequence and returns the first non-null result.
  /// This makes profile loading resilient to relay outages, which is the most
  /// common reason for the picture/name not loading after nsec login.
  static Future<NostrProfile?> fetchProfile(String pubHex) async {
    if (!_isHex32(pubHex)) return null;
    for (final relayUrl in _profileRelays) {
      final result = await _fetchProfileFromRelay(pubHex, relayUrl);
      if (result != null) return result;
    }
    return null;
  }

  static Future<NostrProfile?> _fetchProfileFromRelay(
      String pubHex, String relayUrl) async {
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(relayUrl));
      final completer = Completer<NostrProfile?>();
      final subId = randomSubId();
      NostrProfile? latest;
      var latestCreatedAt = -1;

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            if (raw is! String || raw.length > _maxInboundMessageChars) return;
            final msg = jsonDecode(raw) as List;
            if (msg[0] == 'EVENT' && msg[1] == subId) {
              final json = (msg[2] as Map).cast<String, dynamic>();
              // Verify author + id + signature: a forged kind-0 would let a
              // malicious relay plant an arbitrary display name/avatar.
              if (json['pubkey'] != pubHex || !verifyEventJson(json)) return;
              final meta =
                  jsonDecode(json['content'] as String) as Map<String, dynamic>;
              final createdAt = json['created_at'] as int? ?? -1;
              if (createdAt > latestCreatedAt) {
                latestCreatedAt = createdAt;
                latest = NostrProfile(
                  name: _boundedText(meta['name'], 100),
                  displayName: _boundedText(meta['display_name'], 100),
                  picture: _safePictureUrl(meta['picture']),
                );
              }
            } else if (msg[0] == 'EOSE' && msg[1] == subId) {
              if (!completer.isCompleted) completer.complete(latest);
            }
          } catch (_) {}
        },
        onError: (_) {
          if (!completer.isCompleted) completer.complete(null);
        },
        onDone: () {
          if (!completer.isCompleted) completer.complete(null);
        },
      );

      ws.sink.add(jsonEncode([
        'REQ',
        subId,
        {
          'kinds': [0],
          'authors': [pubHex],
          'limit': 1,
        }
      ]));

      return await completer.future.timeout(
        const Duration(seconds: 5),
        onTimeout: () => null,
      );
    } catch (_) {
      return null;
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }

  static String? _boundedText(dynamic value, int maxLength) {
    if (value is! String) return null;
    final clean = value.trim();
    if (clean.isEmpty) return null;
    return clean.length <= maxLength ? clean : clean.substring(0, maxLength);
  }

  static String? _safePictureUrl(dynamic value) {
    if (value is! String || value.length > 2048) return null;
    final uri = Uri.tryParse(value);
    if (uri == null ||
        uri.scheme != 'https' ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty) {
      return null;
    }
    final host = uri.host.toLowerCase();
    if (host == 'localhost' || host.endsWith('.localhost')) return null;
    final ipv4 = RegExp(r'^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$')
        .firstMatch(host);
    if (ipv4 != null) {
      final bytes = [for (var i = 1; i <= 4; i++) int.parse(ipv4.group(i)!)];
      if (bytes.any((v) => v > 255) ||
          bytes[0] == 10 ||
          bytes[0] == 127 ||
          (bytes[0] == 169 && bytes[1] == 254) ||
          (bytes[0] == 172 && bytes[1] >= 16 && bytes[1] <= 31) ||
          (bytes[0] == 192 && bytes[1] == 168)) {
        return null;
      }
    }
    return uri.toString();
  }
}
