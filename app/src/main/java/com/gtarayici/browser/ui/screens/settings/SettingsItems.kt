package com.gtarayici.browser.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.gtarayici.browser.R
import com.gtarayici.browser.search.TextMatch
import com.gtarayici.browser.settings.ThemeMode
import com.gtarayici.browser.ui.designsystem.AppShape
import com.gtarayici.browser.ui.designsystem.EmptyState
import com.gtarayici.browser.ui.designsystem.NavRow
import com.gtarayici.browser.ui.designsystem.SectionCard
import com.gtarayici.browser.ui.designsystem.SectionHeader
import com.gtarayici.browser.ui.designsystem.Spacing
import com.gtarayici.browser.ui.designsystem.SwitchRow
import com.gtarayici.browser.ui.theme.GTarayiciTheme

/**
 * Ayarlar tek bir veri modelinden çizilir: aynı satırlar hem kendi sayfasında hem de arama sonuçlarında kullanılır
 * (önceden her ayar iki kez yazılıyordu ve ikisi birbirinden ayrışabiliyordu).
 */
sealed interface SettingItem {
    val title: String
    val summary: String?
    val icon: ImageVector

    /** Açık/kapalı ayar. */
    class Toggle(
        override val title: String,
        override val summary: String?,
        override val icon: ImageVector,
        val checked: Boolean,
        val onChange: (Boolean) -> Unit,
    ) : SettingItem

    /** Pencere açan veya bir işlem başlatan satır. [opensPage] ise sonda ok gösterilir. */
    class Action(
        override val title: String,
        override val summary: String?,
        override val icon: ImageVector,
        val opensPage: Boolean = false,
        val onClick: () -> Unit,
    ) : SettingItem

    /** Yalnızca bilgi (sürüm, lisans…). */
    class Info(override val title: String, override val summary: String, override val icon: ImageVector) : SettingItem
}

/** Başlıklı bir ayar grubu (tek segmentli kart). */
class SettingSection(val header: String?, val items: List<SettingItem>)

@Composable
fun SettingRow(item: SettingItem) = when (item) {
    is SettingItem.Toggle -> SwitchRow(item.title, item.summary, item.checked, item.icon, onCheckedChange = item.onChange)
    is SettingItem.Action -> NavRow(item.title, item.summary, item.icon, showChevron = item.opensPage, onClick = item.onClick)
    is SettingItem.Info -> NavRow(item.title, item.summary, item.icon, showChevron = false, onClick = {})
}

@Composable
fun SettingSections(sections: List<SettingSection>) {
    sections.filter { it.items.isNotEmpty() }.forEach { section ->
        if (section.header != null) SectionHeader(section.header) else androidx.compose.foundation.layout.Spacer(Modifier.padding(top = Spacing.sm))
        SectionCard(section.items.map { item -> { SettingRow(item) } })
    }
}

/** Arama sonuçları: eşleşen satırlar, ait oldukları sayfanın başlığı altında. */
@Composable
fun SettingsSearchResults(query: String, pages: List<Pair<String, List<SettingSection>>>) {
    val results = pages.mapNotNull { (pageTitle, sections) ->
        val matches = sections.flatMap { it.items }.filter { matchesSettingQuery(query, it.title, it.summary.orEmpty()) }
        matches.takeIf { it.isNotEmpty() }?.let { SettingSection(pageTitle, it) }
    }
    if (results.isEmpty()) {
        EmptyState(stringResource(R.string.search_no_results), icon = Icons.Rounded.SearchOff)
    } else {
        SettingSections(results)
    }
}

/** Ayar araması: harf/aksan duyarsız; yaygın kısaltmalar ("pip", "doh") açılımlarıyla da aranır. */
fun matchesSettingQuery(query: String, vararg texts: String): Boolean {
    if (query.isBlank()) return true
    if (TextMatch.containsAny(query.trim(), *texts)) return true
    val synonym = SETTINGS_SYNONYMS[TextMatch.fold(query.trim())] ?: return false
    return TextMatch.containsAny(synonym, *texts)
}

private val SETTINGS_SYNONYMS = mapOf(
    "pip" to "resim içinde resim",
    "doh" to "dns",
    "reklam" to "izleyici",
    "adblock" to "izleyici",
    "log" to "tanılama",
    "hata" to "sorun",
)

@Composable
fun SettingsSearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.action_clear)) }
            }
        },
        placeholder = { Text(stringResource(R.string.settings_search_hint)) },
        shape = AppShape.full,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
    )
}

@Preview(name = "Ayar satırları", showBackground = true)
@Preview(name = "Ayar satırları – büyük yazı", showBackground = true, fontScale = 2f)
@Composable
private fun SettingSectionsPreview() {
    GTarayiciTheme(mode = ThemeMode.SYSTEM) {
        Column {
            SettingSections(
                listOf(
                    SettingSection(
                        "Koruma",
                        listOf(
                            SettingItem.Toggle("Sıkı izleyici koruması", "Reklam ve izleyicileri engeller", Icons.Rounded.Search, true) {},
                            SettingItem.Action("Güvenli DNS", "Cloudflare", Icons.Rounded.Search) {},
                            SettingItem.Info("Sürüm", "0.8.0", Icons.Rounded.Search),
                        ),
                    ),
                ),
            )
        }
    }
}
