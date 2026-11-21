package com.gtarayici.browser.diagnostics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnonymousReporterTest {
    @Test
    fun payloadCarriesPreviewedTextAndCategory() {
        val report = IssueReport(IssueCategory.Video, "YouTube indirilemiyor\nayrıntı", environment = "- G-Tarayıcı 0.9.2", log = "satır")
        val json = JSONObject(AnonymousReporter.payload(report, "0.9.2"))
        assertEquals(report.title, json.getString("title"))
        assertEquals("Video", json.getString("category"))
        assertEquals("0.9.2", json.getString("version"))
        assertTrue(json.getString("body").startsWith(report.body().take(20)))
    }

    @Test
    fun longReportsAreTrimmedForTheRelay() {
        val report = IssueReport(IssueCategory.Other, "x", environment = "e", log = "y".repeat(100_000))
        assertTrue(JSONObject(AnonymousReporter.payload(report, "1")).getString("body").length <= 58_000)
    }
}
