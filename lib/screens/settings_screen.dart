import 'dart:async';
import 'dart:convert';
import 'dart:isolate';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:latlong2/latlong.dart';
import 'package:provider/provider.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../l10n/app_localizations.dart';
import '../models/favorite_place.dart';
import '../providers/locale_provider.dart';
import '../theme/app_theme.dart';
import '../theme/theme_provider.dart';
import '../services/routing_service.dart';
import '../services/favorites_crypto.dart';
import '../services/favorites_sync_service.dart';
import '../services/kokoro/kokoro_model_manager.dart';
import '../services/kokoro/kokoro_tts_service.dart';
import '../services/kokoro/kokoro_voices.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});
  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

enum _KokoroStatus { unknown, notDownloaded, downloading, ready }

class _SettingsScreenState extends State<SettingsScreen> {
  static const _maxFavoritesImportBytes = 5 * 1024 * 1024;
  late final Box _box;
  List<FavoritePlace> _favorites = [];
  String _appVersion = '—';

  // ── Nostr identity (for sync) ─────────────────────────────────────────────
  String? _nostrPub;
  String? _nostrPriv; // null when signing via Amber
  String? _syncPassphrase;
  bool _syncBusy = false;
  final _syncSvc = FavoritesSyncService();

  _KokoroStatus _kokoroStatus = _KokoroStatus.unknown;
  double _downloadProgress = 0;
  String _kokoroGender = kKokoroDefaultGender;
  int _kokoroSpeedStage = kKokoroDefaultSpeedStage;
  bool _previewingVoice = false;
  final _previewTts = KokoroTtsService();

  StreamSubscription<double>? _progressSub;
  StreamSubscription<String>? _errorSub;

  static const _st = FlutterSecureStorage();
  final _apiKeyCtrl = TextEditingController();
  final _nwcCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    _box = Hive.box('settings');
    _loadFavorites();
    _loadSecrets();
    _loadNostrIdentity();
    _loadAppVersion();
    _kokoroGender = _box.get('kokoroVoiceGender',
        defaultValue: kKokoroDefaultGender) as String;
    _kokoroSpeedStage = _box.get('kokoroSpeedStage',
        defaultValue: kKokoroDefaultSpeedStage) as int;

    final mgr = KokoroModelManager.instance;
    if (mgr.isDownloading) {
      // Download già in corso — mostra la progress bar, non controllare isReady()
      // perché restituirebbe notDownloaded e sovrascriverebbe questo stato.
      _kokoroStatus = _KokoroStatus.downloading;
      _downloadProgress = mgr.lastProgress;
    } else {
      _checkKokoroStatus();
    }

    _progressSub = mgr.progressStream.listen((p) {
      if (!mounted) return;
      setState(() {
        _downloadProgress = p;
        if (p >= 1.0) _kokoroStatus = _KokoroStatus.ready;
      });
      if (p >= 1.0) {
        final lang = _box.get('language', defaultValue: '') as String;
        unawaited(KokoroTtsService.warmUpLanguage(lang.isNotEmpty ? lang : 'it',
            gender: _kokoroGender));
      }
    });

    _errorSub = mgr.errorStream.listen((err) {
      if (!mounted) return;
      setState(() => _kokoroStatus = _KokoroStatus.notDownloaded);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
            content: Text('${AppLocalizations.of(context).errorTitle}: $err')),
      );
    });
  }

  Future<void> _loadSecrets() async {
    // Read from SecureStorage; migrate from Hive if this is the first run after
    // the security upgrade (Hive values are deleted after migration).
    var apiKey = await _st.read(key: 'routing_api_key');
    if (apiKey == null) {
      final legacy = _box.get('graphhopperApiKey', defaultValue: '') as String;
      if (legacy.isNotEmpty) {
        await _st.write(key: 'routing_api_key', value: legacy);
        await _box.delete('graphhopperApiKey');
        apiKey = legacy;
      }
    }

    var nwcUri = await _st.read(key: 'nwc_uri');
    if (nwcUri == null) {
      final legacy = _box.get('nwcUri', defaultValue: '') as String;
      if (legacy.isNotEmpty) {
        await _st.write(key: 'nwc_uri', value: legacy);
        await _box.delete('nwcUri');
        nwcUri = legacy;
      }
    }

    _syncPassphrase = await _st.read(key: 'favorites_sync_passphrase');
    final legacyPassphrase = _box.get('fav_sync_pass') as String?;
    if (_syncPassphrase == null && legacyPassphrase?.isNotEmpty == true) {
      await _st.write(
          key: 'favorites_sync_passphrase', value: legacyPassphrase);
      await _box.delete('fav_sync_pass');
      _syncPassphrase = legacyPassphrase;
    }

    if (!mounted) return;
    _apiKeyCtrl.text = apiKey ?? '';
    _nwcCtrl.text = nwcUri ?? '';
    setState(() {});
  }

  Future<void> _loadAppVersion() async {
    final info = await PackageInfo.fromPlatform();
    if (mounted) setState(() => _appVersion = info.version);
  }

  @override
  void dispose() {
    _progressSub?.cancel();
    _errorSub?.cancel();
    _apiKeyCtrl.dispose();
    _nwcCtrl.dispose();
    unawaited(_previewTts.dispose());
    super.dispose();
  }

  // ── Kokoro voice gender / speed ──────────────────────────────────────────

  void _setGender(String gender) {
    setState(() => _kokoroGender = gender);
    _box.put('kokoroVoiceGender', gender);
    unawaited(_playPreview());
  }

  void _setSpeedStage(int stage) {
    setState(() => _kokoroSpeedStage = stage);
    _box.put('kokoroSpeedStage', stage);
    unawaited(_playPreview());
  }

  /// Speaks a fixed sample instruction with the currently selected
  /// gender/speed so the user can judge the choice immediately. Falls back
  /// to English when the app's current language has no Kokoro voice, so the
  /// preview always works regardless of the active UI language.
  Future<void> _playPreview() async {
    if (_kokoroStatus != _KokoroStatus.ready || _previewingVoice || !mounted) {
      return;
    }
    final uiLang = Localizations.localeOf(context).languageCode;
    final lang = kokoroSupportedLanguages.contains(uiLang) ? uiLang : 'en';
    setState(() => _previewingVoice = true);
    try {
      _previewTts.setLanguage(lang);
      _previewTts.setGender(_kokoroGender);
      _previewTts.setSpeed(kKokoroSpeedStages[_kokoroSpeedStage]);
      await _previewTts.init(lang);
      await _previewTts.previewVoice();
    } finally {
      if (mounted) setState(() => _previewingVoice = false);
    }
  }

  Future<void> _checkKokoroStatus() async {
    final ready =
        await KokoroModelManager.instance.isReady(kokoroSupportedLanguages);
    if (mounted) {
      setState(() => _kokoroStatus =
          ready ? _KokoroStatus.ready : _KokoroStatus.notDownloaded);
    }
  }

  void _downloadKokoroModel() {
    setState(() {
      _kokoroStatus = _KokoroStatus.downloading;
      _downloadProgress = 0;
    });
    // startDownload is fire-and-forget; progress arrives via progressStream.
    KokoroModelManager.instance.startDownload(kokoroSupportedLanguages);
  }

  void _loadFavorites() {
    final raw =
        _box.get('favorites', defaultValue: <dynamic>[]) as List<dynamic>;
    _favorites = raw
        .whereType<String>()
        .map((s) {
          try {
            return FavoritePlace.fromMapSafe(jsonDecode(s) as Map);
          } catch (_) {
            return null;
          }
        })
        .whereType<FavoritePlace>()
        .toList();
  }

  void _saveFavorites() {
    _box.put(
        'favorites', _favorites.map((f) => jsonEncode(f.toMap())).toList());
  }

  void _deleteFavorite(int idx) {
    setState(() => _favorites.removeAt(idx));
    _saveFavorites();
  }

  // ── Nostr identity ───────────────────────────────────────────────────────

  Future<void> _loadNostrIdentity() async {
    final pub = await _st.read(key: 'nostr_pub_hex');
    final priv = await _st.read(key: 'nostr_priv_hex');
    if (mounted) {
      setState(() {
        _nostrPub = pub;
        _nostrPriv = priv;
      });
    }
  }

  // ── Export / Import (local JSON file, optional password) ────────────────

  Future<void> _exportFavorites() async {
    final l = AppLocalizations.of(context);
    final c = RoadstrColors.of(context);
    if (_favorites.isEmpty) return;
    final ctrl = TextEditingController();
    var encryptIt = false;
    final go = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
          builder: (ctx, setDlg) => AlertDialog(
                backgroundColor: c.surface2,
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(20)),
                title: Text(l.exportFavoritesTitle,
                    style: TextStyle(
                        color: c.textPrimary, fontWeight: FontWeight.w700)),
                content: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(l.exportFavoritesDesc,
                          style: TextStyle(
                              color: c.textSecondary,
                              fontSize: 13,
                              height: 1.4)),
                      const SizedBox(height: 14),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        dense: true,
                        value: encryptIt,
                        onChanged: (v) => setDlg(() => encryptIt = v),
                        title: Text(l.exportEncryptToggle,
                            style:
                                TextStyle(color: c.textPrimary, fontSize: 13)),
                        activeThumbColor: c.accent,
                      ),
                      if (encryptIt) ...[
                        const SizedBox(height: 4),
                        TextField(
                          controller: ctrl,
                          obscureText: true,
                          autocorrect: false,
                          style: TextStyle(color: c.textPrimary, fontSize: 14),
                          decoration: InputDecoration(
                            hintText: l.exportPasswordHint,
                            hintStyle: TextStyle(color: c.textSecondary),
                            filled: true,
                            fillColor: c.surface1,
                            border: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: BorderSide.none),
                            contentPadding: const EdgeInsets.symmetric(
                                horizontal: 14, vertical: 12),
                          ),
                        ),
                      ],
                    ]),
                actions: [
                  TextButton(
                      onPressed: () => Navigator.pop(ctx, false),
                      child: Text(l.cancel,
                          style: TextStyle(color: c.textSecondary))),
                  FilledButton(
                    style: FilledButton.styleFrom(backgroundColor: c.accent),
                    onPressed: (!encryptIt || ctrl.text.isNotEmpty)
                        ? () => Navigator.pop(ctx, true)
                        : null,
                    child: Text(l.exportButton,
                        style: const TextStyle(color: Colors.white)),
                  ),
                ],
              )),
    );
    if (go != true || !mounted) return;

    final plaintext = jsonEncode(_favorites.map((f) => f.toMap()).toList());
    final Map<String, dynamic> envelope;
    if (encryptIt && ctrl.text.isNotEmpty) {
      envelope = {
        'v': 1,
        'encrypted': true,
        ...FavoritesCrypto.encrypt(plaintext, ctrl.text)
      };
    } else {
      envelope = {'v': 1, 'encrypted': false, 'data': plaintext};
    }
    try {
      final bytes = Uint8List.fromList(utf8.encode(jsonEncode(envelope)));
      final path = await FilePicker.saveFile(
        fileName: 'roadstr_favorites.json',
        type: FileType.custom,
        allowedExtensions: ['json'],
        bytes: bytes,
      );
      if (!mounted) return;
      if (path != null) _snackSettings(l.exportSuccessSnack);
    } catch (_) {
      if (mounted) _snackSettings(l.exportFailedSnack);
    }
  }

  Future<void> _importFavorites() async {
    final l = AppLocalizations.of(context);
    try {
      final file = await FilePicker.pickFile(
          type: FileType.custom, allowedExtensions: ['json']);
      if (file == null) return;
      if (file.size > _maxFavoritesImportBytes) {
        throw const FormatException('Favorites import is too large');
      }
      final bytes = await file.readAsBytes();
      if (bytes.length > _maxFavoritesImportBytes) {
        throw const FormatException('Favorites import is too large');
      }
      final envelope = await Isolate.run(
          () => jsonDecode(utf8.decode(bytes)) as Map<String, dynamic>);
      String plaintext;
      if (envelope['encrypted'] == true) {
        if (!mounted) return;
        final password = await _promptPassword(l.importPasswordPrompt);
        if (password == null || password.isEmpty) return;
        plaintext = await Isolate.run(
            () => FavoritesCrypto.decrypt(envelope, password));
      } else {
        plaintext = envelope['data'] as String;
      }
      final list = await Isolate.run(() => jsonDecode(plaintext) as List);
      final imported = list
          .whereType<Map>()
          .map((m) => FavoritePlace.fromMapSafe(m))
          .whereType<FavoritePlace>()
          .toList();
      if (!mounted) return;
      setState(() {
        for (final f in imported) {
          final dup = _favorites.indexWhere((e) => e.label == f.label);
          if (dup >= 0) {
            _favorites[dup] = f;
          } else {
            _favorites.add(f);
          }
        }
      });
      _saveFavorites();
      _snackSettings(l.importSuccessSnack(imported.length));
    } catch (_) {
      if (mounted) _snackSettings(l.importFailedSnack);
    }
  }

  Future<String?> _promptPassword(String title) {
    final ctrl = TextEditingController();
    final c = RoadstrColors.of(context);
    return showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: c.surface2,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title:
            Text(title, style: TextStyle(color: c.textPrimary, fontSize: 15)),
        content: TextField(
          controller: ctrl,
          obscureText: true,
          autocorrect: false,
          autofocus: true,
          style: TextStyle(color: c.textPrimary, fontSize: 14),
          decoration: InputDecoration(
            filled: true,
            fillColor: c.surface1,
            border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
                borderSide: BorderSide.none),
            contentPadding:
                const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          ),
          onSubmitted: (v) => Navigator.pop(ctx, v),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, null),
              child: Text(AppLocalizations.of(ctx).cancel,
                  style: TextStyle(color: c.textSecondary))),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: c.accent),
            onPressed: () => Navigator.pop(ctx, ctrl.text),
            child: Text(AppLocalizations.of(ctx).ok,
                style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  // ── Nostr sync (NIP-44, kind 30078) ──────────────────────────────────────

  Future<void> _pushSync() async {
    final l = AppLocalizations.of(context);
    if (_nostrPub == null || _favorites.isEmpty || _syncBusy) return;
    setState(() => _syncBusy = true);
    final ok = await _syncSvc.push(
        favorites: _favorites,
        pubKeyHex: _nostrPub!,
        privKeyHex: _nostrPriv,
        passphrase: _syncPassphrase);
    if (!mounted) return;
    setState(() => _syncBusy = false);
    if (ok) {
      _box.put('favoritesSyncLastAt', DateTime.now().millisecondsSinceEpoch);
      setState(() {});
    }
    _snackSettings(ok ? l.syncSuccessSnack : l.syncFailedSnack);
  }

  Future<void> _pullSync() async {
    final l = AppLocalizations.of(context);
    if (_nostrPub == null || _syncBusy) return;
    setState(() => _syncBusy = true);
    var result = await _syncSvc.pull(
        pubKeyHex: _nostrPub!,
        privKeyHex: _nostrPriv,
        passphrase: _syncPassphrase);
    if (!mounted) return;
    if (result.needsPassphrase) {
      // Snapshot sealed with a sync passphrase this device doesn't have
      // (new device, or the passphrase changed elsewhere): prompt and retry.
      final entered = await _promptPassword(l.importPasswordPrompt);
      if (!mounted) return;
      if (entered != null && entered.isNotEmpty) {
        result = await _syncSvc.pull(
            pubKeyHex: _nostrPub!, privKeyHex: _nostrPriv, passphrase: entered);
        if (!mounted) return;
        // Remember the passphrase that worked so future syncs are seamless.
        if (result.favorites != null) {
          await _st.write(key: 'favorites_sync_passphrase', value: entered);
          _syncPassphrase = entered;
        }
      }
    }
    setState(() => _syncBusy = false);
    final fetched = result.favorites;
    if (fetched == null) {
      _snackSettings(l.syncFailedSnack);
      return;
    }
    setState(() {
      for (final f in fetched) {
        final dup = _favorites.indexWhere((e) => e.label == f.label);
        if (dup >= 0) {
          _favorites[dup] = f;
        } else {
          _favorites.add(f);
        }
      }
    });
    _saveFavorites();
    _box.put('favoritesSyncLastAt', DateTime.now().millisecondsSinceEpoch);
    _snackSettings(l.syncSuccessSnack);
  }

  Future<void> _editSyncPassphrase() async {
    final l = AppLocalizations.of(context);
    final c = RoadstrColors.of(context);
    final ctrl = TextEditingController();
    final saved = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: c.surface2,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(l.syncPassphraseTitle,
            style: TextStyle(
                color: c.textPrimary,
                fontSize: 15,
                fontWeight: FontWeight.w700)),
        content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(l.syncPassphraseDesc,
                  style: TextStyle(
                      color: c.textSecondary, fontSize: 12.5, height: 1.5)),
              const SizedBox(height: 12),
              TextField(
                controller: ctrl,
                obscureText: true,
                autocorrect: false,
                autofocus: true,
                style: TextStyle(color: c.textPrimary, fontSize: 14),
                decoration: InputDecoration(
                  hintText: l.exportPasswordHint,
                  hintStyle: TextStyle(color: c.textSecondary),
                  filled: true,
                  fillColor: c.surface1,
                  border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(12),
                      borderSide: BorderSide.none),
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                ),
              ),
            ]),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: Text(l.cancel, style: TextStyle(color: c.textSecondary))),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: c.accent),
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(l.ok, style: const TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
    if (saved != true || !mounted) return;
    // Empty input = disable the extra layer; next push publishes unwrapped.
    if (ctrl.text.isEmpty) {
      await _st.delete(key: 'favorites_sync_passphrase');
      _syncPassphrase = null;
    } else {
      await _st.write(key: 'favorites_sync_passphrase', value: ctrl.text);
      _syncPassphrase = ctrl.text;
    }
    setState(() {});
  }

  void _snackSettings(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(msg, style: const TextStyle(color: Colors.white)),
      behavior: SnackBarBehavior.floating,
      backgroundColor: const Color(0xFF1A1A2E),
    ));
  }

  Future<void> _editFavoriteDialog(AppLocalizations l, RoadstrColors c,
      {FavoritePlace? existing, int? existingIdx}) async {
    final labelCtrl = TextEditingController(text: existing?.label ?? '');
    final addressCtrl = TextEditingController(text: existing?.address ?? '');
    final isEdit = existing != null;

    // Dialog-local state (suggestions, picked position, error, loading)
    List<NominatimResult> suggestions = [];
    LatLng? pickedPosition = existing?.position;
    String? error;
    bool searching = false;
    Timer? debounce;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDlg) {
          void onAddressChanged(String v) {
            debounce?.cancel();
            pickedPosition = null; // reset picked position on manual edit
            if (v.trim().length < 3) {
              setDlg(() => suggestions = []);
              return;
            }
            debounce = Timer(const Duration(milliseconds: 450), () async {
              setDlg(() => searching = true);
              final res = await RoutingService.search(v.trim());
              if (ctx.mounted) {
                setDlg(() {
                  suggestions = res;
                  searching = false;
                });
              }
            });
          }

          return AlertDialog(
            backgroundColor: c.surface2,
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            insetPadding:
                const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
            title: Text(isEdit ? existing.label : l.addFavorite,
                style: TextStyle(
                    color: c.textPrimary, fontWeight: FontWeight.w700)),
            content: SizedBox(
              width: double.maxFinite,
              child: SingleChildScrollView(
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                  // Label field
                  TextField(
                    controller: labelCtrl,
                    style: TextStyle(color: c.textPrimary, fontSize: 14),
                    textCapitalization: TextCapitalization.words,
                    decoration: InputDecoration(
                      hintText: l.favoriteLabelHint,
                      hintStyle: TextStyle(color: c.textSecondary),
                      filled: true,
                      fillColor: c.surface1,
                      border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide.none),
                      contentPadding: const EdgeInsets.symmetric(
                          horizontal: 14, vertical: 12),
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Address field
                  TextField(
                    controller: addressCtrl,
                    style: TextStyle(color: c.textPrimary, fontSize: 14),
                    onChanged: onAddressChanged,
                    decoration: InputDecoration(
                      hintText: l.favoriteAddressHint,
                      hintStyle: TextStyle(color: c.textSecondary),
                      filled: true,
                      fillColor: c.surface1,
                      suffixIcon: searching
                          ? Padding(
                              padding: const EdgeInsets.all(12),
                              child: SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: CircularProgressIndicator(
                                      strokeWidth: 2, color: c.accent)))
                          : null,
                      border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(12),
                          borderSide: BorderSide.none),
                      contentPadding: const EdgeInsets.symmetric(
                          horizontal: 14, vertical: 12),
                    ),
                  ),

                  // Nominatim suggestions
                  if (suggestions.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Container(
                      constraints: BoxConstraints(
                          maxHeight: (MediaQuery.of(ctx).size.height * 0.35)
                              .clamp(120.0, 220.0)),
                      decoration: BoxDecoration(
                        color: c.surface1,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: c.border, width: 0.5),
                      ),
                      child: ListView.separated(
                        shrinkWrap: true,
                        padding: EdgeInsets.zero,
                        itemCount: suggestions.length,
                        separatorBuilder: (_, __) =>
                            Divider(height: 0.5, color: c.border),
                        itemBuilder: (_, i) {
                          final s = suggestions[i];
                          return ListTile(
                            dense: true,
                            leading: Text(s.emoji,
                                style: const TextStyle(fontSize: 16)),
                            title: Text(s.shortName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                    color: c.textPrimary, fontSize: 13)),
                            subtitle: Text(
                                s.displayName.split(',').take(3).join(', '),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                    color: c.textSecondary, fontSize: 11)),
                            onTap: () {
                              addressCtrl.text =
                                  s.displayName.split(',').take(3).join(', ');
                              pickedPosition = s.position;
                              setDlg(() => suggestions = []);
                            },
                          );
                        },
                      ),
                    ),
                  ],

                  if (error != null) ...[
                    const SizedBox(height: 8),
                    Text(error!,
                        style: const TextStyle(
                            color: Colors.redAccent, fontSize: 12)),
                  ],
                ]),
              ),
            ),
            actions: [
              TextButton(
                onPressed: () {
                  debounce?.cancel();
                  Navigator.pop(ctx);
                },
                child: Text(l.cancel, style: TextStyle(color: c.textSecondary)),
              ),
              FilledButton(
                style: FilledButton.styleFrom(backgroundColor: c.accent),
                onPressed: () async {
                  final label = labelCtrl.text.trim();
                  final address = addressCtrl.text.trim();
                  if (label.isEmpty || address.isEmpty) return;
                  debounce?.cancel();
                  setDlg(() {
                    error = null;
                    suggestions = [];
                  });

                  LatLng? pos = pickedPosition;
                  if (pos == null) {
                    // User typed manually — geocode now
                    setDlg(() => searching = true);
                    final res = await RoutingService.search(address);
                    if (!ctx.mounted) return;
                    setDlg(() => searching = false);
                    if (res.isEmpty) {
                      setDlg(() => error = l.favoriteGeocodingError);
                      return;
                    }
                    pos = res.first.position;
                  }

                  final place = FavoritePlace(
                      label: label, address: address, position: pos);
                  setState(() {
                    if (isEdit && existingIdx != null) {
                      _favorites[existingIdx] = place;
                    } else {
                      _favorites.add(place);
                    }
                  });
                  _saveFavorites();
                  if (ctx.mounted) Navigator.pop(ctx);
                },
                // "Add favorite" would be wrong when editing an existing one.
                child: Text(isEdit ? l.ok : l.addFavorite),
              ),
            ],
          );
        },
      ),
    );
    debounce?.cancel();
  }

  bool _getBool(String key, bool def) =>
      _box.get(key, defaultValue: def) as bool;
  void _setBool(String key, bool v) {
    _box.put(key, v);
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final c = RoadstrColors.of(context);
    final themeProvider = context.watch<ThemeProvider>();
    final localeProvider = context.watch<LocaleProvider>();
    final l = AppLocalizations.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(l.settingsTitle),
        centerTitle: true,
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(0.5),
          child: Divider(height: 0.5, color: c.border),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // ── THEME ───────────────────────────────────────────────────────
          _SectionHeader(l.sectionTheme, c),
          Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.symmetric(horizontal: 16),
            decoration: BoxDecoration(
              color: c.surface2,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: c.border, width: 0.5),
            ),
            child: DropdownButtonHideUnderline(
              child: DropdownButton<AppThemeId>(
                value: themeProvider.current,
                isExpanded: true,
                dropdownColor: c.surface2,
                borderRadius: BorderRadius.circular(14),
                icon: Icon(Icons.expand_more_rounded, color: c.textSecondary),
                style: TextStyle(color: c.textPrimary, fontSize: 14),
                items: AppThemeId.values.map((id) {
                  return DropdownMenuItem(
                    value: id,
                    child: Row(children: [
                      Container(
                        width: 20,
                        height: 20,
                        margin: const EdgeInsets.only(right: 12),
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: id.isDark
                              ? const Color(0xFF161B22)
                              : Colors.white,
                          border: Border.all(color: id.accent, width: 3),
                        ),
                      ),
                      Text(id.localizedLabel(l),
                          style: TextStyle(color: c.textPrimary, fontSize: 14)),
                      if (themeProvider.current == id) ...[
                        const Spacer(),
                        Icon(Icons.check_rounded, color: c.accent, size: 18),
                      ],
                    ]),
                  );
                }).toList(),
                onChanged: (id) {
                  if (id != null) themeProvider.setTheme(id);
                },
              ),
            ),
          ),

          _SwitchTile(
            title: l.autoDarkMode,
            subtitle: l.autoDarkModeDesc,
            value: themeProvider.autoDarkEnabled,
            onChanged: themeProvider.setAutoDarkEnabled,
            colors: c,
          ),

          const SizedBox(height: 24),

          // ── LANGUAGE ────────────────────────────────────────────────────
          _SectionHeader(l.sectionLanguage, c),
          Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.symmetric(horizontal: 16),
            decoration: BoxDecoration(
              color: c.surface2,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: c.border, width: 0.5),
            ),
            child: DropdownButtonHideUnderline(
              child: DropdownButton<String?>(
                value: localeProvider.locale?.languageCode,
                isExpanded: true,
                dropdownColor: c.surface2,
                borderRadius: BorderRadius.circular(14),
                icon: Icon(Icons.expand_more_rounded, color: c.textSecondary),
                style: TextStyle(color: c.textPrimary, fontSize: 14),
                // Language names are intentionally shown in their native script
                // so users can recognise their own language regardless of the
                // current app language.
                items: [
                  // ── System default ──────────────────────────────────────
                  DropdownMenuItem<String?>(
                    value: null,
                    child: Text(l.langSystem,
                        style: TextStyle(color: c.textPrimary)),
                  ),
                  // ── EU official languages ───────────────────────────────
                  ..._buildLangItems(c, const {
                    'bg': 'Български', // Bulgarian
                    'cs': 'Čeština', // Czech
                    'da': 'Dansk', // Danish
                    'de': 'Deutsch', // German
                    'el': 'Ελληνικά', // Greek
                    'en': 'English', // English
                    'es': 'Español', // Spanish
                    'et': 'Eesti', // Estonian
                    'fi': 'Suomi', // Finnish
                    'fr': 'Français', // French
                    'ga': 'Gaeilge', // Irish
                    'hr': 'Hrvatski', // Croatian
                    'hu': 'Magyar', // Hungarian
                    'it': 'Italiano', // Italian
                    'lt': 'Lietuvių', // Lithuanian
                    'lv': 'Latviešu', // Latvian
                    'mt': 'Malti', // Maltese
                    'nl': 'Nederlands', // Dutch
                    'pl': 'Polski', // Polish
                    'pt': 'Português', // Portuguese
                    'ro': 'Română', // Romanian
                    'sk': 'Slovenčina', // Slovak
                    'sl': 'Slovenščina', // Slovenian
                    'sv': 'Svenska', // Swedish
                    // ── Additional world languages ──────────────────────
                    'ja': '日本語', // Japanese
                    'ru': 'Русский', // Russian
                    'zh': '中文', // Chinese (Simplified)
                  }),
                ],
                onChanged: (code) {
                  localeProvider.setLocale(code != null ? Locale(code) : null);
                },
              ),
            ),
          ),

          const SizedBox(height: 24),

          // ── MAP ─────────────────────────────────────────────────────────
          _SectionHeader(l.sectionMap, c),
          _SwitchTile(
            title: l.keepScreenOn,
            subtitle: l.keepScreenOnDescription,
            value: _getBool('keepScreenOn', true),
            onChanged: (v) => _setBool('keepScreenOn', v),
            colors: c,
          ),
          _SwitchTile(
            title: l.autoCenterOnLaunch,
            subtitle: l.autoCenterOnLaunchDesc,
            value: _getBool('autoCenterOnLaunch', false),
            onChanged: (v) => _setBool('autoCenterOnLaunch', v),
            colors: c,
          ),
          _SwitchTile(
            title: l.settingsImperialUnits,
            subtitle: l.settingsImperialUnitsDesc,
            value: _getBool('imperialUnits', false),
            onChanged: (v) => _setBool('imperialUnits', v),
            colors: c,
          ),
          Container(
            margin: const EdgeInsets.only(top: 8, bottom: 8),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: c.surface2,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: c.border, width: 0.5),
            ),
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(l.mapTileUrlLabel,
                  style: TextStyle(color: c.textSecondary, fontSize: 12)),
              const SizedBox(height: 8),
              TextFormField(
                initialValue:
                    _box.get('mapTileUrl', defaultValue: c.mapTile) as String,
                decoration: const InputDecoration(
                    border: InputBorder.none,
                    hintText: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'),
                onFieldSubmitted: (v) {
                  _box.put('mapTileUrl', v.trim());
                  setState(() {});
                },
              ),
            ]),
          ),

          const SizedBox(height: 12),

          Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            decoration: BoxDecoration(
              color: c.surface2,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: c.border, width: 0.5),
            ),
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(l.routingProviderLabel,
                  style: TextStyle(color: c.textSecondary, fontSize: 12)),
              const SizedBox(height: 8),
              DropdownButton<String>(
                value:
                    _box.get('routingProvider', defaultValue: 'osrm') as String,
                isExpanded: true,
                underline: const SizedBox(),
                items: [
                  DropdownMenuItem(value: 'osrm', child: Text(l.osrmProvider)),
                  DropdownMenuItem(
                      value: 'graphhopper',
                      child: Text(l.graphhopperLocalProvider)),
                  DropdownMenuItem(
                      value: 'graphhopper_public',
                      child: Text(l.graphhopperCloudProvider)),
                  DropdownMenuItem(
                      value: 'openroute', child: Text(l.openrouteProvider)),
                ],
                onChanged: (v) {
                  if (v != null) {
                    _box.put('routingProvider', v);
                    setState(() {});
                  }
                },
              ),
              const SizedBox(height: 8),
              Row(children: [
                Expanded(
                  child: TextFormField(
                    initialValue: _box.get('graphhopperServer',
                        defaultValue: '') as String,
                    decoration: InputDecoration(
                        border: InputBorder.none,
                        hintText: l.graphhopperServerHint),
                    onFieldSubmitted: (v) {
                      _box.put('graphhopperServer', v.trim());
                      setState(() {});
                    },
                  ),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: () async {
                    final navigator = Navigator.of(context);
                    final server = _box.get('graphhopperServer',
                        defaultValue: '') as String;
                    if (server.trim().isEmpty) {
                      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                          content: Text(l.graphhopperServerUrlRequired,
                              style: const TextStyle(color: Colors.white)),
                          backgroundColor: const Color(0xFF1A1A2E)));
                      return;
                    }
                    if (!mounted) return;
                    showDialog(
                        context: context,
                        barrierDismissible: false,
                        builder: (_) => const AlertDialog(
                            content: SizedBox(
                                height: 60,
                                child: Center(
                                    child: CircularProgressIndicator()))));
                    try {
                      final key = _apiKeyCtrl.text.trim();
                      await RoutingService.testGraphHopperServer(server,
                          apiKey: key.isNotEmpty ? key : null);
                      if (!mounted || !navigator.mounted) return;
                      navigator.pop();
                      if (!mounted) return;
                      showDialog(
                          context: this.context,
                          builder: (_) => AlertDialog(
                                  title: Text(l.successTitle),
                                  content: Text(l.graphhopperServerReachable),
                                  actions: [
                                    TextButton(
                                        onPressed: () =>
                                            Navigator.of(context).pop(),
                                        child: Text(l.ok))
                                  ]));
                    } catch (e) {
                      if (mounted && navigator.mounted) navigator.pop();
                      String msg = e.toString();
                      if (e is RoutingException) {
                        final body = e.body ?? '';
                        final snippet = body.length > 300
                            ? '${body.substring(0, 300)}...'
                            : body;
                        msg =
                            'HTTP ${e.statusCode ?? ''} - ${e.message}\n$snippet';
                      }
                      if (!mounted) return;
                      showDialog(
                          context: this.context,
                          builder: (_) => AlertDialog(
                                  title: Text(l.errorTitle),
                                  content: Text(msg),
                                  actions: [
                                    TextButton(
                                        onPressed: () =>
                                            Navigator.of(context).pop(),
                                        child: Text(l.close))
                                  ]));
                    }
                  },
                  child: Text(l.verify),
                ),
              ]),
              const SizedBox(height: 8),
              TextFormField(
                controller: _apiKeyCtrl,
                decoration: InputDecoration(
                    border: InputBorder.none,
                    hintText: l.graphhopperApiKeyHint),
                onFieldSubmitted: (v) async {
                  await _st.write(key: 'routing_api_key', value: v.trim());
                  if (mounted) setState(() {});
                },
              ),
            ]),
          ),

          const SizedBox(height: 24),

          // ── WEB SEARCH ENGINE ───────────────────────────────────────────
          _SectionHeader(l.sectionWebSearch, c),
          _SearchEngineSelector(box: _box, colors: c),

          const SizedBox(height: 24),

          // ── LIGHTNING / NWC ─────────────────────────────────────────────
          _SectionHeader(l.sectionLightning, c),
          Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: c.surface2,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: c.border, width: 0.5),
            ),
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                const Text('⚡', style: TextStyle(fontSize: 14)),
                const SizedBox(width: 6),
                Text(l.nwcLabel,
                    style: TextStyle(color: c.textSecondary, fontSize: 12)),
              ]),
              const SizedBox(height: 6),
              // AutofillGroup lets Bitwarden / 1Password recognise this
              // connection-string field and offer to autofill it.
              AutofillGroup(
                child: TextFormField(
                  controller: _nwcCtrl,
                  obscureText: true,
                  autocorrect: false,
                  enableSuggestions: false,
                  autofillHints: const [AutofillHints.password],
                  style: TextStyle(
                      color: c.textPrimary,
                      fontSize: 12,
                      fontFamily: 'monospace'),
                  decoration: InputDecoration(
                    border: InputBorder.none,
                    hintText: 'nostr+walletconnect://...',
                    hintStyle: TextStyle(color: c.textSecondary, fontSize: 11),
                  ),
                  onFieldSubmitted: (v) async {
                    await _st.write(key: 'nwc_uri', value: v.trim());
                    if (mounted) setState(() {});
                  },
                ),
              ),
              Text(l.nwcDesc,
                  style: TextStyle(
                      color: c.textSecondary, fontSize: 10, height: 1.4)),
            ]),
          ),
          const SizedBox(height: 16),

          // ── SAVED PLACES ─────────────────────────────────────────────────
          _SectionHeader(l.sectionFavorites, c),
          Container(
            margin: const EdgeInsets.only(bottom: 8),
            decoration: BoxDecoration(
              color: c.surface2,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: c.border, width: 0.5),
            ),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              if (_favorites.isNotEmpty)
                ListView.separated(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  padding: EdgeInsets.zero,
                  itemCount: _favorites.length,
                  separatorBuilder: (_, __) =>
                      Divider(height: 0.5, color: c.border),
                  itemBuilder: (_, i) {
                    final fav = _favorites[i];
                    return ListTile(
                      dense: true,
                      onTap: () => _editFavoriteDialog(l, c,
                          existing: fav, existingIdx: i),
                      leading: Container(
                        width: 32,
                        height: 32,
                        decoration: BoxDecoration(
                            color: c.accentSoft,
                            borderRadius: BorderRadius.circular(8)),
                        child: Icon(Icons.favorite_rounded,
                            color: c.accent, size: 16),
                      ),
                      title: Text(fav.label,
                          style: TextStyle(
                              color: c.textPrimary,
                              fontSize: 14,
                              fontWeight: FontWeight.w600)),
                      subtitle: Text(fav.address,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style:
                              TextStyle(color: c.textSecondary, fontSize: 12)),
                      trailing: IconButton(
                        icon: Icon(Icons.delete_outline_rounded,
                            color: c.textSecondary, size: 20),
                        onPressed: () => _deleteFavorite(i),
                      ),
                    );
                  },
                ),
              ListTile(
                dense: true,
                leading: Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                      color: c.accentSoft,
                      borderRadius: BorderRadius.circular(8)),
                  child: Icon(Icons.add_rounded, color: c.accent, size: 18),
                ),
                title: Text(l.addFavorite,
                    style: TextStyle(
                        color: c.accent,
                        fontSize: 14,
                        fontWeight: FontWeight.w500)),
                onTap: () => _editFavoriteDialog(l, c),
              ),
              if (_favorites.isNotEmpty) ...[
                Divider(height: 0.5, color: c.border),
                ListTile(
                  dense: true,
                  leading: Container(
                    width: 32,
                    height: 32,
                    decoration: BoxDecoration(
                        color: c.surface3,
                        borderRadius: BorderRadius.circular(8)),
                    child: Icon(Icons.file_download_outlined,
                        color: c.textSecondary, size: 16),
                  ),
                  title: Text(l.exportFavoritesTitle,
                      style: TextStyle(
                          color: c.textPrimary,
                          fontSize: 14,
                          fontWeight: FontWeight.w500)),
                  onTap: _exportFavorites,
                ),
              ],
              Divider(height: 0.5, color: c.border),
              ListTile(
                dense: true,
                leading: Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                      color: c.surface3,
                      borderRadius: BorderRadius.circular(8)),
                  child: Icon(Icons.file_upload_outlined,
                      color: c.textSecondary, size: 16),
                ),
                title: Text(l.importFavoritesTitle,
                    style: TextStyle(
                        color: c.textPrimary,
                        fontSize: 14,
                        fontWeight: FontWeight.w500)),
                onTap: _importFavorites,
              ),
            ]),
          ),

          const SizedBox(height: 16),

          // ── FAVORITES SYNC (Nostr, NIP-44 encrypted) ────────────────────
          _SectionHeader(l.syncFavoritesTitle, c),
          Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: c.surface2,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: c.border, width: 0.5),
            ),
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(l.syncFavoritesDesc,
                  style: TextStyle(
                      color: c.textSecondary, fontSize: 12, height: 1.5)),
              const SizedBox(height: 12),
              if (_nostrPub == null)
                Text(l.syncNoIdentity,
                    style: TextStyle(
                        color: c.textSecondary,
                        fontSize: 12,
                        fontStyle: FontStyle.italic))
              else ...[
                Row(children: [
                  Expanded(
                      child: OutlinedButton.icon(
                    onPressed:
                        (_syncBusy || _favorites.isEmpty) ? null : _pushSync,
                    style: OutlinedButton.styleFrom(
                      side: BorderSide(color: c.border),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10)),
                      padding: const EdgeInsets.symmetric(vertical: 10),
                    ),
                    icon: Icon(Icons.cloud_upload_outlined,
                        color: c.accent, size: 16),
                    label: Text(l.syncNowButton,
                        style: TextStyle(color: c.textPrimary, fontSize: 12)),
                  )),
                  const SizedBox(width: 8),
                  Expanded(
                      child: OutlinedButton.icon(
                    onPressed: _syncBusy ? null : _pullSync,
                    style: OutlinedButton.styleFrom(
                      side: BorderSide(color: c.border),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10)),
                      padding: const EdgeInsets.symmetric(vertical: 10),
                    ),
                    icon: Icon(Icons.cloud_download_outlined,
                        color: c.accent, size: 16),
                    label: Text(l.syncPullButton,
                        style: TextStyle(color: c.textPrimary, fontSize: 12)),
                  )),
                ]),
                const SizedBox(height: 8),
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(
                    _syncPassphrase != null
                        ? Icons.lock_rounded
                        : Icons.lock_open_rounded,
                    color: _syncPassphrase != null ? c.accent : c.textSecondary,
                    size: 18,
                  ),
                  title: Text(l.syncPassphraseTitle,
                      style: TextStyle(
                          color: c.textPrimary,
                          fontSize: 13,
                          fontWeight: FontWeight.w500)),
                  onTap: _editSyncPassphrase,
                ),
                if (_syncBusy) ...[
                  const SizedBox(height: 10),
                  Center(
                      child: SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(
                              strokeWidth: 2, color: c.accent))),
                ],
                if (!_syncBusy &&
                    (_box.get('favoritesSyncLastAt') as int?) != null) ...[
                  const SizedBox(height: 10),
                  Text(
                      l.syncLastSyncLabel(DateTime.fromMillisecondsSinceEpoch(
                              _box.get('favoritesSyncLastAt') as int)
                          .toLocal()
                          .toString()
                          .substring(0, 16)),
                      style: TextStyle(color: c.textSecondary, fontSize: 11)),
                ],
              ],
            ]),
          ),

          const SizedBox(height: 16),

          // ── NAVIGATION VOICE ────────────────────────────────────────────
          _SectionHeader(l.sectionNavigationVoice, c),
          _SwitchTile(
            title: l.voiceGuidance,
            subtitle: l.voiceGuidanceDesc,
            value: _getBool('voiceEnabled', true),
            onChanged: (v) => _setBool('voiceEnabled', v),
            colors: c,
          ),
          // ── Kokoro voice model download ──────────────────────────────────
          Container(
            margin: const EdgeInsets.only(top: 8, bottom: 8),
            decoration: BoxDecoration(
              color: c.surface2,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: c.border, width: 0.5),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(children: [
                    Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(
                          color: c.accentSoft,
                          borderRadius: BorderRadius.circular(8)),
                      child: Icon(Icons.record_voice_over_rounded,
                          color: c.accent, size: 18),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                        child: Text(l.kokoroModelTitle,
                            style: TextStyle(
                                color: c.textPrimary,
                                fontSize: 14,
                                fontWeight: FontWeight.w600))),
                    if (_kokoroStatus == _KokoroStatus.ready)
                      Icon(Icons.check_circle_rounded,
                          color: Colors.green.shade400, size: 20),
                  ]),
                  const SizedBox(height: 8),
                  Text(
                    switch (_kokoroStatus) {
                      _KokoroStatus.ready => l.kokoroModelStatusReady,
                      _KokoroStatus.downloading =>
                        l.kokoroModelStatusDownloading,
                      _ => l.kokoroModelStatusNotDownloaded,
                    },
                    style: TextStyle(color: c.textSecondary, fontSize: 12),
                  ),
                  if (_kokoroStatus == _KokoroStatus.downloading) ...[
                    const SizedBox(height: 8),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: _downloadProgress,
                        backgroundColor: c.border,
                        color: c.accent,
                        minHeight: 5,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text('${(_downloadProgress * 100).round()}%',
                        style: TextStyle(color: c.textSecondary, fontSize: 11)),
                  ],
                  const SizedBox(height: 6),
                  Text(l.kokoroModelSupportedLangs,
                      style: TextStyle(
                          color: c.textSecondary,
                          fontSize: 11,
                          fontStyle: FontStyle.italic)),
                  if (_kokoroStatus == _KokoroStatus.notDownloaded) ...[
                    const SizedBox(height: 12),
                    FilledButton.icon(
                      onPressed: _downloadKokoroModel,
                      style: FilledButton.styleFrom(backgroundColor: c.accent),
                      icon: const Icon(Icons.download_rounded, size: 16),
                      label: Text(l.kokoroModelDownloadBtn),
                    ),
                  ],
                  if (_kokoroStatus == _KokoroStatus.ready) ...[
                    const SizedBox(height: 14),
                    Divider(height: 0.5, color: c.border),
                    const SizedBox(height: 14),
                    // ── Voice gender ──────────────────────────────────────────
                    Row(children: [
                      Expanded(
                          child: Text(l.kokoroVoiceGenderTitle,
                              style: TextStyle(
                                  color: c.textPrimary,
                                  fontSize: 13,
                                  fontWeight: FontWeight.w600))),
                      if (_previewingVoice)
                        SizedBox(
                            width: 14,
                            height: 14,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: c.accent)),
                    ]),
                    const SizedBox(height: 8),
                    Row(children: [
                      Expanded(
                          child: _VoiceOptionChip(
                        label: l.kokoroVoiceFemale,
                        selected: _kokoroGender == 'f',
                        onTap: () => _setGender('f'),
                        colors: c,
                      )),
                      const SizedBox(width: 8),
                      Expanded(
                          child: _VoiceOptionChip(
                        label: l.kokoroVoiceMale,
                        selected: _kokoroGender == 'm',
                        onTap: () => _setGender('m'),
                        colors: c,
                      )),
                    ]),
                    if (!kokoroHasGenderChoice(
                        Localizations.localeOf(context).languageCode)) ...[
                      const SizedBox(height: 6),
                      Text(l.kokoroVoiceGenderUnavailable,
                          style: TextStyle(
                              color: c.textSecondary,
                              fontSize: 11,
                              fontStyle: FontStyle.italic)),
                    ],
                    const SizedBox(height: 16),
                    // ── Speech speed ──────────────────────────────────────────
                    Row(children: [
                      Expanded(
                          child: Text(l.kokoroSpeedTitle,
                              style: TextStyle(
                                  color: c.textPrimary,
                                  fontSize: 13,
                                  fontWeight: FontWeight.w600))),
                      Text(
                          '${kKokoroSpeedStages[_kokoroSpeedStage].toStringAsFixed(2)}×',
                          style: TextStyle(
                              color: c.accent,
                              fontSize: 13,
                              fontWeight: FontWeight.w600)),
                    ]),
                    SliderTheme(
                      data: SliderTheme.of(context).copyWith(
                        activeTrackColor: c.accent,
                        inactiveTrackColor: c.border,
                        thumbColor: c.accent,
                        overlayColor: c.accent.withValues(alpha: 0.15),
                        trackHeight: 3,
                      ),
                      child: Slider(
                        value: _kokoroSpeedStage.toDouble(),
                        min: 0,
                        max: (kKokoroSpeedStages.length - 1).toDouble(),
                        divisions: kKokoroSpeedStages.length - 1,
                        onChanged: (v) =>
                            setState(() => _kokoroSpeedStage = v.round()),
                        onChangeEnd: (v) => _setSpeedStage(v.round()),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),

          const SizedBox(height: 24),

          // ── INFO ─────────────────────────────────────────────────────────
          _SectionHeader(l.sectionInfo, c),
          _InfoTile(l.infoVersion, _appVersion, c),
          _InfoTile(l.infoProtocol, 'Nostr', c),
          _InfoTile(l.infoMaps, 'openstreetmap.org', c,
              url: 'https://www.openstreetmap.org'),
          _InfoTile(l.infoRouting, () {
            final p =
                _box.get('routingProvider', defaultValue: 'osrm') as String;
            switch (p) {
              case 'graphhopper':
                return l.providerGraphhopperSelfHosted;
              case 'graphhopper_public':
                return l.providerGraphhopperCloud;
              case 'openroute':
                return l.providerOpenroute;
              default:
                return l.providerOsrm;
            }
          }(), c),
          _InfoTile(l.infoSource, 'github.com/roadstrapp/roadstr-app', c,
              url: 'https://github.com/roadstrapp/roadstr-app'),

          const SizedBox(height: 16),
          _DonationTile(c),

          const SizedBox(height: 32),
        ],
      ),
    );
  }
}

class _DonationTile extends StatelessWidget {
  static const _lnAddress = 'lwb89@blink.sv';
  final RoadstrColors c;
  const _DonationTile(this.c);

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () async {
        // Try to open the Lightning wallet directly; fall back to clipboard.
        final uri = Uri.parse('lightning:$_lnAddress');
        final launched =
            await launchUrl(uri, mode: LaunchMode.externalApplication)
                .catchError((_) => false);
        if (!launched) {
          await Clipboard.setData(const ClipboardData(text: _lnAddress));
          if (context.mounted) {
            final l = AppLocalizations.of(context);
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(l.lightningAddressCopied(_lnAddress)),
                duration: const Duration(seconds: 3),
              ),
            );
          }
        }
      },
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
        decoration: BoxDecoration(
          color: c.surface2,
          borderRadius: BorderRadius.circular(14),
          border:
              Border.all(color: c.accent.withValues(alpha: 0.4), width: 0.8),
        ),
        child: Row(
          children: [
            Icon(Icons.bolt_rounded, color: c.accent, size: 18),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(AppLocalizations.of(context).supportRoadstr,
                        style: TextStyle(
                            color: c.textPrimary,
                            fontSize: 13,
                            fontWeight: FontWeight.w600)),
                    const SizedBox(height: 2),
                    Text(_lnAddress,
                        style: TextStyle(color: c.textSecondary, fontSize: 12)),
                  ]),
            ),
            Icon(Icons.open_in_new_rounded, size: 14, color: c.accent),
          ],
        ),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  final RoadstrColors c;
  const _SectionHeader(this.title, this.c);
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(left: 4, bottom: 8),
        child: Text(title.toUpperCase(),
            style: TextStyle(
                color: c.textSecondary,
                fontSize: 11,
                fontWeight: FontWeight.bold,
                letterSpacing: 1)),
      );
}

class _SwitchTile extends StatelessWidget {
  final String title, subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;
  final RoadstrColors colors;
  const _SwitchTile(
      {required this.title,
      required this.subtitle,
      required this.value,
      required this.onChanged,
      required this.colors});
  @override
  Widget build(BuildContext context) => Container(
        margin: const EdgeInsets.only(bottom: 8),
        decoration: BoxDecoration(
          color: colors.surface2,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: colors.border, width: 0.5),
        ),
        child: SwitchListTile(
          title: Text(title,
              style: TextStyle(color: colors.textPrimary, fontSize: 14)),
          subtitle: Text(subtitle,
              style: TextStyle(color: colors.textSecondary, fontSize: 12)),
          value: value,
          onChanged: onChanged,
          dense: true,
        ),
      );
}

class _VoiceOptionChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;
  final RoadstrColors colors;
  const _VoiceOptionChip(
      {required this.label,
      required this.selected,
      required this.onTap,
      required this.colors});
  @override
  Widget build(BuildContext context) => GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            color: selected
                ? colors.accent.withValues(alpha: 0.15)
                : colors.surface3,
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
                color: selected ? colors.accent : colors.border,
                width: selected ? 1.2 : 0.5),
          ),
          child: Center(
              child: Text(label,
                  style: TextStyle(
                      color: selected ? colors.accent : colors.textSecondary,
                      fontSize: 13,
                      fontWeight:
                          selected ? FontWeight.w700 : FontWeight.w500))),
        ),
      );
}

class _InfoTile extends StatelessWidget {
  final String label, value;
  final String? url;
  final RoadstrColors c;
  const _InfoTile(this.label, this.value, this.c, {this.url});

  @override
  Widget build(BuildContext context) {
    final isLink = url != null;
    final tile = Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
      decoration: BoxDecoration(
        color: c.surface2,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: c.border, width: 0.5),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(color: c.textSecondary, fontSize: 13)),
          Flexible(
              child: Row(mainAxisSize: MainAxisSize.min, children: [
            Flexible(
                child: Text(value,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        color: isLink ? c.accent : c.textPrimary,
                        fontSize: 13,
                        decoration: isLink ? TextDecoration.underline : null,
                        decorationColor: c.accent))),
            if (isLink) ...[
              const SizedBox(width: 4),
              Icon(Icons.open_in_new_rounded, size: 13, color: c.accent),
            ],
          ])),
        ],
      ),
    );

    if (!isLink) return tile;
    return GestureDetector(
      onTap: () =>
          launchUrl(Uri.parse(url!), mode: LaunchMode.externalApplication),
      child: tile,
    );
  }
}

// ── Search engine selector ────────────────────────────────────────────────────

class _SearchEngineSelector extends StatefulWidget {
  final Box box;
  final RoadstrColors colors;
  const _SearchEngineSelector({required this.box, required this.colors});
  @override
  State<_SearchEngineSelector> createState() => _SearchEngineSelectorState();
}

class _SearchEngineSelectorState extends State<_SearchEngineSelector> {
  static const _engines = [
    (id: 'qwant', name: 'Qwant', icon: '🇪🇺'),
    (id: 'brave', name: 'Brave Search', icon: '🦁'),
    (id: 'ddg', name: 'DuckDuckGo', icon: '🦆'),
    (id: 'startpage', name: 'Startpage', icon: '🔒'),
    (id: 'google', name: 'Google', icon: '🕵️'),
  ];

  String _desc(String id, AppLocalizations l) => switch (id) {
        'brave' => l.searchEngineBraveDesc,
        'ddg' => l.searchEngineDdgDesc,
        'startpage' => l.searchEngineStartpageDesc,
        'google' => l.searchEngineGoogleDesc,
        _ => l.searchEngineQwantDesc,
      };

  String get _current =>
      widget.box.get('searchEngine', defaultValue: 'qwant') as String;

  @override
  Widget build(BuildContext context) {
    final c = widget.colors;
    final l = AppLocalizations.of(context);
    final cur = _current;
    final eng =
        _engines.firstWhere((e) => e.id == cur, orElse: () => _engines.first);

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: c.surface2,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: c.border, width: 0.5),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: DropdownButtonHideUnderline(
            child: DropdownButton<String>(
              value: cur,
              isExpanded: true,
              dropdownColor: c.surface2,
              borderRadius: BorderRadius.circular(14),
              icon: Icon(Icons.expand_more_rounded, color: c.textSecondary),
              style: TextStyle(color: c.textPrimary, fontSize: 14),
              items: _engines
                  .map((e) => DropdownMenuItem(
                        value: e.id,
                        child: Row(children: [
                          Text(e.icon, style: const TextStyle(fontSize: 16)),
                          const SizedBox(width: 10),
                          Text(e.name,
                              style: TextStyle(
                                  color: c.textPrimary, fontSize: 14)),
                          if (cur == e.id) ...[
                            const Spacer(),
                            Icon(Icons.check_rounded,
                                color: c.accent, size: 16),
                          ],
                        ]),
                      ))
                  .toList(),
              onChanged: (id) {
                if (id != null) {
                  widget.box.put('searchEngine', id);
                  setState(() {});
                }
              },
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
          child: Text(_desc(eng.id, l),
              style:
                  TextStyle(color: c.textSecondary, fontSize: 12, height: 1.4)),
        ),
      ]),
    );
  }
}

// ── Language list helper ──────────────────────────────────────────────────────

/// Builds a sorted list of [DropdownMenuItem]s for the language selector.
///
/// [langs] maps BCP-47 language codes to their native-script display name
/// (e.g. `'de': 'Deutsch'`). Items are sorted alphabetically by native name
/// so the list is easy to scan regardless of which language is currently active.
List<DropdownMenuItem<String?>> _buildLangItems(
    RoadstrColors c, Map<String, String> langs) {
  final sorted = langs.entries.toList()
    ..sort((a, b) => a.value.compareTo(b.value));
  return sorted
      .map((e) => DropdownMenuItem<String?>(
            value: e.key,
            child: Text(e.value, style: TextStyle(color: c.textPrimary)),
          ))
      .toList();
}
