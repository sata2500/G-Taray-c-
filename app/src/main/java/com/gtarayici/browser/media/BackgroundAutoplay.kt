package com.gtarayici.browser.media

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.gtarayici.browser.diagnostics.Diagnostics
import com.gtarayici.browser.network.DataSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.permission.Permission
import mozilla.components.concept.engine.permission.PermissionRequest
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.concept.engine.permission.SitePermissionsStorage
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.ktx.kotlin.getOrigin

/**
 * Ekran kapalıyken (uygulamanın hiçbir penceresi görünür değilken) gelen otomatik oynatma izinlerini yanıtlar.
 *
 * Motor, oynatma listesinde veya YouTube/YouTube Music'te sıradaki video başlarken otomatik oynatma izni ister.
 * Mozilla'nın site izinleri bileşeni yalnızca pencere görünürken çalışır; ekran kapalıyken istek yanıtsız kalır ve
 * yeni video duraklatılmış bekler. Burada yalnızca otomatik oynatma istekleri, site izinlerine ve varsayılan kurallara
 * göre (sesli: izin verilen sitelerde, sessiz: veri tasarrufu kapalıysa) yanıtlanır; diğer izinlere dokunulmaz.
 */
class BackgroundAutoplay(
    private val store: BrowserStore,
    private val storage: SitePermissionsStorage,
    private val dataSaver: DataSaver,
    private val scope: CoroutineScope,
) : Application.ActivityLifecycleCallbacks {
    private val started = MutableStateFlow(0)

    fun start(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        scope.launch {
            combine(store.flow(), started) { state, visible -> if (visible > 0) emptyList() else pending(state) }
                .collect { requests -> requests.forEach { (tabId, request) -> answer(tabId, request) } }
        }
    }

    private val handled = mutableSetOf<String>()

    private fun pending(state: mozilla.components.browser.state.state.BrowserState): List<Pair<String, PermissionRequest>> =
        (state.tabs + state.customTabs).flatMap { tab ->
            tab.content.permissionRequestsList
                .filter { request -> request.permissions.isNotEmpty() && request.permissions.all { it.isAutoplay() } }
                .map { tab.id to it }
        }

    private suspend fun answer(tabId: String, request: PermissionRequest) {
        if (!handled.add(request.id)) return
        val origin = request.uri?.getOrigin()
        val private = store.state.tabs.firstOrNull { it.id == tabId }?.content?.private ?: false
        val stored = origin?.let { runCatching { storage.findSitePermissionsBy(it, includeTemporary = true, private = private) }.getOrNull() }
        val allow = request.permissions.all { permission ->
            when (permission) {
                is Permission.ContentAutoPlayAudible ->
                    stored?.autoplayAudible == SitePermissions.AutoplayStatus.ALLOWED
                is Permission.ContentAutoPlayInaudible ->
                    stored?.let { it.autoplayInaudible == SitePermissions.AutoplayStatus.ALLOWED } ?: !dataSaver.active.value
                else -> false
            }
        }
        if (allow) request.grant() else request.reject()
        store.dispatch(ContentAction.ConsumePermissionsRequest(tabId, request))
        Diagnostics.log("ekran kapalıyken otomatik oynatma ${if (allow) "izin verildi" else "engellendi"}", "medya")
        if (handled.size > MAX_REMEMBERED) handled.clear()
    }

    private fun Permission.isAutoplay() = this is Permission.ContentAutoPlayAudible || this is Permission.ContentAutoPlayInaudible

    override fun onActivityStarted(activity: Activity) {
        started.value++
    }

    override fun onActivityStopped(activity: Activity) {
        started.value = (started.value - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private companion object {
        const val MAX_REMEMBERED = 200
    }
}
