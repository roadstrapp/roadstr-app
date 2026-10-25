import 'package:flutter/material.dart';

import '../models/road_event.dart';

/// Shared road-report pictogram. Keeping it here prevents individual surfaces
/// from falling back to platform-dependent emoji rendering.
class RoadEventCategoryIcon extends StatelessWidget {
  final RoadCategory category;
  final double size;

  const RoadEventCategoryIcon({
    super.key,
    required this.category,
    required this.size,
  });

  @override
  Widget build(BuildContext context) => Image.asset(
        category.iconAsset,
        width: size,
        height: size,
        fit: BoxFit.contain,
        filterQuality: FilterQuality.medium,
        excludeFromSemantics: true,
        errorBuilder: (_, __, ___) => Icon(
          Icons.report_problem_outlined,
          size: size,
          color: category.color,
        ),
      );
}
