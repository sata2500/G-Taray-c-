package com.gtarayici.browser.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.gtarayici.browser.BuildConfig
import java.io.File

/** Sorun bildirimi dosyalarını (rapor metni, ekran görüntüsü) diğer uygulamalara açan sağlayıcı. */
class ReportFileProvider : FileProvider()

/** Raporun cihaz ve uygulama bilgisi satırları. Kişisel bilgi içermez. */
object DiagnosticsEnvironment {
    fun describe(context: Context): String {
        val metrics = context.resources.displayMetrics
        val config = context.resources.configuration
        return listOf(
            "G-Tarayıcı ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}",
            "GeckoView ${org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION}",
            "Ekran ${metrics.widthPixels}×${metrics.heightPixels} · yazı ölçeği ${config.fontScale} · " +
                (if (config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "yatay" else "dikey"),
            "Dil ${config.locales[0].toLanguageTag()}",
        ).joinToString("\n")
    }
}

/** "Sorun bildir" sayfasının dış dünyaya açılan işlemleri. Hiçbiri kullanıcı seçmeden çalışmaz. */
object ReportActions {

    fun copy(context: Context, label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** Raporu .txt (ve varsa ekran görüntüsünü .png) olarak paylaşım penceresiyle gönderir. */
    fun share(context: Context, report: IssueReport, screenshot: Bitmap?) {
        val dir = File(context.cacheDir, "reports").apply { deleteRecursively(); mkdirs() }
        val text = File(dir, "g-tarayici-sorun.txt").apply { writeText(report.title + "\n\n" + report.body()) }
        val uris = arrayListOf(uriFor(context, text))
        if (screenshot != null) {
            val image = File(dir, "g-tarayici-ekran.png")
            image.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            uris += uriFor(context, image)
        }
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.putExtra(Intent.EXTRA_SUBJECT, report.title)
            .putExtra(Intent.EXTRA_TEXT, report.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Ekran görüntüsünü Resimler › G-Tarayıcı klasörüne kaydeder (GitHub sayfasına eklenebilsin diye). */
    fun saveScreenshot(context: Context, screenshot: Bitmap): Boolean = runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "g-tarayici-sorun-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/G-Tarayıcı")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        context.contentResolver.openOutputStream(uri)?.use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } != null
    }.getOrDefault(false)

    private fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
}
