// Bottom sheets for community road events: reporting a new one, inspecting an
// existing one, and zapping its author.
import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:latlong2/latlong.dart';
import 'package:nostr_tools/nostr_tools.dart' show Nip19;

import '../../l10n/app_localizations.dart';
import '../../screens/profile_screen.dart';
import '../../models/road_event.dart';
import '../../services/nostr_relay_service.dart';
import '../../services/zap_service.dart';
import '../../theme/app_theme.dart';
import '../../utils/units.dart';
import '../nav/speed_limit_sign.dart';

/// Bottom sheet that drives the full NIP-57 + LNURL-pay zap flow.
///
/// Payment strategy (tried in order):
///   1. **NWC (NIP-47)** — if the user has configured a `nostr+walletconnect://`
///      URI in Settings, payment is attempted silently in-app via [ZapService.payViaNwc].
///   2. **Deep link** — fallback; opens the `lightning:<invoice>` URI which
///      hands off to any installed Lightning wallet (e.g. Breez, Wallet of Satoshi).
///
/// NWC is tried first because it allows Roadstr to confirm the payment succeeded
/// (preimage received) and fire the [onZapSent] callback without leaving the app.
/// With a deep link we cannot know whether the user actually paid.
class ZapSheet extends StatefulWidget {
  final RoadEvent event;
  final RoadstrColors colors;
  final void Function(int sats) onZapSent;
  const ZapSheet(
      {super.key,
      required this.event,
      required this.colors,
      required this.onZapSent});
  @override
  State<ZapSheet> createState() => _ZapSheetState();
}

class _ZapSheetState extends State<ZapSheet> {
  static const _presets = [21, 100, 500, 1000, 5000, 21000];
  static const _st = FlutterSecureStorage();

  int? _selected;
  final _customCtrl = TextEditingController();
  bool _sending = false;
  String? _status;

  @override
  void dispose() {
    _customCtrl.dispose();
    super.dispose();
  }

  int get _amount => _selected ?? (int.tryParse(_customCtrl.text) ?? 0);

  /// Executes the full zap payment flow.
  ///
  /// Follows the NIP-57 + LNURL-pay protocol in five sequential steps.
  /// Status messages are updated after each step so the UI reflects progress.
  Future<void> _send() async {
    if (_amount <= 0) return;
    final l = AppLocalizations.of(context);
    setState(() {
      _sending = true;
      _status = l.fetchingLightningAddress;
    });

    try {
      // Step 1: Fetch the recipient's Lightning address from their Nostr kind-0 profile.
      final lud16 = await ZapService.fetchLightningAddress(widget.event.pubkey);
      if (!mounted) return;
      if (lud16 == null) {
        setState(() {
          _status = l.noLightningAddress;
          _sending = false;
        });
        return;
      }
      setState(() => _status = l.requestingInvoice);

      // Step 2: Resolve the LNURL-pay endpoint to get the invoice callback URL
      // and learn whether the server supports NIP-57 zap receipts (allowsNostr).
      final payInfo = await ZapService.fetchLnurlPayInfo(lud16);
      if (!mounted) return;
      if (payInfo == null) {
        setState(() {
          _status = l.lnurlUnavailable;
          _sending = false;
        });
        return;
      }

      // Step 3: Build and sign a NIP-57 kind-9734 zap request (only when logged
      // in with nsec — Amber signing of zap requests is not yet supported).
      // The signed event JSON will be URL-encoded into the LNURL callback as the
      // `nostr` parameter, enabling the server to publish a kind-9735 receipt.
      Map<String, dynamic>? zapRequest;
      final privHex = await _st.read(key: 'nostr_priv_hex');
      final pubHex = await _st.read(key: 'nostr_pub_hex');
      if (!mounted) return;
      if (privHex != null && pubHex != null) {
        zapRequest = ZapService.buildZapRequest(
          senderPrivHex: privHex,
          senderPubHex: pubHex,
          recipientPubHex: widget.event.pubkey,
          eventId: widget.event.id,
          amountMsat: _amount * 1000,
        );
      }

      // Step 4: Request a BOLT-11 invoice from the LNURL callback, optionally
      // attaching the NIP-57 zap request to trigger a kind-9735 receipt.
      final invoice = await ZapService.getInvoice(
          payInfo: payInfo, amountMsat: _amount * 1000, zapRequest: zapRequest);
      if (!mounted) return;
      if (invoice == null) {
        setState(() {
          _status = l.invoiceFailed;
          _sending = false;
        });
        return;
      }
      setState(() => _status = l.openingWallet);

      // Step 5: Pay the invoice. NWC (NIP-47) is tried first because it stays
      // in-app and gives us the preimage proof of payment. Deep link is the
      // fallback for users without NWC configured.
      var nwcUri = await _st.read(key: 'nwc_uri') ?? '';
      if (!mounted) return;
      // One-time migration: move legacy Hive value to SecureStorage.
      if (nwcUri.isEmpty) {
        final legacy =
            (Hive.box('settings').get('nwcUri', defaultValue: '') as String)
                .trim();
        if (legacy.isNotEmpty) {
          await _st.write(key: 'nwc_uri', value: legacy);
          await Hive.box('settings').delete('nwcUri');
          if (!mounted) return;
          nwcUri = legacy;
        }
      }

      bool confirmedPaid = false;
      if (nwcUri.trim().isNotEmpty) {
        setState(() => _status = l.payingViaNwc);
        final preimage =
            await ZapService.payViaNwc(invoice: invoice, nwcUri: nwcUri.trim());
        if (!mounted) return;
        confirmedPaid = preimage != null && preimage.isNotEmpty;
      }
      if (!confirmedPaid) {
        final launched = await ZapService.payViaDeepLink(invoice);
        if (!mounted) return;
        if (launched) {
          // Opening a wallet is not proof of settlement. Close the sheet, but
          // do not increment local zap totals or claim the payment succeeded.
          Navigator.pop(context);
          return;
        }
      }

      if (confirmedPaid && mounted) {
        widget.onZapSent(_amount);
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(l.zapSent(_amount),
              style: const TextStyle(color: Colors.white)),
          backgroundColor: const Color(0xFFF7931A),
          behavior: SnackBarBehavior.floating,
        ));
      } else if (mounted) {
        setState(() {
          _status = l.noLightningWallet;
          _sending = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _status = 'Error: $e';
          _sending = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.colors;
    return AlertDialog(
      backgroundColor: c.surface2,
      title: Row(children: [
        const Text('⚡', style: TextStyle(fontSize: 22)),
        const SizedBox(width: 8),
        Text(AppLocalizations.of(context).sendZap,
            style: TextStyle(
                color: c.textPrimary,
                fontSize: 16,
                fontWeight: FontWeight.bold)),
      ]),
      content: Column(mainAxisSize: MainAxisSize.min, children: [
        Text(AppLocalizations.of(context).chooseAmountSats,
            style: TextStyle(color: c.textSecondary, fontSize: 13)),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: _presets.map((sats) {
            final sel = _selected == sats;
            return GestureDetector(
              onTap: () => setState(() {
                _selected = sel ? null : sats;
                if (!sel) _customCtrl.clear();
              }),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 150),
                padding:
                    const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                decoration: BoxDecoration(
                  color: sel
                      ? const Color(0xFFF7931A).withValues(alpha: 0.15)
                      : c.surface2,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(
                    color: sel ? const Color(0xFFF7931A) : c.border,
                    width: sel ? 2 : 0.5,
                  ),
                ),
                child: Text('$sats ⚡',
                    style: TextStyle(
                        color: sel ? const Color(0xFFF7931A) : c.textPrimary,
                        fontSize: 13,
                        fontWeight: FontWeight.w600)),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _customCtrl,
          keyboardType: TextInputType.number,
          onTap: () => setState(() => _selected = null),
          onChanged: (_) => setState(() => _selected = null),
          style: TextStyle(color: c.textPrimary, fontSize: 13),
          decoration: InputDecoration(
            hintText: AppLocalizations.of(context).customAmount,
            hintStyle: TextStyle(color: c.textSecondary),
            suffixText: 'sat',
            suffixStyle: TextStyle(color: c.textSecondary, fontSize: 12),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: const BorderSide(color: Color(0xFFF7931A)),
            ),
            isDense: true,
            contentPadding: const EdgeInsets.all(10),
          ),
        ),
        if (_status != null) ...[
          const SizedBox(height: 10),
          Text(_status!,
              style: TextStyle(color: c.textSecondary, fontSize: 12)),
        ],
      ]),
      actions: [
        TextButton(
          onPressed: _sending ? null : () => Navigator.pop(context),
          child: Text(AppLocalizations.of(context).cancel,
              style: TextStyle(color: c.textSecondary)),
        ),
        FilledButton.icon(
          onPressed: (_sending || _amount <= 0) ? null : _send,
          style: FilledButton.styleFrom(
            backgroundColor: _amount > 0 ? const Color(0xFFF7931A) : c.border,
          ),
          icon: _sending
              ? const SizedBox(
                  width: 14,
                  height: 14,
                  child: CircularProgressIndicator(
                      strokeWidth: 2, color: Colors.white))
              : const Text('⚡', style: TextStyle(fontSize: 14)),
          label: Text(
              _sending
                  ? AppLocalizations.of(context).zapSending
                  : AppLocalizations.of(context).zapAmountButton(_amount),
              style: const TextStyle(color: Colors.white, fontSize: 13)),
        ),
      ],
    );
  }
}

/// Asks for a speed limit and pops with it in km/h (null when cancelled).
///
/// The text controller lives here, in a widget, rather than in the caller.
/// Creating it in an `async` method and disposing it right after `showDialog`
/// resolves looks harmless but is not: the future completes on `Navigator.pop`
/// while the dialog is still animating out and still rebuilding its TextField,
/// which then touches a disposed controller. That threw "A TextEditingController
/// was used after being disposed" and brought the whole route down with the red
/// "_dependents.isEmpty" screen. Owned by a State, the controller is disposed
/// when the route is actually gone.
class SpeedLimitDialog extends StatefulWidget {
  final Color surface;

  /// Resolves the dialog title against the active localisations.
  final String Function(AppLocalizations) title;

  /// Pre-filled value in km/h, or null for an empty field.
  final int? initialLimitKmh;
  const SpeedLimitDialog({
    super.key,
    required this.surface,
    required this.title,
    this.initialLimitKmh,
  });

  @override
  State<SpeedLimitDialog> createState() => _SpeedLimitDialogState();
}

class _SpeedLimitDialogState extends State<SpeedLimitDialog> {
  late final TextEditingController _ctrl = TextEditingController(
      text: widget.initialLimitKmh == null
          ? ''
          : '${Units.imperial ? Units.toDisplaySpeed(widget.initialLimitKmh!.toDouble()).round() : widget.initialLimitKmh}');

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  void _submit() {
    final raw = int.tryParse(_ctrl.text.trim());
    if (raw == null || raw <= 0 || raw > 300) return;
    Navigator.pop(context, Units.imperial ? (raw * 1.60934).round() : raw);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return AlertDialog(
      backgroundColor: widget.surface,
      title: Text(widget.title(l)),
      content: TextField(
        controller: _ctrl,
        autofocus: true,
        keyboardType: TextInputType.number,
        maxLength: 3,
        onSubmitted: (_) => _submit(),
        decoration: InputDecoration(
          labelText: l.speedLimitHint,
          suffixText: Units.speedUnit,
        ),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.pop(context), child: Text(l.cancel)),
        FilledButton(onPressed: _submit, child: Text(l.ok)),
      ],
    );
  }
}

// ── Road event widgets ────────────────────────────────────────────────────────

class RoadEventDetailSheet extends StatefulWidget {
  final RoadEvent event;
  final RoadstrColors colors;
  final bool isLoggedIn;
  final bool isOwner;
  final List<RoadEventEditRequest> editRequests;
  final Future<void> Function(int speedLimit, String? requestId)?
      onEditSpeedLimit;
  final Future<void> Function(int speedLimit)? onRequestSpeedLimit;
  final void Function(bool)? onConfirm;
  const RoadEventDetailSheet(
      {super.key,
      required this.event,
      required this.colors,
      required this.isLoggedIn,
      this.isOwner = false,
      this.editRequests = const [],
      this.onEditSpeedLimit,
      this.onRequestSpeedLimit,
      this.onConfirm});
  @override
  State<RoadEventDetailSheet> createState() => _RoadEventDetailState();
}

class _RoadEventDetailState extends State<RoadEventDetailSheet> {
  int _zapSat = 0;
  bool _reporterPublic = false;
  NostrProfile? _reporterProfile;

  @override
  void initState() {
    super.initState();
    ZapService.fetchZapTotal(widget.event.id, widget.event.pubkey)
        .then((msats) {
      if (mounted) setState(() => _zapSat = msats ~/ 1000);
    });
    _loadReporterProfile();
  }

  Future<void> _loadReporterProfile() async {
    final visibility =
        await NostrRelayService.fetchProfileVisibility(widget.event.pubkey);
    if (!mounted || visibility?.isPublic != true) {
      if (mounted) setState(() => _reporterPublic = false);
      return;
    }
    final profile = await NostrRelayService.fetchProfile(widget.event.pubkey);
    if (mounted) {
      setState(() {
        _reporterPublic = true;
        _reporterProfile = profile;
      });
    }
  }

  void _openReporterProfile() {
    Navigator.of(context).push(MaterialPageRoute<void>(
      builder: (_) => ProfileScreen(pubkeyHex: widget.event.pubkey),
    ));
  }

  void _openZapSheet() {
    final c = widget.colors;
    showDialog(
      context: context,
      builder: (_) => ZapSheet(
        event: widget.event,
        colors: c,
        onZapSent: (sats) {
          if (mounted) setState(() => _zapSat += sats);
        },
      ),
    );
  }

  Future<void> _askSpeedLimit({String? requestId}) async {
    final value = await showDialog<int>(
      context: context,
      builder: (_) => SpeedLimitDialog(
        surface: widget.colors.surface2,
        title: requestId == null && widget.isOwner
            ? (l) => l.editSpeedLimit
            : (l) => l.requestSpeedLimit,
        initialLimitKmh: requestId == null ? widget.event.speedLimit : null,
      ),
    );
    if (value == null || !mounted) return;
    try {
      if (requestId != null) {
        await widget.onEditSpeedLimit!(value, requestId);
      } else if (widget.isOwner) {
        await widget.onEditSpeedLimit!(value, null);
      } else {
        await widget.onRequestSpeedLimit!(value);
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(error.toString())));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.colors;
    final event = widget.event;
    final navBar = MediaQuery.of(context).viewPadding.bottom;
    return Container(
      margin: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: c.surface2,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: c.border, width: 0.5),
      ),
      padding: EdgeInsets.fromLTRB(20, 16, 20, 24 + navBar),
      // Scrollable: a modal sheet is capped at 9/16 of the screen, and an owned
      // speed-camera report with a comment, a speed limit and pending
      // suggestions is taller than that — it used to overflow into the
      // yellow-and-black striped bar instead of scrolling.
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
                child: Container(
                    width: 40,
                    height: 4,
                    decoration: BoxDecoration(
                        color: c.border,
                        borderRadius: BorderRadius.circular(2)))),
            const SizedBox(height: 16),
            Row(children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: event.category.color.withValues(alpha: 0.15),
                  shape: BoxShape.circle,
                ),
                child: Center(
                    child: Text(event.category.emoji,
                        style: const TextStyle(fontSize: 24))),
              ),
              const SizedBox(width: 14),
              Expanded(
                  child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                    Text(
                        event.category
                            .localizedLabel(AppLocalizations.of(context)),
                        style: TextStyle(
                            color: c.textPrimary,
                            fontSize: 16,
                            fontWeight: FontWeight.bold)),
                    Text(event.ageLabel(AppLocalizations.of(context)),
                        style: TextStyle(color: c.textSecondary, fontSize: 12)),
                  ])),
              // Zap total badge
              if (_zapSat > 0)
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: const Color(0xFFF7931A).withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                        color: const Color(0xFFF7931A).withValues(alpha: 0.4)),
                  ),
                  child: Row(mainAxisSize: MainAxisSize.min, children: [
                    const Text('⚡', style: TextStyle(fontSize: 12)),
                    const SizedBox(width: 3),
                    Text('$_zapSat sat',
                        style: const TextStyle(
                            color: Color(0xFFF7931A),
                            fontSize: 11,
                            fontWeight: FontWeight.bold)),
                  ]),
                ),
            ]),
            if (event.speedLimit != null) ...[
              const SizedBox(height: 12),
              Row(mainAxisSize: MainAxisSize.min, children: [
                SpeedLimitSign(event.speedLimit!),
                const SizedBox(width: 8),
                Text(AppLocalizations.of(context).reportedSpeedLimit,
                    style: TextStyle(color: c.textSecondary, fontSize: 12)),
              ]),
            ],
            if (event.comment.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text(event.comment,
                  style: TextStyle(color: c.textSecondary, fontSize: 13)),
            ],
            if ((widget.onEditSpeedLimit != null ||
                    widget.onRequestSpeedLimit != null) &&
                event.category == RoadCategory.speedCamera) ...[
              const SizedBox(height: 10),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton.icon(
                  onPressed: () => _askSpeedLimit(),
                  icon: Icon(widget.isOwner
                      ? Icons.edit_rounded
                      : Icons.lightbulb_outline_rounded),
                  label: Text(widget.isOwner
                      ? AppLocalizations.of(context).editSpeedLimit
                      : AppLocalizations.of(context).requestSpeedLimit),
                ),
              ),
            ],
            if (widget.isOwner && widget.editRequests.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text(AppLocalizations.of(context).pendingEditRequests,
                  style: TextStyle(
                      color: c.textPrimary,
                      fontSize: 13,
                      fontWeight: FontWeight.w700)),
              const SizedBox(height: 6),
              ...widget.editRequests.map((request) => Card(
                    color: c.surface3,
                    margin: const EdgeInsets.only(bottom: 6),
                    child: ListTile(
                      dense: true,
                      title: Text('${request.speedLimit} ${Units.speedUnit}',
                          style: TextStyle(color: c.textPrimary)),
                      subtitle: Text(
                          '${request.requesterPubkey.substring(0, 8)}…',
                          style:
                              TextStyle(color: c.textSecondary, fontSize: 10)),
                      trailing: TextButton(
                        onPressed: widget.onEditSpeedLimit == null
                            ? null
                            : () => _askSpeedLimit(requestId: request.id),
                        child: Text(
                            AppLocalizations.of(context).acceptEditRequest),
                      ),
                    ),
                  )),
            ],
            const SizedBox(height: 14),
            Row(children: [
              const Icon(Icons.check_circle_outline,
                  color: Color(0xFF22C55E), size: 16),
              const SizedBox(width: 4),
              Text('${event.confirmations}',
                  style: TextStyle(color: c.textSecondary, fontSize: 12)),
              const SizedBox(width: 16),
              const Icon(Icons.cancel_outlined,
                  color: Color(0xFFEF4444), size: 16),
              const SizedBox(width: 4),
              Text('${event.denials}',
                  style: TextStyle(color: c.textSecondary, fontSize: 12)),
            ]),
            const SizedBox(height: 12),
            InkWell(
              onTap: _openReporterProfile,
              borderRadius: BorderRadius.circular(10),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Row(children: [
                  CircleAvatar(
                    radius: 16,
                    backgroundColor: c.accentSoft,
                    backgroundImage:
                        _reporterPublic && _reporterProfile?.picture != null
                            ? NetworkImage(_reporterProfile!.picture!)
                            : null,
                    child: _reporterPublic && _reporterProfile?.picture != null
                        ? null
                        : Icon(Icons.person_outline_rounded,
                            color: c.accent, size: 18),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      _reporterPublic &&
                              _reporterProfile?.label.isNotEmpty == true
                          ? _reporterProfile!.label
                          : AppLocalizations.of(context).nostrichLabel,
                      style: TextStyle(
                          color: c.textPrimary,
                          fontSize: 12,
                          fontWeight: FontWeight.w600),
                    ),
                  ),
                  if (_reporterPublic)
                    Flexible(
                      child: Text(
                        Nip19().npubEncode(event.pubkey),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                            color: c.textSecondary,
                            fontSize: 9,
                            fontFamily: 'monospace'),
                      ),
                    ),
                  Icon(Icons.chevron_right_rounded,
                      color: c.textSecondary, size: 18),
                ]),
              ),
            ),
            if (widget.isLoggedIn && widget.onConfirm != null) ...[
              const SizedBox(height: 16),
              Row(children: [
                Expanded(
                    child: OutlinedButton.icon(
                  onPressed: () {
                    Navigator.pop(context);
                    widget.onConfirm!(true);
                  },
                  style: OutlinedButton.styleFrom(
                    side: const BorderSide(color: Color(0xFF22C55E)),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12)),
                  ),
                  icon: const Icon(Icons.check_rounded,
                      color: Color(0xFF22C55E), size: 16),
                  label: Text(AppLocalizations.of(context).stillThere,
                      style: const TextStyle(
                          color: Color(0xFF22C55E), fontSize: 13)),
                )),
                const SizedBox(width: 10),
                Expanded(
                    child: OutlinedButton.icon(
                  onPressed: () {
                    Navigator.pop(context);
                    widget.onConfirm!(false);
                  },
                  style: OutlinedButton.styleFrom(
                    side: const BorderSide(color: Color(0xFFEF4444)),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12)),
                  ),
                  icon: const Icon(Icons.close_rounded,
                      color: Color(0xFFEF4444), size: 16),
                  label: Text(AppLocalizations.of(context).notThereAnymore,
                      style: const TextStyle(
                          color: Color(0xFFEF4444), fontSize: 13)),
                )),
              ]),
            ] else if (!widget.isLoggedIn) ...[
              const SizedBox(height: 10),
              Text(AppLocalizations.of(context).loginToConfirm,
                  style: TextStyle(color: c.textSecondary, fontSize: 11)),
            ],
            // ── Zap button ─────────────────────────────────────────────────────
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: _openZapSheet,
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: Color(0xFFF7931A)),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                  padding: const EdgeInsets.symmetric(vertical: 10),
                ),
                icon: const Text('⚡', style: TextStyle(fontSize: 16)),
                label: Text(AppLocalizations.of(context).zapSendSats,
                    style: const TextStyle(
                        color: Color(0xFFF7931A), fontSize: 13)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class ReportSheet extends StatefulWidget {
  final RoadstrColors colors;
  final LatLng position;
  final Future<void> Function(RoadCategory, String, int?) onSubmit;
  const ReportSheet(
      {super.key,
      required this.colors,
      required this.position,
      required this.onSubmit});
  @override
  State<ReportSheet> createState() => _ReportSheetState();
}

class _ReportSheetState extends State<ReportSheet> {
  RoadCategory? _selected;
  final _ctrl = TextEditingController();
  final _speedCtrl = TextEditingController();
  bool _sending = false;

  @override
  void dispose() {
    _ctrl.dispose();
    _speedCtrl.dispose();
    super.dispose();
  }

  /// Parsed speed-limit value (km/h internally). For imperial users the field
  /// takes mph and we convert to km/h before publishing, so a nearby driver on
  /// metric units still sees a sensible number.
  int? get _speedLimitKmh {
    final raw = int.tryParse(_speedCtrl.text.trim());
    if (raw == null || raw <= 0 || raw > 300) return null;
    return Units.imperial ? (raw * 1.60934).round() : raw;
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.colors;
    final l = AppLocalizations.of(context);
    // viewInsetsOf lifts the sheet above the keyboard when the TextField is focused.
    // viewPadding.bottom handles the home-indicator safe area below the sheet.
    final bottomPad = MediaQuery.of(context).viewPadding.bottom + 24;
    return Padding(
      padding: MediaQuery.viewInsetsOf(context),
      child: Container(
        margin: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: c.surface2,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: c.border, width: 0.5),
        ),
        child: SingleChildScrollView(
          padding: EdgeInsets.fromLTRB(20, 16, 20, bottomPad),
          child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                    child: Container(
                        width: 40,
                        height: 4,
                        decoration: BoxDecoration(
                            color: c.border,
                            borderRadius: BorderRadius.circular(2)))),
                const SizedBox(height: 14),
                Text(l.reportAnEvent,
                    style: TextStyle(
                        color: c.textPrimary,
                        fontSize: 17,
                        fontWeight: FontWeight.bold)),
                const SizedBox(height: 4),
                Text(
                  '📍 ${widget.position.latitude.toStringAsFixed(4)}, '
                  '${widget.position.longitude.toStringAsFixed(4)}',
                  style: TextStyle(color: c.textSecondary, fontSize: 11),
                ),
                const SizedBox(height: 12),
                // Use MaxCrossAxisExtent so each cell is ≥ 64dp on any screen width.
                // crossAxisCount: 5 was breaking on phones narrower than ~360dp.
                GridView(
                  shrinkWrap: true,
                  gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
                      maxCrossAxisExtent: 80,
                      mainAxisSpacing: 8,
                      crossAxisSpacing: 8,
                      childAspectRatio: 0.85),
                  physics: const NeverScrollableScrollPhysics(),
                  children: RoadCategory.values.map((cat) {
                    final sel = _selected == cat;
                    return GestureDetector(
                      onTap: () => setState(() => _selected = cat),
                      child: AnimatedContainer(
                        duration: const Duration(milliseconds: 150),
                        decoration: BoxDecoration(
                          color: sel
                              ? cat.color.withValues(alpha: 0.18)
                              : c.surface3,
                          borderRadius: BorderRadius.circular(10),
                          border: Border.all(
                              color: sel ? cat.color : c.border,
                              width: sel ? 2 : 0.5),
                        ),
                        child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Text(cat.emoji,
                                  style: const TextStyle(
                                      fontSize: 20, height: 1.2)),
                              const SizedBox(height: 2),
                              Text(cat.localizedLabel(l),
                                  textAlign: TextAlign.center,
                                  style: TextStyle(
                                      fontSize: 8,
                                      color: c.textSecondary,
                                      height: 1.1)),
                            ]),
                      ),
                    );
                  }).toList(),
                ),
                // Speed-limit field — only for speed-camera reports. Nearby
                // drivers see this number on the camera and hear it announced.
                if (_selected == RoadCategory.speedCamera) ...[
                  const SizedBox(height: 12),
                  TextField(
                    controller: _speedCtrl,
                    keyboardType: TextInputType.number,
                    maxLength: 3,
                    style: TextStyle(color: c.textPrimary, fontSize: 13),
                    onChanged: (_) => setState(() {}),
                    decoration: InputDecoration(
                      prefixIcon: Icon(Icons.speed_rounded,
                          color: c.textSecondary, size: 18),
                      suffixText: Units.speedUnit,
                      suffixStyle:
                          TextStyle(color: c.textSecondary, fontSize: 12),
                      hintText: l.reportSpeedLimitHint,
                      hintStyle: TextStyle(color: c.textSecondary),
                      border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12)),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide(color: c.accent),
                      ),
                      counterText: '',
                      contentPadding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 14),
                    ),
                  ),
                ],
                const SizedBox(height: 12),
                TextField(
                  controller: _ctrl,
                  maxLines: 2,
                  maxLength: 200,
                  style: TextStyle(color: c.textPrimary, fontSize: 13),
                  decoration: InputDecoration(
                    hintText: AppLocalizations.of(context).optionalComment,
                    hintStyle: TextStyle(color: c.textSecondary),
                    border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12)),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide(color: c.accent),
                    ),
                    counterStyle:
                        TextStyle(color: c.textSecondary, fontSize: 11),
                    contentPadding: const EdgeInsets.all(12),
                  ),
                ),
                const SizedBox(height: 10),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    onPressed: _selected == null || _sending
                        ? null
                        : () async {
                            final messenger = ScaffoldMessenger.of(context);
                            final publishedMessage = l.reportPublished;
                            setState(() => _sending = true);
                            try {
                              await widget.onSubmit(
                                  _selected!,
                                  _ctrl.text.trim(),
                                  _selected == RoadCategory.speedCamera
                                      ? _speedLimitKmh
                                      : null);
                              if (mounted) {
                                Navigator.pop(this.context);
                                messenger.showSnackBar(
                                  SnackBar(
                                    content: Text(publishedMessage,
                                        style: const TextStyle(
                                            color: Colors.white)),
                                    backgroundColor: const Color(0xFF1A1A2E),
                                    behavior: SnackBarBehavior.floating,
                                  ),
                                );
                              }
                            } catch (e) {
                              if (mounted) {
                                setState(() => _sending = false);
                                messenger.showSnackBar(SnackBar(
                                  content: Text(e.toString(),
                                      style:
                                          const TextStyle(color: Colors.white)),
                                  backgroundColor: const Color(0xFFDC2626),
                                  behavior: SnackBarBehavior.floating,
                                ));
                              }
                            }
                          },
                    style: FilledButton.styleFrom(
                      backgroundColor: _selected?.color ?? c.border,
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                      padding: const EdgeInsets.symmetric(vertical: 13),
                    ),
                    icon: _sending
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: Colors.white))
                        : const Icon(Icons.send_rounded,
                            color: Colors.white, size: 18),
                    label: Text(
                        _sending
                            ? AppLocalizations.of(context).publishing
                            : AppLocalizations.of(context).publish,
                        style: const TextStyle(color: Colors.white)),
                  ),
                ),
              ]),
        ), // SingleChildScrollView
      ), // Container
    ); // Padding
  }
}

// ── Place info panel ──────────────────────────────────────────────────────────
