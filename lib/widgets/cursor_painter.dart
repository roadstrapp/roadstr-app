import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

// SVG cursors live in assets/cursors/*.svg (all pointing UP = direction of
// travel). The ostrich is a PNG (assets/cursors/ostrich.png).
// _UserMarker applies a heading rotation on top.

enum CursorStyle {
  arrow,
  ostrich,
  muscleCar,
  suv,
  compact,
  vespa,
  racingMoto,
  bicycle;

  String get label {
    switch (this) {
      case CursorStyle.arrow:      return 'Arrow';
      case CursorStyle.ostrich:    return 'Ostrich';
      case CursorStyle.muscleCar:  return 'Muscle';
      case CursorStyle.suv:        return 'SUV';
      case CursorStyle.compact:    return 'Compact';
      case CursorStyle.vespa:      return 'Vespa';
      case CursorStyle.racingMoto: return 'Moto';
      case CursorStyle.bicycle:    return 'Bike';
    }
  }

  String get emoji {
    switch (this) {
      case CursorStyle.arrow:      return '⬆️';
      case CursorStyle.ostrich:    return '🦤';
      case CursorStyle.muscleCar:  return '🏎️';
      case CursorStyle.suv:        return '🚙';
      case CursorStyle.compact:    return '🚗';
      case CursorStyle.vespa:      return '🛵';
      case CursorStyle.racingMoto: return '🏍️';
      case CursorStyle.bicycle:    return '🚲';
    }
  }

  String get assetPath {
    switch (this) {
      case CursorStyle.arrow:      return 'assets/cursors/arrow.svg';
      case CursorStyle.ostrich:    return 'assets/cursors/ostrich.png';
      case CursorStyle.muscleCar:  return 'assets/cursors/muscle_car.svg';
      case CursorStyle.suv:        return 'assets/cursors/suv.svg';
      case CursorStyle.compact:    return 'assets/cursors/compact.svg';
      case CursorStyle.vespa:      return 'assets/cursors/vespa.svg';
      case CursorStyle.racingMoto: return 'assets/cursors/racing_moto.svg';
      case CursorStyle.bicycle:    return 'assets/cursors/bicycle.svg';
    }
  }

  bool get isPng => this == CursorStyle.ostrich;

  /// Cursors the user can pick in Settings.
  /// The ostrich is excluded — it activates automatically on foot mode.
  static const List<CursorStyle> selectable = [
    arrow, muscleCar, suv, compact, vespa, racingMoto, bicycle,
  ];
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
