package com.gtarayici.browser.tabs

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flow
import org.json.JSONArray
import org.json.JSONObject

data class TabGroup(
    val id: String,
    val name: String,
    val color: Int,
    /** Grubun ait olduğu profil (GeckoView bağlam kimliği); varsayılan profil için null. */
    val contextId: String?,
    val tabIds: List<String>,
    val collapsed: Boolean = false,
)

/**
 * Adlandırılmış, renkli sekme grupları. Gruplar profile özeldir ve uygulama yeniden açıldığında
 * korunur (sekmeler kimlikleriyle geri yüklenir). Kapatılan sekmeler gruplardan kendiliğinden çıkar,
 * boşalan gruplar silinir. Gruptaki bir sekmeden açılan bağlantılar aynı gruba katılır.
 */
class TabGroups(context: Context, private val store: BrowserStore, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "tab_groups.json")
    private val _groups = MutableStateFlow(load())
    val groups: StateFlow<List<TabGroup>> = _groups.asStateFlow()

    /** Sekmeler geri yüklendikten sonra çağrılır; aksi halde açılışta tüm gruplar boş görünüp silinirdi. */
    fun start() {
        scope.launch {
            store.flow()
                .map { state -> state.tabs.map { it.id to it.parentId } }
                .distinctUntilChanged()
                .collect { tabs -> onTabsChanged(tabs) }
        }
    }

    fun groupOf(tabId: String): TabGroup? = _groups.value.firstOrNull { tabId in it.tabIds }

    fun create(name: String, color: Int, tab: TabSessionState): TabGroup {
        val group = TabGroup(UUID.randomUUID().toString(), name.trim(), color, tab.contextId, listOf(tab.id))
        update { groups -> groups.map { it.without(tab.id) } + group }
        return group
    }

    fun addTab(groupId: String, tabId: String) = update { groups ->
        groups.map { group ->
            if (group.id == groupId) group.copy(tabIds = (group.tabIds - tabId) + tabId) else group.without(tabId)
        }
    }

    fun removeTab(tabId: String) = update { groups -> groups.map { it.without(tabId) } }

    fun rename(groupId: String, name: String) = edit(groupId) { it.copy(name = name.trim()) }

    fun setColor(groupId: String, color: Int) = edit(groupId) { it.copy(color = color) }

    fun toggleCollapsed(groupId: String) = edit(groupId) { it.copy(collapsed = !it.collapsed) }

    /** Grubu dağıtır; sekmeler açık kalır. */
    fun ungroup(groupId: String) = update { groups -> groups.filterNot { it.id == groupId } }

    private fun edit(groupId: String, change: (TabGroup) -> TabGroup) = update { groups ->
        groups.map { if (it.id == groupId) change(it) else it }
    }

    private fun onTabsChanged(tabs: List<Pair<String, String?>>) {
        val open = tabs.map { it.first }.toSet()
        update { groups ->
            var result = groups.map { group -> group.copy(tabIds = group.tabIds.filter { it in open }) }
            // Gruptaki bir sekmeden açılan yeni sekmeler aynı gruba eklenir.
            tabs.forEach { (id, parent) ->
                if (parent == null || result.any { id in it.tabIds }) return@forEach
                val parentGroup = result.firstOrNull { parent in it.tabIds } ?: return@forEach
                result = result.map { if (it.id == parentGroup.id) it.copy(tabIds = it.tabIds + id) else it }
            }
            result.filter { it.tabIds.isNotEmpty() }
        }
    }

    private fun update(change: (List<TabGroup>) -> List<TabGroup>) {
        val next = change(_groups.value)
        if (next == _groups.value) return
        _groups.value = next
        val json = serialize(next)
        scope.launch(Dispatchers.IO) { runCatching { file.writeText(json) } }
    }

    private fun TabGroup.without(tabId: String) = if (tabId in tabIds) copy(tabIds = tabIds - tabId) else this

    private fun load(): List<TabGroup> = runCatching { if (file.exists()) parse(file.readText()) else emptyList() }
        .getOrDefault(emptyList())

    companion object {
        /** Grup renkleri (açık ve koyu temada okunabilir tonlar). */
        val COLORS = listOf(
            0xFF1A73E8, 0xFFD93025, 0xFFF9AB00, 0xFF188038,
            0xFFD01884, 0xFF9334E6, 0xFF12B5CB, 0xFFE8710A,
        ).map { it.toInt() }

        internal fun serialize(groups: List<TabGroup>): String = JSONArray().apply {
            groups.forEach { group ->
                put(
                    JSONObject()
                        .put("id", group.id)
                        .put("name", group.name)
                        .put("color", group.color)
                        .put("contextId", group.contextId ?: JSONObject.NULL)
                        .put("collapsed", group.collapsed)
                        .put("tabs", JSONArray(group.tabIds)),
                )
            }
        }.toString()

        internal fun parse(text: String): List<TabGroup> {
            val array = JSONArray(text)
            return (0 until array.length()).map { index ->
                val o = array.getJSONObject(index)
                val tabs = o.getJSONArray("tabs")
                TabGroup(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    color = o.getInt("color"),
                    contextId = if (o.isNull("contextId")) null else o.getString("contextId"),
                    tabIds = (0 until tabs.length()).map { tabs.getString(it) },
                    collapsed = o.optBoolean("collapsed", false),
                )
            }
        }
    }
}
