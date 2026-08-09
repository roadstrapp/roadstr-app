import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme/app_theme.dart';
import '../utils/units.dart';

/// Persisted visual style of the navigation speedometer.
enum SpeedometerStyle {
  classic,
  digital,
  analog,
  sport,
  minimal;

  static const storageKey = 'speedometerStyle';

  static SpeedometerStyle fromStorage(Object? value) {
    final id = value?.toString();
    return SpeedometerStyle.values.firstWhere(
      (style) => style.name == id,
      orElse: () => SpeedometerStyle.classic,
    );
  }
}

class SpeedometerWidget extends StatelessWidget {
  final double speedKmh;
  final double size;
  final SpeedometerStyle style;

  /// Current speed limit in km/h, or null when unknown.
  ///
  /// It is NOT rendered here: the limit already has its own road-sign widget
  /// next to the speedometer (`SpeedLimitSign`), and drawing the number twice
  /// was confusing at a glance. It is still needed to colour the dial red when
  /// the driver is over the limit.
  final int? speedLimit;

  const SpeedometerWidget({
    super.key,
    required this.speedKmh,
    this.size = 140,
    this.speedLimit,
    this.style = SpeedometerStyle.classic,
  });

  @override
  Widget build(BuildContext context) {
    final c = RoadstrColors.of(context);
    final imperial = Units.imperial;
    final displaySpeed = Units.toDisplaySpeed(speedKmh);
    final maxDisplay = imperial ? 130.0 : 200.0;
    final double speed = displaySpeed.clamp(0.0, maxDisplay);
    final bool over = speedLimit != null && speedKmh > speedLimit!;
    final accent = over ? Colors.red.shade400 : c.accent;
    final progress = speed / maxDisplay;

    return SizedBox.square(
      dimension: size,
      child: switch (style) {
        SpeedometerStyle.classic => _classic(c, speed, progress, accent, over),
        SpeedometerStyle.digital => _digital(c, speed, progress, accent, over),
        SpeedometerStyle.analog => _analog(c, speed, progress, accent, over),
        SpeedometerStyle.sport => _sport(c, speed, progress, accent, over),
        SpeedometerStyle.minimal => _minimal(c, speed, progress, accent, over),
      },
    );
  }

  Widget _classic(
      RoadstrColors c, double speed, double progress, Color accent, bool over) {
    final fontSize = size * 0.27;
    return Container(
      decoration: BoxDecoration(
        color: c.surface2.withValues(alpha: 0.92),
        shape: BoxShape.circle,
        border: Border.all(
            color: over ? Colors.red.shade400 : c.border,
            width: over ? 1.5 : 0.5),
        boxShadow: [
          BoxShadow(color: Colors.black.withValues(alpha: 0.2), blurRadius: 12)
        ],
      ),
      child: Stack(
        alignment: Alignment.center,
        children: [
          CustomPaint(
            size: Size.square(size),
            painter: _ClassicSpeedPainter(
              progress: progress,
              accent: accent,
              bg: c.surface3,
            ),
          ),
          _DigitalReadout(
            speed: speed,
            unit: Units.speedUnit,
            color: accent,
            secondary: c.textSecondary,
            fontSize: fontSize,
          ),
        ],
      ),
    );
  }

  Widget _digital(
      RoadstrColors c, double speed, double progress, Color accent, bool over) {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            c.surface3,
            c.surface2,
            accent.withValues(alpha: 0.12),
          ],
        ),
        borderRadius: BorderRadius.circular(size * 0.24),
        border: Border.all(color: accent.withValues(alpha: 0.8), width: 1.4),
        boxShadow: [
          BoxShadow(color: Colors.black.withValues(alpha: 0.2), blurRadius: 12)
        ],
      ),
      child: Stack(alignment: Alignment.center, children: [
        CustomPaint(
          size: Size.square(size),
          painter: _DigitalFramePainter(
            progress: progress,
            accent: accent,
            grid: c.border,
          ),
        ),
        _DigitalReadout(
          speed: speed,
          unit: Units.speedUnit,
          color: accent,
          secondary: c.textSecondary,
          fontSize: size * 0.31,
          letterSpacing: 1.2,
        ),
      ]),
    );
  }

  Widget _analog(
      RoadstrColors c, double speed, double progress, Color accent, bool over) {
    return Container(
      decoration: BoxDecoration(
        color: c.surface2.withValues(alpha: 0.96),
        shape: BoxShape.circle,
        border: Border.all(color: over ? accent : c.border, width: 1.2),
        boxShadow: [
          BoxShadow(color: Colors.black.withValues(alpha: 0.2), blurRadius: 12)
        ],
      ),
      child: Stack(alignment: Alignment.center, children: [
        CustomPaint(
          size: Size.square(size),
          painter: _AnalogSpeedPainter(
            progress: progress,
            accent: accent,
            tick: c.textSecondary,
            hub: c.surface3,
          ),
        ),
        Positioned(
          bottom: size * 0.18,
          child: Container(
            padding: EdgeInsets.symmetric(
                horizontal: size * 0.085, vertical: size * 0.025),
            decoration: BoxDecoration(
              color: c.surface3,
              borderRadius: BorderRadius.circular(size * 0.06),
              border: Border.all(color: c.border, width: 0.5),
            ),
            child: Text(
              speed.toStringAsFixed(0),
              style: TextStyle(
                color: accent,
                fontSize: size * 0.16,
                fontWeight: FontWeight.w800,
                height: 1,
              ),
            ),
          ),
        ),
      ]),
    );
  }

  Widget _sport(
      RoadstrColors c, double speed, double progress, Color accent, bool over) {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF111522).withValues(
            alpha: ThemeData.estimateBrightnessForColor(c.surface2) ==
                    Brightness.dark
                ? 0.94
                : 0.9),
        shape: BoxShape.circle,
        border: Border.all(color: accent.withValues(alpha: 0.75), width: 1.2),
        boxShadow: [
          BoxShadow(
              color: accent.withValues(alpha: 0.16),
              blurRadius: 14,
              spreadRadius: 1),
        ],
      ),
      child: Stack(alignment: Alignment.center, children: [
        CustomPaint(
          size: Size.square(size),
          painter: _SportSpeedPainter(
            progress: progress,
            accent: accent,
            inactive: const Color(0xFF31394C),
          ),
        ),
        _DigitalReadout(
          speed: speed,
          unit: Units.speedUnit,
          color: accent,
          secondary: const Color(0xFF9AA6BF),
          fontSize: size * 0.28,
        ),
      ]),
    );
  }

  Widget _minimal(
      RoadstrColors c, double speed, double progress, Color accent, bool over) {
    return Container(
      decoration: BoxDecoration(
        color: c.surface2.withValues(alpha: 0.86),
        shape: BoxShape.circle,
        border: Border.all(color: c.border.withValues(alpha: 0.7), width: 0.6),
      ),
      child: Stack(alignment: Alignment.center, children: [
        CustomPaint(
          size: Size.square(size),
          painter: _MinimalSpeedPainter(
            progress: progress,
            accent: accent,
            inactive: c.textSecondary.withValues(alpha: 0.18),
          ),
        ),
        _DigitalReadout(
          speed: speed,
          unit: Units.speedUnit,
          color: over ? accent : c.textPrimary,
          secondary: c.textSecondary,
          fontSize: size * 0.3,
        ),
      ]),
    );
  }
}

class _DigitalReadout extends StatelessWidget {
  final double speed;
  final String unit;
  final Color color;
  final Color secondary;
  final double fontSize;
  final double? letterSpacing;

  const _DigitalReadout({
    required this.speed,
    required this.unit,
    required this.color,
    required this.secondary,
    required this.fontSize,
    this.letterSpacing,
  });

  @override
  Widget build(BuildContext context) => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            speed.toStringAsFixed(0),
            style: TextStyle(
              color: color,
              fontSize: fontSize,
              fontWeight: FontWeight.bold,
              height: 1,
              letterSpacing: letterSpacing,
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
          Text(unit,
              style: TextStyle(color: secondary, fontSize: fontSize * 0.29)),
        ],
      );
}

class _ClassicSpeedPainter extends CustomPainter {
  final double progress;
  final Color accent;
  final Color bg;

  const _ClassicSpeedPainter(
      {required this.progress, required this.accent, required this.bg});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2 - 12;
    const startAngle = math.pi * 0.75;
    const sweep = math.pi * 1.5;

    canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        startAngle,
        sweep,
        false,
        Paint()
          ..color = bg
          ..strokeWidth = 8
          ..strokeCap = StrokeCap.round
          ..style = PaintingStyle.stroke);

    if (progress > 0) {
      canvas.drawArc(
          Rect.fromCircle(center: center, radius: radius),
          startAngle,
          sweep * progress,
          false,
          Paint()
            ..color = accent
            ..strokeWidth = 8
            ..strokeCap = StrokeCap.round
            ..style = PaintingStyle.stroke);
    }
  }

  @override
  bool shouldRepaint(_ClassicSpeedPainter old) =>
      old.progress != progress || old.accent != accent || old.bg != bg;
}

class _DigitalFramePainter extends CustomPainter {
  final double progress;
  final Color accent;
  final Color grid;

  const _DigitalFramePainter(
      {required this.progress, required this.accent, required this.grid});

  @override
  void paint(Canvas canvas, Size size) {
    final thin = Paint()
      ..color = grid.withValues(alpha: 0.45)
      ..strokeWidth = math.max(1, size.width * 0.008);
    for (final y in [0.28, 0.72]) {
      canvas.drawLine(Offset(size.width * 0.14, size.height * y),
          Offset(size.width * 0.86, size.height * y), thin);
    }
    final barRect = RRect.fromRectAndRadius(
      Rect.fromLTWH(size.width * 0.14, size.height * 0.82, size.width * 0.72,
          size.height * 0.035),
      Radius.circular(size.height * 0.018),
    );
    canvas.drawRRect(barRect, Paint()..color = grid.withValues(alpha: 0.4));
    if (progress > 0) {
      canvas.save();
      canvas.clipRRect(barRect);
      canvas.drawRect(
        Rect.fromLTWH(barRect.left, barRect.top, barRect.width * progress,
            barRect.height),
        Paint()..color = accent,
      );
      canvas.restore();
    }
  }

  @override
  bool shouldRepaint(_DigitalFramePainter old) =>
      old.progress != progress || old.accent != accent || old.grid != grid;
}

class _AnalogSpeedPainter extends CustomPainter {
  final double progress;
  final Color accent;
  final Color tick;
  final Color hub;

  const _AnalogSpeedPainter({
    required this.progress,
    required this.accent,
    required this.tick,
    required this.hub,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final radius = size.shortestSide * 0.39;
    const start = math.pi * 0.75;
    const sweep = math.pi * 1.5;
    for (var i = 0; i <= 20; i++) {
      final angle = start + sweep * i / 20;
      final major = i % 5 == 0;
      final outer = Offset(center.dx + radius * math.cos(angle),
          center.dy + radius * math.sin(angle));
      final innerRadius = radius - size.shortestSide * (major ? 0.095 : 0.055);
      final inner = Offset(center.dx + innerRadius * math.cos(angle),
          center.dy + innerRadius * math.sin(angle));
      canvas.drawLine(
        inner,
        outer,
        Paint()
          ..color = i / 20 <= progress ? accent : tick.withValues(alpha: 0.5)
          ..strokeWidth = size.shortestSide * (major ? 0.024 : 0.012)
          ..strokeCap = StrokeCap.round,
      );
    }

    final needleAngle = start + sweep * progress;
    final needleTip = Offset(
      center.dx + radius * 0.7 * math.cos(needleAngle),
      center.dy + radius * 0.7 * math.sin(needleAngle),
    );
    canvas.drawLine(
      center,
      needleTip,
      Paint()
        ..color = accent
        ..strokeWidth = size.shortestSide * 0.035
        ..strokeCap = StrokeCap.round,
    );
    canvas.drawCircle(center, size.shortestSide * 0.075, Paint()..color = hub);
    canvas.drawCircle(
        center, size.shortestSide * 0.035, Paint()..color = accent);
  }

  @override
  bool shouldRepaint(_AnalogSpeedPainter old) =>
      old.progress != progress ||
      old.accent != accent ||
      old.tick != tick ||
      old.hub != hub;
}

class _SportSpeedPainter extends CustomPainter {
  final double progress;
  final Color accent;
  final Color inactive;

  const _SportSpeedPainter(
      {required this.progress, required this.accent, required this.inactive});

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final radius = size.shortestSide * 0.4;
    const segments = 28;
    const start = math.pi * 0.72;
    const total = math.pi * 1.56;
    const gap = 0.025;
    for (var i = 0; i < segments; i++) {
      final active = (i + 1) / segments <= progress;
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        start + total * i / segments,
        total / segments - gap,
        false,
        Paint()
          ..color = active ? accent : inactive
          ..strokeWidth = size.shortestSide * 0.065
          ..style = PaintingStyle.stroke
          ..strokeCap = StrokeCap.butt,
      );
    }
  }

  @override
  bool shouldRepaint(_SportSpeedPainter old) =>
      old.progress != progress ||
      old.accent != accent ||
      old.inactive != inactive;
}

class _MinimalSpeedPainter extends CustomPainter {
  final double progress;
  final Color accent;
  final Color inactive;

  const _MinimalSpeedPainter(
      {required this.progress, required this.accent, required this.inactive});

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final radius = size.shortestSide * 0.43;
    const start = math.pi * 0.65;
    const sweep = math.pi * 1.7;
    final base = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = size.shortestSide * 0.025
      ..strokeCap = StrokeCap.round;
    canvas.drawArc(Rect.fromCircle(center: center, radius: radius), start,
        sweep, false, base..color = inactive);
    if (progress > 0) {
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        start,
        sweep * progress,
        false,
        base..color = accent,
      );
      final angle = start + sweep * progress;
      canvas.drawCircle(
        Offset(center.dx + radius * math.cos(angle),
            center.dy + radius * math.sin(angle)),
        size.shortestSide * 0.035,
        Paint()..color = accent,
      );
    }
  }

  @override
  bool shouldRepaint(_MinimalSpeedPainter old) =>
      old.progress != progress ||
      old.accent != accent ||
      old.inactive != inactive;
}
