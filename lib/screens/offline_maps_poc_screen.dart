import 'dart:async';

import 'package:flutter/material.dart';
import 'package:maplibre/maplibre.dart' hide Box, LengthUnit;

/// Throwaway proof-of-concept for offline map downloads — same "build it,
/// look at it, then decide" step the MapLibre rendering engine itself went
/// through before a real branch. Reachable only from a debug-only Settings
/// entry, not part of the shipped app.
///
/// Demonstrates the actual mechanism end to end: OpenFreeMap's vector style
/// (see feature/offline-maps roadmap notes — free, uncapped, no commercial
/// restriction, unlike tile.openstreetmap.org which forbids bulk download)
/// fed through MapLibre's own OfflineManager.downloadRegion. Nothing here is
/// final — zoom range, region size, and the self-imposed download cap are
/// all first-draft numbers to react to, not decisions.
class OfflineMapsPocScreen extends StatefulWidget {
  const OfflineMapsPocScreen({super.key});

  @override
  State<OfflineMapsPocScreen> createState() => _OfflineMapsPocScreenState();
}

class _OfflineMapsPocScreenState extends State<OfflineMapsPocScreen> {
  static const _styleUrl = 'https://tiles.openfreemap.org/styles/liberty';

  // First-draft courtesy cap — OpenFreeMap publishes no fair-use threshold
  // of its own ("no limits on the number of map views or requests"), so
  // this is entirely self-imposed: a small community project, not a paid
  // service with capacity to spare. Keeping the zoom range well short of
  // Roadstr's live driving zoom (usually 17) keeps a single region's tile
  // count sane; the total-tile ceiling below is MapLibre's own
  // OfflineManager mechanism, just made explicit instead of left at its
  // silent default.
  static const _minZoom = 10.0;
  static const _maxZoom = 16.0;
  static const _totalTileCap = 6000;

  MapController? _controller;
  OfflineManager? _offlineManager;
  StreamSubscription<DownloadProgress>? _downloadSub;

  bool _downloading = false;
  int _loadedTiles = 0;
  int _totalTiles = 0;
  int _loadedBytes = 0;
  String? _error;
  List<OfflineRegion> _regions = [];

  @override
  void initState() {
    super.initState();
    unawaited(_initOffline());
  }

  Future<void> _initOffline() async {
    final manager = await OfflineManager.createInstance();
    manager.setOfflineTileCountLimit(amount: _totalTileCap);
    if (!mounted) return;
    setState(() => _offlineManager = manager);
    await _refreshRegions();
  }

  Future<void> _refreshRegions() async {
    final manager = _offlineManager;
    if (manager == null) return;
    final regions = await manager.listOfflineRegions();
    if (!mounted) return;
    setState(() => _regions = regions);
  }

  Future<void> _downloadVisibleArea() async {
    final controller = _controller;
    final manager = _offlineManager;
    if (controller == null || manager == null || _downloading) return;
    final bounds = controller.getVisibleRegion();
    final pixelDensity = MediaQuery.of(context).devicePixelRatio;
    setState(() {
      _downloading = true;
      _loadedTiles = 0;
      _totalTiles = 0;
      _loadedBytes = 0;
      _error = null;
    });
    await _downloadSub?.cancel();
    _downloadSub = manager
        .downloadRegion(
      mapStyleUrl: _styleUrl,
      bounds: bounds,
      minZoom: _minZoom,
      maxZoom: _maxZoom,
      pixelDensity: pixelDensity,
      metadata: {'label': 'PoC region', 'downloadedAtMs': DateTime.now().millisecondsSinceEpoch},
    )
        .listen(
      (p) {
        if (!mounted) return;
        setState(() {
          _loadedTiles = p.loadedTiles;
          _totalTiles = p.totalTiles;
          _loadedBytes = p.loadedBytes;
        });
        if (p.downloadCompleted) {
          setState(() => _downloading = false);
          unawaited(_refreshRegions());
        }
      },
      onError: (Object e) {
        if (!mounted) return;
        setState(() {
          _downloading = false;
          _error = '$e';
        });
      },
    );
  }

  Future<void> _deleteRegion(int id) async {
    final manager = _offlineManager;
    if (manager == null) return;
    await manager.deleteRegion(regionId: id);
    await _refreshRegions();
  }

  @override
  void dispose() {
    _downloadSub?.cancel();
    _offlineManager?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Offline maps — PoC')),
      body: Stack(children: [
        MapLibreMap(
          options: const MapOptions(
            initStyle: _styleUrl,
            initCenter: Geographic(lon: 12.5, lat: 42.5),
            initZoom: 12,
            gestures: MapGestures.all(),
          ),
          onMapCreated: (controller) => _controller = controller,
        ),
        Positioned(
          left: 12,
          right: 12,
          bottom: 12,
          child: Card(
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                Text(
                  'OpenFreeMap is a small, free community project, not a paid '
                  'service — this cap (zoom $_minZoom–$_maxZoom, '
                  '$_totalTileCap tiles total across all downloaded regions) '
                  'is Roadstr being a considerate guest, not a limit they '
                  'imposed on us.',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 8),
                if (_downloading) ...[
                  LinearProgressIndicator(
                    value: _totalTiles > 0 ? _loadedTiles / _totalTiles : null,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '$_loadedTiles / ${_totalTiles > 0 ? _totalTiles : '?'} tiles '
                    '(${(_loadedBytes / 1024 / 1024).toStringAsFixed(1)} MB)',
                  ),
                ] else
                  ElevatedButton(
                    onPressed:
                        _offlineManager == null ? null : _downloadVisibleArea,
                    child: const Text('Download visible area for offline use'),
                  ),
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(_error!,
                        style: const TextStyle(color: Colors.red)),
                  ),
                if (_regions.isNotEmpty) ...[
                  const Divider(),
                  ..._regions.map((r) => ListTile(
                        dense: true,
                        title: Text('Region #${r.id}'),
                        subtitle: Text(
                            'z${r.minZoom.toInt()}–${r.maxZoom.toInt()} · ${r.metadata['label'] ?? ''}'),
                        trailing: IconButton(
                          icon: const Icon(Icons.delete_outline),
                          onPressed: () => _deleteRegion(r.id),
                        ),
                      )),
                ],
              ]),
            ),
          ),
        ),
      ]),
    );
  }
}
