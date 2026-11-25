package com.gtarayici.browser.downloads.manager

import android.content.Context
import com.gtarayici.browser.R
import java.io.IOException

/** Adres beklenen dosya yerine bir web sayfası döndürdü (süresi dolmuş bağlantı, oturum gerektiren dosya…). */
class PageInsteadOfFileException : IOException("Adres dosya yerine bir web sayfası döndürdü")

/** İndirme hatalarının kullanıcıya anlatılan nedenleri. */
object DownloadErrors {
    enum class Kind { EXPIRED, FORBIDDEN, RATE_LIMITED, SERVER, LIVE, PROTECTED, PAGE_INSTEAD, STORAGE, NETWORK, OTHER }

    /** Hatanın türü ve (varsa) HTTP durum kodu (saf, test edilebilir). */
    fun classify(error: Throwable): Pair<Kind, Int?> {
        var current: Throwable? = error
        while (current != null) {
            when (current) {
                is SegmentedDownloader.HttpStatusException -> return when (val status = current.status) {
                    404, 410 -> Kind.EXPIRED to status
                    401, 403 -> Kind.FORBIDDEN to status
                    429 -> Kind.RATE_LIMITED to status
                    in 500..599 -> Kind.SERVER to status
                    else -> Kind.OTHER to status
                }
                is UnsupportedStreamException ->
                    return (if (current.reason == UnsupportedStreamException.Reason.LIVE) Kind.LIVE else Kind.PROTECTED) to null
                is PageInsteadOfFileException -> return Kind.PAGE_INSTEAD to null
            }
            val message = current.message.orEmpty()
            if (message.contains("ENOSPC") || message.contains("No space", ignoreCase = true)) return Kind.STORAGE to null
            current = current.cause?.takeIf { it !== current }
        }
        return (if (error is IOException) Kind.NETWORK else Kind.OTHER) to null
    }

    /** Tanılama kaydı için kısa ve sabit açıklama (sınıf adları küçültülmüş sürümde okunamaz). */
    fun code(error: Throwable): String {
        val (kind, status) = classify(error)
        return listOfNotNull(kind.name.lowercase(), status?.let { "HTTP $it" }).joinToString(" ")
    }

    /** Kullanıcıya gösterilecek neden ve ne yapabileceği. */
    fun reason(context: Context, error: Throwable): String {
        val (kind, status) = classify(error)
        return when (kind) {
            Kind.EXPIRED -> context.getString(R.string.dl_err_expired, status ?: 404)
            Kind.FORBIDDEN -> context.getString(R.string.dl_err_forbidden, status ?: 403)
            Kind.RATE_LIMITED -> context.getString(R.string.dl_err_rate)
            Kind.SERVER -> context.getString(R.string.dl_err_server, status ?: 500)
            Kind.LIVE -> context.getString(R.string.dl_err_live)
            Kind.PROTECTED -> context.getString(R.string.dl_err_protected)
            Kind.PAGE_INSTEAD -> context.getString(R.string.dl_err_page)
            Kind.STORAGE -> context.getString(R.string.dl_err_storage)
            Kind.NETWORK -> context.getString(R.string.dl_err_network)
            Kind.OTHER -> context.getString(R.string.dl_err_other)
        }
    }
}
