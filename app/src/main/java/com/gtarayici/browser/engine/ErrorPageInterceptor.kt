package com.gtarayici.browser.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mozilla.components.browser.errorpages.ErrorPages
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.request.ErrorType
import mozilla.components.concept.engine.request.RequestInterceptor

/**
 * Sayfa yüklenemediğinde (alan adı bulunamadı, bağlantı yok, güvenli site yok…) motorun boş kalmaması için
 * Mozilla'nın yerelleştirilmiş hata sayfasını gösterir. Bu yakalayıcı olmadan GeckoView önceki sayfada kalır ve
 * kullanıcı hiçbir geri bildirim almaz (ör. QR koduyla açılan bir adres sessizce başarısız olur).
 *
 * Hata sayfası gösterilen adresler [failedUrls] içinde tutulur: şablonun dili belirsiz olduğundan motor onu
 * "İngilizce" sanıp çeviri önerir; arayüz bu adreslerde çeviri önerisini ve okuma simgesini gizler.
 */
class ErrorPageInterceptor(private val context: Context) : RequestInterceptor {
    private val failed = MutableStateFlow<Set<String>>(emptySet())
    val failedUrls: StateFlow<Set<String>> = failed.asStateFlow()

    // Adres çubuğundan yapılan yüklemeler de görülsün (yeniden deneme başarılı olunca işaret kalkar).
    override fun interceptsAppInitiatedRequests(): Boolean = true

    override fun onLoadRequest(
        engineSession: EngineSession,
        uri: String,
        lastUri: String?,
        hasUserGesture: Boolean,
        isSameDomain: Boolean,
        isRedirect: Boolean,
        isDirectNavigation: Boolean,
        isSubframeRequest: Boolean,
    ): RequestInterceptor.InterceptionResponse? {
        if (!isSubframeRequest && uri in failed.value) failed.update { it - uri }
        return null
    }

    override fun onErrorRequest(
        session: EngineSession,
        errorType: ErrorType,
        uri: String?,
    ): RequestInterceptor.ErrorResponse {
        if (uri != null) failed.update { (it + uri).toList().takeLast(MAX_TRACKED).toSet() }
        // Yalnızca hata türü yazılır; adres (gizli sekme olabilir) kayda girmez.
        com.gtarayici.browser.diagnostics.Diagnostics.log("yüklenemedi (${errorType.name})", "sayfa")
        return RequestInterceptor.ErrorResponse(ErrorPages.createUrlEncodedErrorPage(context, errorType, uri))
    }

    private companion object {
        const val MAX_TRACKED = 50
    }
}
