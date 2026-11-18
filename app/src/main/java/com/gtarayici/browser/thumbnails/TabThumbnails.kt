package com.gtarayici.browser.thumbnails

import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.concept.base.images.ImageLoadRequest
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * Sekme önizlemelerini küçültülmüş hâlde bellekte tutar; bellekte yoksa diskten yükler.
 * Her yeni ekran görüntüsünde [version] artar, böylece sekme ekranı kendini yeniler.
 */
class TabThumbnails(private val storage: ThumbnailStorage, private val scope: CoroutineScope) {
    private val cache = LruCache<String, Bitmap>(MAX_ENTRIES)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun cached(tabId: String): Bitmap? = cache.get(tabId)

    suspend fun load(tabId: String, private: Boolean): Bitmap? {
        cache.get(tabId)?.let { return it }
        val bitmap = storage.loadThumbnail(ImageLoadRequest(tabId, TARGET_WIDTH, private)).await() ?: return null
        return downscale(bitmap).also { cache.put(tabId, it) }
    }

    private fun put(tabId: String, bitmap: Bitmap) {
        scope.launch(Dispatchers.Default) {
            cache.put(tabId, downscale(bitmap))
            _version.value++
        }
    }

    private fun remove(tabId: String) {
        cache.remove(tabId)
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= TARGET_WIDTH) return bitmap
        val height = (bitmap.height * TARGET_WIDTH.toFloat() / bitmap.width).toInt().coerceAtLeast(1)
        return bitmap.scale(TARGET_WIDTH, height)
    }

    /** Mozilla'nın ThumbnailsMiddleware'inden ÖNCE çalışmalıdır; o eylemi tüketir. */
    val middleware = object : Middleware<BrowserState, BrowserAction> {
        override fun invoke(
            store: Store<BrowserState, BrowserAction>,
            next: (BrowserAction) -> Unit,
            action: BrowserAction,
        ) {
            when (action) {
                is ContentAction.UpdateThumbnailAction -> put(action.sessionId, action.thumbnail)
                is TabListAction.RemoveTabAction -> remove(action.tabId)
                is TabListAction.RemoveTabsAction -> action.tabIds.forEach(::remove)
                is TabListAction.RemoveAllTabsAction -> cache.evictAll()
                else -> Unit
            }
            next(action)
        }
    }

    private companion object {
        const val MAX_ENTRIES = 30
        const val TARGET_WIDTH = 480
    }
}
