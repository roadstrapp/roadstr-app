// Quick actions shown over the map while Roadstr is idle.
//
// This deliberately reuses the map rather than introducing a separate home
// screen: a saved place or an action should be one tap away, while the map
// remains the visual anchor of the product.
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../models/favorite_place.dart';
import '../../theme/app_theme.dart';

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
  bool _collapsed = false;

  String _greeting(AppLocalizations l) {
    final hour = DateTime.now().hour;
    if (hour < 12) return l.homeGoodMorning;
    if (hour < 18) return l.homeGoodAfternoon;
    return l.homeGoodEvening;
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final compact = MediaQuery.sizeOf(context).height < 700;
    if (_collapsed) {
      return Padding(
        padding:
            EdgeInsets.fromLTRB(12, 0, 12, widget.bottomInset > 0 ? 8 : 12),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: () => setState(() => _collapsed = false),
            borderRadius: BorderRadius.circular(22),
            child: Ink(
              height: 56,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              decoration: BoxDecoration(
                gradient:
                    widget.colors.panelGradient ?? widget.colors.surfaceSheen,
                borderRadius: BorderRadius.circular(22),
                border: Border.all(color: widget.colors.panelEdge),
                boxShadow: widget.colors.panelShadow,
              ),
              child: Row(children: [
                Icon(Icons.keyboard_arrow_up_rounded,
                    color: widget.colors.accent, size: 22),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(l.homeReadyToGo,
                      style: TextStyle(
                          color: widget.colors.textPrimary,
                          fontSize: 14,
                          fontWeight: FontWeight.w700)),
                ),
                Icon(Icons.auto_awesome_rounded,
                    color: widget.colors.accent, size: 18),
              ]),
            ),
          ),
        ),
      );
    }
    return Padding(
      padding: EdgeInsets.fromLTRB(12, 0, 12, widget.bottomInset > 0 ? 8 : 12),
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: widget.colors.panelGradient ?? widget.colors.surfaceSheen,
          borderRadius: BorderRadius.circular(28),
          border: Border.all(color: widget.colors.panelEdge),
          boxShadow: widget.colors.panelShadow,
        ),
        child: Padding(
          padding: EdgeInsets.fromLTRB(18, compact ? 12 : 16, 18, 14),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            GestureDetector(
              onTap: () => setState(() => _collapsed = true),
              onVerticalDragEnd: (details) {
                if ((details.primaryVelocity ?? 0) > 80) {
                  setState(() => _collapsed = true);
                }
              },
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 7),
                child: Container(
                  width: 38,
                  height: 4,
                  decoration: BoxDecoration(
                    color: widget.colors.textSecondary.withValues(alpha: 0.42),
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
              ),
            ),
            SizedBox(height: compact ? 8 : 12),
            Row(children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(_greeting(l),
                        style: TextStyle(
                          color: widget.colors.textPrimary,
                          fontSize: compact ? 21 : 24,
                          fontWeight: FontWeight.w800,
                          letterSpacing: -0.5,
                        )),
                    const SizedBox(height: 2),
                    Text(l.homeReadyToGo,
                        style: TextStyle(
                            color: widget.colors.textSecondary, fontSize: 14)),
                  ],
                ),
              ),
              Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: widget.onLocate,
                  borderRadius: BorderRadius.circular(14),
                  child: Ink(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                        color: widget.colors.accentSoft,
                        borderRadius: BorderRadius.circular(14)),
                    child: Icon(Icons.my_location_rounded,
                        color: widget.colors.accent, size: 21),
                  ),
                ),
              ),
            ]),
            if (widget.favorites.isNotEmpty) ...[
              SizedBox(height: compact ? 8 : 12),
              Align(
                alignment: Alignment.centerLeft,
                child: Text(l.homeSavedPlaces,
                    style: TextStyle(
                        color: widget.colors.textSecondary,
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        letterSpacing: 0.4)),
              ),
              const SizedBox(height: 7),
              SizedBox(
                height: 38,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  itemCount: widget.favorites.length.clamp(0, 5),
                  separatorBuilder: (_, __) => const SizedBox(width: 8),
                  itemBuilder: (_, index) => _SavedPlaceChip(
                    favorite: widget.favorites[index],
                    colors: widget.colors,
                    onTap: () => widget.onFavoriteTap(widget.favorites[index]),
                  ),
                ),
              ),
            ],
            SizedBox(height: compact ? 10 : 14),
            Row(children: [
              Expanded(
                  child: _HomeAction(
                      icon: Icons.navigation_rounded,
                      label: l.homeNavigate,
                      colors: widget.colors,
                      highlighted: true,
                      onTap: widget.onNavigate)),
              const SizedBox(width: 10),
              Expanded(
                  child: _HomeAction(
                      icon: Icons.local_parking_rounded,
                      label: l.homeParking,
                      colors: widget.colors,
                      onTap: widget.onParking)),
            ]),
            const SizedBox(height: 10),
            Row(children: [
              Expanded(
                  child: _HomeAction(
                      icon: Icons.notifications_none_rounded,
                      label: l.homeActivity,
                      colors: widget.colors,
                      onTap: widget.onActivity)),
              const SizedBox(width: 10),
              Expanded(
                  child: _HomeAction(
                      icon: Icons.report_problem_outlined,
                      label: l.homeEvents,
                      colors: widget.colors,
                      warning: true,
                      onTap: widget.onEvents)),
            ]),
          ]),
        ),
      ),
    );
  }
}

class _SavedPlaceChip extends StatelessWidget {
  final FavoritePlace favorite;
  final RoadstrColors colors;
  final VoidCallback onTap;
  const _SavedPlaceChip(
      {required this.favorite, required this.colors, required this.onTap});

  @override
  Widget build(BuildContext context) => Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Ink(
            padding: const EdgeInsets.symmetric(horizontal: 10),
            decoration: BoxDecoration(
              color: colors.surface3,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: colors.border),
            ),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              Icon(Icons.bookmark_rounded, color: colors.accent, size: 15),
              const SizedBox(width: 6),
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 108),
                child: Text(favorite.label,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        color: colors.textPrimary,
                        fontSize: 12,
                        fontWeight: FontWeight.w600)),
              ),
            ]),
          ),
        ),
      );
}

class _HomeAction extends StatelessWidget {
  final IconData icon;
  final String label;
  final RoadstrColors colors;
  final bool highlighted;
  final bool warning;
  final VoidCallback onTap;
  const _HomeAction({
    required this.icon,
    required this.label,
    required this.colors,
    required this.onTap,
    this.highlighted = false,
    this.warning = false,
  });

  @override
  Widget build(BuildContext context) {
    final foreground = warning ? const Color(0xFFF59E0B) : colors.accent;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(18),
        child: Ink(
          height: 58,
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(
            gradient: highlighted ? colors.accentGloss : null,
            color: highlighted ? null : colors.surface3,
            borderRadius: BorderRadius.circular(18),
            border: Border.all(
                color: highlighted
                    ? colors.accent
                    : foreground.withValues(alpha: 0.4)),
          ),
          child: Row(children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(
                color: highlighted
                    ? Colors.white.withValues(alpha: 0.16)
                    : foreground.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(11),
              ),
              child: Icon(icon,
                  color: highlighted ? colors.onAccent : foreground, size: 19),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Text(label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: highlighted ? colors.onAccent : colors.textPrimary,
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                  )),
            ),
          ]),
        ),
      ),
    );
  }
}
