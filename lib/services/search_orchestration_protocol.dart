import 'package:latlong2/latlong.dart';

import 'search_ranking_protocol.dart';
import 'search_response_protocol.dart';

enum SearchProviderKind { nominatim, photon, poi }

enum SearchProviderBatch { initial, retry }

typedef SearchOrchestrationDecision = ({
  List<NominatimResult>? partialResults,
  String? retryQuery,
  List<NominatimResult>? finalResults,
});

/// Stateful, socket-free policy for one place-search execution.
///
/// Provider futures may complete in any order. This boundary guarantees one
/// initial partial result, waits for every enabled provider before deciding on
/// the relaxed retry, and publishes one final merged result. Duplicate, stale
/// or batch-inappropriate completions are ignored.
class SearchOrchestrationProtocol {
  SearchOrchestrationProtocol({
    required this.plan,
    required this.near,
  }) : _expectedInitial = {
          if (plan.useNominatim) SearchProviderKind.nominatim,
          if (plan.usePhoton) SearchProviderKind.photon,
          if (plan.usePoi) SearchProviderKind.poi,
        };

  final SearchExecutionPlan plan;
  final LatLng? near;
  final Set<SearchProviderKind> _expectedInitial;
  final Map<SearchProviderKind, List<NominatimResult>> _initial = {};
  final Map<SearchProviderKind, List<NominatimResult>> _retry = {};

  bool _partialEmitted = false;
  bool _retryStarted = false;
  bool _completed = false;
  String? _retryQuery;

  bool get isCompleted => _completed;

  Set<SearchProviderKind> get expectedInitialProviders =>
      Set.unmodifiable(_expectedInitial);

  SearchOrchestrationDecision accept(
    SearchProviderBatch batch,
    SearchProviderKind provider,
    List<NominatimResult> results,
  ) =>
      switch (batch) {
        SearchProviderBatch.initial => _acceptInitial(provider, results),
        SearchProviderBatch.retry => _acceptRetry(provider, results),
      };

  SearchOrchestrationDecision _acceptInitial(
    SearchProviderKind provider,
    List<NominatimResult> results,
  ) {
    if (_completed ||
        _retryStarted ||
        !_expectedInitial.contains(provider) ||
        _initial.containsKey(provider)) {
      return _none;
    }
    _initial[provider] = results;

    List<NominatimResult>? partial;
    if (!_partialEmitted && results.isNotEmpty) {
      _partialEmitted = true;
      partial = SearchRankingProtocol.rankResults(plan.query, results, near);
    }
    if (_initial.length != _expectedInitial.length) {
      return (
        partialResults: partial,
        retryQuery: null,
        finalResults: null,
      );
    }

    final geocoded = SearchRankingProtocol.rankGeocoders(
      plan.query,
      _initial[SearchProviderKind.nominatim] ?? const [],
      _initial[SearchProviderKind.photon] ?? const [],
      near,
    );
    final poi = _initial[SearchProviderKind.poi] ?? const [];
    final relaxed = SearchRankingProtocol.relaxedRetryQuery(
      plan,
      geocoded,
      poi,
    );
    if (relaxed != null) {
      _retryStarted = true;
      _retryQuery = relaxed;
      return (
        partialResults: partial,
        retryQuery: relaxed,
        finalResults: null,
      );
    }

    _completed = true;
    return (
      partialResults: partial,
      retryQuery: null,
      finalResults: SearchRankingProtocol.mergePoiFirst(poi, geocoded),
    );
  }

  SearchOrchestrationDecision _acceptRetry(
    SearchProviderKind provider,
    List<NominatimResult> results,
  ) {
    if (_completed ||
        !_retryStarted ||
        provider == SearchProviderKind.poi ||
        _retry.containsKey(provider)) {
      return _none;
    }
    _retry[provider] = results;
    if (_retry.length != 2) return _none;

    final query = _retryQuery!;
    final geocoded = SearchRankingProtocol.rankGeocoders(
      query,
      _retry[SearchProviderKind.nominatim] ?? const [],
      _retry[SearchProviderKind.photon] ?? const [],
      near,
    );
    _completed = true;
    return (
      partialResults: null,
      retryQuery: null,
      finalResults: SearchRankingProtocol.mergePoiFirst(
        _initial[SearchProviderKind.poi] ?? const [],
        geocoded,
      ),
    );
  }

  static const SearchOrchestrationDecision _none = (
    partialResults: null,
    retryQuery: null,
    finalResults: null,
  );
}
