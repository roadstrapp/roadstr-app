// The route-choice flow, in the order the user meets it: the from/to planner
// bar, the list of alternative routes, and the preview panel that confirms one
// before navigation starts.
import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:latlong2/latlong.dart';

import '../../l10n/app_localizations.dart';
import '../../models/road_event.dart';
import '../../services/routing_service.dart';
import '../../services/weather_service.dart';
import '../../theme/app_theme.dart';
import '../../utils/units.dart';

class RoutePlannerBar extends StatelessWidget {
  final TextEditingController fromCtrl;

  /// One controller per stop, in driving order. The last is the destination;
  /// anything before it is an intermediate stop.
  final List<TextEditingController> stopCtrls;

  final int activeField;
  final bool hasGps, canCalculate, isSearching;
  final String transportMode;
  final RoadstrColors colors;
  final VoidCallback onFromTap, onMyLocation, onClose;
  final VoidCallback onCalculate;
  final ValueChanged<String> onFromChanged;
  final ValueChanged<String> onModeChanged;

  /// Taps and edits, addressed by stop index.
  final ValueChanged<int> onStopTap;
  final void Function(int index, String query) onStopChanged;

  /// Adds a stop before the destination, removes one, or moves one.
  final VoidCallback? onAddStop;
  final ValueChanged<int> onRemoveStop;
  final void Function(int oldIndex, int newIndex) onReorderStops;

  const RoutePlannerBar({super.key, 
    required this.fromCtrl,
    required this.stopCtrls,
    required this.activeField,
    required this.hasGps,
    required this.canCalculate,
    required this.isSearching,
    required this.transportMode,
    required this.colors,
    required this.onFromTap,
    required this.onMyLocation,
    required this.onClose,
    required this.onCalculate,
    required this.onFromChanged,
    required this.onModeChanged,
    required this.onStopTap,
    required this.onStopChanged,
    required this.onRemoveStop,
    required this.onReorderStops,
    this.onAddStop,
  });

  @override
  Widget build(BuildContext context) {
    final c = colors;
    return Container(
      decoration: BoxDecoration(
        color: c.surface2,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: c.border, width: 0.5),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withValues(alpha: 0.16),
              blurRadius: 12,
              offset: const Offset(0, 3))
        ],
      ),
      padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        // ── Da ────────────────────────────────────────────────────────────
        Row(children: [
          Container(
              width: 10,
              height: 10,
              decoration:
                  BoxDecoration(color: c.accent, shape: BoxShape.circle)),
          const SizedBox(width: 10),
          Expanded(
            child: TextField(
              controller: fromCtrl,
              onTap: () {
                onFromTap();
                // If showing "My location", select-all so a single backspace clears it.
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (fromCtrl.text.isNotEmpty) {
                    fromCtrl.selection = TextSelection(
                        baseOffset: 0, extentOffset: fromCtrl.text.length);
                  }
                });
              },
              onChanged: onFromChanged,
              autofocus: false,
              style: TextStyle(color: c.textPrimary, fontSize: 14),
              decoration: InputDecoration(
                hintText: AppLocalizations.of(context).plannerFromHint,
                hintStyle: TextStyle(color: c.textSecondary, fontSize: 14),
                border: InputBorder.none,
                isDense: true,
              ),
            ),
          ),
          if (hasGps)
            GestureDetector(
              onTap: onMyLocation,
              child: Icon(Icons.my_location_rounded, color: c.accent, size: 18),
            ),
        ]),
        Divider(height: 8, color: c.border),
        // ── Stops, in driving order ───────────────────────────────────────
        _StopList(
          controllers: stopCtrls,
          colors: c,
          isSearching: isSearching,
          onStopTap: onStopTap,
          onStopChanged: onStopChanged,
          onRemoveStop: onRemoveStop,
          onReorderStops: onReorderStops,
        ),
        if (onAddStop != null)
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: onAddStop,
              style: TextButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  visualDensity: VisualDensity.compact),
              icon: Icon(Icons.add_location_alt_outlined,
                  size: 16, color: c.accent),
              label: Text(AppLocalizations.of(context).plannerAddStop,
                  style: TextStyle(color: c.accent, fontSize: 13)),
            ),
          ),
        const SizedBox(height: 8),
        // ── Transport mode toggle ─────────────────────────────────────────
        // Scrolls horizontally: three labels in a language with long words
        // (German "ÖPNV" is short, Finnish "Joukkoliikenne" is not) would
        // otherwise overflow the row on a narrow screen.
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(children: [
            TransportModeChip(
              icon: Icons.directions_car_rounded,
              label: AppLocalizations.of(context).transportModeCar,
              selected: transportMode == 'driving',
              colors: c,
              onTap: () => onModeChanged('driving'),
            ),
            const SizedBox(width: 8),
            // Cycling was reachable only from the result panels, so a journey
            // could not be planned by bike from the start — the planner is
            // where a mode is normally chosen.
            TransportModeChip(
              icon: Icons.directions_bike_rounded,
              label: AppLocalizations.of(context).modeBike,
              selected: transportMode == 'cycling',
              colors: c,
              onTap: () => onModeChanged('cycling'),
            ),
            const SizedBox(width: 8),
            TransportModeChip(
              icon: Icons.directions_walk_rounded,
              label: AppLocalizations.of(context).transportModeWalk,
              // Transit journeys start and end on foot, so the walking chip
              // stays lit while its sub-choice is open — otherwise picking
              // public transport would look like leaving walking behind.
              selected: transportMode == 'walking' ||
                  transportMode == 'transit',
              colors: c,
              onTap: () => onModeChanged('walking'),
            ),
          ]),
        ),
        WalkingSubModes(
          transportMode: transportMode,
          colors: c,
          onModeChanged: onModeChanged,
        ),
        const SizedBox(height: 10),
        Row(children: [
          TextButton(
            onPressed: onClose,
            style: TextButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 12)),
            child: Text(AppLocalizations.of(context).cancel,
                style: TextStyle(color: c.textSecondary, fontSize: 13)),
          ),
          const Spacer(),
          FilledButton.icon(
            onPressed: canCalculate ? onCalculate : null,
            style: FilledButton.styleFrom(
              backgroundColor: canCalculate ? c.accent : c.border,
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12)),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            ),
            icon: Icon(
                transportMode == 'walking'
                    ? Icons.directions_walk_rounded
                    : Icons.navigation_rounded,
                color: Colors.white,
                size: 16),
            label: Text(AppLocalizations.of(context).calculateRoute,
                style: const TextStyle(color: Colors.white, fontSize: 13)),
          ),
        ]),
      ]),
    );
  }
}

/// The stops of a journey, in driving order, reorderable by dragging.
///
/// The order is the substance, not a presentation detail: routing through the
/// same three places in a different sequence produces a materially different
/// journey — measured on a real corridor, 58 km one way round and 69 km the
/// other. So the list has to be rearrangeable, and it has to be obvious that
/// it can be, which is what the grip on each row is for.
///
/// Sized rather than scrollable: at five rows maximum the whole list fits, and
/// a scroll view inside a panel that is itself inside a stack makes dragging
/// fight with panning.
class _StopList extends StatelessWidget {
  final List<TextEditingController> controllers;
  final RoadstrColors colors;
  final bool isSearching;
  final ValueChanged<int> onStopTap;
  final void Function(int index, String query) onStopChanged;
  final ValueChanged<int> onRemoveStop;
  final void Function(int oldIndex, int newIndex) onReorderStops;

  const _StopList({
    required this.controllers,
    required this.colors,
    required this.isSearching,
    required this.onStopTap,
    required this.onStopChanged,
    required this.onRemoveStop,
    required this.onReorderStops,
  });

  static const _rowHeight = 44.0;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final single = controllers.length == 1;
    return SizedBox(
      height: _rowHeight * controllers.length,
      child: ReorderableListView.builder(
        buildDefaultDragHandles: false,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: controllers.length,
        onReorderItem: onReorderStops,
        proxyDecorator: (child, _, __) => Material(
          color: Colors.transparent,
          child: Opacity(opacity: 0.92, child: child),
        ),
        itemBuilder: (context, i) {
          final isLast = i == controllers.length - 1;
          return SizedBox(
            key: ValueKey(controllers[i]),
            height: _rowHeight,
            child: Row(children: [
              // Hollow red pin for the destination, solid accent for the stops
              // along the way: which row is the end of the journey has to
              // survive being dragged into a different position.
              Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(
                  color: isLast ? null : colors.accent,
                  border: isLast
                      ? Border.all(color: const Color(0xFFEA4335), width: 2)
                      : null,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextField(
                  controller: controllers[i],
                  onTap: () => onStopTap(i),
                  onChanged: (q) => onStopChanged(i, q),
                  autofocus: isLast && single,
                  style: TextStyle(color: colors.textPrimary, fontSize: 14),
                  decoration: InputDecoration(
                    hintText: isLast ? l.plannerToHint : l.plannerStopHint,
                    hintStyle:
                        TextStyle(color: colors.textSecondary, fontSize: 14),
                    border: InputBorder.none,
                    isDense: true,
                  ),
                ),
              ),
              if (isSearching && !single)
                const SizedBox(width: 4)
              else if (isSearching)
                SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                        strokeWidth: 2, color: colors.accent)),
              // A single destination cannot be removed — there would be no
              // journey left to plan.
              if (!single)
                IconButton(
                  onPressed: () => onRemoveStop(i),
                  visualDensity: VisualDensity.compact,
                  icon: Icon(Icons.close_rounded,
                      size: 16, color: colors.textSecondary),
                ),
              if (!single)
                ReorderableDragStartListener(
                  index: i,
                  child: Padding(
                    padding: const EdgeInsets.only(left: 2, right: 2),
                    child: Icon(Icons.drag_handle_rounded,
                        size: 20, color: colors.textSecondary),
                  ),
                ),
            ]),
          );
        },
      ),
    );
  }
}

/// The on-foot sub-choice: walk the whole way, or take a scheduled service.
///
/// Appears under the walking chip in every panel that offers transport modes.
/// It lives here as one widget rather than being repeated in each panel
/// because there are three of them — the planner, the preview and the
/// alternatives list — and a sub-menu that exists in only one is a feature the
/// user cannot find from where they actually are.
class WalkingSubModes extends StatelessWidget {
  final String transportMode;
  final RoadstrColors colors;
  final ValueChanged<String> onModeChanged;
  final EdgeInsetsGeometry padding;

  const WalkingSubModes({
    super.key,
    required this.transportMode,
    required this.colors,
    required this.onModeChanged,
    this.padding = const EdgeInsets.only(top: 8),
  });

  /// Whether the journey is on foot at all — transit included, since such a
  /// journey starts and ends walking.
  static bool appliesTo(String mode) => mode == 'walking' || mode == 'transit';

  @override
  Widget build(BuildContext context) {
    if (!appliesTo(transportMode)) return const SizedBox.shrink();
    final l = AppLocalizations.of(context);
    return Padding(
      padding: padding,
      child: Row(children: [
        Icon(Icons.subdirectory_arrow_right_rounded,
            size: 16, color: colors.textSecondary),
        const SizedBox(width: 6),
        Flexible(
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              TransportModeChip(
                icon: Icons.directions_walk_rounded,
                label: l.transportModeWalk,
                selected: transportMode == 'walking',
                colors: colors,
                onTap: () => onModeChanged('walking'),
              ),
              const SizedBox(width: 8),
              TransportModeChip(
                icon: Icons.directions_transit_rounded,
                label: l.transportModeTransit,
                selected: transportMode == 'transit',
                colors: colors,
                onTap: () => onModeChanged('transit'),
              ),
            ]),
          ),
        ),
      ]),
    );
  }
}

/// Small selectable pill used in the route-planner transport-mode toggle.
class TransportModeChip extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool selected;
  final RoadstrColors colors;
  final VoidCallback onTap;
  const TransportModeChip({super.key, required this.icon,
      required this.label,
      required this.selected,
      required this.colors,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    final c = colors;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: selected ? c.accent : c.surface3,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
              color: selected ? c.accent : c.border, width: selected ? 2 : 0.5),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          Icon(icon,
              size: 14, color: selected ? Colors.white : c.textSecondary),
          const SizedBox(width: 5),
          Text(label,
              style: TextStyle(
                  fontSize: 12,
                  color: selected ? Colors.white : c.textSecondary,
                  fontWeight: selected ? FontWeight.w600 : FontWeight.normal)),
        ]),
      ),
    );
  }
}

class RoutePreviewPanel extends StatefulWidget {
  final RouteResult route;
  final String? label;
  final List<RoadEvent> trafficEvents;
  final ({String label, Color color})? trafficStatus;
  final double bottomInset;
  final RoadstrColors colors;
  final String transportMode;
  final VoidCallback onStart;
  final VoidCallback onCancel;
  final ValueChanged<String> onModeChanged;
  const RoutePreviewPanel({super.key, 
    required this.route,
    required this.label,
    required this.trafficEvents,
    this.trafficStatus,
    required this.bottomInset,
    required this.colors,
    required this.transportMode,
    required this.onStart,
    required this.onCancel,
    required this.onModeChanged,
  });

  static String _fmtTime(DateTime dt) =>
      '${dt.hour.toString().padLeft(2, '0')}:'
      '${dt.minute.toString().padLeft(2, '0')}';

  @override
  State<RoutePreviewPanel> createState() => _PreviewPanelState();
}

class _PreviewPanelState extends State<RoutePreviewPanel>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<Offset> _slide;
  double _dragDy = 0;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 300));
    _slide = Tween<Offset>(begin: const Offset(0, 1), end: Offset.zero)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOutCubic));
    _ctrl.forward();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _closeAnimated() async {
    await _ctrl.animateTo(0,
        duration: const Duration(milliseconds: 240), curve: Curves.easeInCubic);
    widget.onCancel();
  }

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

  @override
  Widget build(BuildContext context) {
    final c = widget.colors;
    final l = AppLocalizations.of(context);
    final now = DateTime.now();
    final arrival =
        now.add(Duration(seconds: widget.route.totalDurationS.round()));
    final depStr = RoutePreviewPanel._fmtTime(now);
    final arrStr = RoutePreviewPanel._fmtTime(arrival);

    return SlideTransition(
      position: _slide,
      child: Transform.translate(
        offset: Offset(0, _dragDy.clamp(0.0, double.infinity)),
        child: GestureDetector(
          onVerticalDragUpdate: (d) {
            if (d.delta.dy > 0) setState(() => _dragDy += d.delta.dy);
          },
          onVerticalDragEnd: (d) {
            final v = d.primaryVelocity ?? 0;
            if (v > 400 || _dragDy > 120) {
              _closeAnimated();
            } else {
              _springBack();
            }
          },
          child: Container(
            decoration: BoxDecoration(
              color: c.surface2,
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
              const SizedBox(height: 10),
              Center(
                  child: Container(
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(
                          color: c.border,
                          borderRadius: BorderRadius.circular(2)))),
              const SizedBox(height: 14),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (widget.label != null && widget.label!.isNotEmpty) ...[
                        Text(widget.label!,
                            style: TextStyle(
                                color: c.textPrimary,
                                fontSize: 17,
                                fontWeight: FontWeight.bold),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis),
                        const SizedBox(height: 10),
                      ],
                      Row(children: [
                        Icon(Icons.access_time_rounded,
                            color: c.accent, size: 20),
                        const SizedBox(width: 6),
                        Text(widget.route.durationLabel,
                            style: TextStyle(
                                color: c.textPrimary,
                                fontSize: 22,
                                fontWeight: FontWeight.bold)),
                        const SizedBox(width: 16),
                        Text(widget.route.distanceLabel,
                            style: TextStyle(
                                color: c.textSecondary, fontSize: 15)),
                      ]),
                      const SizedBox(height: 8),
                      Row(children: [
                        Icon(Icons.schedule_rounded,
                            color: c.textSecondary, size: 16),
                        const SizedBox(width: 4),
                        Text(l.departEta(depStr, arrStr),
                            style: TextStyle(
                                color: c.textSecondary, fontSize: 13)),
                      ]),
                      if (widget.transportMode != 'walking' &&
                          widget.trafficStatus != null) ...[
                        const SizedBox(height: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 10, vertical: 5),
                          decoration: BoxDecoration(
                            color: widget.trafficStatus!.color
                                .withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(
                                color: widget.trafficStatus!.color
                                    .withValues(alpha: 0.4)),
                          ),
                          child: Text(widget.trafficStatus!.label,
                              style: TextStyle(
                                  color: widget.trafficStatus!.color,
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600)),
                        ),
                      ],
                      if (widget.trafficEvents.isNotEmpty) ...[
                        const SizedBox(height: 12),
                        Divider(height: 0.5, color: c.border),
                        const SizedBox(height: 10),
                        Text(l.conditionsOnRoute,
                            style: TextStyle(
                                color: c.textSecondary,
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                letterSpacing: 0.5)),
                        const SizedBox(height: 6),
                        ...widget.trafficEvents.take(3).map((ev) => Padding(
                              padding: const EdgeInsets.only(bottom: 4),
                              child: Row(children: [
                                Text(ev.category.emoji,
                                    style: const TextStyle(fontSize: 14)),
                                const SizedBox(width: 6),
                                Text(ev.category.localizedLabel(l),
                                    style: TextStyle(
                                        color: c.textPrimary,
                                        fontSize: 13,
                                        fontWeight: FontWeight.w500)),
                                const SizedBox(width: 4),
                                if (ev.comment.isNotEmpty)
                                  Expanded(
                                      child: Text('· ${ev.comment}',
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: TextStyle(
                                              color: c.textSecondary,
                                              fontSize: 12))),
                              ]),
                            )),
                      ],
                    ]),
              ),
              const SizedBox(height: 12),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(children: [
                  TransportModeChip(
                      icon: Icons.directions_car_rounded,
                      label: l.modeCar,
                      selected: widget.transportMode == 'driving',
                      colors: c,
                      onTap: () => widget.onModeChanged('driving')),
                  const SizedBox(width: 8),
                  TransportModeChip(
                      icon: Icons.directions_bike_rounded,
                      label: l.modeBike,
                      selected: widget.transportMode == 'cycling',
                      colors: c,
                      onTap: () => widget.onModeChanged('cycling')),
                  const SizedBox(width: 8),
                  TransportModeChip(
                      icon: Icons.directions_walk_rounded,
                      label: l.modeWalk,
                      selected: widget.transportMode == 'walking',
                      colors: c,
                      onTap: () => widget.onModeChanged('walking')),
                ]),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: WalkingSubModes(
                  transportMode: widget.transportMode,
                  colors: c,
                  onModeChanged: widget.onModeChanged,
                ),
              ),
              const SizedBox(height: 10),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: widget.onCancel,
                      style: OutlinedButton.styleFrom(
                        side: BorderSide(color: c.border),
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12)),
                        padding: const EdgeInsets.symmetric(vertical: 13),
                      ),
                      child: Text(l.cancel,
                          style: TextStyle(color: c.textSecondary)),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: FilledButton.icon(
                      onPressed: widget.onStart,
                      style: FilledButton.styleFrom(
                        backgroundColor: c.accent,
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12)),
                        padding: const EdgeInsets.symmetric(vertical: 13),
                      ),
                      icon: Icon(
                          widget.transportMode == 'walking'
                              ? Icons.directions_walk_rounded
                              : Icons.navigation_rounded,
                          color: Colors.white,
                          size: 18),
                      label: Text(l.startNavigation,
                          style: const TextStyle(color: Colors.white)),
                    ),
                  ),
                ]),
              ),
              SizedBox(
                  height: widget.bottomInset > 0 ? widget.bottomInset : 16),
            ]),
          ),
        ),
      ),
    );
  }
}

// ── Alternatives panel ────────────────────────────────────────────────────────

class RouteAlternativesPanel extends StatefulWidget {
  final List<RouteResult> alternatives;
  final int selected;
  final double bottomInset;
  final RoadstrColors colors;
  final String transportMode;
  final LatLng destination;
  final bool avoidanceEnabled;
  final bool avoidanceLoading;
  final ValueChanged<int> onSelect;
  final ValueChanged<RouteResult> onConfirm;
  final VoidCallback onCancel;
  final ValueChanged<String> onModeChanged;
  final ValueChanged<bool> onAvoidanceChanged;
  const RouteAlternativesPanel({super.key, 
    required this.alternatives,
    required this.selected,
    required this.bottomInset,
    required this.colors,
    required this.transportMode,
    required this.destination,
    required this.avoidanceEnabled,
    required this.avoidanceLoading,
    required this.onSelect,
    required this.onConfirm,
    required this.onCancel,
    required this.onModeChanged,
    required this.onAvoidanceChanged,
  });

  @override
  State<RouteAlternativesPanel> createState() => _AlternativesPanelState();
}

class _AlternativesPanelState extends State<RouteAlternativesPanel>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<Offset> _slide;
  double _dragDy = 0;
  WeatherData? _weather;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 340));
    _slide = Tween<Offset>(begin: const Offset(0, 1), end: Offset.zero)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOutCubic));
    _ctrl.forward();
    _loadWeather();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _loadWeather() async {
    final w = await WeatherService.fetch(widget.destination);
    if (mounted) setState(() => _weather = w);
  }

  Future<void> _closeAnimated() async {
    await _ctrl.animateTo(0,
        duration: const Duration(milliseconds: 260), curve: Curves.easeInCubic);
    widget.onCancel();
  }

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

  @override
  Widget build(BuildContext context) {
    final c = widget.colors;
    final l = AppLocalizations.of(context);
    final avoidanceRoute = widget.alternatives
        .where((route) => route.isHighwayAndTollAvoidance)
        .firstOrNull;
    final avoidanceComplete = avoidanceRoute?.avoidsHighwaysAndTolls ?? true;
    final avoidanceColor =
        avoidanceComplete ? const Color(0xFF14A67A) : const Color(0xFFF59E0B);
    return SlideTransition(
      position: _slide,
      child: Transform.translate(
        offset: Offset(0, _dragDy.clamp(0.0, double.infinity)),
        child: GestureDetector(
          onVerticalDragUpdate: (d) {
            if (d.delta.dy > 0) setState(() => _dragDy += d.delta.dy);
          },
          onVerticalDragEnd: (d) {
            final v = d.primaryVelocity ?? 0;
            if (v > 400 || _dragDy > 120) {
              _closeAnimated();
            } else {
              _springBack();
            }
          },
          child: Container(
            decoration: BoxDecoration(
              color: c.surface2,
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
              const SizedBox(height: 10),
              Center(
                  child: Container(
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(
                          color: c.border,
                          borderRadius: BorderRadius.circular(2)))),
              const SizedBox(height: 10),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Text(l.chooseRoute,
                    style: TextStyle(
                        color: c.textPrimary,
                        fontSize: 16,
                        fontWeight: FontWeight.bold)),
              ),
              const SizedBox(height: 10),
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  TransportModeChip(
                      icon: Icons.directions_car_rounded,
                      label: l.modeCar,
                      selected: widget.transportMode == 'driving',
                      colors: c,
                      onTap: () => widget.onModeChanged('driving')),
                  const SizedBox(width: 6),
                  TransportModeChip(
                      icon: Icons.directions_bike_rounded,
                      label: l.modeBike,
                      selected: widget.transportMode == 'cycling',
                      colors: c,
                      onTap: () => widget.onModeChanged('cycling')),
                  const SizedBox(width: 6),
                  TransportModeChip(
                      icon: Icons.directions_walk_rounded,
                      label: l.modeWalk,
                      selected: widget.transportMode == 'walking',
                      colors: c,
                      onTap: () => widget.onModeChanged('walking')),
                ]),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: WalkingSubModes(
                  transportMode: widget.transportMode,
                  colors: c,
                  onModeChanged: widget.onModeChanged,
                ),
              ),
              if (widget.transportMode == 'driving') ...[
                const SizedBox(height: 8),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Material(
                    color: widget.avoidanceEnabled
                        ? avoidanceColor.withValues(alpha: 0.12)
                        : c.surface3,
                    borderRadius: BorderRadius.circular(14),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(14),
                      onTap: widget.avoidanceLoading
                          ? null
                          : () => widget
                              .onAvoidanceChanged(!widget.avoidanceEnabled),
                      child: Padding(
                        padding: const EdgeInsets.fromLTRB(12, 7, 8, 7),
                        child: Row(children: [
                          Container(
                            width: 34,
                            height: 34,
                            decoration: BoxDecoration(
                              color: avoidanceColor.withValues(alpha: 0.16),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: widget.avoidanceLoading
                                ? const Padding(
                                    padding: EdgeInsets.all(9),
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                      color: Color(0xFF14A67A),
                                    ),
                                  )
                                : Icon(Icons.money_off_csred_rounded,
                                    size: 19, color: avoidanceColor),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(l.avoidHighwaysAndTolls,
                                    style: TextStyle(
                                      color: c.textPrimary,
                                      fontSize: 13,
                                      fontWeight: FontWeight.w600,
                                    )),
                                if (avoidanceRoute?.avoidance ==
                                    RouteAvoidance
                                        .minimizedHighwaysAndTolls) ...[
                                  const SizedBox(height: 2),
                                  Text(l.avoidanceUnavoidableSection,
                                      style: TextStyle(
                                        color: avoidanceColor,
                                        fontSize: 10.5,
                                        fontWeight: FontWeight.w500,
                                      )),
                                ],
                              ],
                            ),
                          ),
                          Switch.adaptive(
                            value: widget.avoidanceEnabled,
                            activeTrackColor: avoidanceColor,
                            onChanged: widget.avoidanceLoading
                                ? null
                                : widget.onAvoidanceChanged,
                          ),
                        ]),
                      ),
                    ),
                  ),
                ),
              ],
              if (_weather != null) ...[
                const SizedBox(height: 6),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: Row(children: [
                    Text(_weather!.emoji, style: const TextStyle(fontSize: 16)),
                    const SizedBox(width: 6),
                    Text(
                      () {
                        final imp = Units.imperial;
                        final temp = imp
                            ? '${(_weather!.tempC * 9 / 5 + 32).toStringAsFixed(0)}°F'
                            : '${_weather!.tempC.toStringAsFixed(0)}°C';
                        final wind = l.windSpeed(
                            '${Units.toDisplaySpeed(_weather!.windKmh).toStringAsFixed(0)}'
                            ' ${Units.speedUnit}');
                        return '$temp · ${_weather!.localizedDescription(l)} · $wind';
                      }(),
                      style: TextStyle(color: c.textSecondary, fontSize: 12),
                    ),
                  ]),
                ),
              ],
              const SizedBox(height: 12),
              SizedBox(
                height: 132,
                child: ListView.builder(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  itemCount: widget.alternatives.length,
                  itemBuilder: (_, i) => RouteCard(
                    route: widget.alternatives[i],
                    isSelected: i == widget.selected,
                    isBest: i == 0,
                    colors: c,
                    onTap: () => widget.onSelect(i),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Row(children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: _closeAnimated,
                      style: OutlinedButton.styleFrom(
                        side: BorderSide(color: c.border),
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12)),
                        padding: const EdgeInsets.symmetric(vertical: 13),
                      ),
                      child: Text(AppLocalizations.of(context).cancel,
                          style: TextStyle(color: c.textSecondary)),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: FilledButton.icon(
                      onPressed: () => widget
                          .onConfirm(widget.alternatives[widget.selected]),
                      style: FilledButton.styleFrom(
                        backgroundColor: c.accent,
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12)),
                        padding: const EdgeInsets.symmetric(vertical: 13),
                      ),
                      icon: Icon(
                          widget.transportMode == 'walking'
                              ? Icons.directions_walk_rounded
                              : Icons.navigation_rounded,
                          color: Colors.white,
                          size: 18),
                      label: Text(AppLocalizations.of(context).startNavigation,
                          style: const TextStyle(color: Colors.white)),
                    ),
                  ),
                ]),
              ),
              SizedBox(
                  height: widget.bottomInset > 0 ? widget.bottomInset : 16),
            ]),
          ),
        ),
      ),
    );
  }
}

class RouteCard extends StatelessWidget {
  final RouteResult route;
  final bool isSelected;
  final bool isBest;
  final RoadstrColors colors;
  final VoidCallback onTap;
  const RouteCard({super.key, required this.route,
      required this.isSelected,
      required this.isBest,
      required this.colors,
      required this.onTap});

  @override
  Widget build(BuildContext context) {
    final isAvoidance = route.isHighwayAndTollAvoidance;
    final avoidanceComplete = route.avoidsHighwaysAndTolls;
    final highlight = isAvoidance
        ? (avoidanceComplete
            ? const Color(0xFF14A67A)
            : const Color(0xFFF59E0B))
        : colors.accent;
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        width: isAvoidance ? 174 : 128,
        margin: const EdgeInsets.only(right: 10),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color:
              isSelected ? highlight.withValues(alpha: 0.12) : colors.surface2,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isSelected ? highlight : colors.border,
            width: isSelected ? 2 : 0.5,
          ),
        ),
        child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(route.durationLabel,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                      color: colors.textPrimary,
                      fontSize: 19,
                      fontWeight: FontWeight.bold)),
              const SizedBox(height: 3),
              Text(route.distanceLabel,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: colors.textSecondary, fontSize: 12)),
              if (isAvoidance || isBest) ...[
                const SizedBox(height: 8),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: highlight.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                      isAvoidance
                          ? (avoidanceComplete
                              ? AppLocalizations.of(context)
                                  .avoidHighwaysAndTolls
                              : AppLocalizations.of(context)
                                  .avoidanceUnavoidableSection)
                          : AppLocalizations.of(context).fastestRoute,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          color: highlight,
                          fontSize: 10,
                          fontWeight: FontWeight.w600)),
                ),
              ],
            ]),
      ),
    );
  }
}

class TimeBubble extends StatelessWidget {
  final String label;
  final bool isSelected;
  final Color accent;
  const TimeBubble({super.key, required this.label, required this.isSelected, required this.accent});

  @override
  Widget build(BuildContext context) {
    return Container(
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: isSelected ? accent : Colors.grey[700],
        borderRadius: BorderRadius.circular(14),
        boxShadow: const [BoxShadow(color: Colors.black26, blurRadius: 4)],
      ),
      child: Text(label,
          style: const TextStyle(
              color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold)),
    );
  }
}

// ── Widgets ───────────────────────────────────────────────────────────────────
