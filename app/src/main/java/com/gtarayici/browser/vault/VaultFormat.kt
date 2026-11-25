package com.gtarayici.browser.vault

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Kasa dosyası biçimi ("GTV1"). Dosya, her biri ayrı doğrulanan AES-256-GCM parçalarıdır; böylece video ileri
 * sarılırken yalnızca gereken parça çözülür ve dosyanın şifresiz bir kopyası hiçbir yere yazılmaz.
 *
 *  - Her dosyanın kendi rastgele 256 bit anahtarı vardır; bu anahtar kasanın ana anahtarıyla şifrelenip başlıkta durur.
 *  - Parça i'nin nonce'u: 8 bayt rastgele taban + i (4 bayt). Parçalar yer değiştiremez.
 *  - Her parçanın doğrulanan ek verisi: başlık + "son parça mı" işareti. Dosyanın sonu kesilirse ya da bir bayt
 *    değişirse okuma hata verir.
 *
 * Başlık: "GTV1" (4) | parça boyutu (4) | nonce tabanı (8) | anahtar IV'si (12) | şifreli dosya anahtarı (48).
 */
object VaultFormat {
    private const val MAGIC = 0x47545631
    const val CHUNK = 64 * 1024
    const val TAG = 16
    const val HEADER = 4 + 4 + 8 + 12 + 48
    private val random = SecureRandom()

    /** [input]'u şifreleyip [output]'a yazar; şifresiz uzunluğu döndürür. */
    fun encrypt(input: InputStream, output: OutputStream, master: SecretKey, chunkSize: Int = CHUNK): Long {
        val fileKey = ByteArray(32).also(random::nextBytes)
        val base = ByteArray(8).also(random::nextBytes)
        val wrapIv = ByteArray(12).also(random::nextBytes)
        val prefix = ByteBuffer.allocate(16).putInt(MAGIC).putInt(chunkSize).put(base).array()
        val wrapped = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, master, GCMParameterSpec(128, wrapIv))
            updateAAD(prefix)
            doFinal(fileKey)
        }
        val header = prefix + wrapIv + wrapped
        check(header.size == HEADER)
        output.write(header)
        val key = SecretKeySpec(fileKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Son parçayı bilmek için bir parça önden okunur.
        var current = readChunk(input, chunkSize)
        var index = 0
        var total = 0L
        while (true) {
            val next = if (current.size == chunkSize) readChunk(input, chunkSize) else ByteArray(0)
            val last = next.isEmpty()
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce(base, index)))
            cipher.updateAAD(header)
            cipher.updateAAD(byteArrayOf(if (last) 1 else 0))
            output.write(cipher.doFinal(current))
            total += current.size
            if (last) break
            current = next
            index++
        }
        output.flush()
        fileKey.fill(0)
        return total
    }

    private fun readChunk(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var filled = 0
        while (filled < size) {
            val read = input.read(buffer, filled, size - filled)
            if (read < 0) break
            filled += read
        }
        return if (filled == size) buffer else buffer.copyOf(filled)
    }

    internal fun nonce(base: ByteArray, index: Int): ByteArray = ByteBuffer.allocate(12).put(base).putInt(index).array()

    /** Şifreli dosya boyutundan şifresiz boyut. */
    fun plainLength(fileLength: Long, chunkSize: Int = CHUNK): Long {
        val body = fileLength - HEADER
        if (body < TAG) throw IOException("Kasa dosyası bozuk")
        val chunks = (body + chunkSize + TAG - 1) / (chunkSize + TAG)
        return body - chunks * TAG
    }

    /** Dosyanın herhangi bir konumundan okuyan çözücü (video ileri sarma, sayfa sayfa PDF, küçük resim). */
    class Reader(file: File, master: SecretKey) : Closeable {
        private val raf = RandomAccessFile(file, "r")
        private val header = ByteArray(HEADER).also { raf.readFully(it) }
        private val chunkSize: Int
        private val base: ByteArray
        private val key: SecretKeySpec
        private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        private val chunkCount: Long
        val size: Long

        private var cachedIndex = -1L
        private var cached = ByteArray(0)

        init {
            val buffer = ByteBuffer.wrap(header)
            if (buffer.int != MAGIC) throw IOException("Kasa dosyası değil")
            chunkSize = buffer.int
            if (chunkSize !in 1024..(16 * 1024 * 1024)) throw IOException("Kasa dosyası bozuk")
            base = ByteArray(8).also { buffer.get(it) }
            val wrapIv = ByteArray(12).also { buffer.get(it) }
            val wrapped = ByteArray(48).also { buffer.get(it) }
            val fileKey = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(128, wrapIv))
                updateAAD(header, 0, 16)
                doFinal(wrapped)
            }
            key = SecretKeySpec(fileKey, "AES")
            fileKey.fill(0)
            size = plainLength(raf.length(), chunkSize)
            val body = raf.length() - HEADER
            chunkCount = (body + chunkSize + TAG - 1) / (chunkSize + TAG)
        }

        /** [position]'dan en fazla [length] bayt okur; dosyanın sonundaysa -1. */
        @Synchronized
        fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= size) return -1
            var copied = 0
            var at = position
            while (copied < length && at < size) {
                val index = at / chunkSize
                val plain = chunk(index)
                val inChunk = (at - index * chunkSize).toInt()
                val count = minOf(length - copied, plain.size - inChunk)
                if (count <= 0) break
                System.arraycopy(plain, inChunk, buffer, offset + copied, count)
                copied += count
                at += count
            }
            return copied
        }

        private fun chunk(index: Long): ByteArray {
            if (index == cachedIndex) return cached
            val sealed = chunkSize + TAG
            val start = HEADER + index * sealed
            val length = minOf(sealed.toLong(), raf.length() - start).toInt()
            val data = ByteArray(length)
            raf.seek(start)
            raf.readFully(data)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce(base, index.toInt())))
            cipher.updateAAD(header)
            cipher.updateAAD(byteArrayOf(if (index == chunkCount - 1) 1 else 0))
            cached = cipher.doFinal(data)
            cachedIndex = index
            return cached
        }

        /** Baştan sona okuyan akış (görsel çözme, dışa aktarma). */
        fun stream(): InputStream = object : InputStream() {
            private var position = 0L
            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (len == 0) return 0
                val n = this@Reader.read(position, b, off, len)
                if (n > 0) position += n
                return n
            }

            override fun skip(n: Long): Long {
                val skipped = minOf(n, size - position).coerceAtLeast(0)
                position += skipped
                return skipped
            }

            override fun available(): Int = (size - position).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        }

        override fun close() {
            raf.close()
        }
    }
}
