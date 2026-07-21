import 'package:flutter/material.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:intl/intl.dart';

import '../l10n/app_localizations.dart';
import '../models/activity_notification.dart';
import '../services/activity_notification_service.dart';
import '../theme/app_theme.dart';

/// Silent inbox for social activity received through Nostr.
///
/// This route is reachable only from the Home bottom bar, which is not rendered
/// during active navigation. Receiving an item never opens this screen and never
/// produces an Android notification, banner or sound.
class NotificationsScreen extends StatefulWidget {
  final String? pubkey;

  const NotificationsScreen({super.key, required this.pubkey});

  @override
  State<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends State<NotificationsScreen> {
  final _service = ActivityNotificationService();

  @override
  void initState() {
    super.initState();
    final pubkey = widget.pubkey;
    if (pubkey != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        _service.markAllRead(pubkey);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = RoadstrColors.of(context);
    final l = AppLocalizations.of(context);
    final pubkey = widget.pubkey;
    return Scaffold(
      appBar: AppBar(
        title: Text(l.notificationsTitle),
        centerTitle: true,
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(0.5),
          child: Divider(height: 0.5, color: c.border),
        ),
      ),
      body: pubkey == null
          ? _EmptyInbox(
              icon: Icons.person_outline_rounded,
              title: l.notificationsLoginRequired,
              body: l.notificationsLoginRequiredBody,
              colors: c,
            )
          : ValueListenableBuilder<Box>(
              valueListenable: Hive.box('settings').listenable(
                  keys: [ActivityNotificationService.storageKey(pubkey)]),
              builder: (_, __, ___) {
                final items = _service.notificationsFor(pubkey);
                if (items.isEmpty) {
                  return _EmptyInbox(
                    icon: Icons.notifications_none_rounded,
                    title: l.notificationsEmpty,
                    body: l.notificationsEmptyBody,
                    colors: c,
                  );
                }
                return ListView.separated(
                  padding: EdgeInsets.fromLTRB(16, 16, 16,
                      20 + MediaQuery.of(context).viewPadding.bottom),
                  itemCount: items.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (_, index) =>
                      _NotificationCard(item: items[index], colors: c),
                );
              },
            ),
    );
  }
}

class _EmptyInbox extends StatelessWidget {
  final IconData icon;
  final String title;
  final String body;
  final RoadstrColors colors;

  const _EmptyInbox({
    required this.icon,
    required this.title,
    required this.body,
    required this.colors,
  });

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(icon, size: 52, color: colors.textSecondary),
            const SizedBox(height: 16),
            Text(title,
                textAlign: TextAlign.center,
                style: TextStyle(
                    color: colors.textPrimary,
                    fontSize: 18,
                    fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            Text(body,
                textAlign: TextAlign.center,
                style: TextStyle(
                    color: colors.textSecondary, fontSize: 13, height: 1.4)),
          ]),
        ),
      );
}

class _NotificationCard extends StatelessWidget {
  final ActivityNotification item;
  final RoadstrColors colors;

  const _NotificationCard({required this.item, required this.colors});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final (icon, color, title, body) = switch (item.type) {
      ActivityNotificationType.zap => (
          Icons.bolt_rounded,
          const Color(0xFFF59E0B),
          l.notifZapTitle,
          l.notifZapBody(item.amountSat ?? 0),
        ),
      ActivityNotificationType.confirmed => (
          Icons.check_circle_outline_rounded,
          const Color(0xFF22C55E),
          l.notifConfirmedTitle,
          l.notifConfirmedBody(item.category!.localizedLabel(l)),
        ),
      ActivityNotificationType.denied => (
          Icons.cancel_outlined,
          const Color(0xFFEF4444),
          l.notifDeniedTitle,
          l.notifDeniedBody(item.category!.localizedLabel(l)),
        ),
    };
    final date = DateTime.fromMillisecondsSinceEpoch(item.createdAt * 1000);
    final locale = Localizations.localeOf(context).toLanguageTag();
    String time;
    try {
      time = DateFormat.yMMMd(locale).add_Hm().format(date);
    } catch (_) {
      time = '${date.year.toString().padLeft(4, '0')}-'
          '${date.month.toString().padLeft(2, '0')}-'
          '${date.day.toString().padLeft(2, '0')} '
          '${date.hour.toString().padLeft(2, '0')}:'
          '${date.minute.toString().padLeft(2, '0')}';
    }

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: colors.surface2,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: item.isRead
              ? colors.border
              : colors.accent.withValues(alpha: 0.55),
          width: item.isRead ? 0.5 : 1,
        ),
      ),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
              color: color.withValues(alpha: 0.14), shape: BoxShape.circle),
          child: Icon(icon, color: color, size: 23),
        ),
        const SizedBox(width: 12),
        Expanded(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Expanded(
                child: Text(title,
                    style: TextStyle(
                        color: colors.textPrimary,
                        fontSize: 14,
                        fontWeight: FontWeight.w700)),
              ),
              if (!item.isRead)
                Container(
                    width: 7,
                    height: 7,
                    decoration: BoxDecoration(
                        color: colors.accent, shape: BoxShape.circle)),
            ]),
            const SizedBox(height: 4),
            Text(body,
                style: TextStyle(
                    color: colors.textSecondary, fontSize: 13, height: 1.3)),
            const SizedBox(height: 7),
            Text(time,
                style: TextStyle(color: colors.textSecondary, fontSize: 10)),
          ]),
        ),
      ]),
    );
  }
}
