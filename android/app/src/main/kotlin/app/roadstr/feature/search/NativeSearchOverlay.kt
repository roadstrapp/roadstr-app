package app.roadstr.feature.search

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
    onNearby: (NativeSearchNearbyCategory) -> Unit,
    onSelectResult: (NativeSearchResultPresentation) -> Unit,
    onSelectFavorite: (NativeSearchFavoritePresentation) -> Unit,
    onSelectHistory: (NativeSearchHistoryPresentation) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot.status == NativeSearchUiStatus.Hidden) return
    val hint = androidx.compose.ui.res.stringResource(R.string.native_search_hint)
    Surface(
        modifier = modifier
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

@Composable
private fun SearchField(
    query: String,
    hint: String,
    onQueryChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClearQuery: () -> Unit,
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
