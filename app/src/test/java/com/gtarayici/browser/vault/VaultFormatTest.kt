package com.gtarayici.browser.vault

import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VaultFormatTest {
    private val master = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    private fun sealed(data: ByteArray, chunk: Int = 4096): File {
        val file = File.createTempFile("vault", ".gtv")
        file.deleteOnExit()
        file.outputStream().use { VaultFormat.encrypt(ByteArrayInputStream(data), it, master, chunk) }
        return file
    }

    @Test fun roundTripsAndSeeks() {
        val data = ByteArray(4096 * 5 + 123) { (it * 31 + 7).toByte() }
        val file = sealed(data)
        VaultFormat.Reader(file, master).use { reader ->
            assertEquals(data.size.toLong(), reader.size)
            assertArrayEquals(data, reader.stream().readBytes())
            // Rastgele konumdan okuma (parça sınırını aşan).
            val buffer = ByteArray(5000)
            val n = reader.read(4000, buffer, 0, buffer.size)
            assertEquals(5000, n)
            assertArrayEquals(data.copyOfRange(4000, 9000), buffer)
            assertEquals(-1, reader.read(data.size.toLong(), buffer, 0, 10))
        }
    }

    @Test fun exactChunkMultipleAndEmpty() {
        for (size in listOf(0, 4096, 8192)) {
            val data = ByteArray(size) { it.toByte() }
            VaultFormat.Reader(sealed(data), master).use { assertArrayEquals(data, it.stream().readBytes()) }
        }
    }

    @Test fun detectsTamperingAndTruncation() {
        val data = ByteArray(4096 * 3) { it.toByte() }
        val tampered = sealed(data)
        RandomAccessFile(tampered, "rw").use { it.seek(VaultFormat.HEADER + 10L); it.write(0x55) }
        expectFailure { VaultFormat.Reader(tampered, master).use { it.stream().readBytes() } }

        val truncated = sealed(data)
        RandomAccessFile(truncated, "rw").use { it.setLength(it.length() - (4096 + VaultFormat.TAG)) }
        expectFailure { VaultFormat.Reader(truncated, master).use { it.stream().readBytes() } }
    }

    @Test fun wrongKeyFails() {
        val file = sealed(ByteArray(100))
        expectFailure { VaultFormat.Reader(file, SecretKeySpec(ByteArray(32), "AES")) }
    }

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("hata bekleniyordu")
        } catch (e: Exception) {
            assertTrue(true)
        }
    }
}
