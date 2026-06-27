import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final c = RoadstrColors.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Profilo'),
        centerTitle: true,
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(0.5),
          child: Divider(height: 0.5, color: c.border),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          // Avatar
          Center(
            child: Container(
              width: 80, height: 80,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: c.accentSoft,
                border: Border.all(color: c.accent, width: 2),
              ),
              child: Icon(Icons.person_outline, color: c.accent, size: 40),
            ),
          ),
          const SizedBox(height: 12),
          Center(
            child: Text('Non connesso',
                style: TextStyle(color: c.textSecondary, fontSize: 14)),
          ),
          const SizedBox(height: 32),

          Text('  ACCEDI CON NOSTR',
              style: TextStyle(color: c.textSecondary, fontSize: 11,
                  fontWeight: FontWeight.bold, letterSpacing: 1)),
          const SizedBox(height: 8),

          // Login con Amber — nessun popup, solo snackbar
          _LoginOption(
            icon: Icons.security_rounded,
            title: 'Amber / App Signer',
            subtitle: 'Firma con un\'app esterna — la chiave privata non lascia mai il telefono',
            colors: c,
            onTap: () => _comingSoon(context, c),
          ),
          const SizedBox(height: 8),

          _LoginOption(
            icon: Icons.vpn_key_outlined,
            title: 'Nostr Connect (Bunker)',
            subtitle: 'Connettiti tramite URI nostrconnect://',
            colors: c,
            onTap: () => _comingSoon(context, c),
          ),
          const SizedBox(height: 8),

          _LoginOption(
            icon: Icons.key_outlined,
            title: 'Inserisci nsec',
            subtitle: 'La chiave privata viene salvata localmente sul dispositivo',
            colors: c,
            isWarning: true,
            onTap: () => _comingSoon(context, c),
          ),

          const SizedBox(height: 32),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: c.accentSoft,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: c.accent.withOpacity(0.3)),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.info_outline, color: c.accent, size: 18),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    'Il login Nostr sarà attivo nella Fase 2. '
                    'Potrai pubblicare alert di traffico in modo decentralizzato.',
                    style: TextStyle(color: c.textSecondary, fontSize: 12),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // Snackbar invece di dialog/popup
  void _comingSoon(BuildContext context, RoadstrColors c) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: const Text('In arrivo nella Fase 2'),
      backgroundColor: c.surface2,
      behavior: SnackBarBehavior.floating,
      duration: const Duration(seconds: 2),
    ));
  }
}

class _LoginOption extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final RoadstrColors colors;
  final VoidCallback onTap;
  final bool isWarning;

  const _LoginOption({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.colors,
    required this.onTap,
    this.isWarning = false,
  });

  @override
  Widget build(BuildContext context) {
    final iconColor = isWarning ? const Color(0xFFFFB800) : colors.accent;
    final borderColor = isWarning
        ? const Color(0xFFFFB800).withOpacity(0.4)
        : colors.border;

    return Material(
      color: colors.surface2,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: borderColor, width: 0.5),
          ),
          child: Row(
            children: [
              Container(
                width: 44, height: 44,
                decoration: BoxDecoration(
                  color: iconColor.withOpacity(0.1),
                  shape: BoxShape.circle,
                ),
                child: Icon(icon, color: iconColor, size: 22),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title,
                        style: TextStyle(color: colors.textPrimary,
                            fontSize: 14, fontWeight: FontWeight.w600)),
                    const SizedBox(height: 2),
                    Text(subtitle,
                        style: TextStyle(
                            color: colors.textSecondary, fontSize: 12)),
                  ],
                ),
              ),
              Icon(Icons.chevron_right,
                  color: colors.textSecondary, size: 20),
            ],
          ),
        ),
      ),
    );
  }
}
