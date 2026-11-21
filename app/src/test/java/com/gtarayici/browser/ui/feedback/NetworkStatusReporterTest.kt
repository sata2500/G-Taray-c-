package com.gtarayici.browser.ui.feedback

import com.gtarayici.browser.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkStatusReporterTest {
    private val messenger = Messenger()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun shown() = messenger.current.value.map { it.text }

    @Test
    fun `kopma bekleme süresinden sonra duyurulur, dönüşte başarı mesajı gelir`() {
        val reporter = NetworkStatusReporter(messenger, scope, graceMs = 0)
        reporter.onOffline()
        assertEquals(listOf(UiText.res(R.string.network_lost)), shown())
        assertEquals(MessageKind.Warning, messenger.current.value.single().kind)

        reporter.onOnline()
        // "Bağlantı yok" mesajı kaldırılır, yerine yalnızca "yeniden kuruldu" kalır.
        assertEquals(listOf(UiText.res(R.string.network_back)), shown())
    }

    @Test
    fun `kısa kopma hiç duyurulmaz`() {
        val reporter = NetworkStatusReporter(messenger, scope, graceMs = 60_000)
        reporter.onOffline()
        reporter.onOnline()
        assertTrue(shown().isEmpty())
    }

    @Test
    fun `bağlantı varken gelen doğrulama mesaj göstermez`() {
        val reporter = NetworkStatusReporter(messenger, scope, graceMs = 0)
        reporter.onOnline()
        reporter.onOnline()
        assertTrue(shown().isEmpty())
    }

    @Test
    fun `art arda kopmalar tek mesaj gösterir`() {
        val reporter = NetworkStatusReporter(messenger, scope, graceMs = 0)
        reporter.onOffline()
        reporter.onOffline()
        assertEquals(1, shown().size)
    }
}
