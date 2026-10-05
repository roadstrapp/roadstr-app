package app.roadstr.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.discovery.web.EndpointRejection
import app.roadstr.core.discovery.web.SafeSearch
import app.roadstr.core.discovery.web.WebDiscoveryMode
import app.roadstr.core.discovery.web.WebDiscoverySettings
import app.roadstr.feature.search.textResource

/** Longer than the address limit so that "too long" is said by the policy, not by a silent cut. */
private const val MAX_TYPED_CHARS = 400

/**
 * Where web results in the search come from: off, asked each time or on, and the SearXNG
 * instance the user chose. Nothing here is built in, and the address is only ever shown as a host.
 */
@Composable
fun NativeWebSearchPanel(
    settings: WebDiscoverySettings,
    status: NativeWebSearchStatus,
    onClose: () -> Unit,
    onModeChanged: (WebDiscoveryMode) -> Unit,
    onEndpointSubmitted: (String) -> EndpointRejection?,
    onOwnInstanceChanged: (Boolean) -> Unit,
    onStrictChanged: (Boolean) -> Unit,
    onSafeSearchChanged: (SafeSearch) -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.native_settings_web_results)
    Surface(
        modifier = modifier.fillMaxSize().semantics { paneTitle = title },
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            item { SettingsHeader(title, onClose) }
            item { Disclosure() }
            item { ModeSection(settings.mode, onModeChanged) }
            item { InstanceSection(settings, onEndpointSubmitted, onOwnInstanceChanged, onStrictChanged) }
            item { SafeSearchSection(settings.safeSearch, onSafeSearchChanged) }
            item { TestSection(status, onTest) }
            item { Spacer(modifier = Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun Disclosure() {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Note(R.string.native_websearch_intro)
        Note(R.string.native_websearch_privacy)
        Note(R.string.native_websearch_google)
    }
}

@Composable
private fun Note(@StringRes text: Int) {
    Text(
        stringResource(text),
        modifier = Modifier.padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModeSection(mode: WebDiscoveryMode, onChanged: (WebDiscoveryMode) -> Unit) {
    SettingsSection(R.string.native_websearch_mode) {
        ChoiceRow(
            label = stringResource(R.string.native_websearch_mode),
            values = WebDiscoveryMode.entries,
            selected = mode,
            labelFor = { stringResource(modeLabel(it)) },
            onSelected = onChanged,
        )
    }
}

@Composable
private fun InstanceSection(
    settings: WebDiscoverySettings,
    onEndpointSubmitted: (String) -> EndpointRejection?,
    onOwnInstanceChanged: (Boolean) -> Unit,
    onStrictChanged: (Boolean) -> Unit,
) {
    SettingsSection(R.string.native_websearch_instance) {
        EndpointField(settings, onEndpointSubmitted)
        ToggleRow(
            R.string.native_websearch_own,
            R.string.native_websearch_own_desc,
            settings.ownInstanceConfirmed,
            onOwnInstanceChanged,
        )
        ToggleRow(
            R.string.native_websearch_strict,
            R.string.native_websearch_strict_desc,
            settings.strictSources,
            onStrictChanged,
        )
    }
}

/**
 * The address box. It keeps what is being typed, tells at once why an address is refused, and
 * shows below it only the host of the saved one, or that none is set.
 */
@Composable
private fun EndpointField(settings: WebDiscoverySettings, onSubmitted: (String) -> EndpointRejection?) {
    var typed by remember(settings.endpointText) { mutableStateOf(settings.endpointText) }
    var refused by remember { mutableStateOf<EndpointRejection?>(null) }
    val focus = LocalFocusManager.current
    val problem = refused ?: NativeWebSearchEditor.rejectionOf(settings)
    val host = NativeWebSearchEditor.hostOf(settings)
    OutlinedTextField(
        value = typed,
        onValueChange = {
            typed = it.take(MAX_TYPED_CHARS)
            refused = null
        },
        label = { Text(stringResource(R.string.native_websearch_instance_title)) },
        placeholder = { Text("https://") },
        isError = problem != null,
        supportingText = {
            val shown = problem?.let { stringResource(rejectionText(it)) }
                ?: host
                ?: stringResource(R.string.native_websearch_instance_none)
            Text(shown, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            // Leaving the box saves it, so an address typed and then left is not lost.
            .onFocusChanged { state ->
                if (!state.isFocused && typed.trim() != settings.endpointText) refused = onSubmitted(typed)
            },
    )
}

@Composable
private fun SafeSearchSection(level: SafeSearch, onChanged: (SafeSearch) -> Unit) {
    SettingsSection(R.string.native_websearch_safe) {
        ChoiceRow(
            label = stringResource(R.string.native_websearch_safe),
            values = SafeSearch.entries,
            selected = level,
            labelFor = { stringResource(safeSearchLabel(it)) },
            onSelected = onChanged,
        )
    }
}

@Composable
private fun TestSection(status: NativeWebSearchStatus, onTest: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
        ActionButton(
            text = stringResource(R.string.native_websearch_test),
            enabled = status != NativeWebSearchStatus.Testing,
            onClick = onTest,
        )
        val message = statusText(status) ?: return
        Text(
            stringResource(message),
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
            color = if (status.isFailure()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun NativeWebSearchStatus.isFailure(): Boolean =
    this is NativeWebSearchStatus.Failed ||
        this is NativeWebSearchStatus.Rejected ||
        this == NativeWebSearchStatus.NotConfigured

@StringRes
private fun statusText(status: NativeWebSearchStatus): Int? = when (status) {
    NativeWebSearchStatus.Idle -> null
    NativeWebSearchStatus.Testing -> R.string.native_websearch_testing
    NativeWebSearchStatus.Works -> R.string.native_websearch_test_ok
    NativeWebSearchStatus.NotConfigured -> R.string.native_websearch_not_configured
    is NativeWebSearchStatus.Failed -> status.problem.textResource()
    is NativeWebSearchStatus.Rejected -> rejectionText(status.reason)
}

@StringRes
private fun modeLabel(mode: WebDiscoveryMode): Int = when (mode) {
    WebDiscoveryMode.OFF -> R.string.native_websearch_mode_off
    WebDiscoveryMode.ASK -> R.string.native_websearch_mode_ask
    WebDiscoveryMode.ON -> R.string.native_websearch_mode_on
}

@StringRes
private fun safeSearchLabel(level: SafeSearch): Int = when (level) {
    SafeSearch.OFF -> R.string.native_websearch_safe_off
    SafeSearch.MODERATE -> R.string.native_websearch_safe_moderate
    SafeSearch.STRICT -> R.string.native_websearch_safe_strict
}

@StringRes
private fun rejectionText(reason: EndpointRejection): Int = when (reason) {
    EndpointRejection.INVALID -> R.string.native_websearch_rej_invalid
    EndpointRejection.NOT_HTTPS -> R.string.native_websearch_rej_not_https
    EndpointRejection.CREDENTIALS -> R.string.native_websearch_rej_credentials
    EndpointRejection.QUERY_OR_FRAGMENT -> R.string.native_websearch_rej_query
    EndpointRejection.TOO_LONG -> R.string.native_websearch_rej_too_long
    EndpointRejection.LOCAL_HTTP_NOT_CONFIRMED -> R.string.native_websearch_rej_local_http
}
