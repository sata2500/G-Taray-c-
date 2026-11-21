package com.gtarayici.browser.ui.feedback

import androidx.annotation.StringRes
import com.gtarayici.browser.R

/** Hatanın uygulamanın hangi işinde çıktığı; mesajın başlığını belirler. */
enum class ErrorArea(@StringRes val lead: Int) {
    VideoInfo(R.string.err_lead_video_info),
    VideoDownload(R.string.err_lead_video_download),
    VpnConnect(R.string.err_lead_vpn_connect),
    VpnTest(R.string.err_lead_vpn_test),
    VpnLink(R.string.err_lead_vpn_link),
    VpnCore(R.string.err_lead_vpn_core),
    WebApk(R.string.err_lead_webapk),
    Translate(R.string.err_lead_translate),
    Install(R.string.err_lead_install),
    Update(R.string.err_lead_update),
}

/** Kullanıcının anlayabileceği hata türü. Her biri için "ne yapılmalı" cümlesi vardır. */
enum class Problem {
    NoConnection,
    ConnectionLost,
    Timeout,
    Unreachable,
    SecureConnection,
    AccessDenied,
    RateLimited,
    NotFound,
    ServerError,
    AuthFailed,
    NoSpace,
    InstallConflict,
    InstallBlocked,
    InstallIncompatible,
    InstallInvalid,
    InvalidLink,
    VideoUnavailable,
    VideoExtraction,
    LanguageUnsupported,
    Unknown,
}

/**
 * Teknik hataları (istisna, sunucu/sistem iletisi) anlaşılır Türkçe bir mesaja çevirir: "<ne oldu>. <ne yapılmalı>".
 * Ham metin yalnızca ayrıntı olarak saklanır ([detail]); ekranda gösterilmez.
 */
object HumanErrors {

    /** Ekrana çıkacak mesaj; [detail] ham hata metnidir (istisna sınıfı ve iletisi, sistem kodu…). */
    fun message(area: ErrorArea, detail: String?): UiText {
        val problem = classify(area, detail)
        return UiText.res(R.string.err_format, UiText.res(area.lead), UiText.res(advice(area, problem)))
    }

    fun message(area: ErrorArea, error: Throwable): UiText = message(area, detail(error))

    /** İstisnayı nedenleriyle birlikte "Sınıf: ileti ← Sınıf: ileti" biçiminde tek satıra döker. */
    fun detail(error: Throwable): String {
        val parts = mutableListOf<String>()
        var current: Throwable? = error
        while (current != null && parts.size < 5) {
            parts += listOfNotNull(current.javaClass.simpleName, current.message?.takeIf { it.isNotBlank() }).joinToString(": ")
            current = current.cause?.takeIf { it !== current }
        }
        return parts.joinToString(" ← ")
    }

    /** Paket yükleyici sonucunu (durum kodu + sistem iletisi) sınıflandırılabilir ayrıntıya çevirir. */
    fun installDetail(status: Int, message: String?): String {
        val name = when (status) {
            STATUS_FAILURE_BLOCKED -> "STATUS_FAILURE_BLOCKED"
            STATUS_FAILURE_INVALID -> "STATUS_FAILURE_INVALID"
            STATUS_FAILURE_CONFLICT -> "STATUS_FAILURE_CONFLICT"
            STATUS_FAILURE_STORAGE -> "STATUS_FAILURE_STORAGE"
            STATUS_FAILURE_INCOMPATIBLE -> "STATUS_FAILURE_INCOMPATIBLE"
            else -> "STATUS_$status"
        }
        return listOfNotNull(name, message?.takeIf { it.isNotBlank() }).joinToString(": ")
    }

    fun classify(area: ErrorArea, detail: String?): Problem {
        val text = detail.orEmpty().lowercase()
        if (text.isBlank()) return areaDefault(area)
        areaRule(area, text)?.let { return it }
        return RULES.firstOrNull { (_, needles) -> needles.any { it in text } }?.first
            ?: httpProblem(text)
            ?: areaDefault(area)
    }

    @StringRes
    fun advice(area: ErrorArea, problem: Problem): Int = when {
        (area == ErrorArea.VpnConnect || area == ErrorArea.VpnTest) &&
            (problem == Problem.Unreachable || problem == Problem.Timeout) -> R.string.err_vpn_unreachable
        area == ErrorArea.Translate && problem == Problem.NoConnection -> R.string.err_translate_model
        else -> when (problem) {
            Problem.NoConnection -> R.string.err_no_connection
            Problem.ConnectionLost -> R.string.err_connection_lost
            Problem.Timeout -> R.string.err_timeout
            Problem.Unreachable -> R.string.err_unreachable
            Problem.SecureConnection -> R.string.err_secure
            Problem.AccessDenied -> R.string.err_access_denied
            Problem.RateLimited -> R.string.err_rate_limited
            Problem.NotFound -> R.string.err_not_found
            Problem.ServerError -> R.string.err_server
            Problem.AuthFailed -> R.string.err_auth
            Problem.NoSpace -> R.string.err_no_space
            Problem.InstallConflict -> R.string.err_install_conflict
            Problem.InstallBlocked -> R.string.err_install_blocked
            Problem.InstallIncompatible -> R.string.err_install_incompatible
            Problem.InstallInvalid -> R.string.err_install_invalid
            Problem.InvalidLink -> R.string.err_invalid_link
            Problem.VideoUnavailable -> R.string.err_video_unavailable
            Problem.VideoExtraction -> R.string.err_video_extraction
            Problem.LanguageUnsupported -> R.string.err_language_unsupported
            Problem.Unknown -> R.string.err_unknown
        }
    }

    /** Yalnızca belirli bir işte anlamlı olan kurallar (ör. çeviri motorunun hata adları). */
    private fun areaRule(area: ErrorArea, text: String): Problem? = when (area) {
        ErrorArea.Translate -> when {
            "notsupported" in text || "not supported" in text || "unsupported" in text -> Problem.LanguageUnsupported
            "model" in text || "couldnotload" in text || "couldnotretrieve" in text || "download" in text -> Problem.NoConnection
            else -> null
        }
        else -> null
    }

    /** Hiçbir kural tutmadığında işe göre en olası açıklama. */
    private fun areaDefault(area: ErrorArea): Problem = when (area) {
        ErrorArea.VpnLink -> Problem.InvalidLink
        else -> Problem.Unknown
    }

    private fun httpProblem(text: String): Problem? {
        val code = HTTP_CODE.find(text)?.groupValues?.get(2)?.toIntOrNull() ?: return null
        return when (code) {
            401 -> Problem.AuthFailed
            403 -> Problem.AccessDenied
            404, 410 -> Problem.NotFound
            429 -> Problem.RateLimited
            in 500..599 -> Problem.ServerError
            else -> null
        }
    }

    /** Sıra önemlidir: daha özel olan önce gelir (ör. video kısıtlaması, genel ağ hatasından önce). */
    private val RULES: List<Pair<Problem, List<String>>> = listOf(
        Problem.AuthFailed to listOf("auth_failed", "authentication failed", "unauthorized", "kimlik doğrulama", "wrong password"),
        Problem.VideoUnavailable to listOf(
            "contentnotavailable", "agerestricted", "privatecontent", "geographicrestriction", "paidcontent",
            "premiumcontent", "accountterminated", "contentnotsupported", "video unavailable", "this video is private",
        ),
        Problem.RateLimited to listOf("recaptcha", "too many requests"),
        Problem.NoSpace to listOf("enospc", "no space left", "insufficient_storage", "insufficient storage", "status_failure_storage"),
        Problem.InstallConflict to listOf(
            "status_failure_conflict", "install_failed_update_incompatible", "install_failed_conflicting",
            "signatures do not match", "inconsistent certificates", "install_failed_dup",
        ),
        Problem.InstallBlocked to listOf(
            "status_failure_blocked", "install_failed_user_restricted", "install_failed_verification", "requestinstallpackages",
        ),
        Problem.InstallIncompatible to listOf(
            "status_failure_incompatible", "install_failed_older_sdk", "no_matching_abis", "install_failed_cpu_abi",
        ),
        Problem.InstallInvalid to listOf("status_failure_invalid", "install_parse_failed", "install_failed_invalid_apk"),
        Problem.SecureConnection to listOf("sslhandshake", "sslexception", "sslpeerunverified", "certificate", "certpath", "tls handshake"),
        Problem.NoConnection to listOf(
            "unknownhost", "unable to resolve host", "no address associated", "network is unreachable", "enetunreach",
            "no network", "nointernet", "cannot resolve host", "adres çözümlenemedi",
        ),
        Problem.Timeout to listOf("timeout", "timed out", "zaman aşımı", "tls key negotiation"),
        Problem.Unreachable to listOf(
            "connectexception", "connection refused", "econnrefused", "failed to connect", "no route to host",
            "ehostunreach", "bağlanılamadı",
        ),
        Problem.VideoExtraction to listOf("parsingexception", "extractionexception", "could not get", "deobfusc"),
        Problem.ConnectionLost to listOf(
            "connection reset", "econnreset", "broken pipe", "unexpected end of stream", "connection abort",
            "socketexception", "eofexception", "interruptedioexception", "ioexception",
        ),
    )

    private val HTTP_CODE = Regex("(http|code|status|response|yanıt|kod)\\D{0,12}([1-5]\\d\\d)\\b")

    // android.content.pm.PackageInstaller sabitleri (birim testlerinde Android sınıfına ihtiyaç kalmasın diye burada).
    const val STATUS_FAILURE_BLOCKED = 2
    const val STATUS_FAILURE_INVALID = 4
    const val STATUS_FAILURE_CONFLICT = 5
    const val STATUS_FAILURE_STORAGE = 6
    const val STATUS_FAILURE_INCOMPATIBLE = 7
}
