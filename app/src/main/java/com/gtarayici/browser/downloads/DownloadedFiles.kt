package com.gtarayici.browser.downloads

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** İndirilen dosyaları diskten silme. */
object DownloadedFiles {
    /**
     * [directoryPath]/[fileName] dosyasını siler. Android 10+ ortak "İndirilenler" klasöründe dosya MediaStore
     * kaydıyla birlikte silinir (uygulama yalnızca kendi indirdiği dosyaları görebilir ve silebilir).
     * Arama hem ada hem klasöre göre yapılır: başka bir alt klasördeki aynı adlı dosyaya dokunulmaz.
     * Dosya zaten yoksa da başarılı sayılır.
     */
    fun delete(context: Context, fileName: String?, directoryPath: String?): Boolean {
        if (fileName.isNullOrBlank() || directoryPath.isNullOrBlank()) return false
        val file = File(directoryPath, fileName)
        val relative = relativePath(directoryPath, Environment.getExternalStorageDirectory().absolutePath)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && relative != null) {
            val deleted = runCatching {
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
                    arrayOf(fileName, relative),
                )
            }.getOrDefault(0)
            if (deleted > 0) return true
        }
        return runCatching { !file.exists() || file.delete() }.getOrDefault(false)
    }

    /**
     * MediaStore'un klasör biçimi: "/storage/emulated/0/Download/Alt" → "Download/Alt/".
     * Klasör ortak depolama alanının dışındaysa (ör. uygulamanın kendi klasörü) null.
     */
    internal fun relativePath(directoryPath: String, storageRoot: String): String? {
        val root = storageRoot.trimEnd('/')
        val dir = directoryPath.trimEnd('/')
        if (!dir.startsWith("$root/")) return null
        return dir.removePrefix("$root/") + "/"
    }
}
