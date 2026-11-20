package com.gtarayici.browser.downloads

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** İndirilen dosyaları diskten silme. */
object DownloadedFiles {
    /**
     * [directoryPath]/[fileName] dosyasını siler. Android 10+ ortak "İndirilenler" klasöründe dosya MediaStore
     * kaydıyla birlikte silinir (uygulama yalnızca kendi indirdiği dosyaları görebilir ve silebilir).
     * Dosya zaten yoksa da başarılı sayılır.
     */
    fun delete(context: Context, fileName: String?, directoryPath: String?): Boolean {
        if (fileName.isNullOrBlank() || directoryPath.isNullOrBlank()) return false
        val file = File(directoryPath, fileName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val deleted = runCatching {
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf(fileName),
                )
            }.getOrDefault(0)
            if (deleted > 0) return true
        }
        return runCatching { !file.exists() || file.delete() }.getOrDefault(false)
    }
}
