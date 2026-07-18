import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

// SVG/PNG cursors live in assets/cursors/ (pointing UP = direction of
// travel). _UserMarker applies a heading rotation on top.

enum CursorStyle {
  arrow,
  ostrich;

  String get assetPath => switch (this) {
        CursorStyle.arrow => 'assets/cursors/arrow.svg',
        CursorStyle.ostrich => 'assets/cursors/ostrich.png',
      };

  bool get isPng => this == CursorStyle.ostrich;
}

/// Renders the cursor icon at [size]×[size].
///
/// SVG styles render via [SvgPicture.asset]; the ostrich uses [Image.asset]
/// since it is a PNG. Pass [colorFilter] to tint SVG icons; it is ignored
/// for PNG (which already carries its own colours).
class CursorWidget extends StatelessWidget {
  final CursorStyle style;
  final double size;
  final ColorFilter? colorFilter;

  const CursorWidget({
    super.key,
    required this.style,
    this.size = 48,
    this.colorFilter,
  });

  @override
  Widget build(BuildContext context) {
    if (style.isPng) {
      return Image.asset(
        style.assetPath,
        width: size,
        height: size,
        fit: BoxFit.contain,
      );
    }
    return SvgPicture.asset(
      style.assetPath,
      width: size,
      height: size,
      fit: BoxFit.contain,
      colorFilter: colorFilter,
    );
  }
}

/// Wraps [CursorWidget] with a cartoon "poof" transition whenever [style]
/// changes (arrow ↔ ostrich when a walking route starts/ends): the current
/// icon shrinks away behind a puff of smoke, then the new icon grows back
/// out of it — a classic Looney-Tunes-style swap instead of an instant cut.
class AnimatedCursorWidget extends StatefulWidget {
  final CursorStyle style;
  final double size;
  final ColorFilter? colorFilter;

  const AnimatedCursorWidget({
    super.key,
    required this.style,
    this.size = 48,
    this.colorFilter,
  });

  @override
  State<AnimatedCursorWidget> createState() => _AnimatedCursorWidgetState();
}

class _AnimatedCursorWidgetState extends State<AnimatedCursorWidget>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late CursorStyle _displayed;

  static const _duration = Duration(milliseconds: 420);

  @override
  void initState() {
    super.initState();
    _displayed = widget.style;
    _ctrl = AnimationController(vsync: this, duration: _duration);
  }

  @override
  void didUpdateWidget(AnimatedCursorWidget old) {
    super.didUpdateWidget(old);
    if (old.style != widget.style && !_ctrl.isAnimating) {
      _ctrl.forward(from: 0).whenComplete(() {
        if (mounted) setState(() => _displayed = widget.style);
      });
    }
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (context, _) {
        final t = _ctrl.value;
        if (t == 0) {
          return CursorWidget(
              style: _displayed,
              size: widget.size,
              colorFilter: widget.colorFilter);
        }
        // First half: shrink the old icon away. Second half: grow the new
        // one back in. The swap itself happens at t=0.5, hidden under the
        // puff, which peaks in the middle and is transparent at both ends.
        final beforeSwap = t < 0.5;
        final iconStyle = beforeSwap ? _displayed : widget.style;
        final iconT = beforeSwap ? (1 - t * 2) : (t - 0.5) * 2;
        final puffT = 1 - (t - 0.5).abs() * 2; // 0 → 1 → 0

        return SizedBox(
          width: widget.size,
          height: widget.size,
          child: Stack(alignment: Alignment.center, children: [
            if (puffT > 0)
              Opacity(
                opacity: puffT.clamp(0.0, 1.0),
                child: CustomPaint(
                  size: Size.square(widget.size * 1.3),
                  painter: _PuffCloudPainter(progress: 1 - puffT),
                ),
              ),
            Transform.scale(
              scale: 0.25 + 0.75 * iconT,
              child: Opacity(
                opacity: iconT.clamp(0.0, 1.0),
                child: CursorWidget(
                    style: iconStyle,
                    size: widget.size,
                    colorFilter: widget.colorFilter),
              ),
            ),
          ]),
        );
      },
    );
  }
}

/// A handful of overlapping circles that puff outward and fade — a simple,
/// deliberately cartoonish "poof" cloud (no particle system, no physics).
class _PuffCloudPainter extends CustomPainter {
  final double progress; // 0 = just appeared, 1 = fully dispersed
  const _PuffCloudPainter({required this.progress});

  // Relative (dx, dy, radiusFactor) for each puff, clustered around the centre.
  static const _puffs = [
    (0.0, 0.0, 0.34),
    (-0.28, -0.12, 0.22),
    (0.30, -0.08, 0.20),
    (-0.14, 0.26, 0.20),
    (0.20, 0.24, 0.18),
  ];

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final spread = 1.0 + progress * 0.9; // puffs drift outward as they fade
    final alpha = ((1 - progress) * 235).clamp(0, 235).toInt();
    final paint = Paint()
      ..color = Colors.white.withAlpha(alpha)
      ..style = PaintingStyle.fill;
    final outline = Paint()
      ..color = Colors.grey.withAlpha((alpha * 0.5).round())
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.2;

    for (final (dx, dy, rf) in _puffs) {
      final c = center + Offset(dx, dy) * size.shortestSide * spread;
      final r = size.shortestSide * rf * (1 + progress * 0.35);
      canvas.drawCircle(c, r, paint);
      canvas.drawCircle(c, r, outline);
    }
  }

  @override
  bool shouldRepaint(covariant _PuffCloudPainter old) =>
      old.progress != progress;
}
