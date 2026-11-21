package com.gtarayici.browser.downloads.manager

import android.Manifest
import android.os.Build
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.downloads.manager.DownloadManager
import mozilla.components.feature.downloads.manager.onDownloadStopped

/**
 * Mozilla'nın indirme bileşenine ([mozilla.components.feature.downloads.DownloadsFeature]) bağlanan indirici:
 * sayfadaki bir indirme bağlantısına dokunulunca dosya, tek bağlantılı yerleşik indirici yerine
 * G-Tarayıcı'nın çok bağlantılı kuyruğuyla ([DownloadQueue]) indirilir.
 */
class FastDownloadManager(
    private val queue: DownloadQueue,
    private val store: BrowserStore,
) : DownloadManager {
    override var onDownloadStopped: onDownloadStopped = { _, _, _ -> }

    // Android 10+ İndirilenler'e MediaStore ile yazılır; izin gerekmez.
    override val permissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) emptyArray() else arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    override fun download(download: DownloadState, cookie: String): String? {
        queue.enqueue(
            NewDownload(
                url = download.url,
                suggestedName = download.fileName,
                mime = download.contentType,
                context = RequestContext(
                    userAgent = download.userAgent,
                    referer = download.referrerUrl,
                    private = download.private,
                    cookie = cookie,
                ),
                response = download.response,
                id = download.id,
            ),
        )
        return download.id
    }

    override fun tryAgain(downloadId: String) {
        val state = store.state.downloads[downloadId] ?: return
        queue.retryFromState(state)
    }
}
