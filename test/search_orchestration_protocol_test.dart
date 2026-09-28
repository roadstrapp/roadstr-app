import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';
import 'package:roadstr/services/search_orchestration_protocol.dart';
import 'package:roadstr/services/search_ranking_protocol.dart';
import 'package:roadstr/services/search_response_protocol.dart';

import '../tools/kotlin_rewrite/generate_search_orchestration_fixture.dart'
    as fixture;

NominatimResult _result(String name) => NominatimResult(
      displayName: name,
      shortName: name,
      position: const LatLng(45, 9),
    );

void main() {
  test('shared search orchestration fixture is current', () {
    expect(
      File(fixture.outputPath).readAsStringSync(),
      fixture.buildSearchOrchestrationFixture(),
    );
  });

  test('phase and location freeze the exact initial provider set', () {
    final typeahead = SearchOrchestrationProtocol(
      plan: SearchRankingProtocol.executionPlan(
        'museum',
        settled: false,
        hasNear: false,
      )!,
      near: null,
    );
    expect(typeahead.expectedInitialProviders, {SearchProviderKind.photon});

    final settledNear = SearchOrchestrationProtocol(
      plan: SearchRankingProtocol.executionPlan(
        'museum',
        settled: true,
        hasNear: true,
      )!,
      near: const LatLng(45, 9),
    );
    expect(settledNear.expectedInitialProviders, SearchProviderKind.values);
  });

  test('duplicate and late completions cannot replace the terminal result', () {
    final state = SearchOrchestrationProtocol(
      plan: SearchRankingProtocol.executionPlan(
        'museum',
        settled: false,
        hasNear: false,
      )!,
      near: null,
    );
    final first = state.accept(
      SearchProviderBatch.initial,
      SearchProviderKind.photon,
      [_result('First')],
    );
    expect(first.partialResults?.single.shortName, 'First');
    expect(first.finalResults?.single.shortName, 'First');
    expect(state.isCompleted, isTrue);

    final late = state.accept(
      SearchProviderBatch.initial,
      SearchProviderKind.photon,
      [_result('Late')],
    );
    expect(late.partialResults, isNull);
    expect(late.retryQuery, isNull);
    expect(late.finalResults, isNull);
  });
}
