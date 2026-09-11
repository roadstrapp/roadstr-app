// The floating search bar and the two lists that drop out of it: live results
// while typing, and saved places plus recent history when the field is empty.
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../models/favorite_place.dart';
import '../../models/search_history_item.dart';
import '../../services/poi_search_service.dart' show NearbyCategory;
import '../../services/routing_service.dart' show NominatimResult;
import '../../theme/app_theme.dart';
import '../../utils/units.dart';
import '../design/roadstr_glass.dart';

/// The translated name of a nearby category, used both on its button and as
/// the label of results OSM has no name for.
String nearbyCategoryLabel(NearbyCategory category, AppLocalizations l) =>
    switch (category) {
      NearbyCategory.fuel => l.nearbyFuel,
      NearbyCategory.restaurant => l.nearbyRestaurant,
      NearbyCategory.supermarket => l.nearbySupermarket,
      NearbyCategory.atm => l.nearbyAtm,
      NearbyCategory.pharmacy => l.nearbyPharmacy,
      NearbyCategory.hospital => l.nearbyHospital,
      NearbyCategory.police => l.nearbyPolice,
      NearbyCategory.postOffice => l.nearbyPostOffice,
      NearbyCategory.parking => l.nearbyParking,
      NearbyCategory.hotel => l.nearbyHotel,
      NearbyCategory.charging => l.nearbyCharging,
    };

/// One tap per thing a driver stops for, scrolling horizontally above the
/// history. Sits in the search overlay because that is where a user goes when
/// they want to *get somewhere* — the whole point of the feature is that it
/// answers "is there a petrol station around here" without typing anything.
class NearbyBar extends StatelessWidget {
  final RoadstrColors colors;

  /// Null while no fix is available: without a position there is no "nearby",
  /// so the buttons are shown disabled rather than lying about being ready.
  final bool enabled;
  final NearbyCategory? selected;
  final ValueChanged<NearbyCategory> onSelect;
  const NearbyBar({
    super.key,
    required this.colors,
    required this.onSelect,
    this.enabled = true,
    this.selected,
  });

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return RoadstrGlassSurface(
      colors: colors,
      level: RoadstrGlassLevel.medium,
      borderRadius: BorderRadius.circular(RoadstrRadius.large),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: Row(children: [
            Icon(Icons.near_me_rounded, color: colors.accent, size: 14),
            const SizedBox(width: 6),
            Text(enabled ? l.nearbyTitle : l.nearbyNeedsGps,
                style: TextStyle(
                    color: colors.mapTextSecondary,
                    fontSize: 12,
                    fontWeight: FontWeight.w600)),
          ]),
        ),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          child: Row(children: [
            for (final category in NearbyCategory.values) ...[
              _NearbyButton(
                category: category,
                label: nearbyCategoryLabel(category, l),
                colors: colors,
                selected: category == selected,
                onTap: enabled ? () => onSelect(category) : null,
              ),
              const SizedBox(width: 8),
            ],
          ]),
        ),
      ]),
    );
  }
}

class _NearbyButton extends StatelessWidget {
  final NearbyCategory category;
  final String label;
  final RoadstrColors colors;
  final bool selected;
  final VoidCallback? onTap;
  const _NearbyButton({
    required this.category,
    required this.label,
    required this.colors,
    required this.selected,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final disabled = onTap == null;
    return Opacity(
      opacity: disabled ? 0.4 : 1,
      child: RoadstrPressable(
        onTap: onTap,
        semanticLabel: label,
        borderRadius: BorderRadius.circular(RoadstrRadius.small),
        child: AnimatedContainer(
          duration: RoadstrMotion.quick,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
          decoration: BoxDecoration(
            color: selected
                ? colors.accent.withValues(alpha: 0.16)
                : Colors.white.withValues(alpha: 0.055),
            borderRadius: BorderRadius.circular(RoadstrRadius.small),
            border: Border.all(
                color: selected
                    ? colors.accent.withValues(alpha: 0.72)
                    : colors.mapGlassBorder,
                width: selected ? 1.2 : 0.8),
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Text(category.emoji,
                style: const TextStyle(fontSize: 15, height: 1)),
            const SizedBox(width: 6),
            Text(label,
                style: TextStyle(
                    color: selected ? colors.accentGlow : colors.mapTextPrimary,
                    fontSize: 13,
                    fontWeight: FontWeight.w600)),
          ]),
        ),
      ),
    );
  }
}

class SearchHistoryList extends StatelessWidget {
  final List<SearchHistoryItem> history;
  final List<FavoritePlace> favorites;
  final RoadstrColors colors;
  final ValueChanged<SearchHistoryItem> onSelect;
  final ValueChanged<FavoritePlace> onSelectFavorite;
  final VoidCallback onClear;
  const SearchHistoryList(
      {super.key,
      required this.history,
      required this.colors,
      required this.onSelect,
      required this.onSelectFavorite,
      required this.onClear,
      this.favorites = const []});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return RoadstrGlassSurface(
      colors: colors,
      level: RoadstrGlassLevel.strong,
      borderRadius: BorderRadius.circular(RoadstrRadius.large),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        // ── Saved places section ─────────────────────────────────────────
        if (favorites.isNotEmpty) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: Row(children: [
              Icon(Icons.favorite_rounded, color: colors.accent, size: 14),
              const SizedBox(width: 6),
              Text(l.sectionFavorites,
                  style: TextStyle(
                      color: colors.mapTextSecondary,
                      fontSize: 12,
                      fontWeight: FontWeight.w600)),
            ]),
          ),
          Material(
            color: Colors.transparent,
            child: ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              padding: EdgeInsets.zero,
              itemCount: favorites.length,
              separatorBuilder: (_, __) =>
                  Divider(height: 0.5, color: colors.mapGlassBorder),
              itemBuilder: (_, i) {
                final fav = favorites[i];
                return ListTile(
                  tileColor: Colors.transparent,
                  dense: true,
                  leading: Container(
                    width: 30,
                    height: 30,
                    decoration: BoxDecoration(
                        // Solid rather than the translucent accentSoft: over
                        // the panel gradient a wash of accent on accent left
                        // the chips barely visible at the tinted edges.
                        color: colors.accent.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(8)),
                    child: Icon(Icons.favorite_rounded,
                        color: colors.accent, size: 14),
                  ),
                  title: Text(fav.label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          color: colors.mapTextPrimary,
                          fontSize: 14,
                          fontWeight: FontWeight.w500)),
                  subtitle: Text(fav.address,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          color: colors.mapTextSecondary, fontSize: 12)),
                  onTap: () => onSelectFavorite(fav),
                );
              },
            ),
          ),
        ],

        // ── History section ──────────────────────────────────────────────
        if (history.isNotEmpty) ...[
          if (favorites.isNotEmpty)
            Divider(height: 0.5, color: colors.mapGlassBorder),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 8, 4),
            child: Row(children: [
              Icon(Icons.history_rounded,
                  color: colors.mapTextSecondary, size: 16),
              const SizedBox(width: 6),
              Text(l.history,
                  style: TextStyle(
                      color: colors.mapTextSecondary,
                      fontSize: 12,
                      fontWeight: FontWeight.w600)),
              const Spacer(),
              TextButton(
                onPressed: onClear,
                style: TextButton.styleFrom(
                    padding: const EdgeInsets.symmetric(horizontal: 8)),
                child: Text(l.clearHistory,
                    style: TextStyle(color: colors.accent, fontSize: 12)),
              ),
            ]),
          ),
          Material(
            color: Colors.transparent,
            child: ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              padding: EdgeInsets.zero,
              itemCount: history.length,
              separatorBuilder: (_, __) =>
                  Divider(height: 0.5, color: colors.mapGlassBorder),
              itemBuilder: (_, i) {
                final h = history[i];
                // "Via Roberto Ricci 12, Torino" → street on the title line,
                // town on the subtitle line, so entries stay identifiable even
                // when the street name is long.
                final comma = h.label.indexOf(',');
                final title =
                    comma > 0 ? h.label.substring(0, comma).trim() : h.label;
                final subtitle =
                    comma > 0 ? h.label.substring(comma + 1).trim() : '';
                return ListTile(
                  tileColor: Colors.transparent,
                  dense: true,
                  leading: Icon(Icons.location_on_outlined,
                      color: colors.accent, size: 20),
                  title: Text(title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          color: colors.mapTextPrimary, fontSize: 14)),
                  subtitle: subtitle.isEmpty
                      ? null
                      : Text(subtitle,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                              color: colors.mapTextSecondary, fontSize: 12)),
                  onTap: () => onSelect(h),
                );
              },
            ),
          ),
        ],

        const SizedBox(height: 6),
      ]),
    );
  }
}

// ── Preview panel ─────────────────────────────────────────────────────────────

class PlaceSearchBar extends StatelessWidget {
  final TextEditingController controller;
  final RoadstrColors colors;
  final VoidCallback onFocus;
  final ValueChanged<String> onChanged;
  final ValueChanged<String> onSubmitted;
  final VoidCallback onClear;

  const PlaceSearchBar({
    super.key,
    required this.controller,
    required this.colors,
    required this.onFocus,
    required this.onChanged,
    required this.onSubmitted,
    required this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 58,
      child: RoadstrGlassSurface(
        colors: colors,
        level: RoadstrGlassLevel.strong,
        borderRadius: BorderRadius.circular(RoadstrRadius.large),
        child: Row(children: [
          const SizedBox(width: 10),
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              color: colors.accent.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(13),
              border: Border.all(
                color: colors.accent.withValues(alpha: 0.26),
              ),
            ),
            child: Icon(Icons.search_rounded, color: colors.accent, size: 20),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: TextField(
              controller: controller,
              onTap: onFocus,
              onChanged: onChanged,
              onSubmitted: onSubmitted,
              textInputAction: TextInputAction.search,
              style: TextStyle(
                  color: colors.mapTextPrimary,
                  fontSize: 15,
                  fontWeight: FontWeight.w600),
              decoration: InputDecoration(
                hintText: AppLocalizations.of(context).searchHint,
                hintStyle:
                    TextStyle(color: colors.mapTextSecondary, fontSize: 15),
                filled: false,
                contentPadding: EdgeInsets.zero,
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                isDense: true,
              ),
            ),
          ),
          if (controller.text.isNotEmpty)
            IconButton(
                icon:
                    Icon(Icons.close, color: colors.mapTextSecondary, size: 20),
                onPressed: onClear),
        ]),
      ),
    );
  }
}

// ── Purple dropped-pin marker ─────────────────────────────────────────────────

class SearchResultsList extends StatelessWidget {
  final List<NominatimResult> results;
  final List<FavoritePlace> favorites;
  final bool isLoading;
  final RoadstrColors colors;
  final ValueChanged<NominatimResult> onSelect;
  final ValueChanged<FavoritePlace> onSelectFavorite;

  /// Message for a finished search that found nothing. A typed search can stay
  /// silent (the user is still editing), but a nearby category that comes back
  /// empty has to say so — otherwise the tap looks like it did nothing.
  final String? emptyMessage;
  const SearchResultsList(
      {super.key,
      required this.results,
      required this.isLoading,
      required this.colors,
      required this.onSelect,
      required this.onSelectFavorite,
      this.favorites = const [],
      this.emptyMessage});

  @override
  Widget build(BuildContext context) {
    return RoadstrGlassSurface(
      colors: colors,
      level: RoadstrGlassLevel.strong,
      borderRadius: BorderRadius.circular(RoadstrRadius.large),
      child: isLoading && favorites.isEmpty
          ? Padding(
              padding: const EdgeInsets.all(16),
              child: Center(
                  child: SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: colors.accent))))
          : Material(
              color: Colors.transparent,
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                // ── Saved places matching the query ──────────────────────
                if (favorites.isNotEmpty)
                  ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    padding: EdgeInsets.zero,
                    itemCount: favorites.length,
                    separatorBuilder: (_, __) =>
                        Divider(height: 0.5, color: colors.mapGlassBorder),
                    itemBuilder: (_, i) {
                      final fav = favorites[i];
                      return ListTile(
                        tileColor: Colors.transparent,
                        leading: Container(
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(
                              color: colors.accent.withValues(alpha: 0.12),
                              borderRadius: BorderRadius.circular(10)),
                          child: Icon(Icons.favorite_rounded,
                              color: colors.accent, size: 18),
                        ),
                        title: Text(fav.label,
                            style: TextStyle(
                                color: colors.mapTextPrimary,
                                fontSize: 14,
                                fontWeight: FontWeight.w600)),
                        subtitle: Text(fav.address,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                                color: colors.mapTextSecondary, fontSize: 12)),
                        onTap: () => onSelectFavorite(fav),
                      );
                    },
                  ),

                // ── Nominatim results ────────────────────────────────────
                if (isLoading)
                  Padding(
                      padding: const EdgeInsets.all(16),
                      child: Center(
                          child: SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                  strokeWidth: 2, color: colors.accent))))
                else if (results.isNotEmpty) ...[
                  if (favorites.isNotEmpty)
                    Divider(height: 0.5, color: colors.mapGlassBorder),
                  ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    padding: EdgeInsets.zero,
                    itemCount: results.length,
                    separatorBuilder: (_, __) =>
                        Divider(height: 0.5, color: colors.mapGlassBorder),
                    itemBuilder: (_, i) {
                      final r = results[i];
                      final catLabel = r.categoryLabel;
                      final distance = r.distanceM;
                      return ListTile(
                        tileColor: Colors.transparent,
                        leading: Container(
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.07),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: colors.mapGlassBorder),
                          ),
                          child: Center(
                            child: Text(r.emoji,
                                style:
                                    const TextStyle(fontSize: 18, height: 1)),
                          ),
                        ),
                        title: Text(r.shortName,
                            style: TextStyle(
                                color: colors.mapTextPrimary,
                                fontSize: 14,
                                fontWeight: FontWeight.w500)),
                        subtitle: Text(
                            catLabel.isNotEmpty ? catLabel : r.displayName,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                                color: colors.mapTextSecondary, fontSize: 12)),
                        // Straight-line distance, present only on nearby
                        // results: with the list sorted by it, it is the one
                        // number that decides which one the driver picks.
                        trailing: distance == null
                            ? null
                            : Text(Units.fmtDist(distance),
                                style: TextStyle(
                                    color: colors.accent,
                                    fontSize: 12,
                                    fontWeight: FontWeight.w600)),
                        onTap: () => onSelect(r),
                      );
                    },
                  ),
                ] else if (emptyMessage != null && favorites.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 16, vertical: 18),
                    child: Row(children: [
                      Icon(Icons.search_off_rounded,
                          color: colors.mapTextSecondary, size: 18),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(emptyMessage!,
                            style: TextStyle(
                                color: colors.mapTextSecondary, fontSize: 13)),
                      ),
                    ]),
                  ),
              ]),
            ), // Material
    );
  }
}
