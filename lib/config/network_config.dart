/// Network budgets — deadlines and response caps — in one place.
///
/// These used to be spelled out at each of the ~39 call sites, which made two
/// things impossible: telling at a glance whether a given service was being
/// unusually patient or unusually strict, and re-tuning a whole class of
/// request without hunting through every file. The tiers below are named for
/// *what the user is waiting on*, not for the service that happens to use
/// them, so a new caller can pick one by asking "is anybody staring at the
/// screen while this runs?".
///
/// Nothing here is a retry budget. A tier is the deadline for one attempt;
/// [RetryPolicy] in `lib/utils/retry.dart` decides whether a failed attempt is
/// worth repeating, and its own total is what bounds the user-visible wait.
library;

/// Per-attempt deadlines.
///
/// The upper tiers are deliberately far apart. A value between [interactive]
/// and [standard] would be long enough to feel broken while typing yet too
/// short for a query that legitimately needs a round trip to a busy mirror.
class NetworkTimeouts {
  const NetworkTimeouts._();

  /// Something is redrawing as the user types — an autocomplete list, a
  /// weather strip. Past this the answer is stale rather than late, so it is
  /// better to give up and let the next keystroke ask again.
  static const interactive = Duration(seconds: 4);

  /// The common case: the user asked for one thing and is watching a spinner.
  /// Long enough to survive a slow mobile handshake, short enough that a dead
  /// endpoint is noticed before the user gives up on the app instead.
  static const standard = Duration(seconds: 8);

  /// Route calculation. Higher than [standard] because the request is worth
  /// more to the user than a POI list — having asked for a route, waiting a
  /// couple of extra seconds beats being told to try again.
  static const routing = Duration(seconds: 12);

  /// Public-transport itineraries. Higher again: the router is searching a
  /// timetable graph across every operator in the area, not a road network,
  /// and a cold cache for a large city genuinely takes longer.
  static const transit = Duration(seconds: 20);

  /// Bulk area downloads that were started on the app's own initiative
  /// (traffic-zone polygons, speed-camera sweeps). Nobody is watching, so the
  /// budget is generous — but still finite, because an unbounded fetch on a
  /// metered connection is the user's money.
  static const background = Duration(seconds: 25);

  /// A relay socket's opening handshake. Distinct from the tiers above: a
  /// WebSocket that has not completed `ready` is not slow, it is unreachable,
  /// and the pool has other relays to try.
  static const socketHandshake = Duration(seconds: 5);
}

/// Response size caps handed to `BoundedHttp`, which aborts mid-stream rather
/// than after buffering. Every value is a defence against a hostile or broken
/// endpoint, not a guess at the real payload: a legitimate response sits far
/// below its cap, so raising one should require a measurement, not a hunch.
class NetworkLimits {
  const NetworkLimits._();

  /// Small fixed-shape JSON: a weather reading, an invoice, a single lookup.
  static const smallJson = 1024 * 1024;

  /// Geocoder answers and route geometries. A route across a continent with
  /// full step geometry stays comfortably inside this.
  static const route = 2 * 1024 * 1024;

  /// A public-transport plan: several itineraries, each with per-leg polylines
  /// and timetable detail. Measured at ~88 KB for a short city hop, so this is
  /// roughly a 20× margin for a dense multi-operator query.
  static const transitPlan = 2 * 1024 * 1024;

  /// Overpass results for a corridor or a modest radius.
  static const areaQuery = 6 * 1024 * 1024;

  /// Wider POI sweeps that legitimately return thousands of elements.
  static const largeAreaQuery = 10 * 1024 * 1024;

  /// Whole-city polygon sets. The largest thing the app ever downloads.
  static const bulkGeometry = 20 * 1024 * 1024;
}

/// Caps on what a relay may push at the app over an open socket.
///
/// This lived as a private `_maxInboundMessageChars` in two services at once,
/// with the same value and the same reasoning duplicated. Two copies of a
/// security limit is one copy too many: raising it in one file and not the
/// other silently widens the hole in whichever was forgotten.
class RelayLimits {
  const RelayLimits._();

  /// Longest inbound relay frame the app will parse, in UTF-16 code units.
  ///
  /// A Nostr event carrying a road report is a few hundred characters; a
  /// favourites snapshot is capped at 64 KB before encryption. 256 KB is
  /// therefore several times the largest legitimate frame, while still small
  /// enough that a relay cannot exhaust memory by streaming one enormous
  /// message.
  static const maxInboundMessageChars = 256 * 1024;
}
