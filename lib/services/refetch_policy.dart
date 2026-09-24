import 'package:latlong2/latlong.dart';

/// When a position-keyed cache of map data (traffic lights, crosswalks, speed
/// cameras, the road's speed limit) is due for a new network fetch.
///
/// Every one of those services had its own copy of this rule, and all of them
/// shared two habits worth ending. Data that is fetched *around* a position
/// was refetched as soon as the vehicle had moved a third of the way to the
/// edge of what it already held — long before the edge was in sight — and it
/// was refetched on a two-minute timer even when nothing had moved at all: a
/// parked phone with the app open asked the server for the same unchanged
/// traffic lights, crosswalks and cameras every two minutes, for as long as
/// the screen stayed on. Each of those requests wakes the radio.
class RefetchPolicy {
  const RefetchPolicy({required this.minMoveM, required this.maxAge});

  /// Travel, from where the cache was last filled, that makes it due.
  final double minMoveM;

  /// How long an unmoved cache may go before it is refreshed anyway. Only a
  /// safety net for a result that was wrong or empty when it arrived — OSM
  /// data does not change under a stationary phone.
  final Duration maxAge;

  /// Whether a fetch is due for a vehicle now at [pos], given where the cache
  /// was [lastQueryPos] filled and when ([lastSuccessAt]) it last succeeded.
  ///
  /// Backing off after a failure and not overlapping a request already in
  /// flight are the caller's business; this is only the "is the data still
  /// good enough" question.
  bool isDue({
    required LatLng? lastQueryPos,
    required LatLng pos,
    required DateTime? lastSuccessAt,
    required DateTime now,
  }) {
    if (lastQueryPos == null) return true;
    if (const Distance().as(LengthUnit.Meter, lastQueryPos, pos) > minMoveM) {
      return true;
    }
    if (lastSuccessAt == null) return true;
    return now.difference(lastSuccessAt) > maxAge;
  }
}
