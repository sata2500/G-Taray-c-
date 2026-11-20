package com.gtarayici.browser.extensions

import com.gtarayici.browser.R
import com.gtarayici.browser.extensions.ExtensionPermissions.Line
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtensionPermissionsTest {
    @Test
    fun describesKnownPermissionsAndHidesSilentOnes() {
        val lines = ExtensionPermissions.describe(listOf("tabs", "storage", "<all_urls>", "alarms", "contextMenus"))
        assertEquals(listOf(Line.Text(R.string.ext_perm_all_sites), Line.Text(R.string.ext_perm_tabs)), lines)
    }

    @Test
    fun listsFewSitesAndSummarizesMany() {
        assertEquals(
            listOf(Line.WithArg(R.string.ext_perm_site, "example.com")),
            ExtensionPermissions.describe(listOf("https://*.example.com/*", "http://example.com/*")),
        )
        val many = (1..5).map { "https://site$it.com/*" }
        assertEquals(listOf(Line.WithArg(R.string.ext_perm_many_sites, "5")), ExtensionPermissions.describe(many))
    }

    @Test
    fun keepsUnknownPermissionsRawAndMergesDuplicates() {
        assertEquals(
            listOf(Line.Text(R.string.ext_perm_history), Line.Raw("somethingNew")),
            ExtensionPermissions.describe(listOf("history", "topSites", "somethingNew")),
        )
    }
}
