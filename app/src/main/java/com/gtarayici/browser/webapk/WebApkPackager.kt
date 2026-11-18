package com.gtarayici.browser.webapk

import com.android.apksig.ApkSigner
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Üretilecek web uygulamasının bilgileri. */
data class WebApkSpec(
    val packageName: String,
    val label: String,
    val startUrl: String,
    val scope: String,
    val hostPackage: String,
    val iconPng: ByteArray,
)

/**
 * Şablon WebAPK'yı kişiselleştirir ve imzalar (cihaz üzerinde, internet gerektirmeden).
 *
 * Adımlar: manifestteki yer tutucuları doldur → simgeyi değiştir → eski imzayı çıkar →
 * APK İmza Şeması v2 ile yeniden imzala (Google'ın apksig kütüphanesi).
 */
object WebApkPackager {
    // webapk-shell/placeholders.properties ile birebir aynı olmalıdır (birim testi denetler).
    val PLACEHOLDER_PACKAGE = "com.gtarayici.webapk.p" + "0".repeat(58)
    val PLACEHOLDER_LABEL = "GTWEBAPKLABEL" + "_".repeat(187)
    val PLACEHOLDER_URL = "GTWEBAPKSTARTURL" + "_".repeat(1984)
    val PLACEHOLDER_HOST = "GTWEBAPKHOST" + "_".repeat(188)
    val PLACEHOLDER_SCOPE = "GTWEBAPKSCOPE" + "_".repeat(1987)

    private const val MAX_LABEL = 60

    /** Başlangıç adresinden kararlı, benzersiz bir paket adı üretir. */
    fun packageNameFor(startUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(startUrl.toByteArray())
        val hex = digest.take(10).joinToString("") { "%02x".format(it) }
        return "com.gtarayici.webapk.w$hex"
    }

    fun build(template: ByteArray, spec: WebApkSpec, key: PrivateKey, certificate: X509Certificate, workDir: File): File {
        require(spec.packageName.length <= PLACEHOLDER_PACKAGE.length)
        require(spec.startUrl.length <= PLACEHOLDER_URL.length) { "Adres çok uzun" }
        val label = spec.label.trim().take(MAX_LABEL).ifBlank { "Web uygulaması" }
        val scope = spec.scope.take(PLACEHOLDER_SCOPE.length)

        val unsigned = File(workDir, "unsigned.apk")
        val signed = File(workDir, "${spec.packageName}.apk")
        workDir.mkdirs()

        ZipInputStream(ByteArrayInputStream(template)).use { input ->
            ZipOutputStream(unsigned.outputStream().buffered()).use { output ->
                var iconReplaced = false
                while (true) {
                    val entry = input.nextEntry ?: break
                    val name = entry.name
                    if (name.startsWith("META-INF/")) continue
                    var data = input.readBytes()
                    when {
                        name == "AndroidManifest.xml" -> data = AxmlPatcher.patch(data) { text ->
                            when {
                                text == PLACEHOLDER_PACKAGE -> spec.packageName
                                text.startsWith("$PLACEHOLDER_PACKAGE.") -> spec.packageName + text.removePrefix(PLACEHOLDER_PACKAGE)
                                text == PLACEHOLDER_LABEL -> label
                                text == PLACEHOLDER_URL -> spec.startUrl
                                text == PLACEHOLDER_HOST -> spec.hostPackage
                                text == PLACEHOLDER_SCOPE -> scope
                                else -> null
                            }
                        }
                        name.startsWith("res/") && name.endsWith(".png") && !iconReplaced -> {
                            data = spec.iconPng
                            iconReplaced = true
                        }
                    }
                    output.putNextEntry(copyEntry(entry, data))
                    output.write(data)
                    output.closeEntry()
                }
            }
        }

        val signer = ApkSigner.SignerConfig.Builder("gtwebapk", key, listOf(certificate)).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(unsigned)
            .setOutputApk(signed)
            .setMinSdkVersion(26)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(false)
            .build()
            .sign()
        unsigned.delete()
        return signed
    }

    /** Sıkıştırılmamış (STORED) girdiler öyle kalmalıdır (resources.arsc için zorunlu). */
    private fun copyEntry(source: ZipEntry, data: ByteArray): ZipEntry {
        val entry = ZipEntry(source.name)
        if (source.method == ZipEntry.STORED || source.name == "resources.arsc") {
            entry.method = ZipEntry.STORED
            entry.size = data.size.toLong()
            entry.compressedSize = data.size.toLong()
            entry.crc = CRC32().apply { update(data) }.value
        } else {
            entry.method = ZipEntry.DEFLATED
        }
        entry.time = 315532800000L // Sabit zaman: aynı girdi aynı çıktıyı üretir.
        return entry
    }
}
