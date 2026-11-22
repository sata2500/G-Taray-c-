package com.gtarayici.browser.ui.feedback

import com.gtarayici.browser.R
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanErrorsTest {

    private fun classify(area: ErrorArea, error: Throwable) = HumanErrors.classify(area, HumanErrors.detail(error))

    @Test
    fun `ağ istisnaları türüne göre ayrılır`() {
        val area = ErrorArea.VideoInfo
        assertEquals(Problem.NoConnection, classify(area, UnknownHostException("Unable to resolve host \"www.youtube.com\"")))
        assertEquals(Problem.Timeout, classify(area, SocketTimeoutException("timeout")))
        assertEquals(Problem.Unreachable, classify(area, ConnectException("failed to connect to /1.2.3.4 (port 443)")))
        assertEquals(Problem.SecureConnection, classify(area, SSLHandshakeException("Chain validation failed")))
        assertEquals(Problem.ConnectionLost, classify(area, IOException("unexpected end of stream")))
    }

    @Test
    fun `neden zincirindeki asıl hata bulunur`() {
        val wrapped = RuntimeException("İstek başarısız", UnknownHostException("No address associated with hostname"))
        assertEquals(Problem.NoConnection, classify(ErrorArea.Update, wrapped))
        assertTrue(HumanErrors.detail(wrapped).contains("UnknownHostException"))
    }

    @Test
    fun `NewPipe kısıtlamaları ve ayrıştırma hataları`() {
        assertEquals(Problem.VideoUnavailable, classify(ErrorArea.VideoInfo, AgeRestrictedContentException("age restricted")))
        assertEquals(Problem.VideoExtraction, classify(ErrorArea.VideoInfo, ParsingException("Could not get video id")))
        assertEquals(
            Problem.VideoBlockedForTools,
            classify(ErrorArea.VideoInfo, org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException("Got error UNPLAYABLE: \"This video is not available\"")),
        )
        assertEquals(
            Problem.VideoUnavailable,
            classify(ErrorArea.VideoInfo, org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException("Got error LOGIN_REQUIRED: \"Sign in to confirm your age\"")),
        )
        assertEquals(Problem.RateLimited, classify(ErrorArea.VideoInfo, ReCaptchaException("reCaptcha Challenge requested")))
    }

    @Test
    fun `HTTP durum kodları`() {
        assertEquals(Problem.AccessDenied, HumanErrors.classify(ErrorArea.Update, "HttpException: HTTP 403"))
        assertEquals(Problem.RateLimited, HumanErrors.classify(ErrorArea.Update, "Response code: 429"))
        assertEquals(Problem.ServerError, HumanErrors.classify(ErrorArea.Update, "status 503 Service Unavailable"))
        assertEquals(Problem.NotFound, HumanErrors.classify(ErrorArea.Update, "HTTP 404"))
        // Sayı içeren ama HTTP olmayan metin yanlış sınıflandırılmaz.
        assertEquals(Problem.Unknown, HumanErrors.classify(ErrorArea.Update, "port 443 kapalı değil"))
    }

    @Test
    fun `VPN iletileri`() {
        assertEquals(Problem.AuthFailed, HumanErrors.classify(ErrorArea.VpnConnect, "AUTH: Received control message: AUTH_FAILED"))
        assertEquals(Problem.Timeout, HumanErrors.classify(ErrorArea.VpnConnect, "IllegalStateException: OpenVPN sunucusuna bağlanılamadı (zaman aşımı)"))
        assertEquals(Problem.NoConnection, HumanErrors.classify(ErrorArea.VpnConnect, "RESOLVE: Cannot resolve host address: vpn.example.com"))
        assertEquals(R.string.err_vpn_unreachable, HumanErrors.advice(ErrorArea.VpnConnect, Problem.Timeout))
        assertEquals(R.string.err_vpn_unreachable, HumanErrors.advice(ErrorArea.VpnTest, Problem.Unreachable))
        // VPN dışındaki işlerde genel cümle kullanılır.
        assertEquals(R.string.err_timeout, HumanErrors.advice(ErrorArea.VideoInfo, Problem.Timeout))
    }

    @Test
    fun `okunamayan VPN bağlantısı biçim hatası sayılır`() {
        assertEquals(Problem.InvalidLink, classify(ErrorArea.VpnLink, IllegalArgumentException("Desteklenmeyen şema")))
        assertEquals(Problem.InvalidLink, HumanErrors.classify(ErrorArea.VpnLink, null))
        assertEquals(Problem.Unknown, HumanErrors.classify(ErrorArea.WebApk, null))
    }

    @Test
    fun `paket yükleyici durumları`() {
        fun install(status: Int, message: String? = null) =
            HumanErrors.classify(ErrorArea.Install, HumanErrors.installDetail(status, message))
        assertEquals(Problem.InstallConflict, install(HumanErrors.STATUS_FAILURE_CONFLICT))
        assertEquals(Problem.NoSpace, install(HumanErrors.STATUS_FAILURE_STORAGE))
        assertEquals(Problem.InstallBlocked, install(HumanErrors.STATUS_FAILURE_BLOCKED))
        assertEquals(Problem.InstallIncompatible, install(HumanErrors.STATUS_FAILURE_INCOMPATIBLE))
        assertEquals(Problem.InstallInvalid, install(HumanErrors.STATUS_FAILURE_INVALID))
        assertEquals(Problem.InstallConflict, install(1, "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match"))
        assertEquals(Problem.Unknown, install(1))
    }

    @Test
    fun `çeviri motoru hata adları`() {
        assertEquals(Problem.LanguageUnsupported, HumanErrors.classify(ErrorArea.Translate, "LanguageNotSupportedError"))
        assertEquals(Problem.NoConnection, HumanErrors.classify(ErrorArea.Translate, "ModelCouldNotRetrieveError"))
        assertEquals(R.string.err_translate_model, HumanErrors.advice(ErrorArea.Translate, Problem.NoConnection))
        assertEquals(Problem.Unknown, HumanErrors.classify(ErrorArea.Translate, "UnknownError"))
    }

    @Test
    fun `mesaj başlık ve ne yapılmalı cümlesinden oluşur, ham metin içermez`() {
        val message = HumanErrors.message(ErrorArea.VpnConnect, ConnectException("ECONNREFUSED 10.0.0.1")) as UiText.Res
        assertEquals(R.string.err_format, message.id)
        assertEquals(UiText.res(R.string.err_lead_vpn_connect), message.args[0])
        assertEquals(UiText.res(R.string.err_vpn_unreachable), message.args[1])
        assertFalse(message.args.any { it is String })
    }

    @Test
    fun `her sorunun bir cümlesi vardır`() {
        ErrorArea.entries.forEach { area ->
            Problem.entries.forEach { problem -> assertTrue(HumanErrors.advice(area, problem) != 0) }
        }
    }
}

// NewPipe istisnalarının taklitleri: sınıflandırma yalnızca sınıf adına bakar. Dosya düzeyinde tanımlanır;
// Türkçe adlı bir test fonksiyonunun içinde tanımlanırsa .class dosya adına Türkçe harfler girer ve
// UTF-8 olmayan ortamlarda derleme bozulur.
private class AgeRestrictedContentException(message: String) : Exception(message)
private class ParsingException(message: String) : Exception(message)
private class ReCaptchaException(message: String) : Exception(message)
