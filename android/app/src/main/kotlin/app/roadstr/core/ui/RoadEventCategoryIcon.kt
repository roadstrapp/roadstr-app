package app.roadstr.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.roadstr.R
import app.roadstr.core.protocol.nostr.RoadCategoryWire

@DrawableRes
fun roadEventIconRes(category: RoadCategoryWire): Int = when (category) {
    RoadCategoryWire.POLICE -> R.drawable.road_event_police
    RoadCategoryWire.POLICE_STATION -> R.drawable.road_event_police_station
    RoadCategoryWire.SPEED_CAMERA -> R.drawable.road_event_speed_camera
    RoadCategoryWire.TRAFFIC_JAM -> R.drawable.road_event_traffic_jam
    RoadCategoryWire.ACCIDENT -> R.drawable.road_event_accident
    RoadCategoryWire.ROAD_CLOSURE -> R.drawable.road_event_road_closure
    RoadCategoryWire.CONSTRUCTION -> R.drawable.road_event_construction
    RoadCategoryWire.HAZARD -> R.drawable.road_event_hazard
    RoadCategoryWire.ROAD_CONDITION -> R.drawable.road_event_road_condition
    RoadCategoryWire.POTHOLE -> R.drawable.road_event_pothole
    RoadCategoryWire.FOG -> R.drawable.road_event_fog
    RoadCategoryWire.ICE -> R.drawable.road_event_ice
    RoadCategoryWire.ANIMAL -> R.drawable.road_event_animal
    RoadCategoryWire.SURVEILLANCE_CAMERA -> R.drawable.road_event_surveillance_camera
    RoadCategoryWire.OTHER -> R.drawable.road_event_other
}

@Composable
fun RoadEventCategoryIcon(
    category: RoadCategoryWire,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(roadEventIconRes(category)),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
