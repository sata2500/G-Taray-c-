package com.gtarayici.browser.reading

import org.json.JSONArray
import org.json.JSONObject

/** Makaledeki bir blok: başlık, paragraf, liste öğesi veya alıntı. */
data class ArticleBlock(val kind: Kind, val text: String) {
    enum class Kind(val code: String) { HEADING("h"), PARAGRAPH("p"), ITEM("li"), QUOTE("q") }
}

/** Sayfadan çıkarılan makale; "Sonra oku" listesinde saklanır ve sesli okunur. */
data class Article(
    val id: String,
    val url: String,
    val title: String,
    val site: String,
    val lang: String,
    val savedAt: Long,
    val blocks: List<ArticleBlock>,
) {
    /** Yaklaşık okuma süresi (dakika; dakikada ~200 sözcük). */
    val minutes: Int get() = (blocks.sumOf { it.text.split(' ').size } / 200).coerceAtLeast(1)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("url", url).put("title", title).put("site", site).put("lang", lang).put("savedAt", savedAt)
        .put("blocks", JSONArray(blocks.map { JSONObject().put("k", it.kind.code).put("t", it.text) }))

    companion object {
        fun fromJson(o: JSONObject): Article = Article(
            id = o.getString("id"),
            url = o.optString("url"),
            title = o.optString("title"),
            site = o.optString("site"),
            lang = o.optString("lang"),
            savedAt = o.optLong("savedAt"),
            blocks = parseBlocks(o.optJSONArray("blocks")),
        )

        /** Yardımcı eklentinin yanıtından makale; boşsa null. Başlığın aynısı olan ilk başlık bloğu atlanır. */
        fun fromExtraction(json: JSONObject, url: String, id: String, now: Long): Article? {
            val title = json.optString("title").trim()
            val blocks = parseBlocks(json.optJSONArray("blocks"))
                .let { list -> if (list.firstOrNull()?.let { it.kind == ArticleBlock.Kind.HEADING && it.text == title } == true) list.drop(1) else list }
            if (blocks.isEmpty()) return null
            return Article(id, url, title.ifEmpty { url }, json.optString("site"), json.optString("lang"), now, blocks)
        }

        private fun parseBlocks(array: JSONArray?): List<ArticleBlock> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val kind = ArticleBlock.Kind.entries.firstOrNull { it.code == o.optString("k") } ?: ArticleBlock.Kind.PARAGRAPH
                o.optString("t").trim().takeIf { it.isNotEmpty() }?.let { ArticleBlock(kind, it) }
            }
        }
    }
}
