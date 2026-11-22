package com.gtarayici.browser.diagnostics

import android.app.Activity
import android.os.Process
import android.os.SystemClock
import android.view.ViewTreeObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flow

/**
 * Soğuk açılış süresini tanılama kaydına yazar (cihazdan gerçek ölçüm):
 *  - "ilk kare": süreç başlangıcından uygulama arayüzünün ilk çizimine,
 *  - "sayfa": açık sekmenin ilk anlamlı içeriği (first contentful paint) ekrana gelene kadar.
 * Yalnızca sürecin ilk etkinliği için ölçülür; uygulama zaten açıkken dönüşler sayılmaz.
 */
object StartupTimer {
    private var measured = false

    fun watch(activity: Activity, store: BrowserStore, scope: CoroutineScope) {
        if (measured) return
        measured = true
        val start = Process.getStartUptimeMillis()
        // Süreç başka bir nedenle (ör. indirme hizmeti) çok önce başladıysa ölçüm anlamsızdır.
        if (SystemClock.uptimeMillis() - start > STALE_MS) return
        val view = activity.window.decorView
        view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                view.viewTreeObserver.removeOnPreDrawListener(this)
                val firstFrame = SystemClock.uptimeMillis() - start
                Diagnostics.log("ilk kare ${firstFrame} ms", "açılış")
                return true
            }
        })
        scope.launch {
            // Sekmeler açılışta geri yüklenir; açık bir sayfa yoksa (ana ekran) süre dolar ve bir şey yazılmaz.
            val painted = withTimeoutOrNull(PAGE_TIMEOUT_MS) {
                store.flow().map { state ->
                    state.selectedTab?.content?.let { it.url != "about:blank" && it.firstContentfulPaint } == true
                }.distinctUntilChanged().first { it }
            }
            if (painted != null) Diagnostics.log("sayfa ${SystemClock.uptimeMillis() - start} ms", "açılış")
        }
    }

    private const val STALE_MS = 10_000L
    private const val PAGE_TIMEOUT_MS = 30_000L
}
