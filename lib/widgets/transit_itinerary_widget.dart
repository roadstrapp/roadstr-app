import 'package:flutter/material.dart';

import '../l10n/app_localizations.dart';
import '../models/transit_itinerary.dart';
import '../theme/app_theme.dart';
import 'route/route_panels.dart';
import '../utils/units.dart';

/// One tappable itinerary card: total time, the chain of lines to take, and
/// how much of it is on foot.
class TransitItineraryCard extends StatelessWidget {
  final TransitItinerary itinerary;
  final RoadstrColors colors;
  final bool selected;
  final VoidCallback? onTap;

  const TransitItineraryCard({
    super.key,
    required this.itinerary,
    required this.colors,
    this.selected = false,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: selected ? colors.accentSoft : colors.surface2,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
              color: selected ? colors.accent : colors.border,
              width: selected ? 1.5 : 1),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _header(l),
            _boarding(),
            const SizedBox(height: 8),
            _LegChain(itinerary: itinerary, colors: colors),
            const SizedBox(height: 6),
            _footer(l),
          ],
        ),
      ),
    );
  }

  Widget _header(AppLocalizations l) {
    final transfers = itinerary.transfers;
    return Row(children: [
      Text(
        _formatDuration(itinerary.duration),
        style: TextStyle(
            color: colors.textPrimary,
            fontSize: 17,
            fontWeight: FontWeight.w700),
      ),
      const SizedBox(width: 8),
      Text(
        '${_formatClock(itinerary.startTime)} – '
        '${_formatClock(itinerary.endTime)}',
        style: TextStyle(color: colors.textSecondary, fontSize: 13),
      ),
      const Spacer(),
      Text(
        transfers == 0 ? l.transitDirect : l.transitTransfers(transfers),
        style: TextStyle(color: colors.textSecondary, fontSize: 12),
      ),
    ]);
  }

  /// Where to walk, and by when. Without this the card says a journey exists
  /// but not how to catch it — and the walk to the station is often the part
  /// that decides whether it can be caught at all.
  Widget _boarding() {
    final board = itinerary.boarding;
    if (board == null) return const SizedBox.shrink();
    final walk = itinerary.accessWalk;
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Row(children: [
        Icon(Icons.directions_walk_rounded, size: 13, color: colors.accent),
        const SizedBox(width: 4),
        if (walk.inMinutes > 0)
          Text('${walk.inMinutes}\u2009min  ',
              style: TextStyle(color: colors.accent, fontSize: 12)),
        Icon(Icons.arrow_right_alt_rounded, size: 14, color: colors.accent),
        const SizedBox(width: 4),
        Expanded(
          child: Text(
            board.name,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
                color: colors.textPrimary,
                fontSize: 12,
                fontWeight: FontWeight.w600),
          ),
        ),
        const SizedBox(width: 6),
        Text(_formatClock(board.time),
            style: TextStyle(
                color: colors.textPrimary,
                fontSize: 12,
                fontWeight: FontWeight.w700)),
      ]),
    );
  }

  Widget _footer(AppLocalizations l) {
    final walking = itinerary.walkingDistanceMeters;
    return Row(children: [
      Icon(Icons.directions_walk_rounded, size: 14, color: colors.textSecondary),
      const SizedBox(width: 4),
      Text(
        Units.fmtDist(walking),
        style: TextStyle(color: colors.textSecondary, fontSize: 12),
      ),
      const Spacer(),
      // Only claimed when every scheduled leg carries live data — a partly
      // live journey is still a timetable estimate overall, and saying
      // otherwise would make a missed connection look like the app's promise.
      if (!itinerary.isFullyRealTime)
        Flexible(
          child: Text(
            l.transitScheduledTimes,
            textAlign: TextAlign.end,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(color: colors.textSecondary, fontSize: 11),
          ),
        ),
    ]);
  }
}

/// The horizontal run of mode badges: walk → S3 → walk.
class _LegChain extends StatelessWidget {
  final TransitItinerary itinerary;
  final RoadstrColors colors;

  const _LegChain({required this.itinerary, required this.colors});

  @override
  Widget build(BuildContext context) {
    final chips = <Widget>[];
    for (final leg in itinerary.legs) {
      if (chips.isNotEmpty) {
        chips.add(Padding(
          padding: const EdgeInsets.symmetric(horizontal: 3),
          child: Icon(Icons.chevron_right_rounded,
              size: 14, color: colors.textSecondary),
        ));
      }
      chips.add(_LegBadge(leg: leg, colors: colors));
    }
    // Scrolls rather than wraps: a journey with several changes must not push
    // the card's height around as the list is browsed.
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(children: chips),
    );
  }
}

class _LegBadge extends StatelessWidget {
  final TransitLeg leg;
  final RoadstrColors colors;

  const _LegBadge({required this.leg, required this.colors});

  @override
  Widget build(BuildContext context) {
    final line = leg.displayLine;
    // A walking leg has no line to name, so it shows its duration instead —
    // which is the thing a traveller actually weighs when comparing options.
    if (!leg.mode.isTransit || line == null) {
      return Row(children: [
        Icon(leg.mode.icon, size: 15, color: colors.textSecondary),
        const SizedBox(width: 3),
        Text('${leg.duration.inMinutes}',
            style: TextStyle(color: colors.textSecondary, fontSize: 12)),
      ]);
    }

    // Operator colours come from the feed. Fall back to the app's accent so a
    // feed that publishes none still looks deliberate.
    final background = leg.routeColor ?? colors.accent;
    final foreground = leg.routeTextColor ??
        (background.computeLuminance() > 0.5 ? Colors.black : Colors.white);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Row(children: [
        Icon(leg.mode.icon, size: 13, color: foreground),
        const SizedBox(width: 4),
        Text(
          line,
          style: TextStyle(
              color: foreground, fontSize: 12, fontWeight: FontWeight.w600),
        ),
      ]),
    );
  }
}

/// Bottom sheet listing the public-transport journeys found for a
/// destination.
///
/// There is no "start navigation" here on purpose: turn-by-turn belongs to a
/// vehicle whose position the app controls. A traveller waiting for the 08:16
/// needs the departure time, the line and the platform name, not a voice
/// telling them to turn left.
class TransitItinerariesPanel extends StatelessWidget {
  final List<TransitItinerary> itineraries;
  final int selected;
  final RoadstrColors colors;
  final double bottomInset;
  final String? label;
  final bool failed;
  final ValueChanged<int> onSelect;
  final VoidCallback onCancel;
  final VoidCallback? onRetry;

  /// Current mode and the switch callback. Without these the panel is a dead
  /// end: every other result panel offers the transport modes, so a traveller
  /// who lands here and decides to drive after all has no way back and the app
  /// stays stuck on public transport for the next journey too.
  final String transportMode;
  final ValueChanged<String> onModeChanged;

  const TransitItinerariesPanel({
    super.key,
    required this.itineraries,
    required this.selected,
    required this.colors,
    required this.bottomInset,
    required this.onSelect,
    required this.onCancel,
    required this.transportMode,
    required this.onModeChanged,
    this.label,
    this.failed = false,
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Container(
      padding: EdgeInsets.fromLTRB(12, 12, 12, 12 + bottomInset),
      decoration: BoxDecoration(
        color: colors.surface1,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(18)),
        border: Border(top: BorderSide(color: colors.border)),
      ),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Row(children: [
          Icon(Icons.directions_transit_rounded, size: 18, color: colors.accent),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              label ?? l.transportModeTransit,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                  color: colors.textPrimary,
                  fontSize: 15,
                  fontWeight: FontWeight.w600),
            ),
          ),
          IconButton(
            onPressed: onCancel,
            icon: Icon(Icons.close_rounded, size: 20, color: colors.textSecondary),
          ),
        ]),
        if (itineraries.isEmpty)
          TransitEmptyState(
            colors: colors,
            failed: failed,
            onRetry: failed ? onRetry : null,
          )
        else
          ConstrainedBox(
            constraints: const BoxConstraints(maxHeight: 300),
            child: ListView.separated(
              shrinkWrap: true,
              itemCount: itineraries.length,
              separatorBuilder: (_, __) => const SizedBox(height: 8),
              itemBuilder: (context, i) => TransitItineraryCard(
                itinerary: itineraries[i],
                colors: colors,
                selected: i == selected,
                onTap: () => onSelect(i),
              ),
            ),
          ),
        const SizedBox(height: 10),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            TransportModeChip(
                icon: Icons.directions_car_rounded,
                label: l.modeCar,
                selected: transportMode == 'driving',
                colors: colors,
                onTap: () => onModeChanged('driving')),
            const SizedBox(width: 6),
            TransportModeChip(
                icon: Icons.directions_bike_rounded,
                label: l.modeBike,
                selected: transportMode == 'cycling',
                colors: colors,
                onTap: () => onModeChanged('cycling')),
            const SizedBox(width: 6),
            TransportModeChip(
                icon: Icons.directions_walk_rounded,
                label: l.modeWalk,
                selected: transportMode == 'walking' ||
                    transportMode == 'transit',
                colors: colors,
                onTap: () => onModeChanged('walking')),
          ]),
        ),
        WalkingSubModes(
          transportMode: transportMode,
          colors: colors,
          onModeChanged: onModeChanged,
        ),
      ]),
    );
  }
}

/// Panel shown when the router has no service for the area, or the request
/// failed. Kept distinct because the two need different words and only one of
/// them is worth a retry button.
class TransitEmptyState extends StatelessWidget {
  final RoadstrColors colors;

  /// null when the area simply has no data.
  final VoidCallback? onRetry;
  final bool failed;

  const TransitEmptyState({
    super.key,
    required this.colors,
    required this.failed,
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 18, horizontal: 12),
      child: Column(children: [
        Icon(failed ? Icons.cloud_off_rounded : Icons.map_outlined,
            size: 28, color: colors.textSecondary),
        const SizedBox(height: 8),
        Text(
          failed ? l.transitRequestFailed : l.transitNoServiceHere,
          textAlign: TextAlign.center,
          style: TextStyle(color: colors.textSecondary, fontSize: 13),
        ),
        if (onRetry != null) ...[
          const SizedBox(height: 10),
          TextButton(onPressed: onRetry, child: Text(l.transitRetry)),
        ],
      ]),
    );
  }
}

String _formatDuration(Duration d) {
  final hours = d.inHours;
  final minutes = d.inMinutes.remainder(60);
  return hours > 0 ? '${hours}h ${minutes}m' : '${minutes}m';
}

String _formatClock(DateTime t) =>
    '${t.hour.toString().padLeft(2, '0')}:'
    '${t.minute.toString().padLeft(2, '0')}';
