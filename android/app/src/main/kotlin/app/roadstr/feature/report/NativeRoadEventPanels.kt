package app.roadstr.feature.report

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.protocol.nostr.RoadCategoryWire
import app.roadstr.core.ui.SheetGrabHandle
import app.roadstr.core.ui.rememberSheetDragState
import java.util.Locale

/** Dormant bounded road-event detail, privacy and report-composer surfaces. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NativeRoadEventPanels(
    snapshot: NativeRoadEventSnapshot,
    onClose: (Long) -> Unit,
    onAcceptPrivacy: (Long) -> Unit,
    onSelectCategory: (Long, RoadCategoryWire) -> Unit,
    onCommentChanged: (Long, String) -> Unit,
    onSpeedChanged: (Long, String) -> Unit,
    onSubmit: (Long) -> Unit,
    onOpenReporter: (Long) -> Unit,
    onVote: (Long, Boolean) -> Unit,
    onEditSpeedLimit: (Long, String?) -> Unit,
    onZap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (snapshot.surface) {
        NativeRoadEventSurface.Hidden -> Unit
        NativeRoadEventSurface.PrivacyNotice -> PrivacyNotice(
            revision = snapshot.revision,
            onClose = onClose,
            onAccept = onAcceptPrivacy,
        )
        NativeRoadEventSurface.Detail -> snapshot.detail?.let { detail ->
            RoadEventDetailPanel(
                revision = snapshot.revision,
                detail = detail,
                onClose = onClose,
                onOpenReporter = onOpenReporter,
                onVote = onVote,
                onEditSpeedLimit = onEditSpeedLimit,
                onZap = onZap,
                modifier = modifier,
            )
        }
        NativeRoadEventSurface.Composer -> snapshot.draft?.let { draft ->
            RoadEventComposer(
                revision = snapshot.revision,
                draft = draft,
                onClose = onClose,
                onSelectCategory = onSelectCategory,
                onCommentChanged = onCommentChanged,
                onSpeedChanged = onSpeedChanged,
                onSubmit = onSubmit,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun PrivacyNotice(
    revision: Long,
    onClose: (Long) -> Unit,
    onAccept: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onClose(revision) },
        title = { Text(stringResource(R.string.native_road_event_privacy_title)) },
        text = { Text(stringResource(R.string.native_road_event_privacy_body)) },
        dismissButton = {
            TextButton(
                onClick = { onClose(revision) },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.native_road_event_cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = { onAccept(revision) },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.native_road_event_understand))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoadEventDetailPanel(
    revision: Long,
    detail: NativeRoadEventDetail,
    onClose: (Long) -> Unit,
    onOpenReporter: (Long) -> Unit,
    onVote: (Long, Boolean) -> Unit,
    onEditSpeedLimit: (Long, String?) -> Unit,
    onZap: (Long) -> Unit,
    modifier: Modifier,
) {
    val title = categoryLabel(detail.category)
    val drag = rememberSheetDragState { onClose(revision) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.84f)
                .then(drag.sheet)
                .semantics { paneTitle = title },
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                SheetGrabHandle(drag.handle)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                ) {
                    DetailHeader(detail, title)
                    detail.speedLimit?.let { speed ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SpeedBadge(speed, detail.speedUnit)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.native_road_event_reported_speed),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (detail.comment.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            detail.comment,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (detail.category == RoadCategoryWire.SPEED_CAMERA && detail.loggedIn) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { onEditSpeedLimit(revision, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .sizeIn(minHeight = 48.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (detail.owner) {
                                        R.string.native_road_event_edit_speed
                                    } else {
                                        R.string.native_road_event_request_speed
                                    },
                                ),
                            )
                        }
                    }
                    if (detail.owner && detail.editRequests.isNotEmpty()) {
                        PendingEditRequests(revision, detail, onEditSpeedLimit)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    ConfirmationCounts(detail)
                    Spacer(modifier = Modifier.height(12.dp))
                    ReporterRow(revision, detail, onOpenReporter)
                    if (detail.loggedIn) {
                        Spacer(modifier = Modifier.height(16.dp))
                        VoteActions(revision, onVote)
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.native_road_event_login_confirm),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { onZap(revision) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 48.dp),
                        border = BorderStroke(1.dp, BitcoinOrange),
                    ) {
                        Text("⚡ ", color = BitcoinOrange)
                        Text(
                            stringResource(R.string.native_road_event_zap),
                            color = BitcoinOrange,
                        )
                    }
                }
                TextButton(
                    onClick = { onClose(revision) },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.native_road_event_close))
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(detail: NativeRoadEventDetail, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val accent = Color(detail.markerKind.accentArgb)
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = accent.copy(alpha = 0.15f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(detail.markerKind.symbol, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                ageLabel(detail.age),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (detail.zapSats > 0) {
            Surface(
                color = BitcoinOrange.copy(alpha = 0.12f),
                contentColor = BitcoinOrange,
                border = BorderStroke(1.dp, BitcoinOrange.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "⚡ ${detail.zapSats} sat",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PendingEditRequests(
    revision: Long,
    detail: NativeRoadEventDetail,
    onEditSpeedLimit: (Long, String?) -> Unit,
) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        stringResource(R.string.native_road_event_pending_edits),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(6.dp))
    detail.editRequests.forEach { request ->
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${request.speedLimit} ${request.speedUnit}")
                    Text(
                        request.requesterLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(
                    onClick = { onEditSpeedLimit(revision, request.id) },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.native_road_event_accept_edit))
                }
            }
        }
    }
}

@Composable
private fun ConfirmationCounts(detail: NativeRoadEventDetail) {
    Row {
        Text("✓", color = ConfirmGreen)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            "${detail.confirmations} ${stringResource(R.string.native_road_event_confirmed)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text("✕", color = DenyRed)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            "${detail.denials} ${stringResource(R.string.native_road_event_removed)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ReporterRow(
    revision: Long,
    detail: NativeRoadEventDetail,
    onOpenReporter: (Long) -> Unit,
) {
    val reporter = if (detail.reporterPublic && !detail.reporterLabel.isNullOrBlank()) {
        detail.reporterLabel
    } else {
        stringResource(R.string.native_road_event_nostrich)
    }
    Surface(
        onClick = { onOpenReporter(revision) },
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(modifier = Modifier.size(32.dp), shape = CircleShape) {
                Box(contentAlignment = Alignment.Center) { Text("♙") }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(reporter, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (detail.reporterPublic) {
                Text(
                    detail.reporterNpub,
                    modifier = Modifier.widthIn(max = 130.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VoteActions(revision: Long, onVote: (Long, Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { onVote(revision, true) },
            modifier = Modifier.weight(1f).sizeIn(minHeight = 48.dp),
            border = BorderStroke(1.dp, ConfirmGreen),
        ) {
            Text("✓ ", color = ConfirmGreen)
            Text(stringResource(R.string.native_road_event_still_there), color = ConfirmGreen)
        }
        OutlinedButton(
            onClick = { onVote(revision, false) },
            modifier = Modifier.weight(1f).sizeIn(minHeight = 48.dp),
            border = BorderStroke(1.dp, DenyRed),
        ) {
            Text("✕ ", color = DenyRed)
            Text(stringResource(R.string.native_road_event_not_there), color = DenyRed)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoadEventComposer(
    revision: Long,
    draft: NativeRoadEventDraft,
    onClose: (Long) -> Unit,
    onSelectCategory: (Long, RoadCategoryWire) -> Unit,
    onCommentChanged: (Long, String) -> Unit,
    onSpeedChanged: (Long, String) -> Unit,
    onSubmit: (Long) -> Unit,
    modifier: Modifier,
) {
    val title = stringResource(R.string.native_road_event_report_title)
    val drag = rememberSheetDragState { onClose(revision) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.9f)
                .then(drag.sheet)
                .semantics { paneTitle = title },
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                SheetGrabHandle(drag.handle)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                ) {
                    Text(
                        title,
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "📍 ${"%.4f".format(Locale.ROOT, draft.latitude)}, " +
                            "${"%.4f".format(Locale.ROOT, draft.longitude)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CategoryGrid(revision, draft.category, draft.submitting, onSelectCategory)
                    if (draft.category == RoadCategoryWire.SPEED_CAMERA) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = draft.speedInput,
                            onValueChange = { onSpeedChanged(revision, it) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !draft.submitting,
                            singleLine = true,
                            label = { Text(stringResource(R.string.native_road_event_report_speed)) },
                            suffix = { Text(draft.speedUnit) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = draft.comment,
                        onValueChange = { onCommentChanged(revision, it) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !draft.submitting,
                        minLines = 2,
                        maxLines = 2,
                        label = { Text(stringResource(R.string.native_road_event_optional_comment)) },
                        supportingText = {
                            Text("${draft.comment.length}/${NativeRoadEventPresenter.MAX_REPORT_COMMENT}")
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onSubmit(revision) },
                        enabled = draft.category != null && !draft.submitting,
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
                    ) {
                        if (draft.submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            stringResource(
                                if (draft.submitting) {
                                    R.string.native_road_event_publishing
                                } else {
                                    R.string.native_road_event_publish
                                },
                            ),
                        )
                    }
                }
                TextButton(
                    onClick = { onClose(revision) },
                    enabled = !draft.submitting,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.native_road_event_cancel))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(
    revision: Long,
    selectedCategory: RoadCategoryWire?,
    submitting: Boolean,
    onSelectCategory: (Long, RoadCategoryWire) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 5,
    ) {
        RoadCategoryWire.entries.forEach { category ->
            val marker = NativeRoadEventPresenter.markerKind(category)
            val label = categoryLabel(category)
            val selected = selectedCategory == category
            val accent = Color(marker.accentArgb)
            Surface(
                modifier = Modifier
                    .widthIn(min = 64.dp, max = 80.dp)
                    .height(76.dp)
                    .selectable(
                        selected = selected,
                        enabled = !submitting,
                        role = Role.RadioButton,
                        onClick = { onSelectCategory(revision, category) },
                    )
                    .semantics {
                        this.selected = selected
                        contentDescription = label
                    },
                color = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(
                    modifier = Modifier.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(marker.symbol, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        label,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedBadge(speed: Int, unit: String) {
    Surface(
        modifier = Modifier.size(54.dp),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(4.dp, DenyRed),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(speed.toString(), color = Color.Black, fontWeight = FontWeight.Black)
            Text(unit, color = Color.Black, style = MaterialTheme.typography.labelSmall)
        }
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

private val ConfirmGreen = Color(0xFF22C55E)
private val DenyRed = Color(0xFFEF4444)
private val BitcoinOrange = Color(0xFFF7931A)
