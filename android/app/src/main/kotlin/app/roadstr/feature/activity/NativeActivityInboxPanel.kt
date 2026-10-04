package app.roadstr.feature.activity

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** Silent, dormant activity inbox. Rendering never posts banners, sounds or system notifications. */
@Composable
fun NativeActivityInboxPanel(
    snapshot: NativeActivityInboxSnapshot,
    onClose: (Long) -> Unit,
    onViewed: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot.status == NativeActivityInboxStatus.Hidden) return
    val title = stringResource(R.string.native_activity_title)
    LaunchedEffect(snapshot.revision, snapshot.status, snapshot.unreadCount) {
        if (snapshot.status == NativeActivityInboxStatus.Ready && snapshot.unreadCount > 0) {
            onViewed(snapshot.revision)
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.9f)
                .semantics { paneTitle = title },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                ActivityHeader(title, snapshot.unreadCount) { onClose(snapshot.revision) }
                when (snapshot.status) {
                    NativeActivityInboxStatus.LoggedOut -> ActivityEmptyState(
                        symbol = "♙",
                        title = stringResource(R.string.native_activity_login_required),
                        body = stringResource(R.string.native_activity_login_required_body),
                    )

                    NativeActivityInboxStatus.Empty -> ActivityEmptyState(
                        symbol = "♧",
                        title = stringResource(R.string.native_activity_empty),
                        body = stringResource(R.string.native_activity_empty_body),
                    )

                    NativeActivityInboxStatus.Ready -> ActivityRows(snapshot.items)
                    NativeActivityInboxStatus.Hidden -> Unit
                }
            }
        }
    }
}

@Composable
private fun ActivityHeader(title: String, unreadCount: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (unreadCount > 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    unreadCount.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        TextButton(
            onClick = onClose,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) {
            Text(stringResource(R.string.native_activity_close))
        }
    }
}

@Composable
private fun ActivityEmptyState(symbol: String, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(symbol, style = MaterialTheme.typography.headlineLarge)
            }
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ColumnScope.ActivityRows(items: List<NativeActivityNotification>) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(items = items, key = { index, item -> "$index:${item.id}" }) { _, item ->
            ActivityCard(item, locale)
        }
    }
}

@Composable
private fun ActivityCard(item: NativeActivityNotification, locale: Locale) {
    val category = item.category?.let { stringResource(categoryLabelId(it)) }
    val (symbol, accent, title, body) = when (item.type) {
        NativeActivityNotificationType.Zap -> ActivityCardValues(
            "⚡",
            ZapAmber,
            stringResource(R.string.native_activity_zap_title),
            stringResource(R.string.native_activity_zap_body, item.amountSat ?: 0),
        )

        NativeActivityNotificationType.Confirmed -> ActivityCardValues(
            "✓",
            ConfirmGreen,
            stringResource(R.string.native_activity_confirmed_title),
            stringResource(R.string.native_activity_confirmed_body, category.orEmpty()),
        )

        NativeActivityNotificationType.Denied -> ActivityCardValues(
            "✕",
            DenyRed,
            stringResource(R.string.native_activity_denied_title),
            stringResource(R.string.native_activity_denied_body, category.orEmpty()),
        )
    }
    val timestamp = formatActivityTimestamp(item.createdAtSeconds, locale)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = listOf(title, body, timestamp).joinToString(". ")
            },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            if (item.isRead) 1.dp else 2.dp,
            if (item.isRead) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = accent.copy(alpha = 0.14f),
                contentColor = accent,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(symbol, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!item.isRead) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier.size(7.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {}
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    timestamp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private data class ActivityCardValues(
    val symbol: String,
    val accent: Color,
    val title: String,
    val body: String,
)

internal fun formatActivityTimestamp(epochSeconds: Long, locale: Locale): String = try {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        .format(Date(Math.multiplyExact(epochSeconds, 1_000L)))
} catch (_: RuntimeException) {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(epochSeconds))
}

@StringRes
private fun categoryLabelId(category: RoadCategoryWire): Int = when (category) {
    RoadCategoryWire.POLICE -> R.string.native_road_event_category_police
    RoadCategoryWire.POLICE_STATION -> R.string.native_road_event_category_police_station
    RoadCategoryWire.SPEED_CAMERA -> R.string.native_road_event_category_speed_camera
    RoadCategoryWire.TRAFFIC_JAM -> R.string.native_road_event_category_traffic_jam
    RoadCategoryWire.ACCIDENT -> R.string.native_road_event_category_accident
    RoadCategoryWire.ROAD_CLOSURE -> R.string.native_road_event_category_road_closure
    RoadCategoryWire.CONSTRUCTION -> R.string.native_road_event_category_construction
    RoadCategoryWire.HAZARD -> R.string.native_road_event_category_hazard
    RoadCategoryWire.ROAD_CONDITION -> R.string.native_road_event_category_road_condition
    RoadCategoryWire.POTHOLE -> R.string.native_road_event_category_pothole
    RoadCategoryWire.FOG -> R.string.native_road_event_category_fog
    RoadCategoryWire.ICE -> R.string.native_road_event_category_ice
    RoadCategoryWire.ANIMAL -> R.string.native_road_event_category_animal
    RoadCategoryWire.OTHER -> R.string.native_road_event_category_other
}

private val ZapAmber = Color(0xFFF59E0B)
private val ConfirmGreen = Color(0xFF22C55E)
private val DenyRed = Color(0xFFEF4444)
