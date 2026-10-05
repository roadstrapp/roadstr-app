package app.roadstr.feature.web

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.roadstr.R
import app.roadstr.core.web.ExternalAction

/**
 * The frame around an in-app page: the host and title with a lock, a bar to go back, forward,
 * reload, open in the user's browser, share or copy the link, and close. The page itself is
 * [content]; nothing here knows which engine draws it.
 */
@Composable
fun NativeWebBrowserScreen(
    state: WebBrowserState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onClose: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onConfirmAction: () -> Unit,
    onDismissAction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val title = stringResource(R.string.native_browser_title)
    Surface(
        modifier = modifier.fillMaxSize().semantics { paneTitle = title },
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Header(state, onClose)
            if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            content(Modifier.weight(1f).fillMaxWidth())
            if (state.failed) FailedNote()
            Toolbar(state, onBack, onForward, onReload, onOpenExternal)
        }
    }
    state.pendingAction?.let { ConfirmActionDialog(it, onConfirmAction, onDismissAction) }
}

@Composable
private fun Header(state: WebBrowserState, onClose: () -> Unit) {
    val connection = stringResource(if (state.secure) R.string.native_browser_secure else R.string.native_browser_insecure)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = (if (state.secure) "🔒 " else "⚠ ") + state.host,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics {
                    heading()
                    contentDescription = "$connection, ${state.host}"
                },
            )
            if (state.title.isNotEmpty()) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onClose, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
            Text(stringResource(R.string.native_browser_close))
        }
    }
}

@Composable
private fun FailedNote() {
    Text(
        text = stringResource(R.string.native_browser_failed),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun Toolbar(
    state: WebBrowserState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton("‹", R.string.native_browser_back, state.canGoBack, onBack)
        ToolButton("›", R.string.native_browser_forward, state.canGoForward, onForward)
        ToolButton("⟳", R.string.native_browser_reload, true, onReload)
        ToolButton("↗", R.string.native_browser_open_external, state.url.isNotEmpty()) { onOpenExternal(state.url) }
        ToolButton("⇪", R.string.native_browser_share, state.url.isNotEmpty()) { share(context, state.url) }
        ToolButton("⧉", R.string.native_browser_copy, state.url.isNotEmpty()) { copy(context, state.url) }
    }
}

@Composable
private fun ToolButton(symbol: String, label: Int, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(label)
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { contentDescription = description },
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ConfirmActionDialog(action: ExternalAction, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val message = when (action) {
        is ExternalAction.Dial -> stringResource(R.string.native_browser_confirm_dial, action.number)
        is ExternalAction.Mail -> stringResource(R.string.native_browser_confirm_mail, action.address)
        is ExternalAction.UseAsDestination -> stringResource(R.string.native_browser_confirm_geo)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.native_browser_confirm_yes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.native_browser_confirm_no)) } },
    )
}

private fun share(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copy(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(null, url))
    Toast.makeText(context, R.string.native_browser_copied, Toast.LENGTH_SHORT).show()
}
