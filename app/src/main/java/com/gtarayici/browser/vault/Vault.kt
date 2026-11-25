package com.gtarayici.browser.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.LruCache
import androidx.core.content.edit
import com.gtarayici.browser.diagnostics.Diagnostics
import com.gtarayici.browser.downloads.manager.DownloadNames
import com.gtarayici.browser.downloads.manager.MediaKind
import com.gtarayici.browser.downloads.manager.PublicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Kasadaki bir dosya. Ad ve tür de şifreli dizinde tutulur. */
data class VaultItem(
    val id: String,
    val name: String,
    val mime: String?,
    val size: Long,
    val added: Long,
    val kind: MediaKind,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0,
    val hasThumbnail: Boolean = false,
) {
    val isPdf: Boolean get() = mime?.startsWith("application/pdf") == true || name.endsWith(".pdf", ignoreCase = true)

    /** Uygulama içinde metin olarak gösterilebilir mi? */
    val isText: Boolean get() = mime?.startsWith("text/") == true ||
        DownloadNames.extension("x/$name") in setOf("txt", "md", "csv", "tsv", "json", "xml", "srt", "vtt", "ass", "ssa", "log", "ovpn", "conf")
}

/**
 * Gizli kasa: gizli sekmelerde indirilen (isteğe bağlı) ve kullanıcının eklediği dosyalar uygulamanın kendi alanında,
 * [VaultFormat] ile şifreli saklanır. Dosya adları, türleri ve küçük resimleri de şifrelidir.
 *
 * Anahtarlar: kasanın 256 bit ana anahtarı, Android Anahtar Deposu'ndaki (donanım destekli, dışarı çıkarılamaz) bir
 * anahtarla şifrelenip saklanır. Telefonun yedeklerine girmez (noBackupFilesDir). Kasayı açmak uygulama kilidine
 * bağlıdır (gizli sekmelerle aynı kilit).
 */
class Vault(private val context: Context) {
    private val dir = File(context.noBackupFilesDir, "vault").apply { mkdirs() }
    private val incoming = File(context.noBackupFilesDir, "vault-incoming").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("gtarayici_vault", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()
    @Volatile private var loaded = false

    private val thumbnails = LruCache<String, Bitmap>(8 * 1024 * 1024).apply { }

    @Volatile private var masterKey: SecretKey? = null

    /** Kasanın ana anahtarı (ilk kullanımda üretilir). */
    @Synchronized
    private fun master(): SecretKey {
        masterKey?.let { return it }
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val wrapping = (keystore.getKey(KEYSTORE_ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }.generateKey()
        val stored = prefs.getString(KEY_MASTER, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val raw = if (stored != null) {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, wrapping, GCMParameterSpec(128, stored.copyOfRange(0, 12)))
                doFinal(stored, 12, stored.size - 12)
            }
        } else {
            val fresh = ByteArray(32).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrapping) }
            val sealed = cipher.iv + cipher.doFinal(fresh)
            prefs.edit(commit = true) { putString(KEY_MASTER, Base64.encodeToString(sealed, Base64.NO_WRAP)) }
            fresh
        }
        return SecretKeySpec(raw, "AES").also { masterKey = it }
    }

    // --- Dizin --------------------------------------------------------------------------

    suspend fun load() = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        lock.withLock {
            if (loaded) return@withLock
            _items.value = runCatching { readIndex() }.onFailure {
                Diagnostics.log("kasa dizini okunamadı: ${it.javaClass.simpleName}", "kasa")
            }.getOrDefault(emptyList())
            loaded = true
        }
    }

    private fun readIndex(): List<VaultItem> {
        val file = File(dir, INDEX)
        if (!file.exists()) return emptyList()
        val sealed = file.readBytes()
        val json = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, master(), GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
            String(doFinal(sealed, 12, sealed.size - 12))
        }
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            VaultItem(
                id = o.getString("id"),
                name = o.getString("name"),
                mime = o.optString("mime").ifEmpty { null },
                size = o.optLong("size"),
                added = o.optLong("added"),
                kind = runCatching { MediaKind.valueOf(o.optString("kind")) }.getOrDefault(MediaKind.OTHER),
                width = o.optInt("w"),
                height = o.optInt("h"),
                durationMs = o.optLong("dur"),
                hasThumbnail = o.optBoolean("thumb"),
            )
        }.filter { File(dir, it.id + DATA).exists() }
    }

    private fun writeIndex(items: List<VaultItem>) {
        val array = JSONArray()
        items.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id).put("name", it.name).put("mime", it.mime ?: "").put("size", it.size)
                    .put("added", it.added).put("kind", it.kind.name).put("w", it.width).put("h", it.height)
                    .put("dur", it.durationMs).put("thumb", it.hasThumbnail),
            )
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, master()) }
        val sealed = cipher.iv + cipher.doFinal(array.toString().toByteArray())
        val tmp = File(dir, "$INDEX.tmp")
        tmp.writeBytes(sealed)
        tmp.renameTo(File(dir, INDEX))
        _items.value = items
    }

    // --- Ekleme -------------------------------------------------------------------------

    /** İndirilen şifresiz dosyayı geçici olarak tutmak için uygulamanın kendi alanında bir yer. */
    fun incomingFile(): File = File(incoming, UUID.randomUUID().toString() + ".part")

    /** [source] dosyasını şifreleyip kasaya ekler ve şifresiz dosyayı siler. */
    suspend fun import(source: File, name: String, mime: String?): VaultItem = withContext(Dispatchers.IO) {
        try {
            source.inputStream().use { importStream(it, name, mime) }
        } finally {
            source.delete()
        }
    }

    /** Bir akışı (ör. telefondan seçilen dosya) şifreleyip kasaya ekler. */
    suspend fun importStream(input: InputStream, name: String, mime: String?): VaultItem = withContext(Dispatchers.IO) {
        load()
        val id = UUID.randomUUID().toString()
        val target = File(dir, id + DATA)
        val size = target.outputStream().buffered(256 * 1024).use { VaultFormat.encrypt(input, it, master()) }
        val type = mime?.substringBefore(';')?.trim()?.takeIf { it.contains('/') && it != "application/octet-stream" }
            ?: android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(DownloadNames.extension("x/$name"))
        var item = VaultItem(id, DownloadNames.sanitize(name).ifBlank { "dosya" }, type, size, System.currentTimeMillis(), DownloadNames.kind("x/$name", type))
        item = runCatching { describe(item) }.getOrDefault(item)
        lock.withLock { writeIndex(_items.value + item) }
        item
    }

    /** Küçük resim, boyutlar ve süre (dosyanın şifresi yalnızca bellekte çözülür). */
    private fun describe(item: VaultItem): VaultItem {
        var result = item
        val thumb: Bitmap? = when {
            item.kind == MediaKind.IMAGE -> reader(item.id).use { reader ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                reader.stream().use { BitmapFactory.decodeStream(it, null, bounds) }
                result = result.copy(width = bounds.outWidth, height = bounds.outHeight)
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, THUMB) }
                reader.stream().use { BitmapFactory.decodeStream(it, null, options) }
            }
            item.kind == MediaKind.VIDEO || item.kind == MediaKind.AUDIO || item.kind == MediaKind.STREAM -> reader(item.id).use { reader ->
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(mediaDataSource(reader))
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val kind = if (w > 0 && h > 0) MediaKind.VIDEO else if (item.kind == MediaKind.STREAM) MediaKind.AUDIO else item.kind
                    result = result.copy(durationMs = duration, width = w, height = h, kind = kind)
                    if (w > 0) {
                        retriever.getScaledFrameAtTime(minOf(duration * 1000 / 10, 3_000_000L), MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB, THUMB)
                    } else {
                        retriever.embeddedPicture?.let { art -> BitmapFactory.decodeByteArray(art, 0, art.size, BitmapFactory.Options().apply { inSampleSize = 2 }) }
                    }
                } finally {
                    runCatching { retriever.release() }
                }
            }
            item.isPdf -> openDescriptor(item.id)?.use { fd ->
                PdfRenderer(fd).use { pdf ->
                    pdf.openPage(0).use { page ->
                        val scale = THUMB.toFloat() / maxOf(page.width, page.height)
                        Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(android.graphics.Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
            else -> null
        }
        if (thumb != null) {
            val bytes = ByteArrayOutputStream().also { thumb.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
            File(dir, item.id + THUMB_SUFFIX).outputStream().use { VaultFormat.encrypt(ByteArrayInputStream(bytes), it, master()) }
            thumbnails.put(item.id, thumb)
            result = result.copy(hasThumbnail = true)
        }
        return result
    }

    // --- Okuma --------------------------------------------------------------------------

    fun reader(id: String): VaultFormat.Reader = VaultFormat.Reader(File(dir, id + DATA), master())

    fun item(id: String): VaultItem? = _items.value.firstOrNull { it.id == id }

    /** Küçük resim (bellekte önbelleklenir). */
    suspend fun thumbnail(id: String): Bitmap? = withContext(Dispatchers.IO) {
        thumbnails.get(id)?.let { return@withContext it }
        val file = File(dir, id + THUMB_SUFFIX)
        if (!file.exists()) return@withContext null
        runCatching {
            VaultFormat.Reader(file, master()).use { r -> r.stream().use { BitmapFactory.decodeStream(it) } }
        }.getOrNull()?.also { thumbnails.put(id, it) }
    }

    /** Ekrana sığacak boyutta görsel ([maxSide] piksel). */
    suspend fun image(id: String, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            reader(id).use { reader ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                reader.stream().use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
                reader.stream().use { BitmapFactory.decodeStream(it, null, options) }
            }
        }.getOrNull()
    }

    /** Metin dosyasının başı (en fazla [limit] bayt). */
    suspend fun text(id: String, limit: Int = 1024 * 1024): String? = withContext(Dispatchers.IO) {
        runCatching {
            reader(id).use { reader ->
                val size = minOf(reader.size, limit.toLong()).toInt()
                val buffer = ByteArray(size)
                var read = 0
                while (read < size) {
                    val n = reader.read(read.toLong(), buffer, read, size - read)
                    if (n <= 0) break
                    read += n
                }
                String(buffer, 0, read, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    /**
     * Şifresi anlık çözülen, konumlanabilir bir dosya tanıtıcısı (PDF görüntüleyici, başka uygulamada açma). Şifresiz
     * kopya diske yazılmaz.
     */
    fun openDescriptor(id: String): ParcelFileDescriptor? = runCatching {
        VaultProxy.open(context, reader(id))
    }.getOrNull()

    /** Başka uygulamalara verilecek adres (yalnızca izin verilen uygulama, geçici olarak okuyabilir). */
    fun uri(id: String): Uri = VaultProvider.uri(context, id)

    // --- Düzenleme ----------------------------------------------------------------------

    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val clean = DownloadNames.sanitize(name).ifBlank { return@withLock }
            writeIndex(_items.value.map { if (it.id == id) it.copy(name = clean) else it })
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            File(dir, id + DATA).delete()
            File(dir, id + THUMB_SUFFIX).delete()
            thumbnails.remove(id)
            writeIndex(_items.value.filterNot { it.id == id })
        }
    }

    /** Dosyanın şifresini çözüp telefonun İndirilenler klasörüne kaydeder; kaydedilen adı döndürür. */
    suspend fun export(id: String): String = withContext(Dispatchers.IO) {
        val item = item(id) ?: error("Dosya bulunamadı")
        val file = PublicFile.create(context, item.name, item.mime)
        try {
            val out = file.outputStream()
            reader(id).use { reader -> reader.stream().use { it.copyTo(out, 256 * 1024) } }
            out.flush()
            file.commit().second
        } catch (e: Exception) {
            file.discard()
            throw e
        }
    }

    /** Yarım kalmış (uygulama kapanırken süren) indirmelerin şifresiz parçalarını siler. */
    fun cleanIncoming(active: Set<String> = emptySet()) {
        incoming.listFiles()?.filter { it.name !in active }?.forEach { it.delete() }
    }

    private fun mediaDataSource(reader: VaultFormat.Reader) = object : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
            if (size == 0) 0 else reader.read(position, buffer, offset, size)

        override fun getSize(): Long = reader.size
        override fun close() = Unit
    }

    companion object {
        /** İndirme kaydında kasadaki dosyayı gösteren klasör işareti ("vault:<kimlik>"). */
        const val DOWNLOAD_PREFIX = "vault:"
        private const val KEYSTORE_ALIAS = "gtarayici_vault"
        private const val KEY_MASTER = "master"
        private const val INDEX = "index.gtv"
        private const val DATA = ".gtv"
        private const val THUMB_SUFFIX = ".thumb.gtv"
        private const val THUMB = 320

        fun sampleSize(width: Int, height: Int, target: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
            return sample
        }
    }
}
