package app.roadstr.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R

/** Flutter-parity place-search overlay packaged only in the private shell. */
@Composable
fun NativeSearchOverlay(
    snapshot: NativeSearchUiSnapshot,
    onQueryChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClearQuery: () -> Unit,
    onDismiss: () -> Unit,
    onNearby: (NativeSearchNearbyCategory) -> Unit,
    onSelectResult: (NativeSearchResultPresentation) -> Unit,
    onSelectFavorite: (NativeSearchFavoritePresentation) -> Unit,
    onSelectHistory: (NativeSearchHistoryPresentation) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onSearchWeb: () -> Unit = {},
    onConfirmWeb: () -> Unit = {},
    onDeclineWeb: () -> Unit = {},
    onOpenWebResult: (String) -> Unit = {},
    onOpenWebPlace: (String) -> Unit = {},
) {
    val hint = androidx.compose.ui.res.stringResource(R.string.native_search_hint)
    AnimatedVisibility(
        visible = snapshot.status != NativeSearchUiStatus.Hidden,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(tween(340)) { -it / 5 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(260)) { -it / 6 },
    ) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .semantics { paneTitle = hint },
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            SearchField(
                query = snapshot.query,
                hint = hint,
                onQueryChanged = onQueryChanged,
                onSubmit = onSubmit,
                onClearQuery = onClearQuery,
                onDismiss = onDismiss,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (snapshot.query.isEmpty()) {
                    item(key = "nearby") {
                        NearbyChoices(
                            enabled = snapshot.nearbyEnabled,
                            selected = snapshot.selectedNearby,
                            onNearby = onNearby,
                        )
                    }
                }

                if (snapshot.favorites.isNotEmpty()) {
                    item(key = "favorites-heading") {
                        SearchSectionHeading(
                            symbol = "♥",
                            text = androidx.compose.ui.res.stringResource(
                                R.string.native_search_favorites,
                            ),
                        )
                    }
                    items(
                        items = snapshot.favorites,
                        key = { "favorite:${it.position.latitude}:${it.position.longitude}:${it.label}" },
                    ) { favorite ->
                        SearchRow(
                            symbol = "♥",
                            title = favorite.label,
                            subtitle = favorite.address,
                            trailing = null,
                            onClick = { onSelectFavorite(favorite) },
                        )
                    }
                }

                snapshot.notice?.let { notice ->
                    item(key = "notice") { SearchNoticeRow(notice) }
                }

                if (snapshot.status == NativeSearchUiStatus.Loading) {
                    item(key = "loading") { SearchLoadingState() }
                } else if (snapshot.status == NativeSearchUiStatus.Results) {
                    items(
                        items = snapshot.results,
                        key = { "result:${it.position.latitude}:${it.position.longitude}:${it.title}" },
                    ) { result ->
                        SearchRow(
                            symbol = result.emoji,
                            title = result.title,
                            subtitle = result.subtitle,
                            trailing = result.distanceLabel,
                            onClick = { onSelectResult(result) },
                        )
                    }
                } else if (snapshot.status == NativeSearchUiStatus.EmptyNearby) {
                    item(key = "empty-nearby") { SearchEmptyNearbyState() }
                }

                webItems(snapshot.web, onSearchWeb, onConfirmWeb, onDeclineWeb, onOpenWebResult, onOpenWebPlace)

                if (snapshot.query.isEmpty() && snapshot.history.isNotEmpty()) {
                    item(key = "history-heading") {
                        HistoryHeading(onClearHistory)
                    }
                    items(
                        items = snapshot.history,
                        key = { "history:${it.position.latitude}:${it.position.longitude}:${it.fullLabel}" },
                    ) { history ->
                        SearchRow(
                            symbol = "⌖",
                            title = history.title,
                            subtitle = history.subtitle,
                            trailing = null,
                            onClick = { onSelectHistory(history) },
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SearchField(
    query: String,
    hint: String,
    onQueryChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClearQuery: () -> Unit,
    onDismiss: () -> Unit,
) {
    val close = androidx.compose.ui.res.stringResource(R.string.native_search_close)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.weight(1f),
            placeholder = { Text(hint) },
            leadingIcon = {
                Text(
                    text = "⌕",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            trailingIcon = if (query.isEmpty()) {
                null
            } else {
                {
                    TextButton(
                        onClick = onClearQuery,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = close },
                    ) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = close },
        ) {
            Text("×", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun NearbyChoices(
    enabled: Boolean,
    selected: NativeSearchNearbyCategory?,
    onNearby: (NativeSearchNearbyCategory) -> Unit,
) {
    val heading = androidx.compose.ui.res.stringResource(
        if (enabled) R.string.native_search_nearby else R.string.native_search_nearby_needs_gps,
    )
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
        SearchSectionHeading(symbol = "⌖", text = heading)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NativeSearchNearbyCategory.entries.forEach { category ->
                val label = androidx.compose.ui.res.stringResource(category.labelResource())
                FilterChip(
                    selected = selected == category,
                    enabled = enabled,
                    onClick = { onNearby(category) },
                    label = { Text("${category.emoji}  $label") },
                    modifier = Modifier.semantics {
                        this.selected = selected == category
                        role = Role.Button
                        contentDescription = label
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchSectionHeading(symbol: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 4.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(symbol, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HistoryHeading(onClearHistory: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.semantics { heading() }) {
            Text(
                text = "◷  ${androidx.compose.ui.res.stringResource(R.string.native_search_history)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
            onClick = onClearHistory,
            modifier = Modifier.sizeIn(minHeight = 48.dp),
        ) {
            Text(androidx.compose.ui.res.stringResource(R.string.native_search_clear_history))
        }
    }
}

@Composable
private fun SearchRow(
    symbol: String,
    title: String,
    subtitle: String,
    trailing: String?,
    onClick: () -> Unit,
) {
    val description = listOfNotNull(
        title,
        subtitle.takeIf(String::isNotEmpty),
        trailing,
    ).joinToString(", ")
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = description
                }
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Text(symbol) }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = trailing,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SearchLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

private fun LazyListScope.webItems(
    web: NativeSearchWeb,
    onSearchWeb: () -> Unit,
    onConfirmWeb: () -> Unit,
    onDeclineWeb: () -> Unit,
    onOpenWebResult: (String) -> Unit,
    onOpenWebPlace: (String) -> Unit,
) {
    when (web) {
        NativeSearchWeb.Hidden -> Unit
        is NativeSearchWeb.Offer -> item(key = "web-offer") {
            SearchRow(
                symbol = "🌐",
                title = androidx.compose.ui.res.stringResource(R.string.native_search_web_offer, web.text),
                subtitle = "",
                trailing = null,
                onClick = onSearchWeb,
            )
        }
        is NativeSearchWeb.Consent -> item(key = "web-consent") {
            WebConsentRow(web, onConfirmWeb, onDeclineWeb)
        }
        NativeSearchWeb.Loading -> item(key = "web-loading") { WebLoadingRow() }
        is NativeSearchWeb.Results -> {
            item(key = "web-heading") {
                SearchSectionHeading(
                    symbol = "🌐",
                    text = androidx.compose.ui.res.stringResource(R.string.native_search_web_heading, web.host),
                )
            }
            if (web.rows.isEmpty()) item(key = "web-empty") { WebTextRow(R.string.native_search_web_empty) }
            items(items = web.rows, key = { "web:${it.url}" }) { row ->
                Column {
                    SearchRow(
                        symbol = "🌐",
                        title = row.title,
                        subtitle = listOf(row.host, row.snippet).filter { it.isNotEmpty() }.joinToString(" · "),
                        trailing = null,
                        onClick = { onOpenWebResult(row.url) },
                    )
                    WebPlaceLinkRow(row.link, onOpenWebPlace)
                }
            }
        }
        is NativeSearchWeb.Unavailable -> item(key = "web-unavailable") {
            WebTextRow(web.problem.textResource())
        }
    }
}

/** Under a web result: the place it was tied to, or the question whether it could be one. */
@Composable
private fun WebPlaceLinkRow(link: NativeWebPlaceLink, onOpen: (String) -> Unit) {
    val (placeId, text) = when (link) {
        NativeWebPlaceLink.None -> return
        is NativeWebPlaceLink.Linked ->
            link.placeId to androidx.compose.ui.res.stringResource(R.string.native_websearch_link_match, link.name)
        is NativeWebPlaceLink.Candidate ->
            link.placeId to androidx.compose.ui.res.stringResource(R.string.native_websearch_link_candidate, link.name)
    }
    TextButton(
        onClick = { onOpen(placeId) },
        modifier = Modifier.padding(start = 40.dp).sizeIn(minHeight = 48.dp),
    ) {
        Text("📍 $text", maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WebConsentRow(
    web: NativeSearchWeb.Consent,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.native_search_web_consent, web.text, web.host),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (web.addsTown) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.native_search_web_consent_town),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onConfirm, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                Text(androidx.compose.ui.res.stringResource(R.string.native_search_web_consent_yes))
            }
            TextButton(onClick = onDecline, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                Text(androidx.compose.ui.res.stringResource(R.string.native_search_web_consent_no))
            }
        }
    }
}

@Composable
private fun WebLoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.native_search_web_loading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun WebTextRow(@StringRes text: Int) {
    Text(
        text = androidx.compose.ui.res.stringResource(text),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@StringRes
internal fun NativeWebProblem.textResource(): Int = when (this) {
    NativeWebProblem.JsonDisabled -> R.string.native_websearch_result_json_disabled
    NativeWebProblem.RateLimited -> R.string.native_websearch_result_rate_limited
    NativeWebProblem.NotSearxng -> R.string.native_websearch_result_not_searxng
    NativeWebProblem.NoEngineList -> R.string.native_websearch_result_no_engine_list
    NativeWebProblem.Unreachable -> R.string.native_websearch_result_unreachable
    NativeWebProblem.Rejected -> R.string.native_websearch_result_rejected
}

@Composable
private fun SearchNoticeRow(notice: NativeSearchNotice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
    ) {
        Text("ⓘ", color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(notice.textResource()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@StringRes
private fun NativeSearchNotice.textResource(): Int = when (this) {
    NativeSearchNotice.FewTagged -> R.string.native_search_notice_few_tagged
    NativeSearchNotice.Widened -> R.string.native_search_notice_widened
    NativeSearchNotice.RouteUnsupported -> R.string.native_search_notice_route_unsupported
    NativeSearchNotice.AreaFallback -> R.string.native_search_notice_area_fallback
    NativeSearchNotice.OpenHoursUnknown -> R.string.native_search_notice_open_hours_unknown
}

@Composable
private fun SearchEmptyNearbyState() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 18.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.native_search_nearby_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@StringRes
private fun NativeSearchNearbyCategory.labelResource(): Int = when (this) {
    NativeSearchNearbyCategory.Fuel -> R.string.native_search_nearby_fuel
    NativeSearchNearbyCategory.Restaurant -> R.string.native_search_nearby_restaurant
    NativeSearchNearbyCategory.Supermarket -> R.string.native_search_nearby_supermarket
    NativeSearchNearbyCategory.Atm -> R.string.native_search_nearby_atm
    NativeSearchNearbyCategory.Pharmacy -> R.string.native_search_nearby_pharmacy
    NativeSearchNearbyCategory.Hospital -> R.string.native_search_nearby_hospital
    NativeSearchNearbyCategory.Police -> R.string.native_search_nearby_police
    NativeSearchNearbyCategory.PostOffice -> R.string.native_search_nearby_post_office
    NativeSearchNearbyCategory.Parking -> R.string.native_search_nearby_parking
    NativeSearchNearbyCategory.Hotel -> R.string.native_search_nearby_hotel
    NativeSearchNearbyCategory.Charging -> R.string.native_search_nearby_charging
}
