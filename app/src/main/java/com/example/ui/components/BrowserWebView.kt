package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.BrowserExtension

@Composable
fun BrowserWebView(
    url: String,
    onPageStarted: (String) -> Unit,
    onPageFinished: (String, String) -> Unit,
    activeExtensions: List<BrowserExtension>,
    onVideoDetected: (String, String) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Re-load URL if changed from outside
    LaunchedEffect(url) {
        webViewInstance?.let {
            if (it.url != url) {
                it.loadUrl(url)
            }
        }
    }

    // Dynamic Injection when Page changes or active extensions toggle
    LaunchedEffect(activeExtensions) {
        webViewInstance?.let { web ->
            activeExtensions.forEach { ext ->
                if (ext.isEnabled) {
                    web.evaluateJavascript(ext.scriptText, null)
                }
            }
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                onWebViewCreated(this)
                webViewInstance = this
                
                // Configure high-performance browsing
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mediaPlaybackRequiresUserGesture = false
                }

                // Add Javascript interface to hook HTML5 videos
                addJavascriptInterface(
                    WebAppInterface(context) { videoUrl, videoTitle ->
                        onVideoDetected(videoUrl, videoTitle)
                    }, 
                    "GBrowserBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        url?.let { onPageStarted(it) }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val loadedUrl = url ?: ""
                        val title = view?.title ?: loadedUrl
                        onPageFinished(loadedUrl, title)

                        // Run video elements scan inside the website
                        view?.evaluateJavascript(
                            """
                            (function() {
                                // Scans DOM for video files
                                const scanVideos = () => {
                                    const vids = document.querySelectorAll('video');
                                    vids.forEach(v => {
                                        if (v.src && v.src.startsWith('http')) {
                                            GBrowserBridge.onVideoFound(v.src, document.title || "Web Video");
                                        }
                                        const sources = v.querySelectorAll('source');
                                        sources.forEach(src => {
                                            if (src.src && src.src.startsWith('http')) {
                                                GBrowserBridge.onVideoFound(src.src, document.title || "Web Video");
                                            }
                                        });
                                    });
                                };
                                scanVideos();
                                setTimeout(scanVideos, 1500);
                                setTimeout(scanVideos, 3500);
                            })();
                            """.trimIndent(),
                            null
                        )

                        // Inject Custom JS plugins/extensions
                        activeExtensions.forEach { ext ->
                            if (ext.isEnabled) {
                                view?.evaluateJavascript(ext.scriptText, null)
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        // Keep navigation inside WebView
                        return false
                    }
                }

                loadUrl(url)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("browser_webview")
    )
}

class WebAppInterface(
    private val context: Context,
    private val onVideoFound: (String, String) -> Unit
) {
    @JavascriptInterface
    fun onVideoFound(url: String, title: String) {
        onVideoFound.invoke(url, title)
    }
}
