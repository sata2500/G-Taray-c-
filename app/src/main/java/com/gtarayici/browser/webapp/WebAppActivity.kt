package com.gtarayici.browser.webapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gtarayici.browser.MainActivity
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import com.gtarayici.browser.media.PipController
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.components.observe
import com.gtarayici.browser.ui.components.VideoDownloadButton
import com.gtarayici.browser.ui.components.YouTubeDownloadSheet
import com.gtarayici.browser.youtube.YouTubeDownloader
import com.gtarayici.browser.ui.components.keepSizeDuringPip
import com.gtarayici.browser.ui.theme.GTarayiciTheme
import mozilla.components.browser.state.selector.findCustomTab
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.intent.ext.getSessionId
import mozilla.components.feature.prompts.PromptFeature
import mozilla.components.feature.pwa.feature.WebAppActivityFeature
import mozilla.components.feature.session.FullScreenFeature
import mozilla.components.feature.session.SessionFeature
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.base.feature.PermissionsFeature
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper

/**
 * Ana ekrana yüklenen web uygulamalarını (PWA) kendi penceresinde, tarayıcı arayüzü olmadan çalıştırır.
 * Her web uygulaması son kullanılanlar listesinde ayrı bir uygulama gibi görünür.
 */
class WebAppActivity : AppCompatActivity() {
    private val components by lazy { applicationContext.components }
    private lateinit var engineView: EngineView
    private var tabId: String? = null
    private var fullscreen by mutableStateOf(false)
    private var inPip by mutableStateOf(false)
    private var pip: PipController? = null
    /** İndirme penceresi açıkken indirilecek video sayfası */
    private var downloadUrl by mutableStateOf<String?>(null)

    private val sessionFeature = ViewBoundFeatureWrapper<SessionFeature>()
    private val promptFeature = ViewBoundFeatureWrapper<PromptFeature>()
    private val fullScreenFeature = ViewBoundFeatureWrapper<FullScreenFeature>()

    private var pendingPermissions: (() -> PermissionsFeature?)? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pendingPermissions?.invoke()?.onPermissionsResult(
                result.keys.toTypedArray(),
                result.values.map { if (it) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED }.toIntArray(),
            )
            pendingPermissions = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val id = intent.getSessionId()?.takeIf { components.store.state.findCustomTab(it) != null }
            ?: run { if (components.webAppIntentProcessor.process(intent)) intent.getSessionId() else null }
        if (id == null) {
            // Bilinmeyen web uygulaması: normal tarayıcıda aç.
            intent.data?.let { startActivity(Intent(Intent.ACTION_VIEW, it, this, MainActivity::class.java)) }
            finish()
            return
        }
        tabId = id
        val manifest = components.store.state.findCustomTab(id)?.content?.webAppManifest

        engineView = components.engine.createView(this)
        engineView.setActivityContext(this)
        val view = engineView.asView()

        sessionFeature.set(
            SessionFeature(components.store, components.sessionUseCases.goBack, components.sessionUseCases.goForward, engineView, id),
            this,
            view,
        )
        promptFeature.set(
            PromptFeature(
                activity = this,
                store = components.store,
                customTabId = id,
                fragmentManager = supportFragmentManager,
                tabsUseCases = components.tabsUseCases,
                fileUploadsDirCleaner = components.fileUploadsDirCleaner,
                isSuggestEmailMaskEnabled = { false },
                isEmailMaskFeatureEnabled = { false },
                onNeedToRequestPermissions = { request(promptFeature, it) },
            ),
            this,
            view,
        )
        fullScreenFeature.set(
            FullScreenFeature(components.store, components.sessionUseCases, tabId = id, fullScreenChanged = { enabled ->
                fullscreen = enabled
                val insets = WindowCompat.getInsetsController(window, window.decorView)
                if (enabled) {
                    insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insets.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    insets.show(WindowInsetsCompat.Type.systemBars())
                }
            }),
            this,
            view,
        )
        manifest?.let { lifecycle.addObserver(WebAppActivityFeature(this, components.icons, it)) }
        pip = PipController(this, components, fixedTabId = id, engineView = { view }).also { it.start() }

        val scope = manifest?.scope ?: manifest?.startUrl
        setContent {
            val themeMode by components.settings.theme.collectAsState()
            val downloadButton by components.settings.videoDownloadButton.collectAsState()
            val url by components.store.observe { it.findCustomTab(id)?.content?.url.orEmpty() }
            val outOfScope = scope != null && url.isNotBlank() && !url.startsWith(scope)
            GTarayiciTheme(mode = themeMode) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxSize().then(if (fullscreen || inPip) Modifier else Modifier.systemBarsPadding()).imePadding()) {
                        if (outOfScope && !fullscreen && !inPip) {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { finish() }) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
                                    Text(
                                        UrlInput.displayUrl(url),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { openInBrowser(url) }) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.webapp_open_in_browser))
                                    }
                                }
                            }
                        }
                        AndroidView(factory = { view }, modifier = Modifier.keepSizeDuringPip(inPip))
                    }
                    // Adres çubuğu ve menü olmadığından YouTube videolarında küçük bir indirme düğmesi gösterilir.
                    VideoDownloadButton(
                        visible = downloadButton && !fullscreen && !inPip && downloadUrl == null &&
                            YouTubeDownloader.isVideoPage(url),
                        onClick = { downloadUrl = url },
                        resetKey = url,
                        modifier = Modifier.systemBarsPadding().padding(top = 56.dp, bottom = 72.dp),
                    )
                    downloadUrl?.let { YouTubeDownloadSheet(it) { downloadUrl = null } }
                    pip?.let { controller -> AndroidView(factory = { controller.pipView }, modifier = Modifier.fillMaxSize()) }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (!fullScreenFeature.onBackPressed() && !sessionFeature.onBackPressed()) finish()
        }
    }

    /** Video oynarken uygulamadan çıkılınca küçük pencereye geçilir (tarayıcıdaki gibi). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pip?.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        pip?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    private fun openInBrowser(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri(), this, MainActivity::class.java))
    }

    private fun <T> request(feature: ViewBoundFeatureWrapper<T>, permissions: Array<String>)
        where T : PermissionsFeature, T : LifecycleAwareFeature {
        pendingPermissions = { feature.get() }
        permissionLauncher.launch(permissions)
    }

    @Deprecated("PromptFeature dosya seçici sonuçlarını bu yolla alır.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        promptFeature.onActivityResult(requestCode, data, resultCode)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::engineView.isInitialized) engineView.setActivityContext(null)
        pip?.stop()
    }
}
