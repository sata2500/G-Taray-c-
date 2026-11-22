package com.gtarayici.browser

import androidx.core.content.edit
import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gtarayici.browser.ui.BrowserApp
import com.gtarayici.browser.ui.BrowserController
import com.gtarayici.browser.ui.Overlay
import com.gtarayici.browser.ui.components.ScrollTrackingLayout
import android.content.res.Configuration
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.browser.thumbnails.facts.BrowserThumbnailsFacts
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.feature.contextmenu.ContextMenuFeature
import mozilla.components.feature.downloads.DownloadsFeature
import mozilla.components.feature.prompts.PromptFeature
import mozilla.components.feature.privatemode.feature.SecureWindowFeature
import mozilla.components.feature.readerview.ReaderViewFeature
import mozilla.components.feature.session.FullScreenFeature
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.session.SwipeRefreshFeature
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch
import mozilla.components.feature.sitepermissions.SitePermissionsFeature
import mozilla.components.feature.sitepermissions.SitePermissionsRules
import mozilla.components.feature.sitepermissions.SitePermissionsRules.Action
import mozilla.components.feature.sitepermissions.SitePermissionsRules.AutoplayAction
import mozilla.components.support.base.feature.PermissionsFeature
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.support.ktx.util.URLStringUtils

class MainActivity : AppCompatActivity() {
    private val components by lazy { applicationContext.components }
    private lateinit var engineView: EngineView
    private lateinit var controller: BrowserController

    private var fullscreen by mutableStateOf(false)
    private var inPictureInPicture by mutableStateOf(false)
    private lateinit var pip: com.gtarayici.browser.media.PipController

    private val sessionFeature = ViewBoundFeatureWrapper<SessionFeature>()
    private val fullScreenFeature = ViewBoundFeatureWrapper<FullScreenFeature>()
    private val downloadsFeature = ViewBoundFeatureWrapper<DownloadsFeature>()
    private val promptFeature = ViewBoundFeatureWrapper<PromptFeature>()
    private val contextMenuFeature = ViewBoundFeatureWrapper<ContextMenuFeature>()
    private val sitePermissionsFeature = ViewBoundFeatureWrapper<SitePermissionsFeature>()
    private val thumbnailsFeature = ViewBoundFeatureWrapper<BrowserThumbnails>()
    private val readerViewFeature = ViewBoundFeatureWrapper<ReaderViewFeature>()
    private val swipeRefreshFeature = ViewBoundFeatureWrapper<SwipeRefreshFeature>()

    /** Motor görünümünü saran "aşağı çekerek yenile" kabı. */
    private lateinit var swipeRefresh: SwipeRefreshLayout

    /** Kaydırmayı izleyip adres çubuğunu gizleyen/gösteren en dış kap. */
    private lateinit var scrollContainer: ScrollTrackingLayout

    private var pendingPermissionFeature: (() -> PermissionsFeature?)? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val feature = pendingPermissionFeature?.invoke() ?: return@registerForActivityResult
            pendingPermissionFeature = null
            feature.onPermissionsResult(
                result.keys.toTypedArray(),
                result.values.map { if (it) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED }
                    .toIntArray(),
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        controller = BrowserController(this, components) {
            thumbnailsFeature.get()?.requestScreenshot(BrowserThumbnailsFacts.CaptureAttemptedTriggers.TAB_COUNTER_CLICK)
        }
        engineView = components.engine.createView(this)
        engineView.setActivityContext(this)
        swipeRefresh = SwipeRefreshLayout(this).apply {
            addView(engineView.asView(), ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setColorSchemeColors(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0xFF0B57D0.toInt()))
        }
        controller.dynamicToolbar.engineView = engineView
        controller.captureScreenshot = { onResult -> engineView.captureThumbnail { onResult(it) } }
        scrollContainer = ScrollTrackingLayout(this, engineView, controller.dynamicToolbar, swipeRefresh)
        setupFeatures()
        lifecycleScope.launch {
            components.settings.pullToRefresh.collect { updatePullToRefresh() }
        }

        pip = com.gtarayici.browser.media.PipController(this, components, engineView = { engineView.asView() }).also { it.start() }
        com.gtarayici.browser.media.BackgroundPlayGuard(this, components).start()
        // Gizli sekmede ekran görüntüsü engeli: ayar değişince uygulamayı yeniden başlatmadan uygulanır.
        lifecycleScope.launch {
            var secure: SecureWindowFeature? = null
            components.settings.securePrivateTabs.collect { enabled ->
                if (enabled && secure == null) {
                    secure = SecureWindowFeature(window, components.store).also { lifecycle.addObserver(it) }
                } else if (!enabled) {
                    secure?.let {
                        lifecycle.removeObserver(it)
                        it.stop()
                    }
                    secure = null
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            BrowserApp(
                controller,
                scrollContainer,
                fullscreen || inPictureInPicture,
                inPictureInPicture,
                pipView = pip.pipView,
            )
        }

        onBackPressedDispatcher.addCallback(this) {
            val handled = fullScreenFeature.onBackPressed() || promptFeature.onBackPressed() || controller.handleBack()
            if (!handled) {
                // Geri tuşuyla arka plana alınmak bir uygulama geçişi değildir.
                pip.noteOwnLaunch()
                moveTaskToBack(true)
            }
        }

        if (savedInstanceState == null) handleIntent(intent)
        controller.onboarding = BrowserController.needsOnboarding(getSharedPreferences("gtarayici_state", MODE_PRIVATE))
        // Bildirim izni tanıtımın üstünde açılmasın: tanıtım varsa bittikten sonra sorulur.
        controller.onOnboardingFinished = { requestNotificationPermissionOnce() }
        if (!controller.onboarding) requestNotificationPermissionOnce()
        // Güncelleme denetimi ağ kullanır; açılıştaki sayfa yüklemesiyle yarışmasın.
        window.decorView.postDelayed({ components.updater.checkIfDue() }, UPDATE_CHECK_DELAY_MS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        engineView.setActivityContext(null)
        pip.stop()
    }

    /** Uygulamadan ayrılırken video oynuyorsa resim içinde resme geçilir. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pip.onUserLeaveHint()
    }

    override fun onResume() {
        super.onResume()
        if (::controller.isInitialized) controller.resumePendingWebApkInstall()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if (::pip.isInitialized) pip.onTopResumedChanged(isTopResumedActivity)
    }

    /** Uygulamanın kendi açtığı ekranlar (paylaşım, dosya seçici…) PiP'i tetiklemesin. */
    @Deprecated("Tüm ekran açma çağrıları buradan geçer.")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        if (::pip.isInitialized) pip.noteOwnLaunch()
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
        updatePullToRefresh()
        pip.onPictureInPictureModeChanged(isInPictureInPictureMode)
        // Küçük pencere kapatıldıysa uygulama arka plandadır: kilitler yeniden devreye girer.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            components.appLock.lockAll()
        }
    }

    /** Uygulama arka plana geçince kilitli profiller ve gizli sekmeler yeniden kilitlenir. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !components.appLock.authenticating && !inPictureInPicture) {
            components.appLock.lockAll()
        }
    }

    @Deprecated("PromptFeature dosya seçici sonuçlarını bu yolla alır.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        promptFeature.onActivityResult(requestCode, data, resultCode)
    }

    private fun setupFeatures() {
        val view = engineView.asView()
        val store = components.store

        sessionFeature.set(
            feature = SessionFeature(
                store,
                components.sessionUseCases.goBack,
                components.sessionUseCases.goForward,
                engineView,
            ),
            owner = this,
            view = view,
        )

        fullScreenFeature.set(
            feature = FullScreenFeature(store, components.sessionUseCases, fullScreenChanged = ::onFullScreenChanged),
            owner = this,
            view = view,
        )

        downloadsFeature.set(
            feature = DownloadsFeature(
                applicationContext,
                store = store,
                useCases = components.downloadsUseCases,
                fragmentManager = supportFragmentManager,
                downloadFileUtils = components.downloadFileUtils,
                // Sayfadaki indirme bağlantıları çok bağlantılı kuyrukla indirilir.
                downloadManager = com.gtarayici.browser.downloads.manager.FastDownloadManager(components.downloadQueue, store),
                onNeedToRequestPermissions = { requestPermissionsFor(downloadsFeature, it) },
            ),
            owner = this,
            view = view,
        )

        promptFeature.set(
            feature = PromptFeature(
                activity = this,
                store = store,
                fragmentManager = supportFragmentManager,
                tabsUseCases = components.tabsUseCases,
                fileUploadsDirCleaner = components.fileUploadsDirCleaner,
                isSuggestEmailMaskEnabled = { false },
                isEmailMaskFeatureEnabled = { false },
                // Parola kaydetme ve doldurma (bkz. passwords/).
                loginValidationDelegate = components.geckoLogins.validation,
                isLoginAutofillEnabled = { components.settings.autofillPasswords.value },
                isSaveLoginEnabled = { components.settings.savePasswords.value },
                loginExceptionStorage = components.geckoLogins.exceptions,
                loginDelegate = object : mozilla.components.feature.prompts.login.LoginDelegate {
                    override val loginPickerView = controller.loginPicker
                    override val onManageLogins: () -> Unit = { controller.open(com.gtarayici.browser.ui.Overlay.PASSWORDS) }
                },
                onNeedToRequestPermissions = { requestPermissionsFor(promptFeature, it) },
            ),
            owner = this,
            view = view,
        )

        contextMenuFeature.set(
            feature = ContextMenuFeature(
                fragmentManager = supportFragmentManager,
                store = store,
                candidates = ContextMenuCandidate.defaultCandidates(
                    context = this,
                    tabsUseCases = components.tabsUseCases,
                    contextMenuUseCases = components.contextMenuUseCases,
                    snackBarParentView = view,
                    downloadsLocation = {
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
                    },
                ),
                engineView = engineView,
                useCases = components.contextMenuUseCases,
            ),
            owner = this,
            view = view,
        )

        thumbnailsFeature.set(
            feature = BrowserThumbnails(this, engineView, store),
            owner = this,
            view = view,
        )

        swipeRefreshFeature.set(
            feature = SwipeRefreshFeature(store, components.sessionUseCases.reload, swipeRefresh),
            owner = this,
            view = view,
        )

        readerViewFeature.set(
            feature = ReaderViewFeature(this, components.engine, store, controller.readerControls),
            owner = this,
            view = view,
        )
        controller.readerFeature = { readerViewFeature.get() }

        sitePermissionsFeature.set(
            feature = SitePermissionsFeature(
                context = this,
                storage = components.permissionStorage,
                fragmentManager = supportFragmentManager,
                sitePermissionsRules = sitePermissionRules(components.dataSaver.active.value),
                onNeedToRequestPermissions = { requestPermissionsFor(sitePermissionsFeature, it) },
                onShouldShowRequestPermissionRationale = { shouldShowRequestPermissionRationale(it) },
                store = store,
                coroutineScope = components.applicationScope,
            ),
            owner = this,
            view = view,
        )
        // Veri tasarrufunda sessiz videolar da kendiliğinden oynamaz (izin verilen siteler hariç).
        lifecycleScope.launch {
            components.dataSaver.active.collect { saving ->
                sitePermissionsFeature.get()?.sitePermissionsRules = sitePermissionRules(saving)
            }
        }
    }

    private fun sitePermissionRules(dataSaver: Boolean) = SitePermissionsRules(
        autoplayAudible = AutoplayAction.BLOCKED,
        autoplayInaudible = if (dataSaver) AutoplayAction.BLOCKED else AutoplayAction.ALLOWED,
        camera = Action.ASK_TO_ALLOW,
        location = Action.ASK_TO_ALLOW,
        notification = Action.ASK_TO_ALLOW,
        microphone = Action.ASK_TO_ALLOW,
        persistentStorage = Action.ASK_TO_ALLOW,
        mediaKeySystemAccess = Action.ASK_TO_ALLOW,
        crossOriginStorageAccess = Action.ASK_TO_ALLOW,
        localDeviceAccess = Action.ASK_TO_ALLOW,
        localNetworkAccess = Action.ASK_TO_ALLOW,
    )

    private fun <T> requestPermissionsFor(feature: ViewBoundFeatureWrapper<T>, permissions: Array<String>)
        where T : PermissionsFeature, T : mozilla.components.support.base.feature.LifecycleAwareFeature {
        pendingPermissionFeature = { feature.get() }
        permissionLauncher.launch(permissions)
    }

    /** Tam ekranda ve küçük pencerede aşağı çekerek yenileme kapalıdır (video hareketleriyle çakışmasın). */
    private fun updatePullToRefresh() {
        swipeRefresh.isEnabled = components.settings.pullToRefresh.value && !fullscreen && !inPictureInPicture
    }

    private fun onFullScreenChanged(enabled: Boolean) {
        fullscreen = enabled
        updatePullToRefresh()
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** Diğer uygulamalardan gelen bağlantı, arama ve paylaşımları yeni sekmede açar. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val url = intent.dataString ?: return
                controller.loadUrl(url, inNewTab = true, private = false)
            }
            ACTION_OPEN_DOWNLOADS -> controller.open(Overlay.DOWNLOADS)
            Intent.ACTION_WEB_SEARCH -> {
                val query = intent.getStringExtra(SearchManager.QUERY) ?: return
                controller.loadUrl(components.settings.searchEngine.value.searchUrl(query), inNewTab = true)
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: return
                val url = if (URLStringUtils.isURLLike(text)) {
                    URLStringUtils.toNormalizedURL(text)
                } else {
                    components.settings.searchEngine.value.searchUrl(text)
                }
                controller.loadUrl(url, inNewTab = true)
            }
        }
    }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences("gtarayici_state", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit { putBoolean("asked_notifications", true) }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            registerNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val registerNotificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    companion object {
        const val ACTION_OPEN_DOWNLOADS = "com.gtarayici.browser.OPEN_DOWNLOADS"
        private const val UPDATE_CHECK_DELAY_MS = 8_000L
    }
}
