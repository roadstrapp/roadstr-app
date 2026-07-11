// First-launch onboarding for Roadstr.
//
// Pages:
//   0 — Welcome: logo, features list
//   1 — Nostr identity: Amber NIP-55 or nsec (optional, skippable)
//   2 — Location permissions + GrapheneOS guide + Kokoro download
//   3 — Ready: navigate to map
//
// Swipe left/right to navigate between pages; the Next/Skip buttons advance
// programmatically. On completion sets Hive keys 'disclaimer_accepted' and
// 'onboarding_v1' to true. Existing users who already have
// 'disclaimer_accepted' skip this screen entirely (handled in main.dart).
import 'dart:async';
import 'package:amberflutter/amberflutter.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:geolocator/geolocator.dart';
import 'package:nostr_tools/nostr_tools.dart';
import 'package:url_launcher/url_launcher.dart';
import '../l10n/app_localizations.dart';
import '../services/kokoro/kokoro_model_manager.dart';
import '../services/kokoro/kokoro_voices.dart';
import '../services/nostr_relay_service.dart';
import '../theme/app_theme.dart';

class OnboardingScreen extends StatefulWidget {
  final VoidCallback onComplete;
  const OnboardingScreen({super.key, required this.onComplete});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final _pageCtrl = PageController();
  int _page = 0;

  // ── Nostr identity state ────────────────────────────────────────────────────
  static const _st = FlutterSecureStorage(
      aOptions: AndroidOptions(encryptedSharedPreferences: true));
  static const _kPriv    = 'nostr_priv_hex';
  static const _kPub     = 'nostr_pub_hex';
  static const _kFlavor  = 'nostr_flavor';
  static const _kPicture = 'nostr_picture';
  static const _kName    = 'nostr_name';

  String? _npub;
  String? _profileName;
  String? _profilePicture;
  bool _waitingAmber = false;
  bool _nsecError    = false;
  bool _fetchingProfile = false;

  // ── Location permission state ───────────────────────────────────────────────
  LocationPermission _locPerm = LocationPermission.denied;
  bool _locChecked = false;
  bool _locLoading = false;

  // ── Kokoro download state ───────────────────────────────────────────────────
  _KokoroStatus _kokoroStatus = _KokoroStatus.unknown;
  double _kokoroProgress = 0;
  StreamSubscription<double>? _progressSub;
  StreamSubscription<String>? _errorSub;

  @override
  void initState() {
    super.initState();
    _checkExistingLogin();
    _checkLocPermission();
    _checkKokoroStatus();
  }

  @override
  void dispose() {
    _progressSub?.cancel();
    _errorSub?.cancel();
    _pageCtrl.dispose();
    super.dispose();
  }

  // ── Login helpers ───────────────────────────────────────────────────────────

  Future<void> _checkExistingLogin() async {
    final pub  = await _st.read(key: _kPub);
    final name = await _st.read(key: _kName);
    final pic  = await _st.read(key: _kPicture);
    if (pub != null && pub.isNotEmpty && mounted) {
      setState(() {
        _npub           = Nip19().npubEncode(pub);
        _profileName    = name;
        _profilePicture = pic;
      });
      if (name == null || pic == null) _fetchAndStoreProfile(pub);
    }
  }

  Future<void> _fetchAndStoreProfile(String pubHex) async {
    if (_fetchingProfile) return;
    setState(() => _fetchingProfile = true);
    try {
      final profile = await NostrRelayService.fetchProfile(pubHex);
      if (profile == null || !mounted) return;
      final name = profile.label;
      if (name.isNotEmpty) {
        await _st.write(key: _kName, value: name);
      }
      if (profile.picture != null) {
        await _st.write(key: _kPicture, value: profile.picture!);
      }
      if (!mounted) return;
      setState(() {
        if (name.isNotEmpty)       _profileName    = name;
        if (profile.picture != null) _profilePicture = profile.picture;
      });
    } finally {
      if (mounted) setState(() => _fetchingProfile = false);
    }
  }

  Future<void> _loginWithAmber() async {
    setState(() { _waitingAmber = true; _nsecError = false; });
    try {
      final result = await Amberflutter().getPublicKey(
        permissions: [Permission(type: 'sign_event')],
      );
      final raw = result['signature'] as String? ?? '';
      if (raw.isEmpty) throw Exception('No key from Amber');
      final hex  = raw.startsWith('npub') ? Nip19().decode(raw)['data'] as String : raw;
      final npub = Nip19().npubEncode(hex);
      await _st.write(key: _kPub,    value: hex);
      await _st.write(key: _kFlavor, value: 'amber');
      if (mounted) setState(() { _npub = npub; _waitingAmber = false; });
      _fetchAndStoreProfile(hex);
    } catch (_) {
      if (mounted) setState(() => _waitingAmber = false);
    }
  }

  Future<void> _loginWithNsec(String nsec) async {
    setState(() => _nsecError = false);
    try {
      final decoded = Nip19().decode(nsec);
      if (decoded['type'] != 'nsec') throw const FormatException('not nsec');
      final privHex = decoded['data'] as String;
      final pubHex  = KeyApi().getPublicKey(privHex);
      final npub    = Nip19().npubEncode(pubHex);
      await _st.write(key: _kPriv,   value: privHex);
      await _st.write(key: _kPub,    value: pubHex);
      await _st.write(key: _kFlavor, value: 'nsec');
      if (mounted) setState(() => _npub = npub);
      _fetchAndStoreProfile(pubHex);
    } catch (_) {
      if (mounted) setState(() => _nsecError = true);
    }
  }

  void _showNsecDialog() {
    final ctrl = TextEditingController();
    final c    = RoadstrColors.of(context);
    final l    = AppLocalizations.of(context)!;
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: c.surface2,
        title: Row(children: [
          const Icon(Icons.warning_amber_rounded,
              color: Color(0xFFFFB800), size: 22),
          const SizedBox(width: 8),
          Expanded(child: Text(l.onboardingEnterNsec,
              style: TextStyle(color: c.textPrimary, fontSize: 17))),
        ]),
        content: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start, children: [
            // Same disclaimer shown in the profile screen's nsec dialog.
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
            AutofillGroup(
              child: TextField(
                controller: ctrl,
                autofillHints: const [AutofillHints.password],
                obscureText: true, autocorrect: false, enableSuggestions: false,
                keyboardType: TextInputType.visiblePassword,
                style: TextStyle(color: c.textPrimary, fontSize: 13),
                decoration: InputDecoration(
                  hintText: l.onboardingNsecHint,
                  hintStyle: TextStyle(color: c.textSecondary),
                  border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10)),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                    borderSide: BorderSide(color: c.accent),
                  ),
                ),
              ),
            ),
          ]),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(l.cancel,
                style: TextStyle(color: c.textSecondary)),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: c.accent),
            onPressed: () {
              Navigator.pop(ctx);
              _loginWithNsec(ctrl.text.trim());
            },
            child: Text(l.loginButton,
                style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  // ── Location helpers ────────────────────────────────────────────────────────

  Future<void> _checkLocPermission() async {
    final perm = await Geolocator.checkPermission();
    if (mounted) setState(() { _locPerm = perm; _locChecked = true; });
  }

  Future<void> _requestLocation() async {
    setState(() => _locLoading = true);
    final perm = await Geolocator.requestPermission();
    if (mounted) setState(() { _locPerm = perm; _locLoading = false; _locChecked = true; });
  }

  // ── Kokoro helpers ──────────────────────────────────────────────────────────

  Future<void> _checkKokoroStatus() async {
    final mgr = KokoroModelManager.instance;
    if (mgr.isDownloading) {
      setState(() { _kokoroStatus = _KokoroStatus.downloading; });
      _listenDownload();
      return;
    }
    final ready = await mgr.isReady(kokoroSupportedLanguages);
    if (mounted) setState(() => _kokoroStatus = ready ? _KokoroStatus.ready : _KokoroStatus.notDownloaded);
  }

  void _listenDownload() {
    final mgr = KokoroModelManager.instance;
    _progressSub?.cancel();
    _errorSub?.cancel();
    // NOTE: progressStream is a broadcast stream on a singleton that never
    // closes, so an onDone callback would never fire. Completion is signalled
    // by the final progress value of 1.0 (emitted after all files are on disk).
    _progressSub = mgr.progressStream.listen((p) async {
      if (!mounted) return;
      setState(() => _kokoroProgress = p);
      if (p >= 1.0) {
        final ok = await mgr.isReady(kokoroSupportedLanguages);
        if (mounted && ok) setState(() => _kokoroStatus = _KokoroStatus.ready);
      }
    });
    _errorSub = mgr.errorStream.listen((_) {
      if (mounted) setState(() => _kokoroStatus = _KokoroStatus.notDownloaded);
    });
  }

  void _startKokoroDownload() {
    final mgr = KokoroModelManager.instance;
    mgr.startDownload(kokoroSupportedLanguages);
    setState(() { _kokoroStatus = _KokoroStatus.downloading; _kokoroProgress = 0; });
    _listenDownload();
  }

  // ── Navigation ──────────────────────────────────────────────────────────────

  void _nextPage() {
    if (_page < 3) {
      _pageCtrl.animateToPage(_page + 1,
          duration: const Duration(milliseconds: 340),
          curve: Curves.easeInOut);
    }
  }

  // ── Build ───────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final c = RoadstrColors.of(context);
    final l = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: c.surface1,
      body: SafeArea(
        child: Column(children: [
          // Progress dots
          Padding(
            padding: const EdgeInsets.only(top: 20, bottom: 8),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(4, (i) => AnimatedContainer(
                duration: const Duration(milliseconds: 260),
                margin: const EdgeInsets.symmetric(horizontal: 4),
                width: _page == i ? 20 : 8,
                height: 8,
                decoration: BoxDecoration(
                  color: _page == i ? c.accent : c.border,
                  borderRadius: BorderRadius.circular(4),
                ),
              )),
            ),
          ),

          Expanded(
            child: PageView(
              controller: _pageCtrl,
              // Allow free swiping in both directions
              physics: const ClampingScrollPhysics(),
              onPageChanged: (p) => setState(() => _page = p),
              children: [
                _WelcomePage(c: c, l: l, onNext: _nextPage),
                _NostrPage(
                  c: c, l: l,
                  npub: _npub,
                  profileName: _profileName,
                  profilePicture: _profilePicture,
                  fetchingProfile: _fetchingProfile,
                  waitingAmber: _waitingAmber,
                  nsecError: _nsecError,
                  onAmber: _loginWithAmber,
                  onNsec: _showNsecDialog,
                  onNext: _nextPage,
                ),
                _PermissionsPage(
                  c: c, l: l,
                  locPerm: _locPerm,
                  locChecked: _locChecked,
                  locLoading: _locLoading,
                  kokoroStatus: _kokoroStatus,
                  kokoroProgress: _kokoroProgress,
                  onRequestLoc: _requestLocation,
                  onDownloadKokoro: _startKokoroDownload,
                  onNext: _nextPage,
                ),
                _ReadyPage(c: c, l: l, onStart: widget.onComplete),
              ],
            ),
          ),
        ]),
      ),
    );
  }
}

// ── Page 0: Welcome ───────────────────────────────────────────────────────────

class _WelcomePage extends StatelessWidget {
  final RoadstrColors c;
  final AppLocalizations l;
  final VoidCallback onNext;
  const _WelcomePage({required this.c, required this.l, required this.onNext});

  @override
  Widget build(BuildContext context) {
    final features = [
      (Icons.navigation_rounded,       l.onboardingFeatureNav),
      (Icons.campaign_rounded,         l.onboardingFeatureNostr),
      (Icons.bolt_rounded,             l.onboardingFeatureLightning),
      (Icons.record_voice_over_rounded,l.onboardingFeatureVoice),
      (Icons.lock_rounded,             l.onboardingFeaturePrivacy),
    ];
    return Padding(
      padding: const EdgeInsets.fromLTRB(28, 16, 28, 24),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          // App logo (same asset as the launcher icon).
          ClipRRect(
            borderRadius: BorderRadius.circular(16),
            child: Image.asset('assets/icons/app_icon.png',
                width: 56, height: 56, fit: BoxFit.cover,
                errorBuilder: (_, __, ___) => Container(
                  width: 56, height: 56,
                  decoration: BoxDecoration(
                    color: c.accent.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Icon(Icons.navigation_rounded,
                      color: c.accent, size: 28),
                )),
          ),
          const SizedBox(width: 14),
          Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('Roadstr', style: TextStyle(
                color: c.accent, fontSize: 26, fontWeight: FontWeight.w800)),
            Text(l.onboardingAppSubtitle,
                style: TextStyle(color: c.textSecondary, fontSize: 13)),
          ]),
        ]),

        const SizedBox(height: 24),
        Text(l.onboardingWelcomeTitle,
            style: TextStyle(color: c.textPrimary, fontSize: 22,
                fontWeight: FontWeight.w700)),
        const SizedBox(height: 6),
        Text(l.onboardingWelcomeBody,
            style: TextStyle(color: c.textSecondary, fontSize: 14, height: 1.5)),

        const SizedBox(height: 20),
        // Features + VPN notice scroll together so small screens never overflow.
        Expanded(child: SingleChildScrollView(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            ...features.map((f) => Padding(
              padding: const EdgeInsets.only(bottom: 14),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Container(
                  width: 36, height: 36,
                  decoration: BoxDecoration(
                    color: c.accent.withValues(alpha: 0.10),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(f.$1, color: c.accent, size: 18),
                ),
                const SizedBox(width: 12),
                Expanded(child: Text(f.$2,
                    style: TextStyle(color: c.textPrimary, fontSize: 14, height: 1.4))),
              ]),
            )),

            const SizedBox(height: 6),
            // ── VPN privacy notice ─────────────────────────────────────────
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: c.surface2,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: c.border, width: 0.5),
              ),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Icon(Icons.vpn_lock_rounded, color: c.accent, size: 18),
                const SizedBox(width: 10),
                Expanded(child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(l.onboardingVpnNotice,
                      style: TextStyle(color: c.textSecondary,
                          fontSize: 12, height: 1.5)),
                  const SizedBox(height: 6),
                  GestureDetector(
                    onTap: () => launchUrl(Uri.parse('https://mullvad.net'),
                        mode: LaunchMode.externalApplication),
                    child: Text('mullvad.net',
                        style: TextStyle(color: c.accent, fontSize: 12,
                            fontWeight: FontWeight.w700,
                            decoration: TextDecoration.underline,
                            decorationColor: c.accent)),
                  ),
                ])),
              ]),
            ),
          ]),
        )),

        const SizedBox(height: 12),
        _NextButton(label: l.onboardingGetStarted, onTap: onNext, c: c),
      ]),
    );
  }
}

// ── Page 1: Nostr identity ────────────────────────────────────────────────────

class _NostrPage extends StatelessWidget {
  final RoadstrColors c;
  final AppLocalizations l;
  final String? npub;
  final String? profileName;
  final String? profilePicture;
  final bool fetchingProfile;
  final bool waitingAmber;
  final bool nsecError;
  final VoidCallback onAmber;
  final VoidCallback onNsec;
  final VoidCallback onNext;
  const _NostrPage({
    required this.c, required this.l,
    this.npub, this.profileName, this.profilePicture,
    required this.fetchingProfile, required this.waitingAmber,
    required this.nsecError, required this.onAmber,
    required this.onNsec, required this.onNext,
  });

  @override
  Widget build(BuildContext context) {
    final loggedIn = npub != null;
    return Padding(
      padding: const EdgeInsets.fromLTRB(28, 16, 28, 24),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(
          width: 52, height: 52,
          decoration: BoxDecoration(
            color: c.accent.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Icon(Icons.key_rounded, color: c.accent, size: 26),
        ),
        const SizedBox(height: 20),
        Text(l.onboardingNostrTitle,
            style: TextStyle(color: c.textPrimary, fontSize: 22,
                fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        Text(l.onboardingNostrSubtitle,
            style: TextStyle(color: c.textSecondary, fontSize: 14, height: 1.5)),

        const SizedBox(height: 28),

        if (loggedIn) ...[
          // Profile card
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.green.shade700.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                  color: Colors.green.shade600.withValues(alpha: 0.5),
                  width: 0.8),
            ),
            child: Row(children: [
              // Avatar
              _ProfileAvatar(
                  picture: profilePicture,
                  name: profileName,
                  fetching: fetchingProfile,
                  c: c),
              const SizedBox(width: 12),
              Expanded(child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Icon(Icons.check_circle_rounded,
                      color: Colors.green.shade600, size: 16),
                  const SizedBox(width: 4),
                  Text(l.onboardingNostrConnected,
                      style: TextStyle(color: Colors.green.shade600,
                          fontSize: 12, fontWeight: FontWeight.w700)),
                ]),
                const SizedBox(height: 4),
                if (profileName != null && profileName!.isNotEmpty)
                  Text(profileName!,
                      style: TextStyle(color: c.textPrimary,
                          fontSize: 14, fontWeight: FontWeight.w600)),
                Text(npub!, maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: TextStyle(color: c.textSecondary, fontSize: 11)),
              ])),
            ]),
          ),
          const SizedBox(height: 20),
        ] else ...[
          _LoginOption(
            c: c,
            icon: Icons.security_rounded,
            title: l.onboardingAmberTitle,
            subtitle: l.onboardingAmberSubtitle,
            accent: true,
            loading: waitingAmber,
            onTap: onAmber,
          ),
          const SizedBox(height: 12),
          _LoginOption(
            c: c,
            icon: Icons.vpn_key_rounded,
            title: l.onboardingNsecTitle,
            subtitle: l.onboardingNsecSubtitle,
            accent: false,
            onTap: onNsec,
          ),
          if (nsecError) ...[
            const SizedBox(height: 8),
            Text(l.onboardingNsecError,
                style: TextStyle(color: Colors.red.shade400, fontSize: 12)),
          ],
        ],

        const Spacer(),
        _NextButton(
          label: loggedIn ? l.onboardingContinue : l.onboardingSkip,
          onTap: onNext, c: c,
        ),
      ]),
    );
  }
}

// ── Profile avatar ────────────────────────────────────────────────────────────

class _ProfileAvatar extends StatelessWidget {
  final String? picture;
  final String? name;
  final bool fetching;
  final RoadstrColors c;
  const _ProfileAvatar(
      {this.picture, this.name, required this.fetching, required this.c});

  @override
  Widget build(BuildContext context) {
    const size = 44.0;
    if (fetching && picture == null) {
      return Container(
        width: size, height: size,
        decoration: BoxDecoration(
            color: c.surface3, borderRadius: BorderRadius.circular(size / 2)),
        child: SizedBox(
          width: 20, height: 20,
          child: CircularProgressIndicator(strokeWidth: 2, color: c.accent),
        ),
      );
    }
    if (picture != null && picture!.isNotEmpty) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(size / 2),
        child: Image.network(
          picture!,
          width: size, height: size,
          fit: BoxFit.cover,
          errorBuilder: (_, __, ___) => _fallbackAvatar(size),
        ),
      );
    }
    return _fallbackAvatar(size);
  }

  Widget _fallbackAvatar(double size) {
    final letter = (name?.isNotEmpty == true) ? name![0].toUpperCase() : '?';
    return Container(
      width: size, height: size,
      decoration: BoxDecoration(
        color: c.accent.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(size / 2),
      ),
      child: Center(
        child: Text(letter,
            style: TextStyle(color: c.accent, fontSize: 20,
                fontWeight: FontWeight.w700)),
      ),
    );
  }
}

class _LoginOption extends StatelessWidget {
  final RoadstrColors c;
  final IconData icon;
  final String title;
  final String subtitle;
  final bool accent;
  final bool loading;
  final VoidCallback onTap;
  const _LoginOption({
    required this.c, required this.icon, required this.title,
    required this.subtitle, required this.accent, this.loading = false,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: loading ? null : onTap,
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: accent ? c.accent.withValues(alpha: 0.08) : c.surface2,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(
            color: accent ? c.accent.withValues(alpha: 0.4) : c.border,
            width: accent ? 1.5 : 0.5,
          ),
        ),
        child: Row(children: [
          Icon(icon, color: accent ? c.accent : c.textSecondary, size: 24),
          const SizedBox(width: 14),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
              children: [
            Text(title,
                style: TextStyle(
                    color: accent ? c.accent : c.textPrimary,
                    fontSize: 14, fontWeight: FontWeight.w600)),
            const SizedBox(height: 3),
            Text(subtitle,
                style: TextStyle(color: c.textSecondary,
                    fontSize: 12, height: 1.4)),
          ])),
          if (loading)
            const SizedBox(width: 20, height: 20,
                child: CircularProgressIndicator(strokeWidth: 2))
          else
            Icon(Icons.chevron_right_rounded, color: c.textSecondary, size: 20),
        ]),
      ),
    );
  }
}

// ── Page 2: Permissions + Kokoro ─────────────────────────────────────────────

class _PermissionsPage extends StatelessWidget {
  final RoadstrColors c;
  final AppLocalizations l;
  final LocationPermission locPerm;
  final bool locChecked;
  final bool locLoading;
  final _KokoroStatus kokoroStatus;
  final double kokoroProgress;
  final VoidCallback onRequestLoc;
  final VoidCallback onDownloadKokoro;
  final VoidCallback onNext;
  const _PermissionsPage({
    required this.c, required this.l,
    required this.locPerm, required this.locChecked,
    required this.locLoading, required this.kokoroStatus,
    required this.kokoroProgress, required this.onRequestLoc,
    required this.onDownloadKokoro, required this.onNext,
  });

  bool get _locGranted =>
      locPerm == LocationPermission.whileInUse ||
      locPerm == LocationPermission.always;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(28, 16, 28, 24),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(
          width: 52, height: 52,
          decoration: BoxDecoration(
            color: c.accent.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Icon(Icons.tune_rounded, color: c.accent, size: 26),
        ),
        const SizedBox(height: 20),
        Text(l.onboardingSetupTitle,
            style: TextStyle(color: c.textPrimary, fontSize: 22,
                fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        Text(l.onboardingSetupSubtitle,
            style: TextStyle(color: c.textSecondary, fontSize: 14, height: 1.5)),

        const SizedBox(height: 24),

        // ── Location ──────────────────────────────────────────────────────────
        _PermSection(
          c: c,
          icon: Icons.location_on_rounded,
          title: l.onboardingLocationTitle,
          subtitle: _locGranted
              ? l.onboardingLocationGranted
              : l.onboardingLocationRequired,
          granted: _locGranted,
          trailing: _locGranted
              ? Icon(Icons.check_circle_rounded,
                  color: Colors.green.shade600, size: 22)
              : locLoading
                  ? const SizedBox(width: 20, height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2))
                  : FilledButton(
                      style: FilledButton.styleFrom(
                        backgroundColor: c.accent,
                        padding: const EdgeInsets.symmetric(
                            horizontal: 14, vertical: 8),
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10)),
                      ),
                      onPressed: onRequestLoc,
                      child: Text(l.onboardingGrantButton,
                          style: const TextStyle(fontSize: 13)),
                    ),
        ),

        // GrapheneOS note
        Container(
          margin: const EdgeInsets.only(top: 10, bottom: 4),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: c.surface2,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: c.border, width: 0.5),
          ),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(Icons.info_outline_rounded, color: c.accent, size: 18),
            const SizedBox(width: 10),
            Expanded(child: Column(
                crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(l.onboardingGrapheneTitle,
                  style: TextStyle(color: c.textPrimary,
                      fontSize: 13, fontWeight: FontWeight.w600)),
              const SizedBox(height: 4),
              Text(l.onboardingGrapheneBody,
                  style: TextStyle(color: c.textSecondary,
                      fontSize: 12, height: 1.5)),
              const SizedBox(height: 6),
              Text(l.onboardingGrapheneAlwaysAllow,
                  style: TextStyle(color: c.textSecondary,
                      fontSize: 12, height: 1.5,
                      fontWeight: FontWeight.w600)),
            ])),
          ]),
        ),

        const SizedBox(height: 16),

        // ── Kokoro AI voice ───────────────────────────────────────────────────
        _PermSection(
          c: c,
          icon: Icons.record_voice_over_rounded,
          title: l.onboardingVoiceTitle,
          subtitle: switch (kokoroStatus) {
            _KokoroStatus.ready         => l.onboardingVoiceReady,
            _KokoroStatus.downloading   => l.onboardingVoiceDownloading,
            _KokoroStatus.notDownloaded => l.onboardingVoiceNotDownloaded,
            _KokoroStatus.unknown       => l.onboardingVoiceChecking,
          },
          granted: kokoroStatus == _KokoroStatus.ready,
          trailing: switch (kokoroStatus) {
            // White checkmark on a solid green circle — unambiguous "done".
            _KokoroStatus.ready => Container(
              width: 28, height: 28,
              decoration: BoxDecoration(
                  color: Colors.green.shade600, shape: BoxShape.circle),
              child: const Icon(Icons.check_rounded,
                  color: Colors.white, size: 20),
            ),
            _KokoroStatus.downloading => SizedBox(
              width: 120,
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                LinearProgressIndicator(
                  value: kokoroProgress,
                  backgroundColor: c.border,
                  color: c.accent,
                  borderRadius: BorderRadius.circular(2),
                ),
                const SizedBox(height: 4),
                Text('${(kokoroProgress * 100).round()}%',
                    style: TextStyle(color: c.textSecondary, fontSize: 11)),
              ]),
            ),
            _KokoroStatus.notDownloaded => FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: c.surface3,
                foregroundColor: c.textPrimary,
                padding: const EdgeInsets.symmetric(
                    horizontal: 12, vertical: 8),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10)),
              ),
              onPressed: onDownloadKokoro,
              child: Text(l.onboardingDownloadButton,
                  style: const TextStyle(fontSize: 13)),
            ),
            _ => const SizedBox.shrink(),
          },
        ),

        const SizedBox(height: 8),
        Text(l.onboardingVoiceLaterHint,
            style: TextStyle(color: c.textSecondary,
                fontSize: 12, height: 1.4)),

        const SizedBox(height: 28),
        _NextButton(label: l.onboardingContinue, onTap: onNext, c: c),
      ]),
    );
  }
}

class _PermSection extends StatelessWidget {
  final RoadstrColors c;
  final IconData icon;
  final String title;
  final String subtitle;
  final bool granted;
  final Widget trailing;
  const _PermSection({
    required this.c, required this.icon, required this.title,
    required this.subtitle, required this.granted, required this.trailing,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: granted
            ? Colors.green.shade700.withValues(alpha: 0.08)
            : c.surface2,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: granted
              ? Colors.green.shade600.withValues(alpha: 0.4)
              : c.border,
          width: granted ? 1.2 : 0.5,
        ),
      ),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(icon,
            color: granted ? Colors.green.shade600 : c.accent, size: 22),
        const SizedBox(width: 12),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start,
            children: [
          Text(title, style: TextStyle(
              color: c.textPrimary, fontSize: 14, fontWeight: FontWeight.w600)),
          const SizedBox(height: 4),
          Text(subtitle, style: TextStyle(
              color: c.textSecondary, fontSize: 12, height: 1.4)),
        ])),
        const SizedBox(width: 12),
        trailing,
      ]),
    );
  }
}

// ── Page 3: Ready ─────────────────────────────────────────────────────────────

class _ReadyPage extends StatelessWidget {
  final RoadstrColors c;
  final AppLocalizations l;
  final VoidCallback onStart;
  const _ReadyPage(
      {required this.c, required this.l, required this.onStart});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(28, 16, 28, 24),
      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        Container(
          width: 88, height: 88,
          decoration: BoxDecoration(
            color: Colors.green.shade600.withValues(alpha: 0.12),
            shape: BoxShape.circle,
          ),
          child: Icon(Icons.check_rounded,
              color: Colors.green.shade600, size: 48),
        ),
        const SizedBox(height: 28),
        Text(l.onboardingReadyTitle,
            textAlign: TextAlign.center,
            style: TextStyle(color: c.textPrimary, fontSize: 24,
                fontWeight: FontWeight.w700)),
        const SizedBox(height: 12),
        Text(l.onboardingReadyBody,
            textAlign: TextAlign.center,
            style: TextStyle(color: c.textSecondary, fontSize: 14, height: 1.6)),
        const SizedBox(height: 48),
        SizedBox(
          width: double.infinity,
          child: FilledButton.icon(
            style: FilledButton.styleFrom(
              backgroundColor: c.accent,
              padding: const EdgeInsets.symmetric(vertical: 16),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(14)),
            ),
            onPressed: onStart,
            icon: const Icon(Icons.navigation_rounded,
                color: Colors.white, size: 20),
            label: Text(l.onboardingLetsGo,
                style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700,
                    color: Colors.white)),
          ),
        ),
      ]),
    );
  }
}

// ── Shared: Next button ───────────────────────────────────────────────────────

class _NextButton extends StatelessWidget {
  final String label;
  final VoidCallback onTap;
  final RoadstrColors c;
  const _NextButton(
      {required this.label, required this.onTap, required this.c});

  @override
  Widget build(BuildContext context) => SizedBox(
    width: double.infinity,
    child: FilledButton(
      style: FilledButton.styleFrom(
        backgroundColor: c.accent,
        padding: const EdgeInsets.symmetric(vertical: 16),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
      onPressed: onTap,
      child: Text(label,
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700,
              color: Colors.white)),
    ),
  );
}

// ── Kokoro status enum ────────────────────────────────────────────────────────

enum _KokoroStatus { unknown, notDownloaded, downloading, ready }
