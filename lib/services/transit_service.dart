import 'dart:convert';

import 'package:flutter/foundation.dart' show visibleForTesting;
import 'package:latlong2/latlong.dart';

import '../config/network_config.dart';
import '../models/transit_itinerary.dart';
import '../utils/retry.dart';
import 'bounded_http.dart';

/// Outcome of a public-transport request.
///
/// Three results have to be told apart, because they mean different things to
/// a traveller and warrant different UI: an answer, "there is no public
/// transport data for where you are", and "something went wrong, try again".
/// Collapsing the last two into an empty list — the shape the rest of this
/// codebase historically used — would tell somebody in a well-served city that
/// their metro does not exist.
sealed class TransitResult {
  const TransitResult();
}

/// At least one journey was found.
class TransitPlan extends TransitResult {
  final List<TransitItinerary> itineraries;
  const TransitPlan(this.itineraries);
}

/// The router answered, but knows no scheduled service linking these points.
/// Coverage depends on whether an operator publishes an open timetable feed
/// for that area, which varies by region and improves over time.
class TransitUnavailable extends TransitResult {
  const TransitUnavailable();
}

/// The request failed. [transient] distinguishes "worth offering a retry
/// button" from "this will not work".
class TransitFailure extends TransitResult {
  final String message;
  final bool transient;
  const TransitFailure(this.message, {required this.transient});
}

/// Public-transport routing.
///
/// Backed by Transitous, a community-run service that aggregates openly
/// published timetable feeds worldwide into a single routing graph. It is the
/// same kind of shared, non-commercial infrastructure this app already relies
/// on for roads and geocoding: free, no account, no API key, and no tracking
/// of who asked for what.
///
/// Two consequences of that choice are worth stating plainly:
///
/// * **One endpoint covers the planet.** There is no per-region router to
///   select and no bounding boxes to maintain — the same URL answers for Tokyo
///   and for São Paulo. Coverage is a property of which feeds exist, not of
///   which server is asked, so a region without data returns
///   [TransitUnavailable] rather than needing to be known in advance.
/// * **The request carries the journey's endpoints to a third party.** That is
///   unavoidable for any routing that is not computed on the device, and it is
///   the same exposure the road routers already carry — but it is why this
///   runs only when the traveller explicitly asks for public transport, and
///   why redirects stay disabled so those coordinates cannot be forwarded to
///   another origin.
class TransitService {
  /// Community routing endpoint. Overridable so tests can point at a local
  /// fake instead of reaching the network.
  final String endpoint;

  const TransitService({this.endpoint = defaultEndpoint});

  static const defaultEndpoint = 'https://api.transitous.org/api/v1/plan';

  /// Journeys to request. Three gives a traveller a real choice — fastest,
  /// fewest changes, least walking — without turning the sheet into a list to
  /// scroll through.
  static const _itineraryCount = 3;

  /// How long the traveller may walk to reach the first stop, and from the
  /// last one, in seconds.
  ///
  /// The router defaults to 15 minutes, which quietly assumes the journey
  /// starts almost on top of a stop. It does not: somebody opens a navigation
  /// app at home. Measured against a real regional corridor, a request from a
  /// point 900 m from the station returned *no service at all* under the
  /// default, and three trains — the fastest 144 minutes — with this value.
  /// Reporting "no public transport here" because the station was a
  /// fifteen-minute walk away is the worst possible failure: it is wrong, and
  /// it looks authoritative.
  ///
  /// 30 minutes is the ceiling rather than the expectation. The router still
  /// prefers the nearest usable stop; this only stops it giving up early.
  static const _maxAccessWalkSeconds = 1800;

  /// Plans a journey from [from] to [to], departing at [departure]
  /// (defaults to now).
  Future<TransitResult> plan({
    required LatLng from,
    required LatLng to,
    DateTime? departure,
  }) async {
    final uri = _buildUri(from: from, to: to, departure: departure);
    try {
      final body = await withRetry(
        () => _fetch(uri),
        host: uri.host,
        policy: RetryPolicy.interactive,
      );
      return _parse(body);
    } on TransientFailure catch (e) {
      return TransitFailure(e.message, transient: true);
    } on NetworkFailure catch (e) {
      return TransitFailure(e.message, transient: false);
    }
  }

  /// Exposes the built query so a test can assert on the parameters without
  /// issuing a request.
  @visibleForTesting
  Uri debugBuildUri({
    required LatLng from,
    required LatLng to,
    DateTime? departure,
  }) =>
      _buildUri(from: from, to: to, departure: departure);

  Uri _buildUri({
    required LatLng from,
    required LatLng to,
    DateTime? departure,
  }) {
    final when = (departure ?? DateTime.now()).toUtc();
    return Uri.parse(endpoint).replace(queryParameters: {
      'fromPlace': '${from.latitude},${from.longitude}',
      'toPlace': '${to.latitude},${to.longitude}',
      'time': when.toIso8601String(),
      'numItineraries': '$_itineraryCount',
      'maxPreTransitTime': '$_maxAccessWalkSeconds',
      'maxPostTransitTime': '$_maxAccessWalkSeconds',
    });
  }

  Future<String> _fetch(Uri uri) async {
    final response = await BoundedHttp.get(
      uri,
      headers: const {'User-Agent': 'Roadstr/1.0 (navigation app)'},
      maxBytes: NetworkLimits.transitPlan,
      timeout: NetworkTimeouts.transit,
    );
    // Classify before touching the body: a 429 from a shared community server
    // is a request to wait, not a malformed answer, and only the typed failure
    // carries that distinction through to the retry policy.
    final failure = classifyStatus(
      response.statusCode,
      host: uri.host,
      retryAfter: response.headers['retry-after'],
    );
    if (failure != null) throw failure;
    return response.body;
  }

  TransitResult _parse(String body) {
    final decoded = jsonDecode(body);
    if (decoded is! Map<String, dynamic>) {
      throw const PermanentFailure('unexpected response shape');
    }
    final raw = decoded['itineraries'];
    if (raw is! List) return const TransitUnavailable();

    final itineraries = <TransitItinerary>[];
    for (final entry in raw) {
      if (entry is! Map<String, dynamic>) continue;
      final itinerary = TransitItinerary.fromJson(entry);
      // Walk-only results are dropped: the router falls back to walking the
      // whole way when it finds no service, and presenting that as a public
      // transport option would be misleading. The road routers already do
      // walking properly.
      if (itinerary != null && !itinerary.isWalkOnly) itineraries.add(itinerary);
    }
    if (itineraries.isEmpty) return const TransitUnavailable();

    itineraries.sort((a, b) => a.duration.compareTo(b.duration));
    return TransitPlan(itineraries);
  }
}
