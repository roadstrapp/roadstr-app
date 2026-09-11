// Compact pre-trip controls floating above the map. The dashboard starts in
// its map-first state and expands only when the user asks for shortcuts.
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../models/favorite_place.dart';
import '../../theme/app_theme.dart';
import '../design/roadstr_glass.dart';

class HomeDashboard extends StatefulWidget {
  final RoadstrColors colors;
  final double bottomInset;
  final List<FavoritePlace> favorites;
  final VoidCallback onNavigate;
  final VoidCallback onLocate;
  final VoidCallback onParking;
  final VoidCallback onActivity;
  final VoidCallback onEvents;
  final ValueChanged<FavoritePlace> onFavoriteTap;

  const HomeDashboard({
    super.key,
    required this.colors,
    required this.bottomInset,
    required this.favorites,
    required this.onNavigate,
    required this.onLocate,
    required this.onParking,
    required this.onActivity,
    required this.onEvents,
    required this.onFavoriteTap,
  });

  @override
  State<HomeDashboard> createState() => _HomeDashboardState();
}

class _HomeDashboardState extends State<HomeDashboard> {
  // The map is the home screen. Shortcuts are available, but they no longer
  // occupy a third of the viewport every time the app opens.
  bool _expanded = false;

  void _toggle() => setState(() => _expanded = !_expanded);

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final colors = widget.colors;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        RoadstrSpacing.sm,
        0,
        RoadstrSpacing.sm,
        widget.bottomInset > 0 ? RoadstrSpacing.xs : RoadstrSpacing.sm,
      ),
      child: RoadstrGlassSurface(
        colors: colors,
        level: RoadstrGlassLevel.strong,
        borderRadius: BorderRadius.circular(RoadstrRadius.xLarge),
        padding: const EdgeInsets.all(RoadstrSpacing.sm),
        child: AnimatedSize(
          duration: RoadstrMotion.emphasized,
          curve: RoadstrMotion.standardCurve,
          alignment: Alignment.bottomCenter,
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Row(children: [
              RoadstrPressable(
                onTap: _toggle,
                semanticLabel: l.homeReadyToGo,
                borderRadius: BorderRadius.circular(RoadstrRadius.medium),
                child: SizedBox(
                  width: 48,
                  height: 48,
                  child: Stack(clipBehavior: Clip.none, children: [
                    const RoadstrBrandMark(size: 44),
                    Positioned(
                      right: -2,
                      bottom: -2,
                      child: Container(
                        width: 18,
                        height: 18,
                        decoration: BoxDecoration(
                          color: colors.accent,
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: Colors.white.withValues(alpha: 0.28),
                          ),
                        ),
                        child: AnimatedRotation(
                          turns: _expanded ? 0.5 : 0,
                          duration: RoadstrMotion.standard,
                          child: Icon(Icons.keyboard_arrow_up_rounded,
                              size: 14,
                              color: colors.accent == kBitcoinOrange
                                  ? Colors.black
                                  : Colors.white),
                        ),
                      ),
                    ),
                  ]),
                ),
              ),
              const SizedBox(width: RoadstrSpacing.sm),
              Expanded(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l.homeReadyToGo,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: colors.mapTextPrimary,
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.25,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      _expanded ? l.homeSavedPlaces : l.homeNavigate,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: colors.mapTextSecondary,
                        fontSize: 11.5,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: RoadstrSpacing.xs),
              _NavigateButton(
                label: l.homeNavigate,
                colors: colors,
                onTap: widget.onNavigate,
              ),
            ]),
            if (_expanded) ...[
              const SizedBox(height: RoadstrSpacing.sm),
              Container(height: 1, color: colors.mapGlassBorder),
              if (widget.favorites.isNotEmpty) ...[
                const SizedBox(height: RoadstrSpacing.sm),
                Row(children: [
                  Text(
                    l.homeSavedPlaces.toUpperCase(),
                    style: TextStyle(
                      color: colors.mapTextMuted,
                      fontSize: 9.5,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 1.1,
                    ),
                  ),
                ]),
                const SizedBox(height: RoadstrSpacing.xs),
                SizedBox(
                  height: 38,
                  child: ListView.separated(
                    scrollDirection: Axis.horizontal,
                    itemCount: widget.favorites.length.clamp(0, 5),
                    separatorBuilder: (_, __) =>
                        const SizedBox(width: RoadstrSpacing.xs),
                    itemBuilder: (_, index) => _SavedPlaceChip(
                      favorite: widget.favorites[index],
                      colors: colors,
                      onTap: () =>
                          widget.onFavoriteTap(widget.favorites[index]),
                    ),
                  ),
                ),
              ],
              const SizedBox(height: RoadstrSpacing.sm),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _QuickAction(
                    icon: Icons.my_location_rounded,
                    label: l.myLocation,
                    colors: colors,
                    onTap: widget.onLocate,
                  ),
                  _QuickAction(
                    icon: Icons.local_parking_rounded,
                    label: l.homeParking,
                    colors: colors,
                    onTap: widget.onParking,
                  ),
                  _QuickAction(
                    icon: Icons.notifications_none_rounded,
                    label: l.homeActivity,
                    colors: colors,
                    onTap: widget.onActivity,
                  ),
                  _QuickAction(
                    icon: Icons.report_problem_outlined,
                    label: l.homeEvents,
                    colors: colors,
                    warning: true,
                    onTap: widget.onEvents,
                  ),
                ],
              ),
              const SizedBox(height: RoadstrSpacing.xxs),
            ],
          ]),
        ),
      ),
    );
  }
}

class _NavigateButton extends StatelessWidget {
  final String label;
  final RoadstrColors colors;
  final VoidCallback onTap;

  const _NavigateButton({
    required this.label,
    required this.colors,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) => RoadstrPressable(
        onTap: onTap,
        semanticLabel: label,
        borderRadius: BorderRadius.circular(RoadstrRadius.pill),
        child: Container(
          height: 44,
          padding: const EdgeInsets.symmetric(horizontal: RoadstrSpacing.sm),
          decoration: BoxDecoration(
            gradient: colors.accentGloss,
            borderRadius: BorderRadius.circular(RoadstrRadius.pill),
            border: Border.all(color: Colors.white.withValues(alpha: 0.22)),
            boxShadow: [
              BoxShadow(
                color: colors.accentGlow.withValues(alpha: 0.30),
                blurRadius: 18,
                spreadRadius: -5,
              ),
            ],
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            const Icon(Icons.navigation_rounded, color: Colors.white, size: 18),
            const SizedBox(width: 7),
            Text(label,
                style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12.5,
                    fontWeight: FontWeight.w700)),
          ]),
        ),
      );
}

class _SavedPlaceChip extends StatelessWidget {
  final FavoritePlace favorite;
  final RoadstrColors colors;
  final VoidCallback onTap;

  const _SavedPlaceChip({
    required this.favorite,
    required this.colors,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) => RoadstrPressable(
        onTap: onTap,
        semanticLabel: favorite.label,
        borderRadius: BorderRadius.circular(RoadstrRadius.pill),
        child: Container(
          height: 38,
          padding: const EdgeInsets.symmetric(horizontal: RoadstrSpacing.sm),
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.07),
            borderRadius: BorderRadius.circular(RoadstrRadius.pill),
            border: Border.all(color: colors.mapGlassBorder),
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Icon(Icons.bookmark_rounded, color: colors.accent, size: 15),
            const SizedBox(width: 6),
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 112),
              child: Text(
                favorite.label,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: colors.mapTextPrimary,
                  fontSize: 11.5,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ]),
        ),
      );
}

class _QuickAction extends StatelessWidget {
  final IconData icon;
  final String label;
  final RoadstrColors colors;
  final VoidCallback onTap;
  final bool warning;

  const _QuickAction({
    required this.icon,
    required this.label,
    required this.colors,
    required this.onTap,
    this.warning = false,
  });

  @override
  Widget build(BuildContext context) {
    final foreground = warning ? const Color(0xFFFFB44A) : colors.accentGlow;
    return Expanded(
      child: RoadstrPressable(
        onTap: onTap,
        semanticLabel: label,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: RoadstrSpacing.xxs),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: foreground.withValues(alpha: 0.11),
                shape: BoxShape.circle,
                border: Border.all(color: foreground.withValues(alpha: 0.25)),
              ),
              child: Icon(icon, color: foreground, size: 19),
            ),
            const SizedBox(height: 6),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: colors.mapTextSecondary,
                fontSize: 10,
                fontWeight: FontWeight.w600,
              ),
            ),
          ]),
        ),
      ),
    );
  }
}
