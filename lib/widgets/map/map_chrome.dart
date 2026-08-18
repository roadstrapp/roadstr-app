// Persistent map chrome: the bottom navigation bar and the floating action
// buttons that sit on top of the map.
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:latlong2/latlong.dart';

import '../../l10n/app_localizations.dart';
import '../../screens/notifications_screen.dart';
import '../../screens/profile_screen.dart';
import '../../screens/settings_screen.dart';
import '../../utils/settings_listenable.dart';
import '../../services/activity_notification_service.dart';
import '../../services/ztl_service.dart';
import '../../theme/app_theme.dart';

class MapBottomBar extends StatelessWidget {
  final double bottomInset;
  final RoadstrColors colors;
  final String? pubkey;
  final String? profilePicture;
  final bool hasNostrLogin;
  final VoidCallback onProfileReturn;
  const MapBottomBar({super.key, required this.bottomInset,
      required this.colors,
      required this.pubkey,
      required this.profilePicture,
      required this.hasNostrLogin,
      required this.onProfileReturn});
  @override
  Widget build(BuildContext context) => Container(
        decoration: BoxDecoration(
          // Same framing as the driving panels, so the home screen and
          // navigation read as one app rather than two.
          gradient: colors.panelGradient,
          color: colors.panelGradient == null ? colors.surface2 : null,
          border: Border(top: BorderSide(color: colors.border, width: 0.5)),
          boxShadow: [
            BoxShadow(
                color: Colors.black.withValues(alpha: 0.12),
                blurRadius: 12,
                offset: const Offset(0, -2))
          ],
        ),
        padding: EdgeInsets.only(
            top: 10, bottom: bottomInset > 0 ? bottomInset : 14),
        child: Row(children: [
          Expanded(
            child: ValueListenableBuilder<Box>(
              valueListenable: SettingsListenable.forKeys(
                pubkey == null
                    ? const []
                    : [ActivityNotificationService.storageKey(pubkey!)],
              ),
              builder: (_, __, ___) {
                final unread = pubkey == null
                    ? 0
                    : ActivityNotificationService().unreadCount(pubkey!);
                return MapBottomBarItem(
                  icon: Stack(clipBehavior: Clip.none, children: [
                    Icon(Icons.notifications_none_rounded,
                        color: colors.textSecondary, size: 26),
                    if (unread > 0)
                      Positioned(
                        right: -7,
                        top: -5,
                        child: Container(
                          constraints:
                              const BoxConstraints(minWidth: 16, minHeight: 16),
                          padding: const EdgeInsets.symmetric(horizontal: 4),
                          decoration: BoxDecoration(
                            color: const Color(0xFFEF4444),
                            borderRadius: BorderRadius.circular(9),
                            border: Border.all(color: colors.surface2),
                          ),
                          alignment: Alignment.center,
                          child: Text(unread > 99 ? '99+' : '$unread',
                              style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 8,
                                  fontWeight: FontWeight.bold)),
                        ),
                      ),
                  ]),
                  label: AppLocalizations.of(context).bottomBarNotifications,
                  colors: colors,
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                        builder: (_) => NotificationsScreen(pubkey: pubkey)),
                  ),
                );
              },
            ),
          ),
          Expanded(
            child: MapBottomBarItem(
              icon: hasNostrLogin &&
                      profilePicture != null &&
                      profilePicture!.isNotEmpty
                  ? Container(
                      width: 26,
                      height: 26,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        border: Border.all(color: colors.accent, width: 1.5),
                      ),
                      child: ClipOval(
                        child: Image.network(
                          profilePicture!,
                          fit: BoxFit.cover,
                          errorBuilder: (_, __, ___) => Icon(
                              Icons.account_circle_outlined,
                              color: colors.textSecondary,
                              size: 25),
                        ),
                      ),
                    )
                  : Icon(Icons.account_circle_outlined,
                      color: colors.textSecondary, size: 26),
              label: AppLocalizations.of(context).bottomBarProfile,
              colors: colors,
              onTap: () async {
                await Navigator.push(context,
                    MaterialPageRoute(builder: (_) => const ProfileScreen()));
                // Login/logout happens on this screen — re-sync activity
                // notifications rather than requiring an app restart.
                onProfileReturn();
              },
            ),
          ),
          Expanded(
            child: MapBottomBarItem(
              icon: Icon(Icons.menu, color: colors.textSecondary, size: 26),
              label: AppLocalizations.of(context).bottomBarMenu,
              colors: colors,
              onTap: () => Navigator.push(context,
                  MaterialPageRoute(builder: (_) => const SettingsScreen())),
            ),
          ),
        ]),
      );
}

class MapBottomBarItem extends StatelessWidget {
  final Widget icon;
  final String label;
  final RoadstrColors colors;
  final VoidCallback onTap;
  const MapBottomBarItem({super.key, required this.icon,
      required this.label,
      required this.colors,
      required this.onTap});
  @override
  Widget build(BuildContext context) => InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            SizedBox(width: 30, height: 26, child: Center(child: icon)),
            const SizedBox(height: 2),
            Text(label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.center,
                style: TextStyle(color: colors.textSecondary, fontSize: 11)),
          ]),
        ),
      );
}

class MapFab extends StatelessWidget {
  final Widget child;
  final VoidCallback onTap;
  final RoadstrColors colors;
  const MapFab({super.key, required this.child, required this.onTap, required this.colors});
  @override
  Widget build(BuildContext context) => Material(
        // Same lit-object treatment as the manoeuvre tile, so the map controls
        // belong to it rather than looking like leftover system chrome.
        color: Colors.transparent,
        shape: const CircleBorder(),
        // Elevation off: the soft wide shadow below does the lifting, and
        // Material's own tight shadow underneath it just muddied the edge.
        elevation: 0,
        child: Ink(
          decoration: BoxDecoration(
            gradient: colors.accentGloss,
            shape: BoxShape.circle,
            // A light rim on a lit sphere. Brightest where the gloss already
            // is, so the highlight and the rim agree on where the light is.
            border: Border.all(
                color: Colors.white.withValues(alpha: 0.35), width: 1),
            boxShadow: colors.panelShadow,
          ),
          child: InkWell(
              onTap: onTap,
              customBorder: const CircleBorder(),
              // 48 px: the minimum comfortable touch target, and these are
              // pressed one-handed while driving.
              child:
                  SizedBox(width: 48, height: 48, child: Center(child: child))),
        ),
      );
}

class CompassFab extends StatelessWidget {
  final double rotDeg;
  final bool active;
  final VoidCallback onTap;
  const CompassFab({super.key, required this.rotDeg, required this.active, required this.onTap});

  static const _purple = Color(0xFF7C3AED);

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 44,
        height: 44,
        decoration: BoxDecoration(
          color: Colors.white,
          shape: BoxShape.circle,
          border: active ? Border.all(color: _purple, width: 2) : null,
          boxShadow: [
            BoxShadow(
                color: Colors.black.withValues(alpha: 0.22),
                blurRadius: 8,
                offset: const Offset(0, 2)),
            if (active)
              BoxShadow(
                  color: _purple.withValues(alpha: 0.3),
                  blurRadius: 10,
                  spreadRadius: 1),
          ],
        ),
        child: Transform.rotate(
          angle: -rotDeg * math.pi / 180,
          child: Stack(
            alignment: Alignment.center,
            children: [
              Icon(Icons.navigation_rounded,
                  color: active ? _purple : const Color(0xFF9CA3AF), size: 26),
              // Red "N" at the arrow tip (top of the icon)
              Positioned(
                top: 5,
                child: Text('N',
                    style: const TextStyle(
                        color: Colors.red,
                        fontSize: 8,
                        fontWeight: FontWeight.w900,
                        height: 1.0)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// "You are inside a restricted traffic zone" banner.
///
/// The zone data has been refreshed on every GPS tick since 0.4.6, but the
/// banner that displayed it was dropped during the map-screen split, leaving
/// the driver with no indication at all — the one thing the ZTL work was for.
class ZtlBanner extends StatelessWidget {
  /// Name of the zone from OSM, when it has one.
  final String? name;

  /// Current position — decides which official acronym to use as a fallback
  /// label (ZTL in Italy/France, ZAC in Portugal, generic wording elsewhere).
  final LatLng pos;
  const ZtlBanner({super.key, this.name, required this.pos});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final label = (name != null && name!.isNotEmpty)
        ? name!
        : (ZtlService.officialAcronymFor(pos) ?? l.ztlInsideWarning);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.red.shade700,
        borderRadius: BorderRadius.circular(10),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withValues(alpha: 0.25),
              blurRadius: 8,
              offset: const Offset(0, 2))
        ],
      ),
      child: Row(children: [
        const Icon(Icons.no_crash_rounded, color: Colors.white, size: 22),
        const SizedBox(width: 10),
        Expanded(
          child: Text('⚠ $label — ${l.ztlInsideWarning}',
              style: const TextStyle(
                  color: Colors.white,
                  fontSize: 14,
                  fontWeight: FontWeight.w600)),
        ),
      ]),
    );
  }
}

/// Advisory shown while driving *past* a restricted street.
///
/// Yellow with black text, deliberately unlike the red "you are inside a
/// restricted zone" banner: nothing is wrong here. The point is that a street
/// which looks open — and may well look like a shortcut — is not, so a driver
/// choosing to turn does so knowing.
class ZtlNearbyNotice extends StatelessWidget {
  /// Street name from OSM, when it has one.
  final String? name;

  /// Decides the official acronym when the street has no name.
  final LatLng pos;

  const ZtlNearbyNotice({super.key, required this.pos, this.name});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final label = (name != null && name!.isNotEmpty)
        ? name!
        : (ZtlService.officialAcronymFor(pos) ?? l.ztlInsideWarning);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFFFD54F),
        borderRadius: BorderRadius.circular(10),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withValues(alpha: 0.22),
              blurRadius: 8,
              offset: const Offset(0, 2))
        ],
      ),
      child: Row(children: [
        const Icon(Icons.warning_amber_rounded, color: Colors.black, size: 22),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            l.ztlNearRouteWarning(label),
            style: const TextStyle(
                color: Colors.black,
                fontSize: 14,
                fontWeight: FontWeight.w600),
          ),
        ),
      ]),
    );
  }
}
