import 'package:flutter/material.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:provider/provider.dart';
import '../l10n/app_localizations.dart';
import '../providers/locale_provider.dart';
import '../theme/app_theme.dart';
import '../theme/theme_provider.dart';
import '../services/routing_service.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});
  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late final Box _box;

  @override
  void initState() {
    super.initState();
    _box = Hive.box('settings');
  }

  bool _getBool(String key, bool def) =>
      _box.get(key, defaultValue: def) as bool;
  void _setBool(String key, bool v) { _box.put(key, v); setState(() {}); }

  @override
  Widget build(BuildContext context) {
    final c             = RoadstrColors.of(context);
    final themeProvider  = context.watch<ThemeProvider>();
    final localeProvider = context.watch<LocaleProvider>();
    final l = AppLocalizations.of(context)!;

    return Scaffold(
      appBar: AppBar(
        title: Text(l.settingsTitle), centerTitle: true,
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(0.5),
          child: Divider(height: 0.5, color: c.border),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [

          // ── TEMA ────────────────────────────────────────────────────────
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
                        width: 20, height: 20,
                        margin: const EdgeInsets.only(right: 12),
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: id.isDark ? const Color(0xFF161B22) : Colors.white,
                          border: Border.all(color: id.accent, width: 3),
                        ),
                      ),
                      Text(id.localizedLabel(l), style: TextStyle(
                          color: c.textPrimary, fontSize: 14)),
                      if (themeProvider.current == id) ...[
                        const Spacer(),
                        Icon(Icons.check_rounded, color: c.accent, size: 18),
                      ],
                    ]),
                  );
                }).toList(),
                onChanged: (id) { if (id != null) themeProvider.setTheme(id); },
              ),
            ),
          ),

          const SizedBox(height: 24),

          // ── LINGUA ──────────────────────────────────────────────────────
          _SectionHeader(l.sectionLanguage, c),
          Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.symmetric(horizontal: 16),
            decoration: BoxDecoration(
              color: c.surface2, borderRadius: BorderRadius.circular(14),
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
                items: [
                  DropdownMenuItem<String?>(
                    value: null,
                    child: Text(l.langSystem, style: TextStyle(color: c.textPrimary)),
                  ),
                  DropdownMenuItem<String?>(
                    value: 'it',
                    child: Text(l.langItalian, style: TextStyle(color: c.textPrimary)),
                  ),
                  DropdownMenuItem<String?>(
                    value: 'en',
                    child: Text(l.langEnglish, style: TextStyle(color: c.textPrimary)),
                  ),
                ],
                onChanged: (code) {
                  localeProvider.setLocale(code != null ? Locale(code) : null);
                },
              ),
            ),
          ),

          const SizedBox(height: 24),

          // ── MAPPA ───────────────────────────────────────────────────────
          _SectionHeader(l.sectionMap, c),
          _SwitchTile(
            title: l.keepScreenOn,
            subtitle: l.keepScreenOnDescription,
            value: _getBool('keepScreenOn', true),
            onChanged: (v) => _setBool('keepScreenOn', v), colors: c,
          ),
          _SwitchTile(
            title: l.rotateMap,
            subtitle: l.rotateMapDescription,
            value: _getBool('rotateMap', false),
            onChanged: (v) => _setBool('rotateMap', v), colors: c,
          ),

          Container(
            margin: const EdgeInsets.only(top: 8, bottom: 8),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: c.surface2, borderRadius: BorderRadius.circular(14),
              border: Border.all(color: c.border, width: 0.5),
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(l.mapTileUrlLabel,
                  style: TextStyle(color: c.textSecondary, fontSize: 12)),
              const SizedBox(height: 8),
              TextFormField(
                initialValue: _box.get('mapTileUrl', defaultValue: c.mapTile) as String,
                decoration: const InputDecoration(
                    border: InputBorder.none,
                    hintText: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'),
                onFieldSubmitted: (v) { _box.put('mapTileUrl', v.trim()); setState(() {}); },
              ),
            ]),
          ),

          const SizedBox(height: 12),

          Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            decoration: BoxDecoration(
              color: c.surface2, borderRadius: BorderRadius.circular(14),
              border: Border.all(color: c.border, width: 0.5),
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(l.routingProviderLabel,
                  style: TextStyle(color: c.textSecondary, fontSize: 12)),
              const SizedBox(height: 8),
              DropdownButton<String>(
                value: _box.get('routingProvider', defaultValue: 'osrm') as String,
                isExpanded: true,
                underline: const SizedBox(),
                items: [
                  DropdownMenuItem(value: 'osrm',
                      child: Text(l.osrmProvider)),
                  DropdownMenuItem(value: 'graphhopper',
                      child: Text(l.graphhopperLocalProvider)),
                  DropdownMenuItem(value: 'graphhopper_public',
                      child: Text(l.graphhopperCloudProvider)),
                  DropdownMenuItem(value: 'openroute',
                      child: Text(l.openrouteProvider)),
                ],
                onChanged: (v) {
                  if (v != null) { _box.put('routingProvider', v); setState(() {}); }
                },
              ),

              const SizedBox(height: 8),

              Row(children: [
                Expanded(
                  child: TextFormField(
                    initialValue: _box.get('graphhopperServer', defaultValue: '') as String,
                    decoration: InputDecoration(
                        border: InputBorder.none,
                        hintText: l.graphhopperServerHint),
                    onFieldSubmitted: (v) {
                      _box.put('graphhopperServer', v.trim()); setState(() {});
                    },
                  ),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: () async {
                    final server = _box.get('graphhopperServer', defaultValue: '') as String;
                    if (server.trim().isEmpty) {
                      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                          content: Text(l.graphhopperServerUrlRequired,
                              style: const TextStyle(color: Colors.white)),
                          backgroundColor: const Color(0xFF1A1A2E)));
                      return;
                    }
                    if (!mounted) return;
                    showDialog(context: context, barrierDismissible: false,
                        builder: (_) => const AlertDialog(
                            content: SizedBox(height: 60,
                                child: Center(child: CircularProgressIndicator()))));
                    try {
                      final key = _box.get('graphhopperApiKey', defaultValue: '') as String;
                      await RoutingService.testGraphHopperServer(server,
                          apiKey: key.isNotEmpty ? key : null);
                      if (!mounted) return;
                      Navigator.of(context).pop();
                      if (!mounted) return;
                      showDialog(context: context, builder: (_) => AlertDialog(
                          title: Text(l.successTitle),
                          content: Text(l.graphhopperServerReachable),
                          actions: [TextButton(
                              onPressed: () => Navigator.of(context).pop(),
                              child: Text(l.ok))]));
                    } catch (e) {
                      if (mounted) Navigator.of(context).pop();
                      String msg = e.toString();
                      if (e is RoutingException) {
                        final body = e.body ?? '';
                        final snippet = body.length > 300 ? '${body.substring(0, 300)}...' : body;
                        msg = 'HTTP ${e.statusCode ?? ''} - ${e.message}\n$snippet';
                      }
                      if (!mounted) return;
                      showDialog(context: context, builder: (_) => AlertDialog(
                          title: Text(l.errorTitle),
                          content: Text(msg),
                          actions: [TextButton(
                              onPressed: () => Navigator.of(context).pop(),
                              child: Text(l.close))]));
                    }
                  },
                  child: Text(l.verify),
                ),
              ]),
              const SizedBox(height: 8),
              TextFormField(
                initialValue: _box.get('graphhopperApiKey', defaultValue: '') as String,
                decoration: InputDecoration(
                    border: InputBorder.none,
                    hintText: l.graphhopperApiKeyHint),
                onFieldSubmitted: (v) {
                  _box.put('graphhopperApiKey', v.trim()); setState(() {});
                },
              ),
            ]),
          ),

          const SizedBox(height: 24),

          // ── MOTORE DI RICERCA ────────────────────────────────────────────
          _SectionHeader(l.sectionWebSearch, c),
          _SearchEngineSelector(box: _box, colors: c),

          const SizedBox(height: 24),

          // ── LIGHTNING / NWC ─────────────────────────────────────────────
          _SectionHeader(l.sectionLightning, c),
          Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: c.surface2, borderRadius: BorderRadius.circular(14),
              border: Border.all(color: c.border, width: 0.5),
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                const Text('⚡', style: TextStyle(fontSize: 14)),
                const SizedBox(width: 6),
                Text(l.nwcLabel,
                    style: TextStyle(color: c.textSecondary, fontSize: 12)),
              ]),
              const SizedBox(height: 6),
              TextFormField(
                initialValue: _box.get('nwcUri', defaultValue: '') as String,
                obscureText: true,
                autocorrect: false,
                enableSuggestions: false,
                style: TextStyle(color: c.textPrimary, fontSize: 12,
                    fontFamily: 'monospace'),
                decoration: InputDecoration(
                  border: InputBorder.none,
                  hintText: 'nostr+walletconnect://...',
                  hintStyle: TextStyle(color: c.textSecondary, fontSize: 11),
                ),
                onFieldSubmitted: (v) { _box.put('nwcUri', v.trim()); setState(() {}); },
              ),
              Text(l.nwcDesc,
                style: TextStyle(color: c.textSecondary, fontSize: 10, height: 1.4)),
            ]),
          ),
          const SizedBox(height: 16),

          // ── PRIVACY ─────────────────────────────────────────────────────
          _SectionHeader(l.sectionPrivacy, c),
          _SwitchTile(
            title: l.privacyMode,
            subtitle: l.privacyModeDescription,
            value: _getBool('privacyMode', true),
            onChanged: (v) => _setBool('privacyMode', v), colors: c,
          ),

          const SizedBox(height: 24),

          // ── INFO ─────────────────────────────────────────────────────────
          _SectionHeader(l.sectionInfo, c),
          _InfoTile(l.infoVersion, '0.1.0', c),
          _InfoTile(l.infoProtocol, 'Nostr', c),
          _InfoTile(l.infoMaps,
              _box.get('mapTileUrl', defaultValue: c.mapTile) as String, c),
          _InfoTile(l.infoRouting, () {
            final p = _box.get('routingProvider', defaultValue: 'osrm') as String;
            switch (p) {
              case 'graphhopper':        return l.providerGraphhopperSelfHosted;
              case 'graphhopper_public': return l.providerGraphhopperCloud;
              case 'openroute':          return l.providerOpenroute;
              default:                   return l.providerOsrm;
            }
          }(), c),
          _InfoTile(l.infoSource, 'github.com/tuonome/roadstr', c),

          const SizedBox(height: 32),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title; final RoadstrColors c;
  const _SectionHeader(this.title, this.c);
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(left: 4, bottom: 8),
    child: Text(title.toUpperCase(), style: TextStyle(
        color: c.textSecondary, fontSize: 11,
        fontWeight: FontWeight.bold, letterSpacing: 1)),
  );
}

class _SwitchTile extends StatelessWidget {
  final String title, subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;
  final RoadstrColors colors;
  const _SwitchTile({required this.title, required this.subtitle,
      required this.value, required this.onChanged, required this.colors});
  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.only(bottom: 8),
    decoration: BoxDecoration(
      color: colors.surface2, borderRadius: BorderRadius.circular(14),
      border: Border.all(color: colors.border, width: 0.5),
    ),
    child: SwitchListTile(
      title: Text(title, style: TextStyle(color: colors.textPrimary, fontSize: 14)),
      subtitle: Text(subtitle, style: TextStyle(color: colors.textSecondary, fontSize: 12)),
      value: value, onChanged: onChanged, dense: true,
    ),
  );
}

class _InfoTile extends StatelessWidget {
  final String label, value; final RoadstrColors c;
  const _InfoTile(this.label, this.value, this.c);
  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.only(bottom: 8),
    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
    decoration: BoxDecoration(
      color: c.surface2, borderRadius: BorderRadius.circular(14),
      border: Border.all(color: c.border, width: 0.5),
    ),
    child: Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: TextStyle(color: c.textSecondary, fontSize: 13)),
        Flexible(child: Text(value, maxLines: 1, overflow: TextOverflow.ellipsis,
            style: TextStyle(color: c.textPrimary, fontSize: 13))),
      ],
    ),
  );
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
    (id: 'qwant',     name: 'Qwant',        icon: '🇪🇺'),
    (id: 'brave',     name: 'Brave Search', icon: '🦁'),
    (id: 'ddg',       name: 'DuckDuckGo',   icon: '🦆'),
    (id: 'startpage', name: 'Startpage',    icon: '🔒'),
    (id: 'google',    name: 'Google',       icon: '🕵️'),
  ];

  String _desc(String id, AppLocalizations l) => switch (id) {
    'brave'     => l.searchEngineBraveDesc,
    'ddg'       => l.searchEngineDdgDesc,
    'startpage' => l.searchEngineStartpageDesc,
    'google'    => l.searchEngineGoogleDesc,
    _           => l.searchEngineQwantDesc,
  };

  String get _current =>
      widget.box.get('searchEngine', defaultValue: 'qwant') as String;

  @override
  Widget build(BuildContext context) {
    final c   = widget.colors;
    final l   = AppLocalizations.of(context)!;
    final cur = _current;
    final eng = _engines.firstWhere((e) => e.id == cur,
        orElse: () => _engines.first);

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: c.surface2, borderRadius: BorderRadius.circular(14),
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
              items: _engines.map((e) => DropdownMenuItem(
                value: e.id,
                child: Row(children: [
                  Text(e.icon, style: const TextStyle(fontSize: 16)),
                  const SizedBox(width: 10),
                  Text(e.name,
                      style: TextStyle(color: c.textPrimary, fontSize: 14)),
                  if (cur == e.id) ...[
                    const Spacer(),
                    Icon(Icons.check_rounded, color: c.accent, size: 16),
                  ],
                ]),
              )).toList(),
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
              style: TextStyle(color: c.textSecondary, fontSize: 12,
                  height: 1.4)),
        ),
      ]),
    );
  }
}
