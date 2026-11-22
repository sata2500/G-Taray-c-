package com.gtarayici.browser.ui.controller

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.lifecycleScope
import com.gtarayici.browser.R
import com.gtarayici.browser.diagnostics.Diagnostics
import com.gtarayici.browser.reading.Article
import com.gtarayici.browser.ui.feedback.MessageAction
import com.gtarayici.browser.ui.feedback.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.engine.EngineView

/** Sayfayı "Sonra oku" listesine kaydetme, sesli okuma, tam sayfa ekran görüntüsü ve PDF. */
class ReadingController(
    private val ctx: ControllerContext,
    private val engineView: () -> EngineView?,
    private val openReadingList: () -> Unit,
) {
    private val components get() = ctx.components
    private val scope get() = ctx.activity.lifecycleScope

    fun saveForLater() {
        ctx.ui.menuOpen = false
        val tab = ctx.selectedTab ?: return
        val session = tab.engineState.engineSession ?: return
        scope.launch {
            val extracted = components.resourceScanner.article(session)
            val saved = extracted?.let { components.readingList.save(it, tab.content.url) }
            if (saved == null) {
                ctx.messenger.warning(UiText.res(R.string.reading_no_article))
            } else {
                ctx.messenger.success(UiText.res(R.string.reading_saved), MessageAction(UiText.res(R.string.reading_open_list)) { openReadingList() })
            }
        }
    }

    fun readAloud() {
        ctx.ui.menuOpen = false
        val tab = ctx.selectedTab ?: return
        val session = tab.engineState.engineSession ?: return
        scope.launch {
            val extracted = components.resourceScanner.article(session)
            val article = extracted?.let { Article.fromExtraction(it, tab.content.url, "page", System.currentTimeMillis()) }
            if (article == null) ctx.messenger.warning(UiText.res(R.string.reading_no_article)) else speak(article)
        }
    }

    fun speak(article: Article, from: Int = 0) {
        components.speaker.start(article, from)
        if (components.speaker.unavailable) ctx.messenger.error(UiText.res(R.string.speaker_unavailable))
    }

    fun saveAsPdf() {
        ctx.ui.menuOpen = false
        val session = ctx.selectedTab?.engineState?.engineSession ?: return
        ctx.messenger.info(UiText.res(R.string.pdf_saving))
        runCatching { session.requestPdfToDownload() }.onFailure {
            Diagnostics.error("pdf", it)
            ctx.messenger.error(UiText.res(R.string.pdf_failed))
        }
    }

    /** Sayfanın tamamının görüntüsü (çok uzun sayfalarda motor sınırlar); olmazsa görünen kısım. */
    fun captureFullPage() {
        ctx.ui.menuOpen = false
        val view = engineView() ?: return
        ctx.messenger.info(UiText.res(R.string.screenshot_working))
        val onBitmap: (Bitmap?) -> Unit = { bitmap ->
            if (bitmap == null) {
                ctx.messenger.error(UiText.res(R.string.screenshot_failed))
            } else {
                scope.launch {
                    val uri = withContext(Dispatchers.IO) { save(bitmap) }
                    if (uri == null) {
                        ctx.messenger.error(UiText.res(R.string.screenshot_failed))
                    } else {
                        ctx.messenger.success(UiText.res(R.string.screenshot_saved), MessageAction(UiText.res(R.string.action_share)) { share(uri) })
                    }
                }
            }
        }
        runCatching {
            view.captureFullPage { full -> if (full != null) onBitmap(full) else view.captureThumbnail(onBitmap) }
        }.onFailure {
            Diagnostics.error("ekran görüntüsü", it)
            view.captureThumbnail(onBitmap)
        }
    }

    private fun save(bitmap: Bitmap): Uri? = runCatching {
        val resolver = ctx.activity.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "g-tarayici-${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/G-Tarayıcı")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) } ?: return null
        uri
    }.onFailure { Diagnostics.error("ekran görüntüsü", it) }.getOrNull()

    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.activity.startActivity(Intent.createChooser(send, null))
    }

    private companion object {
        const val JPEG_QUALITY = 90
    }
}
