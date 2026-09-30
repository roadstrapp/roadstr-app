package app.roadstr.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.feature.report.NativeRoadEventAge
import app.roadstr.feature.report.NativeRoadEventAgeUnit

/** Dormant profile surface. All identity, relay, wallet and storage effects stay in callbacks. */
@Composable
fun NativeProfilePanel(
    snapshot: NativeProfileSnapshot,
    onClose: (Long) -> Unit,
    onAmberLogin: (Long) -> Unit,
    onNsecLogin: (Long) -> Unit,
    onCopyNpub: (String) -> Unit,
    onVisibilityChanged: (Long, Boolean) -> Unit,
    onReportSelected: (String) -> Unit,
    onLogout: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot.status == NativeProfileStatus.Hidden) return
    val title = stringResource(R.string.native_profile_title)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.94f)
                .navigationBarsPadding()
                .semantics { paneTitle = title },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item { ProfileHeader(title) { onClose(snapshot.revision) } }
                when (snapshot.status) {
                    NativeProfileStatus.Loading -> item { ProfileLoading() }
                    NativeProfileStatus.LoggedOut -> item {
                        LoggedOutProfile(
                            waitingAmber = snapshot.waitingAmber,
                            onAmberLogin = { onAmberLogin(snapshot.revision) },
                            onNsecLogin = { onNsecLogin(snapshot.revision) },
                        )
                    }

                    NativeProfileStatus.Ready -> {
                        item {
                            ProfileIdentity(
                                snapshot = snapshot,
                                onCopyNpub = onCopyNpub,
                                onVisibilityChanged = {
                                    onVisibilityChanged(snapshot.revision, it)
                                },
                            )
                        }
                        if (snapshot.showPublicProfile) {
                            snapshot.reputationPercent?.let { percent ->
                                item {
                                    ReputationCard(
                                        percent = percent,
                                        level = requireNotNull(snapshot.reputationLevel),
                                    )
                                }
                            }
                            snapshot.balanceSats?.let { sats ->
                                item { BalanceCard(sats) }
                            }
                            item { ReportsHeading() }
                            if (snapshot.reportsLoading) {
                                item { ReportsLoading() }
                            } else if (snapshot.reports.isEmpty()) {
                                item { NoReports() }
                            } else {
                                items(snapshot.reports, key = NativeProfileReportPresentation::id) { report ->
                                    ProfileReportRow(report) { onReportSelected(report.id) }
                                }
                            }
                        }
                        if (snapshot.ownProfile) {
                            item { LogoutButton { onLogout(snapshot.revision) } }
                        }
                    }

                    NativeProfileStatus.Hidden -> Unit
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun ProfileHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        TextButton(
            onClick = onClose,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) {
            Text(stringResource(R.string.native_profile_close))
        }
    }
}

@Composable
private fun ProfileLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.native_profile_loading))
    }
}

@Composable
private fun LoggedOutProfile(
    waitingAmber: Boolean,
    onAmberLogin: () -> Unit,
    onNsecLogin: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        ProfileAvatar(public = false)
        Text(
            text = stringResource(R.string.native_profile_not_connected),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (waitingAmber) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.native_profile_waiting_amber))
            }
        }
        Text(
            text = stringResource(R.string.native_profile_login_title),
            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LoginChoice(
            symbol = "◇",
            title = stringResource(R.string.native_profile_amber_title),
            description = stringResource(R.string.native_profile_amber_description),
            enabled = !waitingAmber,
            warning = false,
            onClick = onAmberLogin,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LoginChoice(
            symbol = "⚿",
            title = stringResource(R.string.native_profile_nsec_title),
            description = stringResource(R.string.native_profile_nsec_description),
            enabled = !waitingAmber,
            warning = true,
            onClick = onNsecLogin,
        )
        InfoCard(stringResource(R.string.native_profile_identity_info))
    }
}

@Composable
private fun LoginChoice(
    symbol: String,
    title: String,
    description: String,
    enabled: Boolean,
    warning: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 64.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (warning) WarningOrange else MaterialTheme.colorScheme.outline,
        ),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(symbol, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ProfileIdentity(
    snapshot: NativeProfileSnapshot,
    onCopyNpub: (String) -> Unit,
    onVisibilityChanged: (Boolean) -> Unit,
) {
    val copyDescription = stringResource(R.string.native_profile_npub_copied)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (!snapshot.showPublicProfile) {
            InfoCard(stringResource(R.string.native_profile_hidden_notice))
        }
        ProfileAvatar(public = snapshot.showPublicProfile)
        snapshot.displayName?.let { name ->
            Text(
                text = name,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = connectionLabel(snapshot),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        snapshot.npub?.let { npub ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.native_profile_public_key),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = npub,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(
                            onClick = { onCopyNpub(npub) },
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    contentDescription = copyDescription
                                },
                        ) {
                            Text("⧉")
                        }
                    }
                }
            }
        }
        if (!snapshot.showPublicProfile) {
            InfoCard(stringResource(R.string.native_profile_visibility_pseudonymous))
        }
        if (snapshot.ownProfile) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.native_profile_visibility_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (snapshot.profilePublic) {
                            stringResource(R.string.native_profile_visibility_clear)
                        } else {
                            stringResource(R.string.native_profile_visibility_pseudonymous)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.native_profile_visibility_description),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = snapshot.profilePublic,
                    onCheckedChange = onVisibilityChanged,
                )
            }
        }
    }
}

@Composable
private fun ProfileAvatar(public: Boolean) {
    val description = stringResource(R.string.native_profile_title)
    Surface(
        modifier = Modifier
            .size(92.dp)
            .semantics {
                contentDescription = description
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (public) "♟" else "♙", style = MaterialTheme.typography.displaySmall)
        }
    }
}

@Composable
private fun connectionLabel(snapshot: NativeProfileSnapshot): String = when {
    !snapshot.showPublicProfile -> stringResource(R.string.native_profile_nostrich)
    !snapshot.ownProfile -> stringResource(R.string.native_profile_nostrich)
    snapshot.flavor == NativeProfileIdentityFlavor.Amber -> {
        stringResource(R.string.native_profile_connected_amber)
    }

    else -> stringResource(R.string.native_profile_connected_nsec)
}

@Composable
private fun ReputationCard(percent: Int, level: NativeProfileReputationLevel) {
    val color = when (level) {
        NativeProfileReputationLevel.High -> ConfirmGreen
        NativeProfileReputationLevel.Medium -> WarningOrange
        NativeProfileReputationLevel.Low -> DenyRed
    }
    val label = stringResource(
        when (level) {
            NativeProfileReputationLevel.High -> R.string.native_profile_reputation_high
            NativeProfileReputationLevel.Medium -> R.string.native_profile_reputation_medium
            NativeProfileReputationLevel.Low -> R.string.native_profile_reputation_low
        },
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✓", color = color, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.native_profile_reputation, label),
                    fontWeight = FontWeight.Bold,
                )
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = color,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("$percent%", color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BalanceCard(sats: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BitcoinOrange),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚡", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.native_profile_zap_balance), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.native_profile_zap_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("$sats sat", color = BitcoinOrange, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReportsHeading() {
    Text(
        text = stringResource(R.string.native_profile_reports),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReportsLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(stringResource(R.string.native_profile_loading))
    }
}

@Composable
private fun NoReports() {
    InfoCard(stringResource(R.string.native_profile_no_reports))
}

@Composable
private fun ProfileReportRow(report: NativeProfileReportPresentation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(categoryEmoji(report.category), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(categoryLabel(report.category), fontWeight = FontWeight.SemiBold)
            report.address?.let {
                Text(
                    it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                ageLabel(report.age),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            report.comment?.let {
                Text(
                    it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("↑ ${report.confirmations}", color = ConfirmGreen)
            Text("↓ ${report.denials}", color = DenyRed)
            report.reliabilityPercent?.let { Text("$it%", style = MaterialTheme.typography.labelSmall) }
            if (report.zapSats > 0) {
                Text("⚡${report.zapSats}", color = BitcoinOrange, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun LogoutButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)
            .sizeIn(minHeight = 48.dp),
        border = BorderStroke(1.dp, DenyRed),
    ) {
        Text(stringResource(R.string.native_profile_logout), color = DenyRed)
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ageLabel(age: NativeRoadEventAge): String = stringResource(
    when (age.unit) {
        NativeRoadEventAgeUnit.Minutes -> R.string.native_road_event_minutes_ago
        NativeRoadEventAgeUnit.Hours -> R.string.native_road_event_hours_ago
        NativeRoadEventAgeUnit.Days -> R.string.native_road_event_days_ago
    },
    age.value,
)

@Composable
private fun categoryLabel(category: RoadCategoryWire): String = stringResource(categoryLabelId(category))

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

private fun categoryEmoji(category: RoadCategoryWire): String = when (category) {
    RoadCategoryWire.POLICE -> "👮"
    RoadCategoryWire.POLICE_STATION -> "🏛️"
    RoadCategoryWire.SPEED_CAMERA -> "📷"
    RoadCategoryWire.TRAFFIC_JAM -> "🚗"
    RoadCategoryWire.ACCIDENT -> "💥"
    RoadCategoryWire.ROAD_CLOSURE -> "🚫"
    RoadCategoryWire.CONSTRUCTION -> "🚧"
    RoadCategoryWire.HAZARD -> "⚠️"
    RoadCategoryWire.ROAD_CONDITION -> "🛣️"
    RoadCategoryWire.POTHOLE -> "🕳️"
    RoadCategoryWire.FOG -> "🌫️"
    RoadCategoryWire.ICE -> "🧊"
    RoadCategoryWire.ANIMAL -> "🦌"
    RoadCategoryWire.OTHER -> "ℹ️"
}

private val ConfirmGreen = Color(0xFF22C55E)
private val DenyRed = Color(0xFFEF4444)
private val WarningOrange = Color(0xFFF59E0B)
private val BitcoinOrange = Color(0xFFF7931A)
