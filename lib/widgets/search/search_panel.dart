// The floating search bar and the two lists that drop out of it: live results
// while typing, and saved places plus recent history when the field is empty.
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../models/favorite_place.dart';
import '../../models/search_history_item.dart';
import '../../services/routing_service.dart' show NominatimResult;
import '../../theme/app_theme.dart';

class SearchHistoryList extends StatelessWidget {
  final List<SearchHistoryItem> history;
  final List<FavoritePlace> favorites;
  final RoadstrColors colors;
  final ValueChanged<SearchHistoryItem> onSelect;
  final ValueChanged<FavoritePlace> onSelectFavorite;
  final VoidCallback onClear;
  const SearchHistoryList({super.key, required this.history,
      required this.colors,
      required this.onSelect,
      required this.onSelectFavorite,
      required this.onClear,
      this.favorites = const []});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Container(
      decoration: BoxDecoration(
        color: colors.surface2,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colors.border, width: 0.5),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withValues(alpha: 0.15),
              blurRadius: 12,
              offset: const Offset(0, 4))
        ],
      ),
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
                      color: colors.textSecondary,
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
                  Divider(height: 0.5, color: colors.border),
              itemBuilder: (_, i) {
                final fav = favorites[i];
                return ListTile(
                  tileColor: Colors.transparent,
                  dense: true,
                  leading: Container(
                    width: 30,
                    height: 30,
                    decoration: BoxDecoration(
                        color: colors.accentSoft,
                        borderRadius: BorderRadius.circular(8)),
                    child: Icon(Icons.favorite_rounded,
                        color: colors.accent, size: 14),
                  ),
                  title: Text(fav.label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          color: colors.textPrimary,
                          fontSize: 14,
                          fontWeight: FontWeight.w500)),
                  subtitle: Text(fav.address,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style:
                          TextStyle(color: colors.textSecondary, fontSize: 12)),
                  onTap: () => onSelectFavorite(fav),
                );
              },
            ),
          ),
        ],

        // ── History section ──────────────────────────────────────────────
        if (history.isNotEmpty) ...[
          if (favorites.isNotEmpty) Divider(height: 0.5, color: colors.border),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 8, 4),
            child: Row(children: [
              Icon(Icons.history_rounded,
                  color: colors.textSecondary, size: 16),
              const SizedBox(width: 6),
              Text(l.history,
                  style: TextStyle(
                      color: colors.textSecondary,
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
                  Divider(height: 0.5, color: colors.border),
              itemBuilder: (_, i) {
                final h = history[i];
                // "Via Attilio Monti 12, Ravenna" → street on the title line,
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
                      style:
                          TextStyle(color: colors.textPrimary, fontSize: 14)),
                  subtitle: subtitle.isEmpty
                      ? null
                      : Text(subtitle,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                              color: colors.textSecondary, fontSize: 12)),
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
  const PlaceSearchBar({super.key, 
    required this.controller,
    required this.colors,
    required this.onFocus,
    required this.onChanged,
    required this.onSubmitted,
    required this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 52,
      decoration: BoxDecoration(
        color: colors.surface2,
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: colors.border, width: 0.5),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withValues(alpha: 0.18),
              blurRadius: 12,
              offset: const Offset(0, 3))
        ],
      ),
      child: Row(children: [
        const SizedBox(width: 18),
        Icon(Icons.search, color: colors.textSecondary, size: 20),
        const SizedBox(width: 12),
        Expanded(
          child: TextField(
            controller: controller,
            onTap: onFocus,
            onChanged: onChanged,
            onSubmitted: onSubmitted,
            textInputAction: TextInputAction.search,
            style: TextStyle(color: colors.textPrimary, fontSize: 16),
            decoration: InputDecoration(
              hintText: AppLocalizations.of(context).searchHint,
              hintStyle: TextStyle(color: colors.textSecondary, fontSize: 16),
              border: InputBorder.none,
              isDense: true,
            ),
          ),
        ),
        if (controller.text.isNotEmpty)
          IconButton(
              icon: Icon(Icons.close, color: colors.textSecondary, size: 20),
              onPressed: onClear),
      ]),
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
  const SearchResultsList({super.key, required this.results,
      required this.isLoading,
      required this.colors,
      required this.onSelect,
      required this.onSelectFavorite,
      this.favorites = const []});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: colors.surface2,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: colors.border, width: 0.5),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withValues(alpha: 0.15),
              blurRadius: 12,
              offset: const Offset(0, 4))
        ],
      ),
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
                        Divider(height: 0.5, color: colors.border),
                    itemBuilder: (_, i) {
                      final fav = favorites[i];
                      return ListTile(
                        tileColor: Colors.transparent,
                        leading: Container(
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(
                              color: colors.accentSoft,
                              borderRadius: BorderRadius.circular(10)),
                          child: Icon(Icons.favorite_rounded,
                              color: colors.accent, size: 18),
                        ),
                        title: Text(fav.label,
                            style: TextStyle(
                                color: colors.textPrimary,
                                fontSize: 14,
                                fontWeight: FontWeight.w600)),
                        subtitle: Text(fav.address,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                                color: colors.textSecondary, fontSize: 12)),
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
                    Divider(height: 0.5, color: colors.border),
                  ListView.separated(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    padding: EdgeInsets.zero,
                    itemCount: results.length,
                    separatorBuilder: (_, __) =>
                        Divider(height: 0.5, color: colors.border),
                    itemBuilder: (_, i) {
                      final r = results[i];
                      final catLabel = r.categoryLabel;
                      return ListTile(
                        tileColor: Colors.transparent,
                        leading: Container(
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(
                            color: colors.accentSoft,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Center(
                            child: Text(r.emoji,
                                style:
                                    const TextStyle(fontSize: 18, height: 1)),
                          ),
                        ),
                        title: Text(r.shortName,
                            style: TextStyle(
                                color: colors.textPrimary,
                                fontSize: 14,
                                fontWeight: FontWeight.w500)),
                        subtitle: Text(
                            catLabel.isNotEmpty ? catLabel : r.displayName,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                                color: colors.textSecondary, fontSize: 12)),
                        onTap: () => onSelect(r),
                      );
                    },
                  ),
                ],
              ]),
            ), // Material
    );
  }
}
