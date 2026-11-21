package com.gtarayici.browser.diagnostics

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsTest {
    private lateinit var dir: File
    private var enabled = true

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("tanilama").toFile()
        Diagnostics.clear()
        Diagnostics.init(File(dir, "log.txt")) { enabled }
    }

    @After
    fun tearDown() {
        Diagnostics.clear()
        dir.deleteRecursively()
    }

    @Test
    fun `satırlar maskelenerek ve kategoriyle yazılır`() {
        Diagnostics.log("yüklenemedi https://ornek.com/hesap?id=5", "sayfa")
        val line = Diagnostics.dump()
        assertTrue(line.endsWith("[sayfa] yüklenemedi https://ornek.com/…"))
        assertFalse(line.contains("id=5"))
    }

    @Test
    fun `aynı ileti art arda gelince birleştirilir`() {
        repeat(3) { Diagnostics.log("VPN bağlanamadı", "vpn") }
        Diagnostics.log("başka", "vpn")
        val lines = Diagnostics.dump().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith("VPN bağlanamadı (×3)"))
    }

    @Test
    fun `halka tampon son satırları tutar`() {
        repeat(Diagnostics.MAX_LINES + 20) { Diagnostics.log("olay $it") }
        assertEquals(Diagnostics.MAX_LINES, Diagnostics.lineCount())
        assertTrue(Diagnostics.dump().lines().first().endsWith("olay 20"))
    }

    @Test
    fun `kapalıyken kayıt tutulmaz`() {
        enabled = false
        Diagnostics.log("yazılmamalı")
        assertEquals(0, Diagnostics.lineCount())
    }

    @Test
    fun `dosyaya yazılan kayıt yeniden yüklenir`() {
        Diagnostics.log("önceki oturum", "genel")
        Diagnostics.log("tekrar"); Diagnostics.log("tekrar")
        Diagnostics.flush()
        val before = Diagnostics.dump()
        Diagnostics.clear()
        Diagnostics.init(File(dir, "log.txt")) { true }
        assertEquals(before, Diagnostics.dump())
    }

    @Test
    fun `temizlenen kayıt geri alınabilir`() {
        Diagnostics.log("bir")
        Diagnostics.log("iki")
        val removed = Diagnostics.clear()
        assertEquals(0, Diagnostics.lineCount())
        Diagnostics.restore(removed)
        assertEquals(2, Diagnostics.lineCount())
    }
}
