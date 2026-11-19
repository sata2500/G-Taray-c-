package com.gtarayici.browser.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/** GitHub sürümlerinden APK indirme ve Android yükleyicisiyle kurma için ortak yardımcılar. */
internal object Apks {
    /** GitHub sürüm bilgisi; [tag] verilmezse en son sürüm. Bulunamazsa null. */
    fun fetchRelease(repo: String, tag: String? = null): JSONObject? {
        val path = if (tag == null) "latest" else "tags/$tag"
        val connection = open("https://api.github.com/repos/$repo/releases/$path")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            return when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw HttpException(connection.responseCode)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Sürümdeki, adı [suffix] ile biten dosyanın indirme adresi ve boyutu. */
    fun asset(release: JSONObject, suffix: String): Pair<String, Long>? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name").endsWith(suffix)) {
                return asset.getString("browser_download_url") to asset.optLong("size")
            }
        }
        return null
    }

    /** Dosyayı indirir; ilerleme 0..1 arasında bildirilir (en fazla %1 adımlarla). */
    fun download(url: String, target: File, expectedSize: Long = 0, onProgress: (Float) -> Unit = {}) {
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw HttpException(connection.responseCode)
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            var done = 0L
            var reported = 0f
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val progress = if (total > 0) done.toFloat() / total else 0f
                        if (progress - reported >= 0.01f) {
                            reported = progress
                            onProgress(progress)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** APK'yı yükleyiciye verir; sonuç [receiver] sınıfına (ekstralarla) bildirilir. */
    fun install(context: Context, file: File, packageName: String, receiver: Class<*>, extras: Intent.() -> Unit = {}) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = Intent(context, receiver).apply(extras)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
        file.delete()
    }

    /** Paketi imzalayan sertifikaların (döndürme geçmişi dahil) SHA-256 özetleri, büyük harf onaltılık. */
    fun certificateDigests(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certs.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02X".format(it) }

    fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "G-Tarayici-Updater")
    }

    class HttpException(val code: Int) : IOException("HTTP $code")
}
