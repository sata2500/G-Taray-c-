package com.gtarayici.browser.reading

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Listede gösterilen özet (metin dosyadan gerektiğinde okunur). */
data class ReadingItem(val id: String, val url: String, val title: String, val site: String, val savedAt: Long, val minutes: Int)

/**
 * "Sonra oku" listesi: makaleler uygulamanın kendi klasöründe (her biri bir JSON dosyası) saklanır; internetsiz
 * okunabilir. Liste profiller arasında ortaktır, gizli sekmelerden kaydedilen de listeye girer (kullanıcı ister).
 */
class ReadingList(context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "reading").apply { mkdirs() }
    private val _items = MutableStateFlow<List<ReadingItem>>(emptyList())
    val items: StateFlow<List<ReadingItem>> = _items.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) { reload() }
    }

    /** Aynı adres zaten kayıtlıysa güncellenir (yeni metinle). */
    suspend fun save(extracted: JSONObject, url: String): Article? = withContext(Dispatchers.IO) {
        val existing = _items.value.firstOrNull { it.url == url }
        val article = Article.fromExtraction(extracted, url, existing?.id ?: UUID.randomUUID().toString(), System.currentTimeMillis())
            ?: return@withContext null
        file(article.id).writeText(article.toJson().toString())
        reload()
        article
    }

    suspend fun load(id: String): Article? = withContext(Dispatchers.IO) {
        runCatching { Article.fromJson(JSONObject(file(id).readText())) }.getOrNull()
    }

    /** Silinen makalenin metni "Geri al" için döner. */
    suspend fun delete(id: String): String? = withContext(Dispatchers.IO) {
        val text = runCatching { file(id).readText() }.getOrNull()
        file(id).delete()
        reload()
        text
    }

    suspend fun restore(json: String) = withContext(Dispatchers.IO) {
        val article = runCatching { Article.fromJson(JSONObject(json)) }.getOrNull() ?: return@withContext
        file(article.id).writeText(json)
        reload()
    }

    fun isSaved(url: String): Boolean = _items.value.any { it.url == url }

    private fun reload() {
        _items.value = dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { Article.fromJson(JSONObject(f.readText())) }.getOrNull() }
            .map { ReadingItem(it.id, it.url, it.title, it.site, it.savedAt, it.minutes) }
            .sortedByDescending { it.savedAt }
    }

    private fun file(id: String) = File(dir, "${id.filter { it.isLetterOrDigit() || it == '-' }}.json")
}
