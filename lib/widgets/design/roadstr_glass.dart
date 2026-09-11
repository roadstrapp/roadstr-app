import 'package:flutter/material.dart';

import '../../theme/app_theme.dart';

/// The compact 4/8 rhythm used by map chrome. Keeping these values named
/// prevents every floating control from drifting into its own spacing system.
abstract final class RoadstrSpacing {
  static const xxs = 4.0;
  static const xs = 8.0;
  static const sm = 12.0;
  static const md = 16.0;
  static const lg = 20.0;
  static const xl = 24.0;
  static const xxl = 32.0;
}

abstract final class RoadstrRadius {
  static const small = 12.0;
  static const medium = 16.0;
  static const large = 22.0;
  static const xLarge = 28.0;
  static const pill = 999.0;
}

abstract final class RoadstrMotion {
  static const press = Duration(milliseconds: 120);
  static const quick = Duration(milliseconds: 180);
  static const standard = Duration(milliseconds: 260);
  static const emphasized = Duration(milliseconds: 320);

  static const standardCurve = Curves.easeOutCubic;
}

enum RoadstrGlassLevel { light, medium, strong }

/// A clipped, bounded glass surface intended for controls floating over the
/// map. The translucent gradient suggests glass without a backdrop filter:
/// Android platform-map frames therefore stay outside Flutter's blur pipeline.
class RoadstrGlassSurface extends StatelessWidget {
  final RoadstrColors colors;
  final Widget child;
  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final double? width;
  final double? height;
  final BorderRadius borderRadius;
  final RoadstrGlassLevel level;
  final Color? borderColor;

  const RoadstrGlassSurface({
    super.key,
    required this.colors,
    required this.child,
    this.padding,
    this.margin,
    this.width,
    this.height,
    this.borderRadius = const BorderRadius.all(
      Radius.circular(RoadstrRadius.large),
    ),
    this.level = RoadstrGlassLevel.medium,
    this.borderColor,
  });

  @override
  Widget build(BuildContext context) {
    final fill = switch (level) {
      RoadstrGlassLevel.light => colors.mapGlassLight,
      RoadstrGlassLevel.medium => colors.mapGlassMedium,
      RoadstrGlassLevel.strong => colors.mapGlassStrong,
    };
    final content = DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color.lerp(fill, Colors.white, 0.055)!,
            fill,
            Color.lerp(fill, colors.accent, 0.055)!,
          ],
          stops: const [0, 0.54, 1],
        ),
        borderRadius: borderRadius,
        border: Border.all(
          color: borderColor ?? colors.mapGlassBorder,
          width: 0.9,
        ),
      ),
      child: padding == null ? child : Padding(padding: padding!, child: child),
    );

    return RepaintBoundary(
      child: Container(
        margin: margin,
        width: width,
        height: height,
        decoration: BoxDecoration(
          borderRadius: borderRadius,
          boxShadow: colors.mapGlassShadow(
            strong: level == RoadstrGlassLevel.strong,
          ),
        ),
        child: ClipRRect(
          borderRadius: borderRadius,
          child: content,
        ),
      ),
    );
  }
}

/// Shared tactile response for map controls. It changes only presentation;
/// the original callback still owns every action and side effect.
class RoadstrPressable extends StatefulWidget {
  final Widget child;
  final VoidCallback? onTap;
  final String? semanticLabel;
  final BorderRadius borderRadius;

  const RoadstrPressable({
    super.key,
    required this.child,
    required this.onTap,
    this.semanticLabel,
    this.borderRadius = const BorderRadius.all(
      Radius.circular(RoadstrRadius.medium),
    ),
  });

  @override
  State<RoadstrPressable> createState() => _RoadstrPressableState();
}

class _RoadstrPressableState extends State<RoadstrPressable> {
  bool _pressed = false;

  void _setPressed(bool value) {
    if (_pressed == value || widget.onTap == null) return;
    setState(() => _pressed = value);
  }

  @override
  Widget build(BuildContext context) => Semantics(
        button: widget.onTap != null,
        enabled: widget.onTap != null,
        label: widget.semanticLabel,
        child: AnimatedScale(
          scale: _pressed ? 0.955 : 1,
          duration: RoadstrMotion.press,
          curve: Curves.easeOut,
          child: GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: widget.onTap,
            onTapDown: (_) => _setPressed(true),
            onTapUp: (_) => _setPressed(false),
            onTapCancel: () => _setPressed(false),
            child: widget.child,
          ),
        ),
      );
}

/// Uses the real launcher artwork as a compact signature rather than drawing
/// an unrelated pseudo-logo for the map shell.
class RoadstrBrandMark extends StatelessWidget {
  final double size;
  const RoadstrBrandMark({super.key, this.size = 34});

  @override
  Widget build(BuildContext context) {
    final colors = RoadstrColors.of(context);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(size * 0.29),
        border: Border.all(color: Colors.white.withValues(alpha: 0.16)),
        boxShadow: [
          BoxShadow(
            color: colors.accentGlow.withValues(alpha: 0.24),
            blurRadius: 14,
            spreadRadius: -2,
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(size * 0.26),
        child: Image.asset('assets/icons/app_icon.png', fit: BoxFit.cover),
      ),
    );
  }
}
