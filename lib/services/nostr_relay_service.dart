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
import 'package:latlong2/latlong.dart';
import 'package:nostr_tools/nostr_tools.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../models/road_event.dart';

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

  /// Primary relays used for subscriptions (tried in round-robin order on failure).
  static const _relays = [
    'wss://relay.damus.io',
    'wss://nos.lol',
    'wss://relay.nostr.band',
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
  final _events     = <String, RoadEvent>{};
  final _controller = StreamController<List<RoadEvent>>.broadcast();

  WebSocketChannel? _ws;
  StreamSubscription<dynamic>? _wsSub;
  Timer? _reconnectTimer;

  /// Debounce timer that delays sending the confirmation (kind-1316) REQ until
  /// all kind-1315 historical events have been received (after EOSE).
  Timer? _confTimer;
  Timer? _cleanupTimer;

  /// Index into [_relays] for round-robin failover.
  int    _relayIdx  = 0;
  bool   _connected = false;

  /// NIP-01 subscription IDs so we can send CLOSE before a new REQ.
  String _eventsSubId  = '';
  String _confSubId    = '';

  /// The two geohash strings (precision 4 and 5) of the last subscribed area.
  /// Compared on each [subscribeArea] call to avoid redundant REQ messages.
  List<String> _lastGeohashes = [];

  /// IDs of kind-1315 events received in the current batch; used to build the
  /// follow-up kind-1316 confirmation REQ after EOSE.
  final _pendingIds = <String>{};

  // ── public API ─────────────────────────────────────────────────────────────

  Stream<List<RoadEvent>> get stream => _controller.stream;

  /// Returns all non-expired events currently in the in-memory cache.
  List<RoadEvent> get currentEvents =>
      _events.values.where((e) => !e.isExpired).toList();

  /// Opens a WebSocket connection to the next relay in the round-robin list.
  ///
  /// If a previous subscription existed, it is replayed on the new connection
  /// so the caller does not need to call [subscribeArea] again after reconnect.
  Future<void> connect() async {
    _wsSub?.cancel();
    _ws?.sink.close().catchError((_) {});
    _connected = false;
    // Remove stale events every 2 minutes without waiting for a new GPS position.
    _cleanupTimer?.cancel();
    _cleanupTimer = Timer.periodic(const Duration(minutes: 2), (_) => _removeExpired());

    final url = _relays[_relayIdx % _relays.length];
    try {
      _ws = WebSocketChannel.connect(Uri.parse(url));
      _wsSub = _ws!.stream.listen(
        _onMessage,
        onError: (_) => _scheduleReconnect(),
        onDone:  _scheduleReconnect,
        cancelOnError: false,
      );
      _connected = true;
      // Replay the last area subscription if the app already had one active.
      if (_lastGeohashes.isNotEmpty) _sendEventsReq(_lastGeohashes);
    } catch (_) {
      _scheduleReconnect();
    }
  }

  /// Subscribes to road events (kind 1315) within the geohash cell that contains
  /// [center].
  ///
  /// **Why geohash levels 4 and 5?**
  /// Level 4 covers roughly 40 × 20 km — wide enough to show events ahead on a
  /// highway. Level 5 halves that to ~5 × 5 km for urban precision. Using both
  /// ensures the relay filter matches events tagged at either granularity.
  ///
  /// **Why _events is NOT cleared here:**
  /// Clearing the map would cause all map markers to disappear briefly while the
  /// relay sends new EVENTs. Instead, valid events accumulate; the 2-minute
  /// cleanup timer removes expired ones.
  void subscribeArea(LatLng center) {
    final g4 = _gh(center.latitude, center.longitude, 4);
    final g5 = _gh(center.latitude, center.longitude, 5);
    // Skip if the user is still inside the same geohash cell as before.
    if (_lastGeohashes.length == 2 &&
        _lastGeohashes[0] == g4 &&
        _lastGeohashes[1] == g5) return;
    _lastGeohashes = [g4, g5];
    _pendingIds.clear();
    // Keep _events intact — valid markers remain visible during relay round-trip.
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
  }) async {
    _requireConnected();
    final now     = _nowS();
    final expires = now + 14 * 86400;
    final signed  = _eventApi.finishEvent(
      Event(
        pubkey:     pubKeyHex,
        created_at: now,
        kind:       1315,
        tags: [
          ['lat', position.latitude.toStringAsFixed(7)],
          ['lon', position.longitude.toStringAsFixed(7)],
          ['g', _gh(position.latitude, position.longitude, 4)],
          ['g', _gh(position.latitude, position.longitude, 5)],
          ['g', _gh(position.latitude, position.longitude, 6)],
          ['t', category.nostrKey],
          ['expiration', '$expires'],
        ],
        content: comment,
      ),
      privKeyHex,
    );
    // Publish to all configured relays for maximum redundancy.
    _sendToAll(['EVENT', signed.toJson()]);
    // Return the local event with the same ID the relay will assign.
    return RoadEvent(
      id:        signed.id,
      pubkey:    pubKeyHex,
      category:  category,
      position:  position,
      comment:   comment,
      createdAt: now,
      expiresAt: expires,
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
    final signed = _eventApi.finishEvent(
      Event(
        pubkey:     pubKeyHex,
        created_at: _nowS(),
        kind:       1316,
        tags: [
          ['e', eventId],
          ['status', stillThere ? 'still_there' : 'no_longer_there'],
        ],
        content: '',
      ),
      privKeyHex,
    );
    _send(['EVENT', signed.toJson()]);
  }

  void dispose() {
    _reconnectTimer?.cancel();
    _confTimer?.cancel();
    _cleanupTimer?.cancel();
    _wsSub?.cancel();
    _ws?.sink.close().catchError((_) {});
    _controller.close();
  }

  /// Removes expired events from the cache and pushes an updated list downstream.
  void _removeExpired() {
    final before = _events.length;
    _events.removeWhere((_, ev) => ev.isExpired);
    if (_events.length != before) _controller.add(currentEvents);
  }

  // ── internals ─────────────────────────────────────────────────────────────

  void _requireConnected() {
    if (!_connected || _ws == null) {
      throw Exception('Not connected to Nostr relay');
    }
  }

  /// Increments [_relayIdx] so the next [connect] call uses a different relay.
  void _scheduleReconnect() {
    _connected = false;
    _reconnectTimer?.cancel();
    _relayIdx++;
    _reconnectTimer = Timer(const Duration(seconds: 5), connect);
  }

  void _send(dynamic msg) {
    try { _ws?.sink.add(jsonEncode(msg)); } catch (_) {}
  }

  /// Sends [msg] to the active relay AND to every relay in [_publishRelays] via
  /// short-lived WebSocket connections (fire-and-forget). This ensures events
  /// reach as many relays as possible even if the user's primary relay is slow.
  void _sendToAll(dynamic msg) {
    _send(msg); // Primary relay (persistent WebSocket)
    // Secondary relays: open a one-shot connection just to publish.
    for (final url in _publishRelays) {
      if (url == _relays[_relayIdx % _relays.length]) continue; // already sent above
      _publishToRelay(url, msg);
    }
  }

  /// Opens a temporary WebSocket to [url], publishes [msg], waits 3 s for the
  /// relay OK, then closes. Errors are silently swallowed — this is best-effort.
  Future<void> _publishToRelay(String url, dynamic msg) async {
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse(url));
      ws.sink.add(jsonEncode(msg));
      // Give the relay time to process before closing.
      await Future.delayed(const Duration(seconds: 3));
    } catch (_) {
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }

  /// Sends a NIP-01 REQ for kind-1315 events tagged with [geohashes].
  /// Closes any previous events subscription first to avoid duplicate messages.
  void _sendEventsReq(List<String> geohashes) {
    if (_eventsSubId.isNotEmpty) _send(['CLOSE', _eventsSubId]);
    _eventsSubId = 'rs-${_nowS()}';
    _send(['REQ', _eventsSubId, {
      'kinds': [1315],
      '#g':    geohashes,
      // Fetch up to 14 days of history to match the maximum event expiration TTL.
      'since': _nowS() - 14 * 86400,
      'limit': 500,
    }]);
  }

  /// Sends a NIP-01 REQ for kind-1316 confirmation/denial events that reference
  /// the given [ids]. Called after EOSE for the events subscription.
  void _sendConfReq(List<String> ids) {
    if (ids.isEmpty) return;
    if (_confSubId.isNotEmpty) _send(['CLOSE', _confSubId]);
    _confSubId = 'rc-${_nowS()}';
    _send(['REQ', _confSubId, {
      'kinds': [1316],
      '#e':    ids,
      'since': _nowS() - 14 * 86400,
      'limit': 1000,
    }]);
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
      final msg = jsonDecode(raw as String) as List;
      switch (msg[0] as String) {
        case 'OK':
          // Format: ['OK', event_id, accepted, reason]
          if (msg.length >= 3 && msg[2] == false) {
            debugPrint('[Nostr] Relay rejected event ${msg[1]}: ${msg.length > 3 ? msg[3] : ""}');
          }
        case 'NOTICE':
          debugPrint('[Nostr] NOTICE from relay: ${msg.length > 1 ? msg[1] : ""}');
        case 'EVENT':
          final json = msg[2] as Map<String, dynamic>;
          switch (json['kind'] as int) {
            case 1315: _handleRoadEvent(json);
            case 1316: _handleConfirmation(json);
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

  void _handleRoadEvent(Map<String, dynamic> json) {
    final event = RoadEvent.fromNostr(json);
    if (event == null || event.isExpired) return;
    _events[event.id] = event;
    _pendingIds.add(event.id);
    _controller.add(currentEvents);
  }

  void _handleConfirmation(Map<String, dynamic> json) {
    final tags = (json['tags'] as List)
        .map((t) => List<String>.from(t as List))
        .toList();
    String? targetId, status;
    for (final t in tags) {
      if (t.isEmpty) continue;
      if (t[0] == 'e')      targetId = t[1];
      if (t[0] == 'status') status   = t[1];
    }
    if (targetId == null || status == null) return;
    final ev = _events[targetId];
    if (ev == null) return;
    if (status == 'still_there')      ev.confirmations++;
    else if (status == 'no_longer_there') ev.denials++;
    _controller.add(currentEvents);
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
        if (lon >= mid) { bits = (bits << 1) | 1; minLon = mid; }
        else            { bits = bits << 1;         maxLon = mid; }
      } else {
        final mid = (minLat + maxLat) / 2;
        if (lat >= mid) { bits = (bits << 1) | 1; minLat = mid; }
        else            { bits = bits << 1;         maxLat = mid; }
      }
      isLon = !isLon;
      // Every 5 bits form one base-32 character.
      if (++count == 5) { buf.write(_gh32[bits]); bits = 0; count = 0; }
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
  }) {
    final event = Event(
      pubkey:     pubKeyHex,
      created_at: now,
      kind:       1315,
      tags: [
        ['lat', position.latitude.toStringAsFixed(7)],
        ['lon', position.longitude.toStringAsFixed(7)],
        ['g', _gh(position.latitude, position.longitude, 4)],
        ['g', _gh(position.latitude, position.longitude, 5)],
        ['g', _gh(position.latitude, position.longitude, 6)],
        ['t', category.nostrKey],
        ['expiration', '$expires'],
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
      pubkey:     pubKeyHex,
      created_at: _nowS(),
      kind:       1316,
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
  }) async {
    _requireConnected();
    _send(['EVENT', eventJson]);
    return RoadEvent(
      id:        eventJson['id'] as String,
      pubkey:    eventJson['pubkey'] as String,
      category:  category,
      position:  position,
      comment:   comment,
      createdAt: now,
      expiresAt: expires,
    );
  }

  /// Publishes a pre-signed event of any kind (typically kind 1316) without
  /// returning a value. Used when the caller handles local state updates itself.
  void publishRawEvent(Map<String, dynamic> eventJson) {
    if (_connected) _sendToAll(['EVENT', eventJson]);
  }

  /// Fetches all kind-1315 road events published by [pubHex] from a public relay,
  /// then enriches each event with its kind-1316 confirmation/denial counts.
  ///
  /// Uses two sequential subscriptions:
  ///   1. Fetch events (`evSub`) — wait for EOSE.
  ///   2. Fetch confirmations (`confSub`) filtered by the collected event IDs.
  static Future<List<RoadEvent>> fetchUserEvents(String pubHex,
      {int limit = 100}) async {
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse('wss://relay.damus.io'));
      final events    = <RoadEvent>[];
      final eventIds  = <String>[];
      final completer = Completer<List<RoadEvent>>();
      final evSub  = 'ue-${_nowS()}';
      final confSub = 'uc-${_nowS() + 1}';

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            final msg = jsonDecode(raw as String) as List;
            if (msg[0] == 'EVENT') {
              final json = msg[2] as Map<String, dynamic>;
              if (msg[1] == evSub && json['kind'] == 1315) {
                final ev = RoadEvent.fromNostr(json);
                if (ev != null) { events.add(ev); eventIds.add(ev.id); }
              } else if (msg[1] == confSub && json['kind'] == 1316) {
                final tags = (json['tags'] as List)
                    .map((t) => List<String>.from(t as List)).toList();
                String? targetId, status;
                for (final t in tags) {
                  if (t.isEmpty) continue;
                  if (t[0] == 'e') targetId = t[1];
                  if (t[0] == 'status') status = t[1];
                }
                if (targetId == null || status == null) return;
                final idx = events.indexWhere((e) => e.id == targetId);
                if (idx < 0) return;
                if (status == 'still_there') events[idx].confirmations++;
                else if (status == 'no_longer_there') events[idx].denials++;
              }
            } else if (msg[0] == 'EOSE') {
              if (msg[1] == evSub) {
                if (eventIds.isEmpty) { completer.complete(events); return; }
                ws?.sink.add(jsonEncode(['REQ', confSub, {
                  'kinds': [1316], '#e': eventIds, 'limit': 500,
                }]));
              } else if (msg[1] == confSub) {
                if (!completer.isCompleted) completer.complete(events);
              }
            }
          } catch (_) {}
        },
        onError: (_) { if (!completer.isCompleted) completer.complete(events); },
        onDone:  () { if (!completer.isCompleted) completer.complete(events); },
      );

      ws.sink.add(jsonEncode(['REQ', evSub, {
        'kinds': [1315],
        'authors': [pubHex],
        'since': _nowS() - 30 * 86400, // last 30 days
        'limit': limit,
      }]));

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
  static Future<NostrProfile?> fetchProfile(String pubHex) async {
    WebSocketChannel? ws;
    try {
      ws = WebSocketChannel.connect(Uri.parse('wss://relay.damus.io'));
      final completer = Completer<NostrProfile?>();
      final subId = 'prof-${_nowS()}';

      ws.stream.listen(
        (raw) {
          if (completer.isCompleted) return;
          try {
            final msg = jsonDecode(raw as String) as List;
            if (msg[0] == 'EVENT' && msg[1] == subId) {
              final meta = jsonDecode((msg[2] as Map)['content'] as String)
                  as Map<String, dynamic>;
              completer.complete(NostrProfile(
                name:        meta['name']         as String?,
                displayName: meta['display_name'] as String?,
                picture:     meta['picture']      as String?,
              ));
            } else if (msg[0] == 'EOSE') {
              if (!completer.isCompleted) completer.complete(null);
            }
          } catch (_) {
            if (!completer.isCompleted) completer.complete(null);
          }
        },
        onError: (_) { if (!completer.isCompleted) completer.complete(null); },
        onDone:  () { if (!completer.isCompleted) completer.complete(null); },
      );

      ws.sink.add(jsonEncode(['REQ', subId, {
        'kinds': [0], 'authors': [pubHex], 'limit': 1,
      }]));

      return await completer.future.timeout(
        const Duration(seconds: 6),
        onTimeout: () => null,
      );
    } catch (_) {
      return null;
    } finally {
      ws?.sink.close().catchError((_) {});
    }
  }
}
