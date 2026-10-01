package app.roadstr.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** Map-first home chrome. It emits typed actions and owns no product integration. */
@Composable
fun NativeHomeChrome(
    snapshot: NativeHomeSnapshot,
    onToggleExpanded: (Long) -> Unit,
    onAction: (Long, NativeHomeAction) -> Unit,
    onFavorite: (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!snapshot.visible) return

    Box(modifier = modifier.fillMaxSize()) {
        NativeHomeDashboard(
            snapshot = snapshot,
            onToggleExpanded = { onToggleExpanded(snapshot.revision) },
            onAction = { onAction(snapshot.revision, it) },
            onFavorite = { onFavorite(snapshot.revision, it) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 88.dp),
        )
        NativeHomeBottomBar(
            snapshot = snapshot,
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(role = Role.Button, onClick = onToggleExpanded)
                        .semantics {
                            contentDescription = readyLabel
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (snapshot.expanded) "⌄" else "R",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                }
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
                            if (snapshot.expanded) {
                                R.string.native_home_saved_places
                            } else {
                                R.string.native_home_navigate
                            },
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = { onAction(NativeHomeAction.Navigate) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.native_home_navigate))
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
                                        .heightIn(min = 44.dp)
                                        .clickable(
                                            role = Role.Button,
                                            onClick = { onFavorite(favorite.id) },
                                        ),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = RoundedCornerShape(14.dp),
                                ) {
                                    Text(
                                        text = favorite.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .widthIn(max = 180.dp)
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        HomeActionButton(
                            symbol = "◎",
                            label = stringResource(R.string.native_home_my_location),
                            action = NativeHomeAction.Locate,
                            onAction = onAction,
                            modifier = Modifier.weight(1f),
                        )
                        HomeActionButton(
                            symbol = "P",
                            label = stringResource(R.string.native_home_parking),
                            action = NativeHomeAction.Parking,
                            onAction = onAction,
                            modifier = Modifier.weight(1f),
                        )
                        HomeActionButton(
                            symbol = "●",
                            label = stringResource(R.string.native_home_activity),
                            action = NativeHomeAction.Activity,
                            onAction = onAction,
                            modifier = Modifier.weight(1f),
                        )
                        HomeActionButton(
                            symbol = "!",
                            label = stringResource(R.string.native_home_events),
                            action = NativeHomeAction.Events,
                            onAction = onAction,
                            modifier = Modifier.weight(1f),
                            warning = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionButton(
    symbol: String,
    label: String,
    action: NativeHomeAction,
    onAction: (NativeHomeAction) -> Unit,
    modifier: Modifier,
    warning: Boolean = false,
) {
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { onAction(action) }
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = symbol,
            color = if (warning) Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun NativeHomeBottomBar(
    snapshot: NativeHomeSnapshot,
    onAction: (NativeHomeAction) -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            BottomBarButton(
                symbol = "●",
                label = stringResource(R.string.native_home_notifications),
                badge = snapshot.unreadActivityLabel,
                action = NativeHomeAction.Notifications,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
            BottomBarButton(
                symbol = "♙",
                label = stringResource(R.string.native_home_profile),
                action = NativeHomeAction.Profile,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
            BottomBarButton(
                symbol = "≡",
                label = stringResource(R.string.native_home_menu),
                action = NativeHomeAction.Menu,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomBarButton(
    symbol: String,
    label: String,
    action: NativeHomeAction,
    onAction: (NativeHomeAction) -> Unit,
    modifier: Modifier,
    badge: String? = null,
) {
    Column(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button) { onAction(action) }
            .padding(vertical = 4.dp)
            .semantics {
                contentDescription = if (badge == null) label else "$label, $badge"
                role = Role.Button
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = symbol,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (badge != null) {
                Surface(
                    modifier = Modifier
                        .padding(start = 28.dp)
                        .heightIn(min = 16.dp),
                    color = Color(0xFFEF4444),
                    contentColor = Color.White,
                    shape = CircleShape,
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
