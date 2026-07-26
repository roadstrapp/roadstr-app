// One end of a planned trip: a named position the user picked as origin or
// destination in the route planner.
import 'package:latlong2/latlong.dart';

class WayPoint {
  final String label;
  final LatLng position;
  const WayPoint(this.label, this.position);
}

// ── Route planner bar ─────────────────────────────────────────────────────────
