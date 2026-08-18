// The place-information panel shown when a search result is picked or the map
// is long-pressed, plus the OSM detail cards it is built from.
import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:intl/intl.dart';
import 'package:latlong2/latlong.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../l10n/app_localizations.dart';
import '../../screens/wikipedia_webview_screen.dart';
import '../../services/opening_hours.dart';
import '../../services/poi_search_service.dart';
import '../../services/routing_service.dart';
import '../../theme/app_theme.dart';

class PlaceInfoPanel extends StatefulWidget {
  final LatLng point;
  final String? address;
  final WikiSummary? wiki;
  final OsmPoiDetails? details;
  final String? wikiQuery;
  final String? openingHours;
  final bool loading;
  final double bottomInset;
  final RoadstrColors colors;
  final VoidCallback onNavigate, onClose;
  const PlaceInfoPanel({super.key, 
    required this.point,
    required this.address,
    required this.wiki,
    required this.details,
    this.wikiQuery,
    this.openingHours,
    required this.loading,
    required this.bottomInset,
    required this.colors,
    required this.onNavigate,
    required this.onClose,
  });

  @override
  State<PlaceInfoPanel> createState() => _PlaceInfoPanelState();
}

class _PlaceInfoPanelState extends State<PlaceInfoPanel>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<Offset> _slide;
  double _dragDy = 0; // real-time drag offset (positive = downward)

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 340));
    _slide = Tween<Offset>(begin: const Offset(0, 1), end: Offset.zero)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOutCubic));
    _ctrl.forward(); // slide in when panel first appears
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _closeAnimated() async {
    await _ctrl.animateTo(0,
        duration: const Duration(milliseconds: 260), curve: Curves.easeInCubic);
    widget.onClose();
  }

  /// Animated spring-back: smoothly returns the panel to its resting position
  /// so users can abort a downward drag without committing to close.
  void _springBack() {
    if (_dragDy <= 0) {
      setState(() => _dragDy = 0);
      return;
    }
    final start = _dragDy;
    final startMs = DateTime.now().millisecondsSinceEpoch;
    Timer.periodic(const Duration(milliseconds: 8), (t) {
      if (!mounted) {
        t.cancel();
        return;
      }
      final p = ((DateTime.now().millisecondsSinceEpoch - startMs) / 220.0)
          .clamp(0.0, 1.0);
      final e = 1 - math.pow(1 - p, 3);
      setState(() => _dragDy = start * (1 - e));
      if (p >= 1.0) {
        t.cancel();
        setState(() => _dragDy = 0);
      }
    });
  }

  void _finishPanelDrag(DragEndDetails details) {
    final velocity = details.primaryVelocity ?? 0;
    if (velocity > 400 || _dragDy > 120) {
      unawaited(_closeAnimated());
    } else if (velocity < -500 &&
        _dragDy < 10 &&
        widget.wiki?.pageUrl != null) {
      _openMore();
    } else {
      _springBack();
    }
  }

  /// Wikipedia articles stay in Roadstr's restricted reader. Generic search
  /// results remain external because they can navigate to arbitrary origins.
  void _openMore() {
    if (widget.wiki?.pageUrl != null) {
      final uri = Uri.tryParse(widget.wiki!.pageUrl!);
      if (uri != null && WikipediaWebViewScreen.isAllowedArticleUri(uri)) {
        Navigator.of(context).push(MaterialPageRoute<void>(
          builder: (_) => WikipediaWebViewScreen(
            articleUri: uri,
            title: widget.wiki!.title,
          ),
        ));
      }
      return;
    }
    final query = widget.wikiQuery;
    if (query == null) return;
    final uri = Uri.tryParse(_searchUrl(query));
    if (uri != null && uri.scheme == 'https') {
      unawaited(launchUrl(uri, mode: LaunchMode.externalApplication));
    }
  }

  void _openWebsite() {
    final uri = widget.details?.website;
    if (uri != null) {
      unawaited(launchUrl(uri, mode: LaunchMode.externalApplication));
    }
  }

  static const _heroTag = 'roadstr_place_info_panel';

  static String _engineName() {
    final e = Hive.box('settings').get('searchEngine', defaultValue: 'qwant')
        as String;
    return switch (e) {
      'brave' => 'Brave',
      'ddg' => 'DuckDuckGo',
      'startpage' => 'Startpage',
      'google' => 'Google',
      _ => 'Qwant',
    };
  }

  static String _searchUrl(String query) {
    final q = Uri.encodeComponent(query);
    final engine = Hive.box('settings')
        .get('searchEngine', defaultValue: 'qwant') as String;
    return switch (engine) {
      'brave' => 'https://search.brave.com/search?q=$q',
      'ddg' => 'https://duckduckgo.com/?q=$q',
      'startpage' => 'https://www.startpage.com/search?query=$q',
      'google' => 'https://www.google.com/search?q=$q',
      _ => 'https://www.qwant.com/?q=$q',
    };
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.colors;
    // Drag limit: 55% of available height so the panel stays partly visible
    // on short screens (480dp) while allowing full dismiss on tall ones.
    final maxDrag = MediaQuery.of(context).size.height * 0.55;
    return SlideTransition(
      position: _slide,
      child: Transform.translate(
        offset: Offset(0, _dragDy.clamp(0.0, maxDrag)),
        // Keep a stable Hero tag for any future in-app panel transition.
        child: Hero(
          tag: _heroTag,
          flightShuttleBuilder: (_, anim, __, ___, ____) => Material(
            color: c.surface2,
            borderRadius: BorderRadius.lerp(
                const BorderRadius.vertical(top: Radius.circular(20)),
                BorderRadius.zero,
                anim.value),
          ),
          child: Container(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.sizeOf(context).height * 0.80,
            ),
            decoration: BoxDecoration(
              gradient: c.panelGradient,
              color: c.panelGradient == null ? c.surface2 : null,
              borderRadius:
                  const BorderRadius.vertical(top: Radius.circular(20)),
              border: Border(top: BorderSide(color: c.border, width: 0.5)),
              boxShadow: [
                BoxShadow(
                    color: Colors.black.withValues(alpha: 0.12),
                    blurRadius: 16,
                    offset: const Offset(0, -4))
              ],
            ),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              // Drag gestures live only on the handle, so they never steal
              // vertical scrolling from a richly-tagged POI card.
              GestureDetector(
                behavior: HitTestBehavior.opaque,
                onVerticalDragUpdate: (details) {
                  if (details.delta.dy > 0) {
                    setState(() => _dragDy += details.delta.dy);
                  }
                },
                onVerticalDragEnd: _finishPanelDrag,
                child: SizedBox(
                  height: 26,
                  child: Center(
                    child: Container(
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(
                        color: c.border,
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                ),
              ),

              // Long, richly-tagged POIs remain usable on short screens.
              // The drag handle and route actions stay fixed while only the
              // informational content scrolls.
              Flexible(
                child: SingleChildScrollView(
                  physics: const ClampingScrollPhysics(),
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    // Title / address.
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 20),
                      child: widget.loading && widget.address == null
                          ? Row(children: [
                              SizedBox(
                                  width: 14,
                                  height: 14,
                                  child: CircularProgressIndicator(
                                      strokeWidth: 2, color: c.accent)),
                              const SizedBox(width: 10),
                              Text(AppLocalizations.of(context).loadingInfo,
                                  style: TextStyle(
                                      color: c.textSecondary, fontSize: 13)),
                            ])
                          : Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                  Text(
                                      widget.wiki?.title ??
                                          widget.details?.name ??
                                          widget.address ??
                                          '${widget.point.latitude.toStringAsFixed(5)}, '
                                              '${widget.point.longitude.toStringAsFixed(5)}',
                                      style: TextStyle(
                                          color: c.textPrimary,
                                          fontSize:
                                              widget.wiki != null ? 17 : 15,
                                          fontWeight: FontWeight.w600)),
                                  if (widget.address != null &&
                                      widget.address !=
                                          widget.details?.name) ...[
                                    const SizedBox(height: 2),
                                    Text(widget.address!,
                                        maxLines: 2,
                                        overflow: TextOverflow.ellipsis,
                                        style: TextStyle(
                                            color: c.textSecondary,
                                            fontSize: 12)),
                                  ],
                                  if (widget.openingHours != null) ...[
                                    const SizedBox(height: 6),
                                    OpeningHoursBadge(
                                        raw: widget.openingHours!, colors: c),
                                  ],
                                ]),
                    ),

                    // Wikipedia image + extract.
                    if (widget.wiki != null) ...[
                      const SizedBox(height: 10),
                      if (widget.wiki!.imageUrl != null)
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 16),
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(10),
                            child: Image.network(widget.wiki!.imageUrl!,
                                height: 110,
                                width: double.infinity,
                                fit: BoxFit.cover,
                                errorBuilder: (_, __, ___) =>
                                    const SizedBox.shrink()),
                          ),
                        ),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
                        child: Text(widget.wiki!.extract,
                            maxLines: 3,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                                color: c.textSecondary,
                                fontSize: 13,
                                height: 1.4)),
                      ),
                    ],

                    // OSM operational details complement Wikipedia when it
                    // exists and become the primary source for smaller POIs.
                    if (widget.details != null) ...[
                      const SizedBox(height: 10),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: OsmPoiDetailsCard(
                          details: widget.details!,
                          colors: c,
                          showDescription: widget.wiki == null,
                          onOpenWebsite: widget.details!.website == null
                              ? null
                              : _openWebsite,
                        ),
                      ),
                    ],

                    // "Learn more" / search engine button (shown only when there is something to open).
                    if (!widget.loading &&
                        (widget.wiki?.pageUrl != null ||
                            widget.wikiQuery != null)) ...[
                      const SizedBox(height: 8),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 20),
                        child: GestureDetector(
                          onTap: _openMore,
                          child: Row(mainAxisSize: MainAxisSize.min, children: [
                            Icon(
                                widget.wiki?.pageUrl != null
                                    ? Icons.article_outlined
                                    : Icons.search_rounded,
                                color: c.accent,
                                size: 14),
                            const SizedBox(width: 4),
                            Text(
                              widget.wiki?.pageUrl != null
                                  ? AppLocalizations.of(context).readOnWikipedia
                                  : AppLocalizations.of(context)
                                      .searchOnEngine(_engineName()),
                              style: TextStyle(
                                  color: c.accent,
                                  fontSize: 12,
                                  decoration: TextDecoration.underline),
                            ),
                          ]),
                        ),
                      ),
                    ],
                  ]),
                ),
              ),

              const SizedBox(height: 12),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(children: [
                  OutlinedButton(
                    onPressed: _closeAnimated,
                    style: OutlinedButton.styleFrom(
                      side: BorderSide(color: c.border),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 11),
                    ),
                    child: Text(AppLocalizations.of(context).cancel,
                        style: TextStyle(color: c.textSecondary)),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                      child: FilledButton.icon(
                    onPressed: widget.onNavigate,
                    style: FilledButton.styleFrom(
                      backgroundColor: c.accent,
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 11),
                    ),
                    icon: const Icon(Icons.navigation_rounded,
                        color: Colors.white, size: 18),
                    label: Text(AppLocalizations.of(context).navigateHere,
                        style: const TextStyle(color: Colors.white)),
                  )),
                ]),
              ),
              SizedBox(
                  height: widget.bottomInset > 0 ? widget.bottomInset : 14),
            ]),
          ), // Container
        ), // Hero
      ), // Transform.translate
    ); // SlideTransition
  }
}

// ── Opening hours ────────────────────────────────────────────────────────────

class OsmPoiDetailsCard extends StatelessWidget {
  final OsmPoiDetails details;
  final RoadstrColors colors;
  final bool showDescription;
  final VoidCallback? onOpenWebsite;

  const OsmPoiDetailsCard({super.key, 
    required this.details,
    required this.colors,
    this.showDescription = true,
    this.onOpenWebsite,
  });

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final wheelchairLabel = switch (details.wheelchair) {
      'yes' || 'designated' => l.poiWheelchairYes,
      'limited' => l.poiWheelchairLimited,
      'no' => l.poiWheelchairNo,
      _ => null,
    };
    final wheelchairColor = switch (details.wheelchair) {
      'yes' || 'designated' => const Color(0xFF2E9D67),
      'limited' => const Color(0xFFE09A2D),
      'no' => const Color(0xFFD55245),
      _ => colors.textSecondary,
    };
    final accessLabel = switch (details.access) {
      'private' => l.poiAccessPrivate,
      'customers' => l.poiAccessCustomers,
      'permit' => l.poiAccessPermit,
      'no' => l.poiAccessNo,
      'destination' => l.poiAccessDestination,
      _ => null,
    };
    final restrictedAccess = details.access == 'private' ||
        details.access == 'no' ||
        details.access == 'permit';
    final parkingTypeLabel = switch (details.parkingType) {
      'surface' => l.poiParkingSurface,
      'underground' => l.poiParkingUnderground,
      'multi-storey' => l.poiParkingMultiStorey,
      'street_side' => l.poiParkingStreetSide,
      'lane' => l.poiParkingLane,
      'rooftop' => l.poiParkingRooftop,
      _ => null,
    };
    final feeLabel = switch (details.fee?.trim().toLowerCase()) {
      'no' => l.poiFree,
      'yes' => l.poiPaid,
      final value? => value,
      _ => null,
    };
    final smokingLabel = switch (details.smoking) {
      'yes' => l.poiSmokingAllowed,
      'outside' => l.poiSmokingOutside,
      'separated' || 'isolated' || 'dedicated' => l.poiSmokingAreas,
      'no' => l.poiSmokeFree,
      _ => null,
    };
    final contact = [details.phone, details.email]
        .whereType<String>()
        .where((value) => value.isNotEmpty)
        .join(' · ');

    String connectorLabel(OsmEvConnector connector) {
      final type = switch (connector.type) {
        'type2' => l.poiConnectorType2,
        'chademo' => l.poiConnectorChademo,
        'type2_combo' => l.poiConnectorCcs,
        _ => connector.type,
      };
      return [
        type,
        if (connector.count != null) '× ${connector.count}',
        if (connector.output != null) connector.output!,
      ].join(' · ');
    }

    final featureChips = <Widget>[
      if (details.acceptsLightning)
        PoiFeatureChip(
          icon: Icons.bolt_rounded,
          label: l.poiLightningAccepted,
          color: const Color(0xFFF7931A),
        ),
      if (details.acceptsBitcoin)
        PoiFeatureChip(
          icon: Icons.currency_bitcoin_rounded,
          label: l.poiBitcoinAccepted,
          color: const Color(0xFFF7931A),
        ),
      if (details.stars != null)
        PoiFeatureChip(
          icon: Icons.star_rounded,
          label: '${details.stars} ★',
          color: const Color(0xFFE4A11B),
        ),
      if (wheelchairLabel != null)
        PoiFeatureChip(
          icon: Icons.accessible_rounded,
          label: wheelchairLabel,
          color: wheelchairColor,
        ),
      if (details.fuels.contains('diesel'))
        PoiFeatureChip(
          icon: Icons.local_gas_station_rounded,
          label: l.poiDiesel,
          color: colors.accent,
        ),
      if (details.fuels.contains('octane_95'))
        PoiFeatureChip(
          icon: Icons.local_gas_station_outlined,
          label: l.poiPetrol95,
          color: colors.accent,
        ),
      if (smokingLabel != null)
        PoiFeatureChip(
          icon: details.smoking == 'no'
              ? Icons.smoke_free_rounded
              : Icons.smoking_rooms_rounded,
          label: smokingLabel,
          color: colors.textSecondary,
        ),
      if (details.outdoorSeating == 'yes')
        PoiFeatureChip(
          icon: Icons.deck_outlined,
          label: l.poiOutdoorSeating,
          color: const Color(0xFF3A8D6D),
        ),
      if (details.takeaway == 'yes' || details.takeaway == 'only')
        PoiFeatureChip(
          icon: Icons.takeout_dining_rounded,
          label: details.takeaway == 'only' ? l.poiTakeawayOnly : l.poiTakeaway,
          color: colors.accent,
        ),
    ];

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(13, 12, 13, 13),
      decoration: BoxDecoration(
        gradient: colors.panelGradient,
        color: colors.panelGradient == null ? colors.surface1 : null,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colors.border, width: 0.5),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 10,
            offset: const Offset(0, 3),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Container(
              width: 27,
              height: 27,
              decoration: BoxDecoration(
                color: colors.accent.withValues(alpha: 0.10),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(Icons.map_outlined, size: 15, color: colors.accent),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                l.poiDetailsFromOsm,
                style: TextStyle(
                  color: colors.textPrimary,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            const SizedBox(width: 8),
            Flexible(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: colors.surface2,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  details.category,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: colors.textSecondary,
                    fontSize: 10.5,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
            ),
          ]),
          if (accessLabel != null) ...[
            const SizedBox(height: 10),
            PoiAccessBanner(
              label: accessLabel,
              restricted: restrictedAccess,
            ),
          ],
          if (showDescription && details.description != null) ...[
            const SizedBox(height: 10),
            Text(
              details.description!,
              maxLines: 4,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: colors.textPrimary,
                fontSize: 12.5,
                height: 1.4,
              ),
            ),
          ],
          if (featureChips.isNotEmpty) ...[
            const SizedBox(height: 10),
            Wrap(spacing: 6, runSpacing: 6, children: featureChips),
          ],
          if (details.kind == OsmPoiKind.parking &&
              (parkingTypeLabel != null ||
                  feeLabel != null ||
                  details.charge != null ||
                  details.capacity != null ||
                  details.maxStay != null)) ...[
            const SizedBox(height: 12),
            PoiDetailSection(
              icon: Icons.local_parking_rounded,
              title: l.poiParkingDetails,
              colors: colors,
              children: [
                if (parkingTypeLabel != null)
                  PoiDetailLine(
                    icon: Icons.layers_outlined,
                    label: l.poiCategory,
                    value: parkingTypeLabel,
                    colors: colors,
                  ),
                if (feeLabel != null)
                  PoiDetailLine(
                    icon: Icons.payments_outlined,
                    label: l.poiFee,
                    value: feeLabel,
                    colors: colors,
                  ),
                if (details.charge != null)
                  PoiDetailLine(
                    icon: Icons.sell_outlined,
                    label: l.poiPrice,
                    value: details.charge!,
                    colors: colors,
                  ),
                if (details.capacity != null)
                  PoiDetailLine(
                    icon: Icons.directions_car_outlined,
                    label: l.poiCapacity,
                    value: details.capacity.toString(),
                    colors: colors,
                  ),
                if (details.maxStay != null)
                  PoiDetailLine(
                    icon: Icons.timer_outlined,
                    label: l.poiMaxStay,
                    value: details.maxStay!,
                    colors: colors,
                  ),
              ],
            ),
          ],
          if (details.kind == OsmPoiKind.chargingStation &&
              (details.evConnectors.isNotEmpty ||
                  feeLabel != null ||
                  details.charge != null ||
                  details.capacity != null)) ...[
            const SizedBox(height: 12),
            PoiDetailSection(
              icon: Icons.ev_station_rounded,
              title: l.poiChargingDetails,
              colors: colors,
              children: [
                if (details.evConnectors.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 5),
                    child: Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: details.evConnectors
                          .map((connector) => PoiFeatureChip(
                                icon: Icons.electrical_services_rounded,
                                label: connectorLabel(connector),
                                color: const Color(0xFF2E9D67),
                              ))
                          .toList(),
                    ),
                  ),
                if (feeLabel != null)
                  PoiDetailLine(
                    icon: Icons.payments_outlined,
                    label: l.poiFee,
                    value: feeLabel,
                    colors: colors,
                  ),
                if (details.charge != null)
                  PoiDetailLine(
                    icon: Icons.sell_outlined,
                    label: l.poiPrice,
                    value: details.charge!,
                    colors: colors,
                  ),
                if (details.capacity != null)
                  PoiDetailLine(
                    icon: Icons.ev_station_outlined,
                    label: l.poiCapacity,
                    value: details.capacity.toString(),
                    colors: colors,
                  ),
              ],
            ),
          ],
          if (details.operatorName != null ||
              (details.cuisine != null && details.cuisine!.isNotEmpty) ||
              contact.isNotEmpty ||
              details.address != null ||
              details.website != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Divider(height: 1, color: colors.border),
            ),
          if (details.operatorName != null)
            PoiDetailLine(
              icon: Icons.business_outlined,
              label: l.poiOperator,
              value: details.operatorName!,
              colors: colors,
            ),
          if (details.cuisine != null && details.cuisine!.isNotEmpty)
            PoiDetailLine(
              icon: Icons.restaurant_menu_rounded,
              label: l.poiCuisine,
              value: details.cuisine!,
              colors: colors,
            ),
          if (contact.isNotEmpty)
            PoiDetailLine(
              icon: Icons.contact_phone_outlined,
              label: l.poiContact,
              value: contact,
              colors: colors,
            ),
          if (details.address != null)
            PoiDetailLine(
              icon: Icons.location_on_outlined,
              label: l.poiAddress,
              value: details.address!,
              colors: colors,
            ),
          if (details.website != null) ...[
            const SizedBox(height: 5),
            InkWell(
              onTap: onOpenWebsite,
              borderRadius: BorderRadius.circular(6),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 3),
                child: Row(children: [
                  Icon(Icons.open_in_new_rounded,
                      size: 13, color: colors.accent),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      '${l.poiWebsite}: ${details.website!.host}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: colors.accent, fontSize: 11.5),
                    ),
                  ),
                ]),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class PoiAccessBanner extends StatelessWidget {
  final String label;
  final bool restricted;

  const PoiAccessBanner({super.key, required this.label, required this.restricted});

  @override
  Widget build(BuildContext context) {
    final color =
        restricted ? const Color(0xFFD55245) : const Color(0xFFE09A2D);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.11),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: color.withValues(alpha: 0.28), width: 0.7),
      ),
      child: Row(children: [
        Icon(
          restricted ? Icons.lock_outline_rounded : Icons.badge_outlined,
          size: 15,
          color: color,
        ),
        const SizedBox(width: 7),
        Expanded(
          child: Text(
            label,
            style: TextStyle(
              color: color,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ]),
    );
  }
}

class PoiFeatureChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;

  const PoiFeatureChip({super.key, 
    required this.icon,
    required this.label,
    required this.color,
  });

  @override
  Widget build(BuildContext context) => Container(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.sizeOf(context).width - 72,
        ),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.10),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: color.withValues(alpha: 0.22), width: 0.6),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          Icon(icon, size: 13, color: color),
          const SizedBox(width: 5),
          Flexible(
            child: Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: color,
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ]),
      );
}

class PoiDetailSection extends StatelessWidget {
  final IconData icon;
  final String title;
  final RoadstrColors colors;
  final List<Widget> children;

  const PoiDetailSection({super.key, 
    required this.icon,
    required this.title,
    required this.colors,
    required this.children,
  });

  @override
  Widget build(BuildContext context) => Container(
        width: double.infinity,
        padding: const EdgeInsets.fromLTRB(10, 9, 10, 10),
        decoration: BoxDecoration(
          color: colors.surface2,
          borderRadius: BorderRadius.circular(11),
          border: Border.all(color: colors.border, width: 0.5),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              Icon(icon, size: 15, color: colors.accent),
              const SizedBox(width: 6),
              Text(
                title,
                style: TextStyle(
                  color: colors.textPrimary,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ]),
            ...children,
          ],
        ),
      );
}

class PoiDetailLine extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final RoadstrColors colors;

  const PoiDetailLine({super.key, 
    required this.icon,
    required this.label,
    required this.value,
    required this.colors,
  });

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, size: 13, color: colors.textSecondary),
            const SizedBox(width: 6),
            Expanded(
              child: Text.rich(
                TextSpan(children: [
                  TextSpan(
                    text: '$label: ',
                    style: TextStyle(
                      color: colors.textSecondary,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  TextSpan(
                    text: value,
                    style: TextStyle(color: colors.textPrimary),
                  ),
                ]),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 11.5, height: 1.25),
              ),
            ),
          ],
        ),
      );
}

/// Open/closed badge derived from a raw OSM `opening_hours` string.
///
/// Parses the string locally (see [OpeningHours]) and shows a coloured dot +
/// status + next-change time. For strings the parser can't safely evaluate
/// (month scoping, comments…) it degrades to showing the raw hours with a
/// clock icon — never a possibly-wrong open/closed claim.
class OpeningHoursBadge extends StatelessWidget {
  final String raw;
  final RoadstrColors colors;
  const OpeningHoursBadge({super.key, required this.raw, required this.colors});

  /// 24-hour HH:MM. Uses intl for the active locale but falls back to a manual
  /// format if that locale's date symbols were never initialized (e.g. `ga`/
  /// `mt`, which fall back to English Material localizations) — an uninitialized
  /// locale would otherwise throw LocaleDataException and blank the panel.
  static String _fmtTime(DateTime d, String locale) {
    try {
      return DateFormat.Hm(locale).format(d);
    } catch (_) {
      return '${d.hour.toString().padLeft(2, '0')}:'
          '${d.minute.toString().padLeft(2, '0')}';
    }
  }

  static String _fmtWeekday(DateTime d, String locale) {
    try {
      return DateFormat.E(locale).format(d);
    } catch (_) {
      const en = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
      return en[d.weekday - 1];
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final c = colors;
    final now = DateTime.now();
    final status = OpeningHours.evaluate(raw, now);
    final locale = Localizations.localeOf(context).toString();

    if (status.state == OpenState.unknown) {
      // Fallback: show the raw hours verbatim, no colour claim.
      return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(Icons.schedule_rounded, size: 13, color: c.textSecondary),
        const SizedBox(width: 5),
        Expanded(
            child: Text(raw,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(color: c.textSecondary, fontSize: 12))),
      ]);
    }

    final open = status.state == OpenState.open;
    final dot = open ? const Color(0xFF2FBF71) : const Color(0xFFE0533D);
    final label = open ? l.poiOpenNow : l.poiClosedNow;

    String? detail;
    final change = status.nextChange;
    if (change != null) {
      final time = _fmtTime(change, locale);
      if (open) {
        detail = l.poiClosesAt(time);
      } else {
        // Same calendar day → just the time; otherwise prefix the weekday.
        final sameDay = change.year == now.year &&
            change.month == now.month &&
            change.day == now.day;
        final when = sameDay ? time : '${_fmtWeekday(change, locale)} $time';
        detail = l.poiOpensAt(when);
      }
    }

    return Row(children: [
      Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(color: dot, shape: BoxShape.circle)),
      const SizedBox(width: 6),
      Text(label,
          style: TextStyle(
              color: dot, fontSize: 12.5, fontWeight: FontWeight.w600)),
      if (detail != null) ...[
        const SizedBox(width: 6),
        Flexible(
            child: Text('· $detail',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(color: c.textSecondary, fontSize: 12))),
      ],
    ]);
  }
}

// ── Waypoint ──────────────────────────────────────────────────────────────────
