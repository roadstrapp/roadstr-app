import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

class SpeedometerWidget extends StatelessWidget {
  final double speedKmh;
  final double maxSpeed;
  final double size;

  const SpeedometerWidget({
    super.key,
    required this.speedKmh,
    this.maxSpeed = 200,
    this.size = 140,
  });

  @override
  Widget build(BuildContext context) {
    final c = RoadstrColors.of(context);
    final double speed = speedKmh.clamp(0.0, maxSpeed);
    final fontSize = size * 0.27;

    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: c.surface2.withOpacity(0.92),
        shape: BoxShape.circle,
        border: Border.all(color: c.border, width: 0.5),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.2), blurRadius: 12)],
      ),
      child: Stack(
        alignment: Alignment.center,
        children: [
          CustomPaint(
            size: Size(size, size),
            painter: _SpeedPainter(
              progress: speed / maxSpeed,
              accent: c.accent,
              bg: c.surface3,
            ),
          ),
          Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                speed.toStringAsFixed(0),
                style: TextStyle(
                  color: c.accent,
                  fontSize: fontSize,
                  fontWeight: FontWeight.bold,
                  height: 1,
                ),
              ),
              Text('km/h',
                  style: TextStyle(color: c.textSecondary,
                      fontSize: fontSize * 0.29)),
            ],
          ),
        ],
      ),
    );
  }
}

class _SpeedPainter extends CustomPainter {
  final double progress;
  final Color accent;
  final Color bg;
  const _SpeedPainter({required this.progress, required this.accent, required this.bg});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2 - 12;
    const startAngle = math.pi * 0.75;
    const sweep = math.pi * 1.5;

    canvas.drawArc(Rect.fromCircle(center: center, radius: radius),
        startAngle, sweep, false,
        Paint()..color = bg..strokeWidth = 8..strokeCap = StrokeCap.round..style = PaintingStyle.stroke);

    if (progress > 0) {
      canvas.drawArc(Rect.fromCircle(center: center, radius: radius),
          startAngle, sweep * progress, false,
          Paint()..color = accent..strokeWidth = 8..strokeCap = StrokeCap.round..style = PaintingStyle.stroke);
    }
  }

  @override
  bool shouldRepaint(_SpeedPainter old) =>
      old.progress != progress || old.accent != accent;
}
