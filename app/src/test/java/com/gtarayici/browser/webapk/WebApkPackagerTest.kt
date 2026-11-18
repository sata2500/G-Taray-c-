package com.gtarayici.browser.webapk

import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WebApkPackagerTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "webapk-shell").exists() }

    @Test
    fun placeholdersMatchShellTemplate() {
        val props = Properties().apply { File(root, "webapk-shell/placeholders.properties").inputStream().use { load(it) } }
        assertEquals(props.getProperty("package"), WebApkPackager.PLACEHOLDER_PACKAGE)
        assertEquals(props.getProperty("label"), WebApkPackager.PLACEHOLDER_LABEL)
        assertEquals(props.getProperty("url"), WebApkPackager.PLACEHOLDER_URL)
        assertEquals(props.getProperty("host"), WebApkPackager.PLACEHOLDER_HOST)
        assertEquals(props.getProperty("scope"), WebApkPackager.PLACEHOLDER_SCOPE)
    }

    /** Şablonu kişiselleştirip imzalar; sonucu çıktı klasörüne yazar (apksigner/aapt2 ile denetlenir). */
    @Test
    fun buildsSignedPersonalizedApk() {
        val template = File(root, "webapk-shell/build/outputs/apk/release/webapk-shell-release.apk")
        assumeTrue("Şablon derlenmemiş", template.exists())

        val ks = File(System.getProperty("java.io.tmpdir"), "gt-webapk-test.p12")
        if (!ks.exists()) {
            val keytool = File(System.getProperty("java.home"), "bin/keytool").absolutePath
            ProcessBuilder(
                keytool, "-genkeypair", "-alias", "t", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
                "-dname", "CN=Test", "-storetype", "PKCS12", "-keystore", ks.absolutePath, "-storepass", "testpass",
            ).inheritIO().start().waitFor()
        }
        val store = KeyStore.getInstance("PKCS12").apply { ks.inputStream().use { load(it, "testpass".toCharArray()) } }
        val key = store.getKey("t", "testpass".toCharArray()) as PrivateKey
        val cert = store.getCertificate("t") as X509Certificate

        val icon = File(root, "webapk-shell/src/main/res/mipmap-xxxhdpi/ic_launcher.png").readBytes()
        val url = "https://m.youtube.com/?feature=ytca"
        val spec = WebApkSpec(
            packageName = WebApkPackager.packageNameFor(url),
            label = "YouTube Müzik Ğüşiöç",
            startUrl = url,
            scope = "https://m.youtube.com/",
            hostPackage = "com.gtarayici.browser",
            iconPng = icon,
        )
        val out = File(System.getProperty("java.io.tmpdir"), "gt-webapk-out").apply { deleteRecursively() }
        val apk = WebApkPackager.build(template.readBytes(), spec, key, cert, out)
        println("WEBAPK=${apk.absolutePath} PKG=${spec.packageName}")
        assertTrue(apk.length() > 0)
    }
}
