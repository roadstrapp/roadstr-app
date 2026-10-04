package app.roadstr.feature.place

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import app.roadstr.R
import app.roadstr.core.search.OsmEvConnector
import app.roadstr.core.search.OsmPlaceDetails
import app.roadstr.core.search.OsmPlaceKind
import java.net.URI

/** Bounded place-information sheet packaged only in the private native shell. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NativePlaceDetailsPanel(
    snapshot: NativePlaceUiSnapshot,
    onCancel: () -> Unit,
    onNavigate: () -> Unit,
    onOpenWebsite: (URI) -> Unit,
    onOpenArticle: (URI) -> Unit,
    onSearchWeb: (String) -> Unit,
    modifier: Modifier = Modifier,
    searchEngineName: String = "Qwant",
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val paneTitle = snapshot.title
        ?: androidx.compose.ui.res.stringResource(R.string.native_place_loading)
    AnimatedVisibility(
        visible = snapshot.status != NativePlaceUiStatus.Hidden,
        modifier = modifier,
        enter = fadeIn(tween(240)) + slideInVertically(tween(360)) { it / 4 },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(300)) { it / 5 },
    ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.8f)
                .offset { IntOffset(0, dragOffset.toInt()) }
                .semantics { this.paneTitle = paneTitle },
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                PlaceDragHandle(
                    modifier = Modifier.pointerInput(onCancel) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                            },
                            onDragEnd = {
                                if (dragOffset >= 120.dp.toPx()) onCancel()
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f },
                        )
                    },
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    if (snapshot.status == NativePlaceUiStatus.Loading && snapshot.title == null) {
                        PlaceLoadingState()
                    } else {
                        PlaceHeader(snapshot)
                    }
                    snapshot.article?.let { article ->
                        if (article.extract.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = article.extract,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    snapshot.details?.let { details ->
                        Spacer(modifier = Modifier.height(10.dp))
                        OsmDetailsCard(details, onOpenWebsite)
                    }
                    if (snapshot.status == NativePlaceUiStatus.Ready) {
                        PlaceLearnMore(
                            articleUrl = snapshot.article?.pageUrl,
                            wikiQuery = snapshot.wikiQuery,
                            searchEngineName = searchEngineName,
                            onOpenArticle = onOpenArticle,
                            onSearchWeb = onSearchWeb,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                PlaceActions(onCancel, onNavigate)
            }
        }
    }
    }
}

@Composable
private fun PlaceDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(width = 40.dp, height = 4.dp),
            color = MaterialTheme.colorScheme.outline,
            shape = RoundedCornerShape(2.dp),
        ) {}
    }
}

@Composable
private fun PlaceLoadingState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            androidx.compose.ui.res.stringResource(R.string.native_place_loading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PlaceHeader(snapshot: NativePlaceUiSnapshot) {
    val title = snapshot.title ?: return
    Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    val detailsName = snapshot.details?.name
    if (snapshot.address != null && snapshot.address != detailsName && snapshot.address != title) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = snapshot.address,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    snapshot.opening?.let { opening ->
        Spacer(modifier = Modifier.height(6.dp))
        PlaceOpeningBadge(opening)
    }
}

@Composable
private fun PlaceOpeningBadge(opening: NativePlaceOpeningPresentation) {
    if (opening.state == NativePlaceOpeningState.Unknown) {
        Row(verticalAlignment = Alignment.Top) {
            Text("◷", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = opening.raw,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        return
    }
    val open = opening.state == NativePlaceOpeningState.Open
    val color = if (open) Color(0xFF2F_BF71) else Color(0xFFE0_533D)
    val label = androidx.compose.ui.res.stringResource(
        if (open) R.string.native_place_open_now else R.string.native_place_closed_now,
    )
    val detail = opening.changeLabel?.let { change ->
        androidx.compose.ui.res.stringResource(
            if (open) R.string.native_place_closes_at else R.string.native_place_opens_at,
            change,
        )
    }
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(8.dp), color = color, shape = RoundedCornerShape(4.dp)) {}
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        if (detail != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "· $detail",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OsmDetailsCard(details: OsmPlaceDetails, onOpenWebsite: (URI) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌖", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.native_place_osm_details),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = details.category,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            accessLabel(details.access)?.let { (label, restricted) ->
                Spacer(modifier = Modifier.height(10.dp))
                PlaceAccessBanner(label, restricted)
            }
            details.description?.let { description ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = description,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val features = placeFeatures(details)
            if (features.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    features.forEach { feature -> PlaceFeatureChip(feature) }
                }
            }
            if (details.kind == OsmPlaceKind.Parking) {
                PlaceParkingDetails(details)
            }
            if (details.kind == OsmPlaceKind.ChargingStation) {
                PlaceChargingDetails(details)
            }
            val contact = listOfNotNull(details.phone, details.email).joinToString(" · ")
            if (
                details.operatorName != null ||
                !details.cuisine.isNullOrEmpty() ||
                contact.isNotEmpty() ||
                details.address != null ||
                details.website != null
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
            }
            details.operatorName?.let {
                PlaceDetailLine(R.string.native_place_operator, it)
            }
            details.cuisine?.takeIf(String::isNotEmpty)?.let {
                PlaceDetailLine(R.string.native_place_cuisine, it)
            }
            contact.takeIf(String::isNotEmpty)?.let {
                PlaceDetailLine(R.string.native_place_contact, it)
            }
            details.address?.let {
                PlaceDetailLine(R.string.native_place_address, it)
            }
            details.website?.let { website ->
                TextButton(
                    onClick = { onOpenWebsite(website) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = website.host
                        },
                ) {
                    Text(
                        text = "${androidx.compose.ui.res.stringResource(R.string.native_place_website)}: ${website.host}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private data class PlaceFeature(val symbol: String, val label: String, val color: Color)

@Composable
private fun placeFeatures(details: OsmPlaceDetails): List<PlaceFeature> = buildList {
    if (details.acceptsLightning) {
        add(PlaceFeature("⚡", androidx.compose.ui.res.stringResource(R.string.native_place_lightning), Color(0xFFF7_931A)))
    }
    if (details.acceptsBitcoin) {
        add(PlaceFeature("₿", androidx.compose.ui.res.stringResource(R.string.native_place_bitcoin), Color(0xFFF7_931A)))
    }
    details.stars?.let { add(PlaceFeature("★", "$it ★", Color(0xFFE4_A11B))) }
    wheelchairLabel(details.wheelchair)?.let { (label, color) ->
        add(PlaceFeature("♿", label, color))
    }
    if ("diesel" in details.fuels) {
        add(PlaceFeature("⛽", androidx.compose.ui.res.stringResource(R.string.native_place_diesel), MaterialTheme.colorScheme.primary))
    }
    if ("octane_95" in details.fuels) {
        add(PlaceFeature("⛽", androidx.compose.ui.res.stringResource(R.string.native_place_petrol95), MaterialTheme.colorScheme.primary))
    }
    smokingLabel(details.smoking)?.let { add(PlaceFeature("◌", it, MaterialTheme.colorScheme.onSurfaceVariant)) }
    if (details.outdoorSeating == "yes") {
        add(PlaceFeature("☀", androidx.compose.ui.res.stringResource(R.string.native_place_outdoor_seating), Color(0xFF3A_8D6D)))
    }
    if (details.takeaway == "yes" || details.takeaway == "only") {
        val label = androidx.compose.ui.res.stringResource(
            if (details.takeaway == "only") R.string.native_place_takeaway_only else R.string.native_place_takeaway,
        )
        add(PlaceFeature("▣", label, MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun PlaceFeatureChip(feature: PlaceFeature) {
    Surface(color = feature.color.copy(alpha = 0.10f), shape = RoundedCornerShape(20.dp)) {
        Text(
            text = "${feature.symbol}  ${feature.label}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = feature.color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PlaceParkingDetails(details: OsmPlaceDetails) {
    val type = parkingTypeLabel(details.parkingType)
    val fee = feeLabel(details.fee)
    if (type == null && fee == null && details.charge == null && details.capacity == null && details.maxStay == null) {
        return
    }
    PlaceDetailSection(R.string.native_place_parking) {
        type?.let { PlaceDetailLine(R.string.native_place_category, it) }
        fee?.let { PlaceDetailLine(R.string.native_place_fee, it) }
        details.charge?.let { PlaceDetailLine(R.string.native_place_price, it) }
        details.capacity?.let { PlaceDetailLine(R.string.native_place_capacity, it.toString()) }
        details.maxStay?.let { PlaceDetailLine(R.string.native_place_max_stay, it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceChargingDetails(details: OsmPlaceDetails) {
    val fee = feeLabel(details.fee)
    if (details.evConnectors.isEmpty() && fee == null && details.charge == null && details.capacity == null) {
        return
    }
    PlaceDetailSection(R.string.native_place_charging) {
        if (details.evConnectors.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                details.evConnectors.forEach { connector ->
                    PlaceFeatureChip(
                        PlaceFeature("⌁", connectorLabel(connector), Color(0xFF2E_9D67)),
                    )
                }
            }
        }
        fee?.let { PlaceDetailLine(R.string.native_place_fee, it) }
        details.charge?.let { PlaceDetailLine(R.string.native_place_price, it) }
        details.capacity?.let { PlaceDetailLine(R.string.native_place_capacity, it.toString()) }
    }
}

@Composable
private fun PlaceDetailSection(@StringRes title: Int, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = androidx.compose.ui.res.stringResource(title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun PlaceDetailLine(@StringRes label: Int, value: String) {
    Text(
        text = "${androidx.compose.ui.res.stringResource(label)}: $value",
        modifier = Modifier.padding(top = 5.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun PlaceAccessBanner(label: String, restricted: Boolean) {
    val color = if (restricted) Color(0xFFD5_5245) else Color(0xFFE0_9A2D)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.11f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
    ) {
        Text(
            text = "${if (restricted) "▣" else "◇"}  $label",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PlaceLearnMore(
    articleUrl: URI?,
    wikiQuery: String?,
    searchEngineName: String,
    onOpenArticle: (URI) -> Unit,
    onSearchWeb: (String) -> Unit,
) {
    val label = when {
        articleUrl != null -> androidx.compose.ui.res.stringResource(R.string.native_place_read_wikipedia)
        wikiQuery != null -> androidx.compose.ui.res.stringResource(
            R.string.native_place_search_engine,
            searchEngineName,
        )
        else -> return
    }
    TextButton(
        onClick = {
            if (articleUrl != null) onOpenArticle(articleUrl) else onSearchWeb(requireNotNull(wikiQuery))
        },
        modifier = Modifier
            .padding(top = 4.dp)
            .sizeIn(minHeight = 48.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun PlaceActions(onCancel: () -> Unit, onNavigate: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.sizeIn(minHeight = 48.dp),
        ) {
            Text(androidx.compose.ui.res.stringResource(R.string.native_place_cancel))
        }
        Button(
            onClick = onNavigate,
            modifier = Modifier
                .weight(1f)
                .sizeIn(minHeight = 48.dp),
        ) {
            Text("➤  ${androidx.compose.ui.res.stringResource(R.string.native_place_navigate)}")
        }
    }
}

@Composable
private fun accessLabel(value: String?): Pair<String, Boolean>? = when (value) {
    "private" -> androidx.compose.ui.res.stringResource(R.string.native_place_access_private) to true
    "customers" -> androidx.compose.ui.res.stringResource(R.string.native_place_access_customers) to false
    "permit" -> androidx.compose.ui.res.stringResource(R.string.native_place_access_permit) to true
    "no" -> androidx.compose.ui.res.stringResource(R.string.native_place_access_no) to true
    "destination" -> androidx.compose.ui.res.stringResource(R.string.native_place_access_destination) to false
    else -> null
}

@Composable
private fun wheelchairLabel(value: String?): Pair<String, Color>? = when (value) {
    "yes", "designated" -> androidx.compose.ui.res.stringResource(R.string.native_place_wheelchair_yes) to Color(0xFF2E_9D67)
    "limited" -> androidx.compose.ui.res.stringResource(R.string.native_place_wheelchair_limited) to Color(0xFFE0_9A2D)
    "no" -> androidx.compose.ui.res.stringResource(R.string.native_place_wheelchair_no) to Color(0xFFD5_5245)
    else -> null
}

@Composable
private fun parkingTypeLabel(value: String?): String? = when (value) {
    "surface" -> androidx.compose.ui.res.stringResource(R.string.native_place_parking_surface)
    "underground" -> androidx.compose.ui.res.stringResource(R.string.native_place_parking_underground)
    "multi-storey" -> androidx.compose.ui.res.stringResource(R.string.native_place_parking_multi_storey)
    "street_side" -> androidx.compose.ui.res.stringResource(R.string.native_place_parking_street_side)
    "lane" -> androidx.compose.ui.res.stringResource(R.string.native_place_parking_lane)
    "rooftop" -> androidx.compose.ui.res.stringResource(R.string.native_place_parking_rooftop)
    else -> null
}

@Composable
private fun feeLabel(value: String?): String? = when (value?.trim()?.lowercase()) {
    "no" -> androidx.compose.ui.res.stringResource(R.string.native_place_free)
    "yes" -> androidx.compose.ui.res.stringResource(R.string.native_place_paid)
    null -> null
    else -> value
}

@Composable
private fun smokingLabel(value: String?): String? = when (value) {
    "yes" -> androidx.compose.ui.res.stringResource(R.string.native_place_smoking_allowed)
    "outside" -> androidx.compose.ui.res.stringResource(R.string.native_place_smoking_outside)
    "separated", "isolated", "dedicated" ->
        androidx.compose.ui.res.stringResource(R.string.native_place_smoking_areas)
    "no" -> androidx.compose.ui.res.stringResource(R.string.native_place_smoke_free)
    else -> null
}

@Composable
private fun connectorLabel(connector: OsmEvConnector): String {
    val type = when (connector.type) {
        "type2" -> androidx.compose.ui.res.stringResource(R.string.native_place_connector_type2)
        "chademo" -> androidx.compose.ui.res.stringResource(R.string.native_place_connector_chademo)
        "type2_combo" -> androidx.compose.ui.res.stringResource(R.string.native_place_connector_ccs)
        else -> connector.type
    }
    return listOfNotNull(
        type,
        connector.count?.let { "× $it" },
        connector.output,
    ).joinToString(" · ")
}
