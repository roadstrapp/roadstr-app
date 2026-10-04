package app.roadstr.feature.home

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.ui.RoadstrAccentButton
import app.roadstr.core.ui.RoadstrGlassBox
import app.roadstr.core.ui.RoadstrGlassLevel
import app.roadstr.feature.profile.NativeProfilePictureLoader

/** Map-first chrome matching main's glass dashboard and bottom navigation. */
@Composable
fun NativeHomeChrome(
    snapshot: NativeHomeSnapshot,
    profilePictureUrl: String? = null,
    onToggleExpanded: (Long) -> Unit,
    onAction: (Long, NativeHomeAction) -> Unit,
    onFavorite: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!snapshot.visible) return
    var profileBitmap by remember(profilePictureUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(profilePictureUrl) {
        profileBitmap = profilePictureUrl?.let { NativeProfilePictureLoader.load(it) }
    }
    Box(modifier = modifier.fillMaxSize()) {
        NativeHomeDashboard(
            snapshot = snapshot,
            onToggleExpanded = { onToggleExpanded(snapshot.revision) },
            onAction = { onAction(snapshot.revision, it) },
            onFavorite = { onFavorite(snapshot.revision, it) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 82.dp),
        )
        NativeHomeBottomBar(
            snapshot = snapshot,
            profileBitmap = profileBitmap,
            onAction = { onAction(snapshot.revision, it) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun NativeHomeDashboard(
    snapshot: NativeHomeSnapshot,
    onToggleExpanded: () -> Unit,
    onAction: (NativeHomeAction) -> Unit,
    onFavorite: (String) -> Unit,
    modifier: Modifier,
) {
    val readyLabel = stringResource(R.string.native_home_ready)
    RoadstrGlassBox(
        modifier = modifier.fillMaxWidth().widthIn(max = 640.dp),
        level = RoadstrGlassLevel.Strong,
        shape = RoundedCornerShape(24.dp),
        padding = 12.dp,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RoadstrBrandMark(snapshot.expanded, readyLabel, onToggleExpanded)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = readyLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(
                            if (snapshot.expanded) R.string.native_home_saved_places
                            else R.string.native_home_navigate,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                RoadstrAccentButton(onClick = { onAction(NativeHomeAction.Navigate) }) {
                    Icon(Icons.Outlined.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(7.dp))
                    Text(stringResource(R.string.native_home_navigate), fontWeight = FontWeight.Bold)
                }
            }
            AnimatedVisibility(visible = snapshot.expanded) {
                Column {
                    if (snapshot.favorites.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.native_home_saved_places),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(snapshot.favorites, key = NativeHomeFavorite::id) { favorite ->
                                Surface(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onFavorite(favorite.id) },
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(
                                        text = favorite.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 180.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        HomeActionButton(Icons.Outlined.MyLocation, stringResource(R.string.native_home_my_location), NativeHomeAction.Locate, onAction)
                        HomeActionButton(Icons.Outlined.LocalParking, stringResource(R.string.native_home_parking), NativeHomeAction.Parking, onAction)
                        HomeActionButton(Icons.Outlined.History, stringResource(R.string.native_home_activity), NativeHomeAction.Activity, onAction)
                        HomeActionButton(Icons.Outlined.ReportProblem, stringResource(R.string.native_home_events), NativeHomeAction.Events, onAction, warning = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoadstrBrandMark(expanded: Boolean, label: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember {
        runCatching {
            context.assets.open("icons/app_icon.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { contentDescription = label; role = androidx.compose.ui.semantics.Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = label, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)))
        }
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).size(18.dp),
        )
    }
}

@Composable
private fun RowScope.HomeActionButton(
    icon: ImageVector,
    label: String,
    action: NativeHomeAction,
    onAction: (NativeHomeAction) -> Unit,
    warning: Boolean = false,
) {
    Column(
        modifier = Modifier.weight(1f).heightIn(min = 62.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { onAction(action) }
            .semantics { contentDescription = label; role = androidx.compose.ui.semantics.Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            color = (if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun NativeHomeBottomBar(
    snapshot: NativeHomeSnapshot,
    profileBitmap: ImageBitmap?,
    onAction: (NativeHomeAction) -> Unit,
    modifier: Modifier,
) {
    RoadstrGlassBox(
        modifier = modifier.fillMaxWidth().widthIn(max = 640.dp),
        level = RoadstrGlassLevel.Strong,
        shape = RoundedCornerShape(24.dp),
        padding = 7.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            BottomBarButton(Icons.Outlined.NotificationsNone, stringResource(R.string.native_home_notifications), NativeHomeAction.Notifications, onAction, snapshot.unreadActivityLabel)
            BottomBarButton(
                Icons.Outlined.AccountCircle,
                stringResource(R.string.native_home_profile),
                NativeHomeAction.Profile,
                onAction,
                profileBitmap = profileBitmap,
            )
            BottomBarButton(Icons.Outlined.Menu, stringResource(R.string.native_home_menu), NativeHomeAction.Menu, onAction)
        }
    }
}

@Composable
private fun RowScope.BottomBarButton(
    icon: ImageVector,
    label: String,
    action: NativeHomeAction,
    onAction: (NativeHomeAction) -> Unit,
    badge: String? = null,
    profileBitmap: ImageBitmap? = null,
) {
    Column(
        modifier = Modifier.weight(1f).heightIn(min = 58.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { onAction(action) }
            .semantics { contentDescription = if (badge == null) label else "$label, $badge"; role = androidx.compose.ui.semantics.Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BadgedBox(badge = { if (badge != null) Badge { Text(badge) } }) {
            Surface(
                modifier = Modifier.size(width = 38.dp, height = 31.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (profileBitmap != null) {
                        Image(
                            bitmap = profileBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    }
}
