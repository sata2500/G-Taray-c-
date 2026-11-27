@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.gtarayici.browser.viewer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import com.gtarayici.browser.vault.Vault
import com.gtarayici.browser.vault.VaultDataSource
import com.gtarayici.browser.vault.VaultItem
import java.io.InputStream

/**
 * Görüntüleyicinin açtığı dosya: kasadaki şifreli bir dosya ya da telefondaki bir dosya. Görüntüleyiciler yalnızca
 * bu arayüzü kullanır; şifre çözme (gerekirse) kaynağın içinde, bellekte yapılır.
 */
interface FileSource {
    /** Okuma konumunun saklandığı kalıcı anahtar. */
    val key: String
    val name: String
    val mime: String?
    val size: Long
    val kind: FileKind get() = FileKind.of(name, mime)

    /** Medya oynatıcının açacağı adres. */
    val mediaUri: Uri

    fun open(): InputStream

    /** Konumlanabilir dosya tanıtıcısı (PDF); yoksa null. */
    fun descriptor(): ParcelFileDescriptor?

    /** Oynatıcının veri kaynağı. */
    @UnstableApi
    fun dataSourceFactory(context: Context): DataSource.Factory

    suspend fun thumbnail(): Bitmap? = null
}

class VaultSource(private val vault: Vault, val item: VaultItem) : FileSource {
    override val key = "vault:${item.id}"
    override val name get() = item.name
    override val mime get() = item.mime
    override val size get() = item.size
    override val mediaUri: Uri get() = VaultDataSource.uri(item.id)
    override fun open(): InputStream = vault.reader(item.id).let { reader -> ClosingStream(reader.stream()) { reader.close() } }
    override fun descriptor(): ParcelFileDescriptor? = vault.openDescriptor(item.id)

    @UnstableApi
    override fun dataSourceFactory(context: Context): DataSource.Factory = DataSource.Factory { VaultDataSource(vault) }

    override suspend fun thumbnail(): Bitmap? = if (item.hasThumbnail) vault.thumbnail(item.id) else null
}

/** Telefondaki bir dosya (content:// ya da file://). */
class UriSource(
    private val context: Context,
    val uri: Uri,
    override val name: String,
    override val mime: String?,
    override val size: Long = -1,
) : FileSource {
    override val key = uri.toString()
    override val mediaUri: Uri get() = uri
    override fun open(): InputStream = context.contentResolver.openInputStream(uri) ?: error("Dosya açılamadı")
    override fun descriptor(): ParcelFileDescriptor? = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()

    @UnstableApi
    override fun dataSourceFactory(context: Context): DataSource.Factory = DefaultDataSource.Factory(context)
}

private class ClosingStream(input: InputStream, private val onClose: () -> Unit) : java.io.FilterInputStream(input) {
    override fun close() {
        runCatching { super.close() }
        onClose()
    }
}

/**
 * Kaldığın yer: video/ses konumu (ms), PDF sayfası, kitapta öğe sırası. Dosya adresine göre saklanır; kasadaki
 * dosyalar için adres yalnızca rastgele bir kimliktir, ad içermez.
 */
class ReadingPositions(context: Context) {
    private val prefs = context.getSharedPreferences("gtarayici_positions", Context.MODE_PRIVATE)

    fun get(key: String): Long = prefs.getLong(hash(key), 0)
    fun set(key: String, value: Long) = prefs.edit { putLong(hash(key), value) }

    /** Kitaplarda ilerleme yüzdesi (0..1). */
    fun progress(key: String): Float = prefs.getFloat(hash(key) + ":p", 0f)
    fun setProgress(key: String, value: Float) = prefs.edit { putFloat(hash(key) + ":p", value) }

    private fun hash(key: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}
