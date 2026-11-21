package com.gtarayici.browser.ui.feedback

import com.gtarayici.browser.R
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Uzun işler için kalan süre tahmini. Hız, ani dalgalanmalar yumuşatılarak (üstel ortalama) hesaplanır; yeterli
 * örnek birikmeden (ilk [WARMUP_MS]) tahmin verilmez, böylece "1 sa kaldı → 5 sn kaldı" sıçramaları görünmez.
 */
class ProgressEstimator(private val clock: () -> Long = System::currentTimeMillis) {
    private var startedAt = -1L
    private var lastTime = -1L
    private var lastDone = 0L
    private var rate = 0.0 // birim / ms

    /** [done] / [total] ilerlemesini kaydeder ve kalan süreyi (ms) döner; henüz bilinmiyorsa null. */
    fun update(done: Long, total: Long): Long? {
        val now = clock()
        if (startedAt < 0) {
            startedAt = now
            lastTime = now
            lastDone = done
            return null
        }
        val elapsed = now - lastTime
        if (elapsed > 0 && done >= lastDone) {
            val instant = (done - lastDone).toDouble() / elapsed
            rate = if (rate == 0.0) instant else rate + SMOOTHING * (instant - rate)
            lastTime = now
            lastDone = done
        }
        if (total <= 0 || done >= total || now - startedAt < WARMUP_MS || rate <= 0.0) return null
        return ((total - done) / rate).roundToLong()
    }

    companion object {
        const val WARMUP_MS = 3_000L
        private const val SMOOTHING = 0.3

        /** "yaklaşık 2 dk kaldı" gibi okunur metin; 1 dakikanın altı 5 saniyelik adımlarla yuvarlanır. */
        fun remainingText(ms: Long): UiText {
            val seconds = ceil(ms / 1000.0).toLong().coerceAtLeast(1)
            return when {
                seconds < 60 -> UiText.res(R.string.progress_seconds_left, ((seconds + 4) / 5 * 5).coerceAtMost(55).toInt())
                seconds < 3600 -> UiText.res(R.string.progress_minutes_left, ceil(seconds / 60.0).toInt())
                else -> UiText.res(R.string.progress_hours_left, (seconds / 3600).toInt(), ((seconds % 3600) / 60).toInt())
            }
        }
    }
}
