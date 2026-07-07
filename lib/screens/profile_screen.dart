// Nostr profile screen for Roadstr.
//
// Supports two login methods:
//
//   1. Amber (NIP-55) — the recommended, keyless flow. Amber is a dedicated
//      Android signer app; it holds the user's private key and signs events via
//      startActivityForResult (wrapped by the amberflutter package). The app
//      never sees or stores the private key; only the public key is persisted.
//
//   2. nsec — the user pastes their private key directly. This is intentionally
//      de-emphasised (shown with a warning colour and requires a checkbox
//      acknowledgment) because storing a private key on-device is a security risk.
//
// Identity is persisted in FlutterSecureStorage (Android Keystore-backed
// encrypted shared preferences), not in Hive, to ensure the keys survive app
// reinstalls only when the device's keystore also persists.
//
// The profile screen also shows:
//   - Reputation score: confirmations / (confirmations + denials) across all
//     of the user's road events, loaded in parallel with the balance fetch.
//   - Lightning balance: total satoshis received as NIP-57 zap receipts.
import 'package:amberflutter/amberflutter.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:nostr_tools/nostr_tools.dart';
import '../l10n/app_localizations.dart';
import '../models/road_event.dart';
import '../services/nostr_relay_service.dart';
import '../services/routing_service.dart';
import '../services/zap_service.dart';
import '../theme/app_theme.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});
  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  /// Secure storage backed by the Android Keystore (EncryptedSharedPreferences).
  /// Used for all Nostr key material — never stored in plain SharedPreferences.
  static const _st = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
  );
  static const _kPriv    = 'nostr_priv_hex';
  static const _kPub     = 'nostr_pub_hex';
  /// Login method identifier: `"amber"` or `"nsec"`.
  static const _kFlavor  = 'nostr_flavor';
  static const _kPicture = 'nostr_picture';
  static const _kName    = 'nostr_name';

  final _amber = Amberflutter();

  String? _npub;
  String? _flavor;
  String? _picture;
  String? _name;
  bool _loading = true;
  bool _waitingAmber = false;

  List<RoadEvent>? _myEvents;
  bool _loadingEvents = false;
  /// Reputation score in [0.0, 1.0], or -1 if not yet computed.
  /// Formula: `confirmations / (confirmations + denials)` across all events.
  double _reputation  = -1;
  /// Total satoshis received as zaps, or -1 if not yet loaded.
  int    _balanceSat  = -1;

  @override
  void initState() {
    super.initState();
    _loadProfile().then((_) { if (_loggedIn) _loadMyEvents(); });
  }

  Future<void> _loadProfile() async {
    final pub     = await _st.read(key: _kPub);
    final flav    = await _st.read(key: _kFlavor);
    final picture = await _st.read(key: _kPicture);
    final name    = await _st.read(key: _kName);
    if (!mounted) return;
    setState(() {
      _npub    = pub != null ? Nip19().npubEncode(pub) : null;
      _flavor  = flav;
      _picture = picture;
      _name    = name;
      _loading = false;
    });
    // Re-fetch Nostr profile whenever name or picture is missing in storage.
    // This covers the case where:
    //   a) the user logged in with nsec before the profile-fetch feature existed,
    //   b) the initial fetch timed out or failed,
    //   c) the user updated their Nostr profile since last opening the app.
    if (pub != null && (name == null || picture == null)) {
      _fetchAndStoreProfile(pub);
    }
  }

  /// Loads the user's road events and Lightning balance in parallel.
  ///
  /// Using [Future.wait] for parallelism is important here: fetching events
  /// (a Nostr REQ/EOSE round-trip) and fetching the balance (another Nostr
  /// REQ/EOSE round-trip) are independent operations, each taking ~1–3 s.
  /// Running them sequentially would double the perceived loading time.
  ///
  /// Reputation formula: `confirmations / (confirmations + denials)`.
  /// Returns -1 (shown as "no reputation yet") when the user has no events or
  /// no confirmations/denials have been received.
  Future<void> _loadMyEvents() async {
    if (_loadingEvents || _myEvents != null) return;
    final pub = await _st.read(key: _kPub);
    if (pub == null) return;
    setState(() => _loadingEvents = true);

    // Fetch events and Lightning balance in parallel to minimise wait time.
    final results = await Future.wait([
      NostrRelayService.fetchUserEvents(pub),
      ZapService.fetchBalance(pub),
    ]);
    final events  = results[0] as List<RoadEvent>;
    final balMsat = results[1] as int;

    events.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    int up = 0, down = 0;
    for (final e in events) { up += e.confirmations; down += e.denials; }
    final total = up + down;
    if (!mounted) return;
    setState(() {
      _myEvents      = events;
      _loadingEvents = false;
      _reputation    = total > 0 ? up / total : -1;
      // ZapService returns millisatoshi; convert to satoshi for display.
      _balanceSat    = balMsat ~/ 1000;
    });
  }

  /// Converts the public key returned by Amber to a `(pubHex, npub)` pair.
  ///
  /// Amber may return the key as either:
  ///   - A 64-character lowercase hex string (standard NIP-55 behaviour).
  ///   - A bech32 `npub1...` string (observed on some Amber versions).
  ///
  /// Both formats are normalised here so the rest of the app always stores hex
  /// in secure storage and only derives the npub display string on demand.
  ({String pubHex, String npub}) _parsePubkey(String raw) {
    final nip19 = Nip19();
    if (raw.startsWith('npub1')) {
      // Bech32 npub — decode to hex, keep the original npub for display.
      final decoded = nip19.decode(raw);
      return (pubHex: decoded['data'] as String, npub: raw);
    }
    // Hex pubkey — encode to npub for display.
    final hex = raw.toLowerCase().trim();
    return (pubHex: hex, npub: nip19.npubEncode(hex));
  }

  /// After login, fetches the user name and avatar from the relay (kind-0 metadata).
  Future<void> _fetchAndStoreProfile(String pubHex) async {
    final profile = await NostrRelayService.fetchProfile(pubHex);
    if (profile == null) return;
    final name = profile.label;
    if (name.isNotEmpty) await _st.write(key: _kName, value: name);
    if (profile.picture != null) {
      await _st.write(key: _kPicture, value: profile.picture!);
    }
    if (!mounted) return;
    setState(() {
      if (name.isNotEmpty) _name = name;
      if (profile.picture != null) _picture = profile.picture;
    });
  }

  bool get _loggedIn => _npub != null;

  // ── Amber (NIP-55) ────────────────────────────────────────────────────────

  /// Initiates the Amber NIP-55 login flow.
  ///
  /// NIP-55 uses Android's `startActivityForResult` mechanism: the app sends an
  /// Intent to the Amber signer app, which shows its own UI for the user to
  /// approve sharing their public key. The result is returned via the `amberflutter`
  /// plugin's [Amberflutter.getPublicKey] method.
  ///
  /// No private key is ever transmitted — Amber only returns the public key plus
  /// a `sign_event` permission grant so that subsequent signing requests (kind-1315,
  /// kind-1316 events) are automatically approved without further prompts.
  ///
  /// The private key is NEVER stored by Roadstr when using the Amber flow.
  Future<void> _loginWithAmber() async {
    setState(() => _waitingAmber = true);
    try {
      final result = await _amber.getPublicKey(
        permissions: [Permission(type: 'sign_event')],
      );
      // Amber may return hex (standard NIP-55) or bech32 npub depending on version.
      final raw = result['signature'] as String? ?? '';
      if (raw.isEmpty) throw Exception('No key received from Amber');

      final (:pubHex, :npub) = _parsePubkey(raw);
      await _st.write(key: _kPub,    value: pubHex);
      await _st.write(key: _kFlavor, value: 'amber');
      if (!mounted) return;
      setState(() { _npub = npub; _flavor = 'amber'; _waitingAmber = false; });
      _fetchAndStoreProfile(pubHex); // fire-and-forget; updates name/avatar async
      _loadMyEvents();
    } catch (e) {
      if (!mounted) return;
      setState(() => _waitingAmber = false);
      _showError(AppLocalizations.of(context)!.invalidNpubTitle, e.toString());
    }
  }

  void _startAmberFlow() {
    final l = AppLocalizations.of(context)!;
    final c = RoadstrColors.of(context);
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: c.surface2,
        title: Row(children: [
          Icon(Icons.security_rounded, color: c.accent, size: 20),
          const SizedBox(width: 8),
          Text(l.amberDialogTitle,
              style: TextStyle(color: c.textPrimary, fontSize: 16)),
        ]),
        content: Text(
          l.amberDescription,
          style: TextStyle(color: c.textSecondary, fontSize: 13, height: 1.5),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(l.cancel, style: TextStyle(color: c.textSecondary)),
          ),
          FilledButton.icon(
            onPressed: () {
              Navigator.pop(ctx);
              _loginWithAmber();
            },
            style: FilledButton.styleFrom(
              backgroundColor: c.accent,
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
            ),
            icon: const Icon(Icons.security_rounded,
                color: Colors.white, size: 18),
            label: Text(l.requestKeyFromAmber,
                style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  // ── nsec ──────────────────────────────────────────────────────────────────

  void _startNsecFlow() {
    showDialog(
      context: context,
      builder: (_) => _WarningDialog(
        colors: RoadstrColors.of(context),
        l: AppLocalizations.of(context)!,
        onConfirm: _showNsecInputDialog,
      ),
    );
  }

  void _showNsecInputDialog() {
    final l    = AppLocalizations.of(context)!;
    final c    = RoadstrColors.of(context);
    final ctrl = TextEditingController();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: c.surface2,
        title: Text(l.enterNsecTitle,
            style: TextStyle(color: c.textPrimary, fontSize: 17)),
        // AutofillGroup is required for Android's Autofill Framework (and
        // Bitwarden / 1Password accessibility overlays) to recognise this
        // as a password field and offer to fill it.
        content: AutofillGroup(
         child: TextField(
          controller: ctrl,
          autofillHints: const [AutofillHints.password],
          obscureText: true, autocorrect: false, enableSuggestions: false,
          keyboardType: TextInputType.visiblePassword,
          style: TextStyle(color: c.textPrimary, fontSize: 13,
              fontFamily: 'monospace'),
          decoration: InputDecoration(
            hintText: 'nsec1...',
            hintStyle: TextStyle(color: c.textSecondary),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide(color: c.accent),
            ),
          ),
         ), // TextField
        ), // AutofillGroup
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(l.cancel, style: TextStyle(color: c.textSecondary)),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: c.accent),
            onPressed: () {
              final nsec = ctrl.text.trim();
              Navigator.pop(ctx);
              _loginWithNsec(nsec);
            },
            child: Text(l.loginButton,
                style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  Future<void> _loginWithNsec(String nsec) async {
    final l = AppLocalizations.of(context)!;
    try {
      final nip19   = Nip19();
      final decoded = nip19.decode(nsec);
      if (decoded['type'] != 'nsec') throw const FormatException('not nsec');
      final privHex = decoded['data'] as String;
      final pubHex  = KeyApi().getPublicKey(privHex);
      final npub    = nip19.npubEncode(pubHex);
      await _st.write(key: _kPriv,   value: privHex);
      await _st.write(key: _kPub,    value: pubHex);
      await _st.write(key: _kFlavor, value: 'nsec');
      if (!mounted) return;
      setState(() { _npub = npub; _flavor = 'nsec'; });
      _fetchAndStoreProfile(pubHex); // fire-and-forget
      _loadMyEvents();
    } catch (_) {
      if (mounted) _showError(l.invalidNsecTitle, l.invalidNsecMessage);
    }
  }

  // ── logout ────────────────────────────────────────────────────────────────

  Future<void> _logout() async {
    final l = AppLocalizations.of(context)!;
    final c = RoadstrColors.of(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: c.surface2,
        title: Text(l.logoutTitle,
            style: TextStyle(color: c.textPrimary, fontSize: 17)),
        content: Text(l.logoutConfirmation,
            style: TextStyle(color: c.textSecondary, fontSize: 13)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l.cancel, style: TextStyle(color: c.textSecondary)),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFFFF4444)),
            onPressed: () => Navigator.pop(context, true),
            child: Text(l.logoutButton,
                style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
    if (ok == true) {
      await _st.deleteAll();
      if (mounted) setState(() {
        _npub = null; _flavor = null; _picture = null; _name = null;
        _myEvents = null; _reputation = -1; _balanceSat = -1;
      });
    }
  }

  void _showEventDetail(RoadEvent event, RoadstrColors c) {
    showDialog(
      context: context,
      builder: (_) => _EventDetailDialog(event: event, colors: c),
    );
  }


  void _showError(String title, String msg) {
    if (!mounted) return;
    final c = RoadstrColors.of(context);
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: c.surface2,
        title: Row(children: [
          const Icon(Icons.error_outline, color: Color(0xFFFF4444), size: 20),
          const SizedBox(width: 8),
          Text(title, style: TextStyle(color: c.textPrimary, fontSize: 16)),
        ]),
        content: Text(msg,
            style: TextStyle(color: c.textSecondary, fontSize: 13)),
        actions: [
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: c.accent),
            onPressed: () => Navigator.pop(context),
            child: Text(AppLocalizations.of(context)!.ok,
                style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  // ── build ─────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final c = RoadstrColors.of(context);
    final l = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        title: Text(l.profileTitle), centerTitle: true,
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(0.5),
          child: Divider(height: 0.5, color: c.border),
        ),
      ),
      body: _loading
          ? Center(child: CircularProgressIndicator(color: c.accent))
          : ListView(
              // Bottom padding via SafeArea so the Disconnect button is never
              // hidden by the Android navigation bar in edge-to-edge mode.
              padding: EdgeInsets.fromLTRB(
                  20, 20, 20,
                  20 + MediaQuery.of(context).viewPadding.bottom),
              children: _loggedIn
                  ? _buildLoggedIn(c, l)
                  : _buildLoggedOut(c, l),
            ),
    );
  }

  List<Widget> _buildLoggedIn(RoadstrColors c, AppLocalizations l) => [
    Center(child: Stack(clipBehavior: Clip.none, children: [
      Container(
        width: 80, height: 80,
        decoration: BoxDecoration(
          shape: BoxShape.circle, color: c.accentSoft,
          border: Border.all(color: c.accent, width: 2),
        ),
        child: ClipOval(
          child: _picture != null
              ? Image.network(
                  _picture!,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) =>
                      Icon(Icons.person_rounded, color: c.accent, size: 40),
                )
              : Icon(Icons.person_rounded, color: c.accent, size: 40),
        ),
      ),
      Positioned(bottom: 0, right: 0,
        child: Container(
          width: 24, height: 24,
          decoration: BoxDecoration(
            color: const Color(0xFF22C55E), shape: BoxShape.circle,
            border: Border.all(color: c.surface2, width: 2),
          ),
          child: const Icon(Icons.check_rounded, color: Colors.white, size: 13),
        ),
      ),
    ])),
    const SizedBox(height: 8),
    if (_name != null && _name!.isNotEmpty)
      Center(child: Text(_name!,
          style: TextStyle(color: c.textPrimary, fontSize: 15,
              fontWeight: FontWeight.w600))),
    const SizedBox(height: 4),
    Center(child: Text(
      _flavor == 'amber' ? l.connectedViaAmber : l.connectedViaNsec,
      style: TextStyle(color: c.textSecondary, fontSize: 12),
    )),
    const SizedBox(height: 24),
    Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: c.surface2, borderRadius: BorderRadius.circular(14),
        border: Border.all(color: c.border, width: 0.5),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(l.publicKeyLabel, style: TextStyle(color: c.textSecondary,
            fontSize: 10, fontWeight: FontWeight.bold, letterSpacing: 1)),
        const SizedBox(height: 8),
        Row(children: [
          Expanded(
            child: Text(_npub!, maxLines: 2, overflow: TextOverflow.ellipsis,
                style: TextStyle(color: c.textPrimary, fontSize: 11,
                    fontFamily: 'monospace')),
          ),
          const SizedBox(width: 8),
          IconButton(
            onPressed: () {
              Clipboard.setData(ClipboardData(text: _npub!));
              ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                content: Text(l.npubCopiedToClipboard,
                    style: const TextStyle(color: Colors.white)),
                backgroundColor: const Color(0xFF1A1A2E),
                behavior: SnackBarBehavior.floating,
              ));
            },
            icon: Icon(Icons.copy_rounded, color: c.accent, size: 20),
            visualDensity: VisualDensity.compact,
          ),
        ]),
      ]),
    ),
    const SizedBox(height: 20),

    // ── Reputation + Lightning balance ────────────────────────────────────────
    if (_reputation >= 0) _ReputationBadge(score: _reputation, colors: c),
    if (_reputation >= 0) const SizedBox(height: 10),
    if (_balanceSat >= 0) _BalanceBadge(sats: _balanceSat, colors: c),
    if (_balanceSat >= 0) const SizedBox(height: 10),

    // ── My reports ─────────────────────────────────────────────────────────────
    Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 8),
      child: Text(AppLocalizations.of(context)!.myReports, style: TextStyle(
          color: c.textSecondary, fontSize: 11,
          fontWeight: FontWeight.bold, letterSpacing: 1)),
    ),
    if (_loadingEvents)
      Center(child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: CircularProgressIndicator(color: c.accent, strokeWidth: 2),
      ))
    else if (_myEvents == null || _myEvents!.isEmpty)
      Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(color: c.surface2,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: c.border, width: 0.5)),
        child: Center(child: Text(AppLocalizations.of(context)!.noReportsYet,
            style: TextStyle(color: c.textSecondary, fontSize: 13))),
      )
    else
      ...(_myEvents!.map((ev) => _EventTile(
          event: ev,
          colors: c,
          onTap: () => _showEventDetail(ev, c),
      )).toList()),

    const SizedBox(height: 24),
    SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
        onPressed: _logout,
        style: OutlinedButton.styleFrom(
          side: const BorderSide(color: Color(0xFFFF4444)),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          padding: const EdgeInsets.symmetric(vertical: 14),
        ),
        icon: const Icon(Icons.logout_rounded,
            color: Color(0xFFFF4444), size: 18),
        label: Text(l.logoutButton,
            style: const TextStyle(color: Color(0xFFFF4444))),
      ),
    ),
  ];

  List<Widget> _buildLoggedOut(RoadstrColors c, AppLocalizations l) => [
    Center(child: Container(
      width: 80, height: 80,
      decoration: BoxDecoration(
        shape: BoxShape.circle, color: c.accentSoft,
        border: Border.all(color: c.accent, width: 2),
      ),
      child: Icon(Icons.person_outline, color: c.accent, size: 40),
    )),
    const SizedBox(height: 12),
    Center(child: Text(l.notConnected,
        style: TextStyle(color: c.textSecondary, fontSize: 14))),
    if (_waitingAmber) ...[
      const SizedBox(height: 16),
      Center(child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
        decoration: BoxDecoration(
          color: c.accentSoft, borderRadius: BorderRadius.circular(10),
          border: Border.all(color: c.accent.withValues(alpha: 0.3)),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          SizedBox(width: 14, height: 14,
              child: CircularProgressIndicator(
                  strokeWidth: 2, color: c.accent)),
          const SizedBox(width: 10),
          Text(l.waitingForAmberResponse,
              style: TextStyle(color: c.accent, fontSize: 12)),
        ]),
      )),
    ],
    const SizedBox(height: 32),
    Padding(
      padding: const EdgeInsets.only(left: 4, bottom: 8),
      child: Text(l.loginWithNostrTitle, style: TextStyle(
          color: c.textSecondary, fontSize: 11,
          fontWeight: FontWeight.bold, letterSpacing: 1)),
    ),
    _LoginTile(
      icon: Icons.security_rounded,
      title: l.amberNip55Title,
      subtitle: l.amberLoginDescription,
      colors: c, onTap: _startAmberFlow,
    ),
    const SizedBox(height: 8),
    _LoginTile(
      icon: Icons.key_outlined,
      title: l.nsecLoginOption,
      subtitle: l.nsecLoginDescription,
      colors: c, isWarning: true, onTap: _startNsecFlow,
    ),
    const SizedBox(height: 24),
    Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: c.accentSoft, borderRadius: BorderRadius.circular(12),
        border: Border.all(color: c.accent.withValues(alpha: 0.3)),
      ),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(Icons.info_outline, color: c.accent, size: 18),
        const SizedBox(width: 10),
        Expanded(child: Text(l.nostrIdentityInfo,
            style: TextStyle(color: c.textSecondary, fontSize: 12,
                height: 1.5))),
      ]),
    ),
  ];
}

// ── Balance badge ─────────────────────────────────────────────────────────────

class _BalanceBadge extends StatelessWidget {
  final int sats;
  final RoadstrColors colors;
  const _BalanceBadge({required this.sats, required this.colors});

  @override
  Widget build(BuildContext context) {
    final c = colors;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFFF7931A).withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
            color: const Color(0xFFF7931A).withValues(alpha: 0.35), width: 0.5),
      ),
      child: Row(children: [
        const Text('⚡', style: TextStyle(fontSize: 24)),
        const SizedBox(width: 14),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
            children: [
          Text(AppLocalizations.of(context)!.zapBalance, style: TextStyle(
              color: c.textPrimary, fontSize: 15,
              fontWeight: FontWeight.bold)),
          Text(AppLocalizations.of(context)!.satoshiFromReports,
              style: TextStyle(color: c.textSecondary, fontSize: 11)),
        ])),
        Text(sats > 0 ? '$sats sat' : '0 sat',
            style: const TextStyle(
                color: Color(0xFFF7931A), fontSize: 18,
                fontWeight: FontWeight.bold)),
      ]),
    );
  }
}

// ── Reputation badge ─────────────────────────────────────────────────────────

class _ReputationBadge extends StatelessWidget {
  final double score; // 0.0 – 1.0
  final RoadstrColors colors;
  const _ReputationBadge({required this.score, required this.colors});

  Color get _color {
    if (score >= 0.67) return const Color(0xFF22C55E);
    if (score >= 0.34) return const Color(0xFFF59E0B);
    return const Color(0xFFEF4444);
  }

  String _label(AppLocalizations l) {
    if (score >= 0.67) return l.reputationHigh;
    if (score >= 0.34) return l.reputationMedium;
    return l.reputationLow;
  }

  @override
  Widget build(BuildContext context) {
    final pct = (score * 100).round();
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: _color.withValues(alpha: 0.35), width: 0.5),
      ),
      child: Row(children: [
        Icon(Icons.verified_rounded, color: _color, size: 28),
        const SizedBox(width: 14),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
            children: [
          Text(AppLocalizations.of(context)!.reputationLabel(_label(AppLocalizations.of(context)!)),
              style: TextStyle(
              color: colors.textPrimary, fontSize: 15,
              fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: score,
              backgroundColor: colors.border,
              color: _color,
              minHeight: 6,
            ),
          ),
        ])),
        const SizedBox(width: 12),
        Text('$pct%', style: TextStyle(
            color: _color, fontSize: 18, fontWeight: FontWeight.bold)),
      ]),
    );
  }
}

// ── Event tile ────────────────────────────────────────────────────────────────

class _EventTile extends StatefulWidget {
  final RoadEvent event;
  final RoadstrColors colors;
  final VoidCallback? onTap;
  const _EventTile({required this.event, required this.colors, this.onTap});
  @override
  State<_EventTile> createState() => _EventTileState();
}

class _EventTileState extends State<_EventTile> {
  String? _address;
  int     _zapSat = 0;

  @override
  void initState() {
    super.initState();
    RoutingService.reverseGeocode(widget.event.position, parts: 4)
        .then((addr) { if (mounted) setState(() => _address = addr); });
    ZapService.fetchZapTotal(widget.event.id)
        .then((msats) { if (mounted) setState(() => _zapSat = msats ~/ 1000); });
  }

  @override
  Widget build(BuildContext context) {
    final c     = widget.colors;
    final event = widget.event;
    final total = event.confirmations + event.denials;
    return InkWell(
      onTap: widget.onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: c.surface2, borderRadius: BorderRadius.circular(12),
          border: Border.all(color: c.border, width: 0.5),
        ),
        child: Row(children: [
          Text(event.category.emoji, style: const TextStyle(fontSize: 22)),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
              children: [
            Text(event.category.localizedLabel(AppLocalizations.of(context)!), style: TextStyle(
                color: c.textPrimary, fontSize: 14,
                fontWeight: FontWeight.w600)),
            const SizedBox(height: 2),
            // Indirizzo completo o placeholder di caricamento
            _address != null
                ? Text(_address!, maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(color: c.textSecondary, fontSize: 11))
                : Row(children: [
                    SizedBox(width: 10, height: 10,
                        child: CircularProgressIndicator(
                            strokeWidth: 1.5, color: c.accent)),
                    const SizedBox(width: 6),
                    Text(AppLocalizations.of(context)!.loadingLabel,
                        style: TextStyle(color: c.textSecondary, fontSize: 10)),
                  ]),
            const SizedBox(height: 2),
            Text(event.ageLabel(AppLocalizations.of(context)!),
                style: TextStyle(color: c.textSecondary, fontSize: 10)),
            if (event.comment.isNotEmpty)
              Text(event.comment, maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                      color: c.textSecondary, fontSize: 11,
                      fontStyle: FontStyle.italic)),
          ])),
          const SizedBox(width: 8),
          Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Row(mainAxisSize: MainAxisSize.min, children: [
              const Icon(Icons.thumb_up_outlined,
                  color: Color(0xFF22C55E), size: 14),
              const SizedBox(width: 3),
              Text('${event.confirmations}',
                  style: TextStyle(color: c.textSecondary, fontSize: 12)),
            ]),
            const SizedBox(height: 2),
            Row(mainAxisSize: MainAxisSize.min, children: [
              const Icon(Icons.thumb_down_outlined,
                  color: Color(0xFFEF4444), size: 14),
              const SizedBox(width: 3),
              Text('${event.denials}',
                  style: TextStyle(color: c.textSecondary, fontSize: 12)),
            ]),
            if (total > 0)
              Text('${((event.confirmations / total) * 100).round()}%',
                  style: TextStyle(color: c.textSecondary, fontSize: 10)),
          if (_zapSat > 0) ...[
            const SizedBox(height: 4),
            Text('⚡$_zapSat',
                style: const TextStyle(
                    color: Color(0xFFF7931A), fontSize: 11,
                    fontWeight: FontWeight.bold)),
          ],
          ]),
        ]),
      ),
    );
  }
}

// ── Event detail dialog con indirizzo asincrono ───────────────────────────────

class _EventDetailDialog extends StatefulWidget {
  final RoadEvent event;
  final RoadstrColors colors;
  const _EventDetailDialog({required this.event, required this.colors});
  @override
  State<_EventDetailDialog> createState() => _EventDetailDialogState();
}

class _EventDetailDialogState extends State<_EventDetailDialog> {
  String? _address;
  bool _loadingAddr = true;

  @override
  void initState() {
    super.initState();
    RoutingService.reverseGeocode(widget.event.position, parts: 4).then((addr) {
      if (mounted) setState(() { _address = addr; _loadingAddr = false; });
    });
  }

  @override
  Widget build(BuildContext context) {
    final c   = widget.colors;
    final ev  = widget.event;
    final dt  = DateTime.fromMillisecondsSinceEpoch(ev.createdAt * 1000);
    final pad = (int n) => n.toString().padLeft(2, '0');
    final dateStr =
        '${pad(dt.day)}/${pad(dt.month)}/${dt.year}  ${pad(dt.hour)}:${pad(dt.minute)}';
    final total = ev.confirmations + ev.denials;

    return AlertDialog(
      backgroundColor: c.surface2,
      titlePadding: const EdgeInsets.fromLTRB(20, 20, 20, 0),
      title: Row(children: [
        Text(ev.category.emoji, style: const TextStyle(fontSize: 26)),
        const SizedBox(width: 10),
        Expanded(child: Text(ev.category.localizedLabel(AppLocalizations.of(context)!), style: TextStyle(
            color: c.textPrimary, fontSize: 16, fontWeight: FontWeight.bold))),
      ]),
      content: Column(mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (ev.comment.isNotEmpty) ...[
          Text(ev.comment, style: TextStyle(
              color: c.textSecondary, fontSize: 13)),
          const SizedBox(height: 12),
          Divider(height: 0.5, color: c.border),
          const SizedBox(height: 12),
        ],
        _Row(AppLocalizations.of(context)!.dateTimeLabel, dateStr, c),

        // Indirizzo (caricamento asincrono)
        if (_loadingAddr)
          Row(children: [
            Text(AppLocalizations.of(context)!.positionLabel, style: TextStyle(color: c.textSecondary, fontSize: 12)),
            const Spacer(),
            SizedBox(width: 12, height: 12,
                child: CircularProgressIndicator(strokeWidth: 1.5, color: c.accent)),
          ])
        else if (_address != null)
          _Row(AppLocalizations.of(context)!.positionLabel, _address!, c)
        else ...[
          _Row('Lat', ev.position.latitude.toStringAsFixed(6),  c),
          _Row('Lon', ev.position.longitude.toStringAsFixed(6), c),
        ],

        const SizedBox(height: 10),
        Row(children: [
          Expanded(child: Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFF22C55E).withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                  color: const Color(0xFF22C55E).withValues(alpha: 0.3)),
            ),
            child: Column(children: [
              const Icon(Icons.thumb_up_outlined,
                  color: Color(0xFF22C55E), size: 18),
              const SizedBox(height: 4),
              Text('${ev.confirmations}', style: const TextStyle(
                  color: Color(0xFF22C55E), fontSize: 18,
                  fontWeight: FontWeight.bold)),
              Text(AppLocalizations.of(context)!.confirmedLabel,
                  style: TextStyle(color: c.textSecondary, fontSize: 10)),
            ]),
          )),
          const SizedBox(width: 8),
          Expanded(child: Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFFEF4444).withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                  color: const Color(0xFFEF4444).withValues(alpha: 0.3)),
            ),
            child: Column(children: [
              const Icon(Icons.thumb_down_outlined,
                  color: Color(0xFFEF4444), size: 18),
              const SizedBox(height: 4),
              Text('${ev.denials}', style: const TextStyle(
                  color: Color(0xFFEF4444), fontSize: 18,
                  fontWeight: FontWeight.bold)),
              Text(AppLocalizations.of(context)!.removedLabel,
                  style: TextStyle(color: c.textSecondary, fontSize: 10)),
            ]),
          )),
        ]),
        if (total > 0) ...[
          const SizedBox(height: 10),
          Center(child: Text(
            AppLocalizations.of(context)!.reliability(
                ((ev.confirmations / total) * 100).round()),
            style: TextStyle(color: c.textSecondary, fontSize: 12),
          )),
        ],
      ]),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(AppLocalizations.of(context)!.close, style: TextStyle(color: c.accent)),
        ),
      ],
    );
  }

  Widget _Row(String label, String value, RoadstrColors c) => Padding(
    padding: const EdgeInsets.only(bottom: 6),
    child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
      Text(label, style: TextStyle(color: c.textSecondary, fontSize: 12)),
      Flexible(child: Text(value, maxLines: 2,
          overflow: TextOverflow.ellipsis, textAlign: TextAlign.end,
          style: TextStyle(color: c.textPrimary, fontSize: 12,
              fontFamily: 'monospace'))),
    ]),
  );
}

// ── Warning Dialog ────────────────────────────────────────────────────────────

class _WarningDialog extends StatefulWidget {
  final RoadstrColors colors;
  final AppLocalizations l;
  final VoidCallback onConfirm;
  const _WarningDialog({required this.colors, required this.l,
      required this.onConfirm});
  @override
  State<_WarningDialog> createState() => _WarningDialogState();
}

class _WarningDialogState extends State<_WarningDialog> {
  bool _ack = false;
  @override
  Widget build(BuildContext context) {
    final c = widget.colors;
    final l = widget.l;
    return AlertDialog(
      backgroundColor: c.surface2,
      title: Row(children: [
        const Icon(Icons.warning_amber_rounded,
            color: Color(0xFFFFB800), size: 22),
        const SizedBox(width: 8),
        Text(l.warningTitle, style: TextStyle(color: c.textPrimary,
            fontSize: 17, fontWeight: FontWeight.bold)),
      ]),
      content: Column(mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(l.nsecWarning, style: TextStyle(
            color: c.textSecondary, fontSize: 13, height: 1.5)),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: const Color(0xFFFFB800).withValues(alpha: 0.10),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(
                color: const Color(0xFFFFB800).withValues(alpha: 0.40)),
          ),
          child: Text(l.amberSecureMethodHint,
              style: const TextStyle(color: Color(0xFFFFB800),
                  fontSize: 12, height: 1.4)),
        ),
        const SizedBox(height: 14),
        GestureDetector(
          onTap: () => setState(() => _ack = !_ack),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Checkbox(
              value: _ack,
              onChanged: (v) => setState(() => _ack = v ?? false),
              activeColor: c.accent,
              visualDensity: VisualDensity.compact,
            ),
            const SizedBox(width: 4),
            Expanded(child: Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Text(l.nsecRiskAcknowledgment,
                  style: TextStyle(color: c.textSecondary, fontSize: 12)),
            )),
          ]),
        ),
      ]),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l.cancel, style: TextStyle(color: c.textSecondary)),
        ),
        FilledButton(
          style: FilledButton.styleFrom(
              backgroundColor: _ack ? c.accent : c.border),
          onPressed: _ack
              ? () { Navigator.pop(context); widget.onConfirm(); }
              : null,
          child: Text(l.continueButton,
              style: const TextStyle(color: Colors.white)),
        ),
      ],
    );
  }
}

// ── Login tile ────────────────────────────────────────────────────────────────

class _LoginTile extends StatelessWidget {
  final IconData icon;
  final String title, subtitle;
  final RoadstrColors colors;
  final bool isWarning;
  final VoidCallback onTap;
  const _LoginTile({required this.icon, required this.title,
      required this.subtitle, required this.colors,
      required this.onTap, this.isWarning = false});

  @override
  Widget build(BuildContext context) {
    final iconColor   = isWarning ? const Color(0xFFFFB800) : colors.accent;
    final borderColor = isWarning
        ? const Color(0xFFFFB800).withValues(alpha: 0.4) : colors.border;
    return InkWell(
      onTap: onTap, borderRadius: BorderRadius.circular(14),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: colors.surface2, borderRadius: BorderRadius.circular(14),
          border: Border.all(color: borderColor, width: 0.5),
        ),
        child: Row(children: [
          Container(width: 44, height: 44,
              decoration: BoxDecoration(
                  color: iconColor.withValues(alpha: 0.1), shape: BoxShape.circle),
              child: Icon(icon, color: iconColor, size: 22)),
          const SizedBox(width: 14),
          Expanded(child: Column(
              crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: TextStyle(color: colors.textPrimary,
                fontSize: 14, fontWeight: FontWeight.w600)),
            const SizedBox(height: 2),
            Text(subtitle, style: TextStyle(
                color: colors.textSecondary, fontSize: 12)),
          ])),
          Icon(Icons.chevron_right, color: colors.textSecondary, size: 20),
        ]),
      ),
    );
  }
}
