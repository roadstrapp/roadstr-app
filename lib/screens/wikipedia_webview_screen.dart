import 'dart:async';

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:webview_flutter_android/webview_flutter_android.dart';

import '../l10n/app_localizations.dart';
import '../theme/app_theme.dart';

/// A deliberately narrow in-app browser for Wikipedia articles.
///
/// It is not a general browser: JavaScript, WebView permissions, non-Wikipedia
/// main-frame navigation, mixed content and local-file access are disabled.
class WikipediaWebViewScreen extends StatefulWidget {
  final Uri articleUri;
  final String title;

  WikipediaWebViewScreen({
    super.key,
    required this.articleUri,
    required this.title,
  }) {
    if (!isAllowedArticleUri(articleUri)) {
      throw ArgumentError.value(articleUri, 'articleUri');
    }
  }

  /// Only normal HTTPS article URLs on a language subdomain are accepted.
  /// `wikipedia.org.evil.test`, credentials and custom ports are rejected.
  static bool isAllowedArticleUri(Uri uri) {
    final labels = uri.host.toLowerCase().split('.');
    final standardHost =
        labels.length == 3 && labels[1] == 'wikipedia' && labels[2] == 'org';
    final mobileHost = labels.length == 4 &&
        labels[1] == 'm' &&
        labels[2] == 'wikipedia' &&
        labels[3] == 'org';
    return uri.scheme == 'https' &&
        uri.userInfo.isEmpty &&
        !uri.hasPort &&
        (standardHost || mobileHost) &&
        RegExp(r'^[a-z0-9-]{1,24}$').hasMatch(labels[0]) &&
        uri.path.startsWith('/wiki/') &&
        uri.path.length > '/wiki/'.length;
  }

  @override
  State<WikipediaWebViewScreen> createState() => _WikipediaWebViewScreenState();
}

class _WikipediaWebViewScreenState extends State<WikipediaWebViewScreen> {
  late final WebViewController _controller;
  final _cookies = WebViewCookieManager();
  int _progress = 0;
  bool _pageFailed = false;
  bool _canGoBack = false;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController(
      onPermissionRequest: (request) => unawaited(request.deny()),
    );
    unawaited(_prepareAndLoad());
  }

  Future<void> _prepareAndLoad() async {
    try {
      await _controller.setJavaScriptMode(JavaScriptMode.disabled);
      await _controller.setBackgroundColor(const Color(0x00000000));
      await _controller.setNavigationDelegate(
        NavigationDelegate(
          onProgress: (value) {
            if (mounted) setState(() => _progress = value.clamp(0, 100));
          },
          onPageStarted: (_) {
            if (mounted) {
              setState(() {
                _progress = 0;
                _pageFailed = false;
              });
            }
          },
          onPageFinished: (_) => unawaited(_refreshHistoryState()),
          onWebResourceError: (error) {
            if (error.isForMainFrame == true && mounted) {
              setState(() => _pageFailed = true);
            }
          },
          onSslAuthError: (error) => unawaited(error.cancel()),
          onNavigationRequest: (request) {
            if (!request.isMainFrame) return NavigationDecision.prevent;
            final uri = Uri.tryParse(request.url);
            if (uri != null &&
                WikipediaWebViewScreen.isAllowedArticleUri(uri)) {
              return NavigationDecision.navigate;
            }
            // Never turn an automatic redirect into an external-app launch.
            // Leaving the restricted reader is possible only through the
            // explicit app-bar action, whose destination is the original
            // validated Wikipedia article.
            return NavigationDecision.prevent;
          },
        ),
      );
      await _cookies.clearCookies();
      await _controller.clearCache();
      await _controller.clearLocalStorage();
      final platform = _controller.platform;
      if (platform is AndroidWebViewController) {
        await platform.setAllowFileAccess(false);
        await platform.setMixedContentMode(MixedContentMode.neverAllow);
        await platform.setMediaPlaybackRequiresUserGesture(true);
        await platform.setOnShowFileSelector((_) async => const <String>[]);
        await platform.setGeolocationPermissionsPromptCallbacks(
          onShowPrompt: (_) async => const GeolocationPermissionsResponse(
            allow: false,
            retain: false,
          ),
        );
      }
      await _controller.loadRequest(widget.articleUri);
    } catch (_) {
      if (mounted) setState(() => _pageFailed = true);
    }
  }

  @override
  void dispose() {
    unawaited(_controller.clearCache());
    unawaited(_controller.clearLocalStorage());
    unawaited(_cookies.clearCookies());
    super.dispose();
  }

  Future<void> _openExternally() => launchUrl(
        widget.articleUri,
        mode: LaunchMode.externalApplication,
      );

  Future<void> _refreshHistoryState() async {
    final canGoBack = await _controller.canGoBack();
    if (mounted && canGoBack != _canGoBack) {
      setState(() => _canGoBack = canGoBack);
    }
  }

  Future<void> _goBack() async {
    if (await _controller.canGoBack()) {
      await _controller.goBack();
      await _refreshHistoryState();
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = RoadstrColors.of(context);
    final l = AppLocalizations.of(context);
    return PopScope(
      canPop: !_canGoBack,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_goBack());
      },
      child: Scaffold(
        appBar: AppBar(
          titleSpacing: 0,
          title: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(widget.title, maxLines: 1, overflow: TextOverflow.ellipsis),
              Text(
                widget.articleUri.host,
                style: TextStyle(color: colors.textSecondary, fontSize: 11),
              ),
            ],
          ),
          actions: [
            if (_canGoBack)
              IconButton(
                tooltip: MaterialLocalizations.of(context).backButtonTooltip,
                onPressed: () => unawaited(_goBack()),
                icon: const Icon(Icons.arrow_back_rounded),
              ),
            IconButton(
              tooltip: l.openInBrowser,
              onPressed: () => unawaited(_openExternally()),
              icon: const Icon(Icons.open_in_browser_rounded),
            ),
          ],
          bottom: _progress < 100
              ? PreferredSize(
                  preferredSize: const Size.fromHeight(2),
                  child: LinearProgressIndicator(
                    value: _progress / 100,
                    minHeight: 2,
                    color: colors.accent,
                  ),
                )
              : null,
        ),
        body: _pageFailed
            ? Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.cloud_off_rounded,
                          color: colors.textSecondary, size: 42),
                      const SizedBox(height: 12),
                      Text(l.wikipediaLoadFailed, textAlign: TextAlign.center),
                      const SizedBox(height: 12),
                      OutlinedButton.icon(
                        onPressed: () => unawaited(_controller.reload()),
                        icon: const Icon(Icons.refresh_rounded),
                        label: Text(l.retry),
                      ),
                    ],
                  ),
                ),
              )
            : WebViewWidget(controller: _controller),
      ),
    );
  }
}
