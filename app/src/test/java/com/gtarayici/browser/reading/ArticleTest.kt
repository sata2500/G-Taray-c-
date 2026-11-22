package com.gtarayici.browser.reading

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleTest {
    private fun block(k: String, t: String) = JSONObject().put("k", k).put("t", t)

    @Test
    fun extractionDropsDuplicateTitleAndRoundTrips() {
        val json = JSONObject().put("title", "Büyük Haber").put("site", "Gazete").put("lang", "tr")
            .put("blocks", JSONArray().put(block("h", "Büyük Haber")).put(block("p", "Birinci paragraf.")).put(block("li", "Madde")).put(block("x", "Bilinmeyen tür")))
        val article = Article.fromExtraction(json, "https://ornek.com/haber", "a1", 5L)!!
        assertEquals(listOf(ArticleBlock.Kind.PARAGRAPH, ArticleBlock.Kind.ITEM, ArticleBlock.Kind.PARAGRAPH), article.blocks.map { it.kind })
        assertEquals(article, Article.fromJson(JSONObject(article.toJson().toString())))
    }

    @Test
    fun emptyExtractionIsNull() {
        assertNull(Article.fromExtraction(JSONObject().put("title", "x").put("blocks", JSONArray()), "u", "id", 0))
    }

    @Test
    fun longParagraphsAreSplitAtSentenceEnds() {
        val sentence = "Bu bir cümledir ve biraz uzundur. "
        val text = sentence.repeat(100)
        val parts = Speaker.split(text, max = 200)
        assertTrue(parts.all { it.length <= 200 })
        assertTrue(parts.dropLast(1).all { it.endsWith(".") })
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
    }
}
