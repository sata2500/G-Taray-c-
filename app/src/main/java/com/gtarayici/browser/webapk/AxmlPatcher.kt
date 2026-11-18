package com.gtarayici.browser.webapk

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Derlenmiş (ikili) AndroidManifest.xml dosyasındaki metinleri yerinde değiştirir.
 *
 * Şablon manifestteki yer tutucu metinler, gerçek değerlerden uzun tutulur. Yeni değer aynı bayt
 * alanına yazılır, uzunluk alanı güncellenir ve artan kısım sıfırlanır. Böylece dosyanın geri kalanındaki
 * hiçbir konum (offset) değişmez ve manifest yapısı bozulmaz.
 */
object AxmlPatcher {
    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val UTF8_FLAG = 0x100

    class PatchException(message: String) : Exception(message)

    /** [replace] her metin için çağrılır; null dönerse metin olduğu gibi kalır. */
    fun patch(axml: ByteArray, replace: (String) -> String?): ByteArray {
        val out = axml.copyOf()
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.getShort(0).toInt() and 0xFFFF != RES_XML_TYPE) throw PatchException("İkili XML değil")
        val pool = buf.getShort(2).toInt() and 0xFFFF
        if (buf.getShort(pool).toInt() and 0xFFFF != RES_STRING_POOL_TYPE) throw PatchException("Metin havuzu bulunamadı")

        val headerSize = buf.getShort(pool + 2).toInt() and 0xFFFF
        val chunkSize = buf.getInt(pool + 4)
        val stringCount = buf.getInt(pool + 8)
        val styleCount = buf.getInt(pool + 12)
        val utf8 = buf.getInt(pool + 16) and UTF8_FLAG != 0
        val stringsStart = pool + buf.getInt(pool + 20)
        val stylesStart = buf.getInt(pool + 24).let { if (styleCount > 0 && it > 0) pool + it else pool + chunkSize }

        val offsets = IntArray(stringCount) { stringsStart + buf.getInt(pool + headerSize + it * 4) }
        val sorted = offsets.distinct().sorted()
        val regionEnd = sorted.associateWith { start -> sorted.firstOrNull { it > start } ?: stylesStart }

        offsets.distinct().forEach { start ->
            val end = regionEnd.getValue(start)
            val original = if (utf8) readUtf8(buf, start) else readUtf16(buf, start)
            val replacement = replace(original.value) ?: return@forEach
            if (utf8) writeUtf8(buf, start, end, original, replacement) else writeUtf16(buf, start, end, original, replacement)
        }
        return out
    }

    /** Okunan metin ve uzunluk alanlarının kaç bayt/birim tuttuğu. */
    private data class Decoded(val value: String, val wideHeader: Boolean, val wideByteHeader: Boolean = false)

    private fun readUtf16(buf: ByteBuffer, start: Int): Decoded {
        var pos = start
        val first = buf.getShort(pos).toInt() and 0xFFFF
        pos += 2
        val wide = first and 0x8000 != 0
        val length = if (wide) {
            val second = buf.getShort(pos).toInt() and 0xFFFF
            pos += 2
            ((first and 0x7FFF) shl 16) or second
        } else {
            first
        }
        val chars = CharArray(length) { buf.getChar(pos + it * 2) }
        return Decoded(String(chars), wide)
    }

    private fun writeUtf16(buf: ByteBuffer, start: Int, end: Int, original: Decoded, value: String) {
        val units = value.length
        val header = if (original.wideHeader) 4 else 2
        if (!original.wideHeader && units >= 0x8000) throw PatchException("Metin çok uzun")
        val needed = header + units * 2 + 2
        if (needed > end - start) throw PatchException("Yeni metin yer tutucudan uzun: ${value.take(40)}")
        var pos = start
        if (original.wideHeader) {
            buf.putShort(pos, (((units shr 16) and 0x7FFF) or 0x8000).toShort())
            buf.putShort(pos + 2, (units and 0xFFFF).toShort())
        } else {
            buf.putShort(pos, units.toShort())
        }
        pos += header
        value.forEach { buf.putChar(pos, it); pos += 2 }
        while (pos < end) buf.put(pos++, 0)
    }

    private fun readLength8(buf: ByteBuffer, pos: Int): Pair<Int, Boolean> {
        val first = buf.get(pos).toInt() and 0xFF
        return if (first and 0x80 != 0) {
            (((first and 0x7F) shl 8) or (buf.get(pos + 1).toInt() and 0xFF)) to true
        } else {
            first to false
        }
    }

    private fun readUtf8(buf: ByteBuffer, start: Int): Decoded {
        val (_, wideChars) = readLength8(buf, start)
        val bytePos = start + if (wideChars) 2 else 1
        val (byteLength, wideBytes) = readLength8(buf, bytePos)
        val dataPos = bytePos + if (wideBytes) 2 else 1
        val bytes = ByteArray(byteLength) { buf.get(dataPos + it) }
        return Decoded(String(bytes, Charsets.UTF_8), wideChars, wideBytes)
    }

    private fun putLength8(buf: ByteBuffer, pos: Int, value: Int, wide: Boolean): Int {
        return if (wide) {
            if (value > 0x7FFF) throw PatchException("Metin çok uzun")
            buf.put(pos, (((value shr 8) and 0x7F) or 0x80).toByte())
            buf.put(pos + 1, (value and 0xFF).toByte())
            2
        } else {
            if (value > 0x7F) throw PatchException("Metin yer tutucu alanına sığmıyor")
            buf.put(pos, value.toByte())
            1
        }
    }

    private fun writeUtf8(buf: ByteBuffer, start: Int, end: Int, original: Decoded, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val needed = (if (original.wideHeader) 2 else 1) + (if (original.wideByteHeader) 2 else 1) + bytes.size + 1
        if (needed > end - start) throw PatchException("Yeni metin yer tutucudan uzun: ${value.take(40)}")
        var pos = start
        pos += putLength8(buf, pos, value.length, original.wideHeader)
        pos += putLength8(buf, pos, bytes.size, original.wideByteHeader)
        bytes.forEach { buf.put(pos++, it) }
        while (pos < end) buf.put(pos++, 0)
    }
}
