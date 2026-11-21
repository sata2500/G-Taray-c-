package com.gtarayici.browser.ui.controller

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import com.gtarayici.browser.R
import com.gtarayici.browser.data.Bookmark
import com.gtarayici.browser.search.UrlInput
import com.gtarayici.browser.ui.Overlay
import com.gtarayici.browser.ui.feedback.ErrorArea
import com.gtarayici.browser.ui.feedback.HumanErrors
import com.gtarayici.browser.ui.feedback.UiText
import com.gtarayici.browser.webapk.WebApkInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Açık sayfayla ilgili işlemler: gezinme, paylaşma, yer imi, sayfada bul, okuma görünümü, uygulama olarak yükleme. */
interface PageActions {
    fun goBack(): Unit?
    fun goForward(): Unit?
    fun reloadOrStop()
    fun toggleDesktopMode()
    fun share()
    fun copyLink()

    /** Panodaki metni adres çubuğuna yazılmış gibi açar (adresse gider, değilse arar). */
    fun pasteAndGo()
    fun toggleBookmark(isBookmarked: Boolean)
    fun removeBookmark(url: String)
    fun openFindInPage()
    fun closeFindInPage()

    /** Okuma görünümünü açar veya kapatır. */
    fun toggleReader(active: Boolean)
    fun openReaderSettings()
    fun openTranslate()
    fun openQrScanner()
    fun openYouTubeDownload()
    fun openInstallDialog()

    /** Siteyi gerçek bir Android uygulaması (WebAPK) olarak yükler. */
    fun installWebApk()

    /**
     * Etkinlik öne gelince çağrılır: "Bilinmeyen uygulamaları yükle" ekranından dönüldüyse ve izin verildiyse
     * yarım kalan yükleme kendiliğinden sürer; izin verilmediyse bekleyen istek bırakılır.
     */
    fun resumePendingWebApkInstall()

    /** Ana ekrana basit bir kısayol (veya web uygulaması kısayolu) ekler. */
    fun addToHomescreen()
}

class PageController(private val ctx: ControllerContext, private val tabs: TabActions) : PageActions {
    private val components get() = ctx.components
    private val activity get() = ctx.activity

    /** İzin ekranına gidilirken yüklenmek istenen sekme; dönüşte izin verildiyse yükleme sürdürülür. */
    private var pendingWebApkTabId: String? = null

    override fun goBack() = ctx.selectedTab?.let { components.sessionUseCases.goBack(it.id) }

    override fun goForward() = ctx.selectedTab?.let { components.sessionUseCases.goForward(it.id) }

    override fun reloadOrStop() {
        val tab = ctx.selectedTab ?: return
        if (tab.content.loading) components.sessionUseCases.stopLoading(tab.id) else components.sessionUseCases.reload(tab.id)
    }

    override fun toggleDesktopMode() {
        val tab = ctx.selectedTab ?: return
        components.sessionUseCases.requestDesktopSite(!tab.content.desktopMode, tab.id)
        ctx.ui.menuOpen = false
    }

    override fun share() {
        val tab = ctx.selectedTab ?: return
        ctx.ui.menuOpen = false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, tab.content.title)
            putExtra(Intent.EXTRA_TEXT, tab.content.url)
        }
        activity.startActivity(Intent.createChooser(send, null))
    }

    override fun copyLink() {
        val tab = ctx.selectedTab ?: return
        ctx.ui.menuOpen = false
        activity.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("URL", tab.content.url))
        ctx.messenger.copied(UiText.res(R.string.link_copied))
    }

    override fun pasteAndGo() {
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            ctx.messenger.info(UiText.res(R.string.toolbar_clipboard_empty))
            return
        }
        tabs.submit(text)
    }

    override fun toggleBookmark(isBookmarked: Boolean) {
        val tab = ctx.selectedTab ?: return
        ctx.ui.menuOpen = false
        val dao = components.database.bookmarks()
        val profileId = ctx.profile.id
        // Menü kapandığı için işlemin sonucu bir mesajla gösterilir; kaldırma "Geri al" ile geri alınabilir.
        components.applicationScope.launch(Dispatchers.IO) {
            if (isBookmarked) {
                val removed = dao.list(profileId).firstOrNull { it.url == tab.content.url }
                dao.deleteByUrl(profileId, tab.content.url)
                if (removed != null) {
                    ctx.messenger.undoable(UiText.res(R.string.bookmark_removed), UNDO_BOOKMARK_KEY) {
                        components.applicationScope.launch(Dispatchers.IO) { dao.upsert(removed) }
                    }
                } else {
                    ctx.messenger.info(UiText.res(R.string.bookmark_removed))
                }
            } else {
                ctx.messenger.success(UiText.res(R.string.bookmark_added))
                dao.upsert(
                    Bookmark(
                        profileId = profileId,
                        url = tab.content.url,
                        title = tab.content.title.ifBlank { UrlInput.host(tab.content.url) },
                    ),
                )
            }
        }
    }

    override fun removeBookmark(url: String) {
        val profileId = ctx.profile.id
        components.applicationScope.launch(Dispatchers.IO) { components.database.bookmarks().deleteByUrl(profileId, url) }
    }

    override fun openFindInPage() {
        ctx.ui.menuOpen = false
        ctx.ui.findInPageOpen = true
    }

    override fun closeFindInPage() {
        ctx.ui.findInPageOpen = false
        ctx.selectedTab?.engineState?.engineSession?.clearFindMatches()
    }

    override fun toggleReader(active: Boolean) {
        ctx.ui.menuOpen = false
        val feature = ctx.readerFeature() ?: return
        if (active) feature.hideReaderView() else feature.showReaderView()
    }

    override fun openReaderSettings() {
        ctx.ui.menuOpen = false
        ctx.readerFeature()?.showControls()
    }

    override fun openTranslate() {
        ctx.ui.menuOpen = false
        ctx.ui.translateOpen = true
    }

    override fun openQrScanner() {
        ctx.ui.menuOpen = false
        ctx.ui.overlay = Overlay.QR
    }

    override fun openYouTubeDownload() {
        ctx.ui.menuOpen = false
        ctx.ui.youtubeUrl = ctx.selectedTab?.content?.url
    }

    override fun openInstallDialog() {
        ctx.ui.menuOpen = false
        ctx.ui.installDialogOpen = true
    }

    override fun installWebApk() {
        ctx.ui.installDialogOpen = false
        val tab = ctx.selectedTab ?: return
        val installer = components.webApkInstaller
        if (!installer.canInstall()) {
            pendingWebApkTabId = tab.id
            ctx.messenger.info(UiText.res(R.string.webapk_permission))
            installer.requestPermission()
            return
        }
        pendingWebApkTabId = null
        ctx.messenger.info(UiText.res(R.string.webapk_preparing))
        components.applicationScope.launch {
            when (val result = installer.install(tab)) {
                is WebApkInstaller.Result.Failed -> ctx.messenger.error(HumanErrors.message(ErrorArea.WebApk, result.message))
                WebApkInstaller.Result.NeedsPermission -> installer.requestPermission()
                WebApkInstaller.Result.Started -> Unit
            }
        }
    }

    override fun resumePendingWebApkInstall() {
        val id = pendingWebApkTabId ?: return
        pendingWebApkTabId = null
        if (components.webApkInstaller.canInstall() && ctx.selectedTab?.id == id) installWebApk()
    }

    override fun addToHomescreen() {
        ctx.ui.menuOpen = false
        ctx.ui.installDialogOpen = false
        if (!components.webAppUseCases.isPinningSupported()) {
            ctx.messenger.warning(UiText.res(R.string.webapp_not_supported))
            return
        }
        components.applicationScope.launch { components.webAppUseCases.addToHomescreen() }
    }

    private companion object {
        const val UNDO_BOOKMARK_KEY = "geri-al-yer-imi"
    }
}
