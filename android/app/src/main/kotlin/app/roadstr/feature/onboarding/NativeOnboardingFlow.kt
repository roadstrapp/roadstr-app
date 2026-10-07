package app.roadstr.feature.onboarding

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.roadstr.R
import app.roadstr.core.ui.RoadstrAccentButton
import app.roadstr.core.ui.RoadstrSwitch
import app.roadstr.core.ui.RoadstrGlassBox
import app.roadstr.core.ui.RoadstrGlassLevel
import app.roadstr.feature.settings.ActionButton
import app.roadstr.feature.settings.SettingsCard
import app.roadstr.feature.settings.SettingsSection
import kotlinx.coroutines.flow.distinctUntilChanged

/** Full-screen but dormant first-launch surface. All effects leave through typed callbacks. */
@Composable
fun NativeOnboardingFlow(
    snapshot: NativeOnboardingSnapshot,
    onPageSelected: (Long, NativeOnboardingPage) -> Unit,
    onAmberLogin: () -> Unit,
    onBunkerLogin: () -> Unit,
    onProfileVisibilityChanged: (Long, Boolean) -> Unit,
    onRequestLocation: () -> Unit,
    onDownloadVoice: () -> Unit,
    onOpenDisclosure: (Long) -> Unit,
    onAcceptDisclosure: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Offered on the recovery screen only by a host that can try the import again or go on without it. */
    onRetryMigration: (() -> Unit)? = null,
    onSkipMigration: (() -> Unit)? = null,
) {
    when (snapshot.status) {
        NativeStartupGateStatus.Hidden,
        NativeStartupGateStatus.Ready,
        -> return

        NativeStartupGateStatus.Migrating -> StartupMessage(
            title = stringResource(R.string.native_onboarding_loading),
            body = null,
            loading = true,
            modifier = modifier,
        )

        NativeStartupGateStatus.RecoveryRequired -> if (onRetryMigration != null && onSkipMigration != null) {
            MigrationFailedMessage(onRetryMigration, onSkipMigration, modifier)
        } else {
            StartupMessage(
                title = stringResource(R.string.native_onboarding_recovery_title),
                body = stringResource(R.string.native_onboarding_recovery_body),
                loading = false,
                modifier = modifier,
            )
        }

        NativeStartupGateStatus.Onboarding -> OnboardingPages(
            snapshot = snapshot,
            onPageSelected = onPageSelected,
            onAmberLogin = onAmberLogin,
            onBunkerLogin = onBunkerLogin,
            onProfileVisibilityChanged = onProfileVisibilityChanged,
            onRequestLocation = onRequestLocation,
            onDownloadVoice = onDownloadVoice,
            onOpenDisclosure = onOpenDisclosure,
            modifier = modifier,
        )
    }
    if (snapshot.disclosureVisible) {
        DisclosureDialog(snapshot.revision, onAcceptDisclosure)
    }
}

/** The old data could not be brought over: nothing was touched, and the person chooses what happens next. */
@Composable
private fun MigrationFailedMessage(
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier,
) {
    StartupMessage(
        title = stringResource(R.string.native_onboarding_migration_failed_title),
        body = stringResource(R.string.native_onboarding_migration_failed_body),
        loading = false,
        modifier = modifier,
    ) {
        PrimaryAction(stringResource(R.string.native_onboarding_migration_retry), onRetry)
        ActionButton(
            text = stringResource(R.string.native_onboarding_migration_skip),
            modifier = Modifier.padding(top = 12.dp),
            onClick = onSkip,
        )
    }
}

@Composable
private fun StartupMessage(
    title: String,
    body: String?,
    loading: Boolean,
    modifier: Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                paneTitle = title
                liveRegion = LiveRegionMode.Assertive
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(48.dp))
            else Text("⚿", style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            body?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            actions?.let {
                Spacer(modifier = Modifier.height(28.dp))
                it()
            }
        }
    }
}

@Composable
private fun OnboardingPages(
    snapshot: NativeOnboardingSnapshot,
    onPageSelected: (Long, NativeOnboardingPage) -> Unit,
    onAmberLogin: () -> Unit,
    onBunkerLogin: () -> Unit,
    onProfileVisibilityChanged: (Long, Boolean) -> Unit,
    onRequestLocation: () -> Unit,
    onDownloadVoice: () -> Unit,
    onOpenDisclosure: (Long) -> Unit,
    modifier: Modifier,
) {
    val title = stringResource(R.string.app_name)
    val pagerState = rememberPagerState(
        initialPage = snapshot.page.ordinal,
        pageCount = { NativeOnboardingPage.entries.size },
    )
    LaunchedEffect(snapshot.page) {
        if (pagerState.currentPage != snapshot.page.ordinal) {
            pagerState.animateScrollToPage(snapshot.page.ordinal)
        }
    }
    LaunchedEffect(pagerState, snapshot.revision) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { index ->
                onPageSelected(snapshot.revision, NativeOnboardingPage.entries[index])
            }
    }
    Surface(
        modifier = modifier.fillMaxSize().semantics { paneTitle = title },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            OnboardingHeader(snapshot.page)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.Top,
            ) { index ->
                when (NativeOnboardingPage.entries[index]) {
                    NativeOnboardingPage.Welcome -> WelcomePage {
                        onPageSelected(snapshot.revision, NativeOnboardingPage.Identity)
                    }
                    NativeOnboardingPage.Identity -> IdentityPage(
                        snapshot = snapshot,
                        onAmberLogin = onAmberLogin,
                        onBunkerLogin = onBunkerLogin,
                        onProfileVisibilityChanged = onProfileVisibilityChanged,
                        onContinue = {
                            onPageSelected(snapshot.revision, NativeOnboardingPage.Setup)
                        },
                    )
                    NativeOnboardingPage.Setup -> SetupPage(
                        snapshot = snapshot,
                        onRequestLocation = onRequestLocation,
                        onDownloadVoice = onDownloadVoice,
                        onContinue = {
                            onPageSelected(snapshot.revision, NativeOnboardingPage.Ready)
                        },
                    )
                    NativeOnboardingPage.Ready -> ReadyPage {
                        onOpenDisclosure(snapshot.revision)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingHeader(page: NativeOnboardingPage) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OnboardingBrandMark()
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Roadstr", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.native_onboarding_app_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            NativeOnboardingPage.entries.forEach { value ->
                Surface(
                    modifier = Modifier.padding(horizontal = 4.dp).size(
                        width = if (value == page) 24.dp else 8.dp,
                        height = 8.dp,
                    ),
                    shape = CircleShape,
                    color = if (value == page) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ) {}
            }
        }
    }
}

@Composable
private fun OnboardingBrandMark() {
    val context = LocalContext.current
    val bitmap = androidx.compose.runtime.remember {
        runCatching {
            context.assets.open("icons/app_icon.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.asImageBitmap()
    }
    RoadstrGlassBox(
        modifier = Modifier.size(48.dp),
        level = RoadstrGlassLevel.Medium,
        shape = RoundedCornerShape(14.dp),
        padding = 0.dp,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = "Roadstr", modifier = Modifier.fillMaxSize())
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text("➤", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun WelcomePage(onContinue: () -> Unit) = PageColumn {
    PageTitle(
        stringResource(R.string.native_onboarding_welcome_title),
        stringResource(R.string.native_onboarding_welcome_body),
    )
    Spacer(modifier = Modifier.height(6.dp))
    val features = listOf(
        "⌖" to stringResource(R.string.native_onboarding_feature_navigation),
        "⚑" to stringResource(R.string.native_onboarding_feature_nostr),
        "ϟ" to stringResource(R.string.native_onboarding_feature_lightning),
        "◖" to stringResource(R.string.native_onboarding_feature_voice),
        "◇" to stringResource(R.string.native_onboarding_feature_privacy),
    )
    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            features.forEach { (symbol, text) -> FeatureRow(symbol, text) }
        }
    }
    InfoCard(stringResource(R.string.native_onboarding_vpn_notice))
    PrimaryAction(stringResource(R.string.native_onboarding_get_started), onContinue)
}

@Composable
private fun IdentityPage(
    snapshot: NativeOnboardingSnapshot,
    onAmberLogin: () -> Unit,
    onBunkerLogin: () -> Unit,
    onProfileVisibilityChanged: (Long, Boolean) -> Unit,
    onContinue: () -> Unit,
) = PageColumn {
    PageTitle(
        stringResource(R.string.native_onboarding_identity_title),
        stringResource(R.string.native_onboarding_identity_subtitle),
    )
    when (snapshot.identityStatus) {
        NativeOnboardingIdentityStatus.Connected -> InfoCard(
            listOfNotNull(
                stringResource(R.string.native_onboarding_identity_connected),
                snapshot.identityLabel,
            ).joinToString(" · "),
        )
        NativeOnboardingIdentityStatus.WaitingAmber -> LoadingRow(
            stringResource(R.string.native_onboarding_loading),
        )
        NativeOnboardingIdentityStatus.Disconnected -> Unit
    }
    ChoiceCard(
        symbol = "◇",
        title = stringResource(R.string.native_onboarding_amber_title),
        body = stringResource(R.string.native_onboarding_amber_subtitle),
        enabled = snapshot.identityStatus != NativeOnboardingIdentityStatus.WaitingAmber,
        onClick = onAmberLogin,
    )
    ChoiceCard(
        symbol = "⚿",
        title = stringResource(R.string.native_bunker_title),
        body = stringResource(R.string.native_bunker_subtitle),
        enabled = snapshot.identityStatus != NativeOnboardingIdentityStatus.WaitingAmber,
        onClick = onBunkerLogin,
    )
    InfoCard(stringResource(R.string.native_onboarding_favorites_sync_notice))
    SettingsSection(R.string.native_onboarding_profile_visibility_title) {
        SettingsCard {
            Text(
                stringResource(R.string.native_onboarding_profile_visibility_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (snapshot.profilePublic) {
                        stringResource(R.string.native_onboarding_profile_visibility_clear)
                    } else {
                        stringResource(R.string.native_onboarding_profile_visibility_pseudonymous)
                    },
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                RoadstrSwitch(
                    checked = snapshot.profilePublic,
                    onCheckedChange = { onProfileVisibilityChanged(snapshot.revision, it) },
                )
            }
        }
    }
    InfoCard(stringResource(R.string.native_onboarding_profile_visibility_notice))
    PrimaryAction(
        if (snapshot.identityStatus == NativeOnboardingIdentityStatus.Connected) {
            stringResource(R.string.native_onboarding_continue)
        } else {
            stringResource(R.string.native_onboarding_skip)
        },
        onContinue,
    )
}

@Composable
private fun SetupPage(
    snapshot: NativeOnboardingSnapshot,
    onRequestLocation: () -> Unit,
    onDownloadVoice: () -> Unit,
    onContinue: () -> Unit,
) = PageColumn {
    PageTitle(
        stringResource(R.string.native_onboarding_setup_title),
        stringResource(R.string.native_onboarding_setup_subtitle),
    )
    SetupCard(
        title = stringResource(R.string.native_onboarding_location_title),
        status = when (snapshot.locationStatus) {
            NativeOnboardingLocationStatus.Checking ->
                stringResource(R.string.native_onboarding_loading)
            NativeOnboardingLocationStatus.Required ->
                stringResource(R.string.native_onboarding_location_required)
            NativeOnboardingLocationStatus.Granted ->
                stringResource(R.string.native_onboarding_location_granted)
        },
        action = if (snapshot.locationStatus == NativeOnboardingLocationStatus.Required) {
            stringResource(R.string.native_onboarding_grant) to onRequestLocation
        } else {
            null
        },
    )
    InfoCard(
        "${stringResource(R.string.native_onboarding_graphene_title)}\n" +
            stringResource(R.string.native_onboarding_graphene_body),
    )
    SetupCard(
        title = stringResource(R.string.native_onboarding_voice_title),
        status = when (snapshot.voiceStatus) {
            NativeOnboardingVoiceStatus.Checking ->
                stringResource(R.string.native_onboarding_voice_checking)
            NativeOnboardingVoiceStatus.NotDownloaded ->
                stringResource(R.string.native_onboarding_voice_not_downloaded)
            NativeOnboardingVoiceStatus.Downloading ->
                stringResource(R.string.native_onboarding_voice_downloading)
            NativeOnboardingVoiceStatus.Ready ->
                stringResource(R.string.native_onboarding_voice_ready)
        },
        action = if (snapshot.voiceStatus == NativeOnboardingVoiceStatus.NotDownloaded) {
            stringResource(R.string.native_onboarding_download) to onDownloadVoice
        } else {
            null
        },
        progress = snapshot.voiceProgress.takeIf {
            snapshot.voiceStatus == NativeOnboardingVoiceStatus.Downloading
        },
    )
    Text(
        stringResource(R.string.native_onboarding_voice_later),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PrimaryAction(stringResource(R.string.native_onboarding_continue), onContinue)
}

@Composable
private fun ReadyPage(onStart: () -> Unit) = PageColumn {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("✓", style = MaterialTheme.typography.displaySmall)
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        PageTitle(
            stringResource(R.string.native_onboarding_ready_title),
            stringResource(R.string.native_onboarding_ready_body),
            centered = true,
        )
        Spacer(modifier = Modifier.height(28.dp))
        PrimaryAction(stringResource(R.string.native_onboarding_lets_go), onStart)
    }
}

@Composable
private fun DisclosureDialog(revision: Long, onAccept: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text(stringResource(R.string.native_onboarding_disclosure_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                item { Text(stringResource(R.string.native_onboarding_disclosure_body)) }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
                    Text(stringResource(R.string.native_onboarding_profile_visibility_notice))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAccept(revision) },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.native_onboarding_disclosure_accept))
            }
        },
    )
}

@Composable
private fun PageTitle(title: String, body: String, centered: Boolean = false) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
    )
    Text(
        body,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
    )
}

@Composable
private fun FeatureRow(symbol: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) { Text(symbol) }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoCard(text: String) {
    SettingsCard {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text)
    }
}

@Composable
private fun ChoiceCard(
    symbol: String,
    title: String,
    body: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingsCard(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .sizeIn(minHeight = 64.dp)
            .semantics { role = Role.Button },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(symbol, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetupCard(
    title: String,
    status: String,
    action: Pair<String, () -> Unit>?,
    progress: Double? = null,
) {
    SettingsCard {
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.let { (label, callback) ->
                ActionButton(
                    text = label,
                    modifier = Modifier.padding(top = 10.dp),
                    onClick = callback,
                )
            }
            progress?.let {
                LinearProgressIndicator(
                    progress = { it.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    RoadstrAccentButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 52.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}
