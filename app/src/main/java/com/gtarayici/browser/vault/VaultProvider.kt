package com.gtarayici.browser.vault

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import com.gtarayici.browser.components

/**
 * Şifresi anlık çözülen dosya tanıtıcıları: okuyan taraf (PDF görüntüleyici, başka bir uygulama) istediği konumu
 * okudukça yalnızca o parçanın şifresi bellekte çözülür. Şifresiz kopya diske yazılmaz.
 */
internal object VaultProxy {
    private val handler: Handler by lazy { Handler(HandlerThread("gt-vault-io").apply { start() }.looper) }

    fun open(context: Context, reader: VaultFormat.Reader): ParcelFileDescriptor {
        val storage = context.getSystemService(StorageManager::class.java)
        return storage.openProxyFileDescriptor(
            ParcelFileDescriptor.MODE_READ_ONLY,
            object : ProxyFileDescriptorCallback() {
                override fun onGetSize(): Long = reader.size

                override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
                    var done = 0
                    try {
                        while (done < size) {
                            val n = reader.read(offset + done, data, done, size - done)
                            if (n <= 0) break
                            done += n
                        }
                    } catch (e: Exception) {
                        throw ErrnoException("read", OsConstants.EIO)
                    }
                    return done
                }

                override fun onRelease() {
                    runCatching { reader.close() }
                }
            },
            handler,
        )
    }
}

/**
 * Kasadaki dosyaları başka uygulamalara (paylaşma, "başka uygulamada aç") geçici izinle veren sağlayıcı. Dışa açık
 * değildir: yalnızca uygulamanın izin verdiği adresler, izin verilen uygulama tarafından okunabilir.
 */
class VaultProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun vault() = context!!.applicationContext.components.vault

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") throw SecurityException("Yalnızca okunabilir")
        val id = uri.lastPathSegment ?: return null
        return vault().openDescriptor(id)
    }

    override fun getType(uri: Uri): String? = uri.lastPathSegment?.let { vault().item(it)?.mime }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val item = uri.lastPathSegment?.let { vault().item(it) } ?: return null
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) item.name else if (it == OpenableColumns.SIZE) item.size else null })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        fun uri(context: Context, id: String): Uri = Uri.parse("content://${context.packageName}.vault/item/$id")
    }
}
