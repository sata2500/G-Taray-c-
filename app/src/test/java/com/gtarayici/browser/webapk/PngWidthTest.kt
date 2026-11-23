package com.gtarayici.browser.webapk

import org.junit.Assert.assertEquals
import org.junit.Test

class PngWidthTest {
    private fun header(width: Int, height: Int): ByteArray {
        val bytes = ByteArray(33)
        byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10).copyInto(bytes)
        fun putInt(at: Int, v: Int) {
            bytes[at] = (v ushr 24).toByte(); bytes[at + 1] = (v ushr 16).toByte(); bytes[at + 2] = (v ushr 8).toByte(); bytes[at + 3] = v.toByte()
        }
        putInt(8, 13)
        "IHDR".toByteArray().copyInto(bytes, 12)
        putInt(16, width)
        putInt(20, height)
        return bytes
    }

    @Test
    fun readsWidthFromHeader() {
        assertEquals(1, WebApkPackager.pngWidth(header(1, 1)))
        assertEquals(432, WebApkPackager.pngWidth(header(432, 432)))
        assertEquals(192, WebApkPackager.pngWidth(header(192, 192)))
        assertEquals(-1, WebApkPackager.pngWidth("<xml/>".toByteArray()))
    }
}
