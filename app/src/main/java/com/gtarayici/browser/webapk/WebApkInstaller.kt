package com.gtarayici.browser.webapk

import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.widget.Toast
import com.gtarayici.browser.Components
import com.gtarayici.browser.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar
import javax.security.auth.x500.X500Principal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.IconRequest
import mozilla.components.browser.icons.extension.toIconRequest
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.manifest.WebAppManifest

/**
 * Web sitelerini Chrome'daki gibi gerçek bir Android uygulaması (WebAPK) olarak yükler.
 *
 * Uygulama çekmecesinde kendi adı ve simgesiyle görünür, ayarlardan normal bir uygulama gibi
 * kaldırılabilir. Açıldığında G-Tarayıcı'nın web uygulaması penceresini (adres çubuğu olmadan) başlatır.
 * APK cihazda üretilir ve cihaza özel bir anahtarla imzalanır; hiçbir sunucuya gönderilmez.
 */
class WebApkInstaller(private val context: Context, private val components: Components) {

    sealed interface Result {
        data object NeedsPermission : Result
        data object Started : Result
        data class Failed(val message: String) : Result
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** "Bilinmeyen uygulamaları yükle" izni ekranını açar. */
    fun requestPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    suspend fun install(tab: SessionState): Result = withContext(Dispatchers.IO) {
        if (!canInstall()) return@withContext Result.NeedsPermission
        runCatching {
            val manifest = manifestFor(tab)
            components.manifestStorage.saveManifest(manifest)

            val spec = WebApkSpec(
                packageName = WebApkPackager.packageNameFor(manifest.startUrl),
                label = manifest.shortName?.takeIf { it.isNotBlank() } ?: manifest.name,
                startUrl = manifest.startUrl,
                scope = manifest.scope ?: manifest.startUrl,
                hostPackage = context.packageName,
                iconPng = iconPng(manifest, tab),
            )
            val template = context.assets.open("webapk/shell.apk").use { it.readBytes() }
            val (key, certificate) = signingKey()
            val workDir = File(context.cacheDir, "webapk").apply { deleteRecursively(); mkdirs() }
            val apk = WebApkPackager.build(template, spec, key, certificate, workDir)
            commit(apk, spec)
            Result.Started
        }.getOrElse { Result.Failed(it.message ?: it.javaClass.simpleName) }
    }

    /** Sitenin web uygulaması bildirimi; yoksa sayfadan bağımsız bir pencere için oluşturulur. */
    private fun manifestFor(tab: SessionState): WebAppManifest {
        val existing = tab.content.webAppManifest
        val pageUrl = tab.content.url
        if (existing != null) {
            return if (existing.display == WebAppManifest.DisplayMode.BROWSER) {
                existing.copy(display = WebAppManifest.DisplayMode.STANDALONE)
            } else {
                existing
            }
        }
        val uri = pageUrl.toUri()
        return WebAppManifest(
            name = tab.content.title.ifBlank { uri.host.orEmpty() },
            startUrl = pageUrl,
            display = WebAppManifest.DisplayMode.STANDALONE,
            scope = "${uri.scheme}://${uri.host}/",
        )
    }

    /** 192 piksel başlatıcı simgesi. Maskelenebilir simgeler daire içine, diğerleri kenar boşluğuyla çizilir. */
    private suspend fun iconPng(manifest: WebAppManifest, tab: SessionState): ByteArray {
        val request = if (manifest.icons.isNotEmpty()) {
            manifest.toIconRequest().copy(size = IconRequest.Size.LAUNCHER)
        } else {
            IconRequest(tab.content.url, size = IconRequest.Size.LAUNCHER)
        }
        val icon = runCatching { components.icons.loadIcon(request).await() }.getOrNull()
        val source = icon?.bitmap ?: tab.content.icon
        val size = 192
        val output = createBitmap(size, size)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (source == null) {
            paint.color = 0xFF1A73E8.toInt()
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.color = 0xFFFFFFFF.toInt()
            paint.textSize = size * 0.5f
            paint.textAlign = Paint.Align.CENTER
            val letter = manifest.name.firstOrNull()?.uppercase() ?: "W"
            canvas.drawText(letter, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        } else if (icon?.maskable == true) {
            canvas.clipPath(Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) })
            canvas.drawBitmap(source, null, RectF(0f, 0f, size.toFloat(), size.toFloat()), paint)
        } else {
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            val inset = size * 0.18f
            canvas.drawBitmap(source, null, RectF(inset, inset, size - inset, size - inset), paint)
        }
        return ByteArrayOutputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }

    /** Cihaza özel imza anahtarı (Android Keystore'da, dışarı çıkarılamaz). Tüm WebAPK'lar aynı anahtarı kullanır. */
    private fun signingKey(): Pair<PrivateKey, X509Certificate> {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!store.containsAlias(KEY_ALIAS)) {
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
            val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=G-Tarayici WebAPK"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(start.time)
                .setCertificateNotAfter(end.time)
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE).apply { initialize(spec) }.generateKeyPair()
        }
        val entry = store.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey to (entry.certificate as X509Certificate)
    }

    private fun commit(apk: File, spec: WebApkSpec) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(spec.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = Intent(context, WebApkInstallReceiver::class.java)
                .putExtra(WebApkInstallReceiver.EXTRA_LABEL, spec.label)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
        apk.delete()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "gtarayici_webapk_signing"
    }
}

/** Yükleme sonucunu karşılar: onay gerekiyorsa sistem penceresini açar, sonra kullanıcıyı bilgilendirir. */
class WebApkInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(context, context.getString(R.string.webapk_installed, label), Toast.LENGTH_LONG).show()
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Toast.makeText(context, context.getString(R.string.webapk_failed, message), Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_LABEL = "label"
    }
}
