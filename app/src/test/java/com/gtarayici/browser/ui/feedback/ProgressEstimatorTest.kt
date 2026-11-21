package com.gtarayici.browser.ui.feedback

import com.gtarayici.browser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressEstimatorTest {
    private var now = 0L
    private val estimator = ProgressEstimator { now }

    @Test
    fun `ısınma süresinde tahmin verilmez`() {
        assertNull(estimator.update(0, 1000))
        now = 1_000; assertNull(estimator.update(100, 1000))
        now = 2_000; assertNull(estimator.update(200, 1000))
    }

    @Test
    fun `sabit hızda kalan süre doğru hesaplanır`() {
        // Saniyede 100 birim; 1000 birimin 400'ü bitti → 6 sn kaldı.
        estimator.update(0, 1000)
        for (second in 1..4) { now = second * 1000L; estimator.update(second * 100L, 1000) }
        val remaining = estimator.update(400, 1000)!!
        assertEquals(6_000.0, remaining.toDouble(), 1.0)
    }

    @Test
    fun `ani hız sıçraması yumuşatılır`() {
        estimator.update(0, 10_000)
        for (second in 1..4) { now = second * 1000L; estimator.update(second * 100L, 10_000) }
        // Tek seferlik 10 kat hızlı örnek: tahmin 10 kat kısalmaz.
        now = 5_000
        val remaining = estimator.update(1_400, 10_000)!!
        val naive = (10_000 - 1_400) / 1.0 // 1 birim/ms ile
        assertTrue(remaining > naive * 2)
    }

    @Test
    fun `bitince veya toplam bilinmiyorsa tahmin yok`() {
        estimator.update(0, 1000)
        now = 5_000
        assertNull(estimator.update(1000, 1000))
        assertNull(ProgressEstimator { now }.apply { update(0, -1); now += 5_000 }.update(10, -1))
    }

    @Test
    fun `kalan süre metni`() {
        assertEquals(UiText.res(R.string.progress_seconds_left, 5), ProgressEstimator.remainingText(1_200))
        assertEquals(UiText.res(R.string.progress_seconds_left, 45), ProgressEstimator.remainingText(41_000))
        assertEquals(UiText.res(R.string.progress_seconds_left, 55), ProgressEstimator.remainingText(59_000))
        assertEquals(UiText.res(R.string.progress_minutes_left, 2), ProgressEstimator.remainingText(61_000))
        assertEquals(UiText.res(R.string.progress_hours_left, 1, 30), ProgressEstimator.remainingText(5_400_000))
    }
}
