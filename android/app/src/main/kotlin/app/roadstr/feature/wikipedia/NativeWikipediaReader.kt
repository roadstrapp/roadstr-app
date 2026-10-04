package app.roadstr.feature.wikipedia

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.roadstr.R
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/** Restricted native Wikipedia reader; hidden state creates no WebView or request. */
@Composable
fun NativeWikipediaReader(
    snapshot: NativeWikipediaSnapshot,
    onClose: () -> Unit,
    onOpenExternal: (URI) -> Unit,
    onProgress: (Long, Int) -> Unit,
    onPageStarted: (Long, String) -> Unit,
    onPageFinished: (Long, String, Boolean) -> Unit,
    onFailed: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshot.status == NativeWikipediaStatus.Hidden) return
    val originalUri = snapshot.originalUri ?: return
    val revision = snapshot.revision
    val context = LocalContext.current
    val currentProgress = rememberUpdatedState(onProgress)
    val currentStarted = rememberUpdatedState(onPageStarted)
    val currentFinished = rememberUpdatedState(onPageFinished)
    val currentFailed = rememberUpdatedState(onFailed)
    val host = remember(context, snapshot.revision) {
        NativeWikipediaWebViewHost.create(
            context = context,
            originalUri = originalUri,
            onProgress = { currentProgress.value(revision, it) },
            onPageStarted = { currentStarted.value(revision, it) },
            onPageFinished = { url, canGoBack ->
                currentFinished.value(revision, url, canGoBack)
            },
            onFailed = { currentFailed.value(revision) },
        )
    }
    DisposableEffect(host) { onDispose { host.dispose() } }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .semantics { paneTitle = snapshot.title },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            ReaderToolbar(
                snapshot = snapshot,
                onClose = onClose,
                onBack = host::goBack,
                onOpenExternal = { onOpenExternal(originalUri) },
            )
            if (snapshot.progress < 100 && snapshot.status != NativeWikipediaStatus.Failed) {
                LinearProgressIndicator(
                    progress = { snapshot.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { host.webView },
                    modifier = Modifier.fillMaxSize(),
                )
                if (snapshot.status == NativeWikipediaStatus.Failed) {
                    ReaderFailure {
                        onRetry(revision)
                        host.reload()
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderToolbar(
    snapshot: NativeWikipediaSnapshot,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClose, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
            Text(stringResource(R.string.native_wikipedia_close))
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                snapshot.title,
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                snapshot.currentUri?.host.orEmpty(),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (snapshot.canGoBack) {
            TextButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Text("←")
            }
        }
        TextButton(
            onClick = onOpenExternal,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
        ) {
            Text(stringResource(R.string.native_wikipedia_open_browser))
        }
    }
}

@Composable
private fun ReaderFailure(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.native_wikipedia_failed),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                Text(stringResource(R.string.native_wikipedia_retry))
            }
        }
    }
}

private class NativeWikipediaWebViewHost private constructor(
    val webView: WebView,
    private val active: AtomicBoolean,
) {
    fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    fun reload() = webView.reload()

    fun dispose() {
        active.set(false)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.clearHistory()
        webView.clearCache(true)
        webView.removeAllViews()
        webView.destroy()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    companion object {
        @SuppressLint("SetJavaScriptEnabled")
        fun create(
            context: android.content.Context,
            originalUri: URI,
            onProgress: (Int) -> Unit,
            onPageStarted: (String) -> Unit,
            onPageFinished: (String, Boolean) -> Unit,
            onFailed: () -> Unit,
        ): NativeWikipediaWebViewHost {
            require(NativeWikipediaUriPolicy.isAllowedArticle(originalUri))
            val active = AtomicBoolean(true)
            val cookies = CookieManager.getInstance()
            val view = WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.databaseEnabled = false
                settings.setGeolocationEnabled(false)
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.mediaPlaybackRequiresUserGesture = true
                // Off: navigation is already confined to Wikipedia articles,
                // so Safe Browsing would only report every visited URL's hash
                // prefix to Google's service and protect nothing.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = false
                cookies.setAcceptThirdPartyCookies(this, false)
                clearCache(true)
                clearHistory()
                clearFormData()
            }
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean = !NativeWikipediaUriPolicy.allowsNavigation(
                    request.url.toString(),
                    request.isForMainFrame,
                )

                @Deprecated("Legacy callback is still required below API 24")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    !NativeWikipediaUriPolicy.allowsNavigation(url, isMainFrame = true)

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (active.get()) onPageStarted(url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (active.get()) onPageFinished(url, view.canGoBack())
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (active.get() && request.isForMainFrame) onFailed()
                }

                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: SslError,
                ) {
                    handler.cancel()
                    if (active.get()) onFailed()
                }
            }
            view.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (active.get()) onProgress(newProgress.coerceIn(0, 100))
                }

                override fun onPermissionRequest(request: PermissionRequest) = request.deny()

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String,
                    callback: GeolocationPermissions.Callback,
                ) = callback.invoke(origin, false, false)

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams,
                ): Boolean {
                    filePathCallback.onReceiveValue(emptyArray())
                    return true
                }
            }
            cookies.removeAllCookies {
                cookies.flush()
                view.post {
                    if (active.get()) view.loadUrl(originalUri.toASCIIString())
                }
            }
            return NativeWikipediaWebViewHost(view, active)
        }
    }
}
