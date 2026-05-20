package com.example.data

import kotlinx.coroutines.flow.Flow

class BrowserRepository(private val dao: BrowserDao) {

    // --- TABS ---
    fun getAllTabs(): Flow<List<BrowserTab>> = dao.getAllTabs()
    
    fun getTabsScoped(isIncognito: Boolean): Flow<List<BrowserTab>> = dao.getTabsScoped(isIncognito)

    suspend fun insertTab(tab: BrowserTab): Long = dao.insertTab(tab)

    suspend fun updateTab(tab: BrowserTab) = dao.updateTab(tab)

    suspend fun deleteTab(tab: BrowserTab) = dao.deleteTab(tab)

    suspend fun clearIncognitoTabs() = dao.clearIncognitoTabs()

    // --- HISTORY ---
    val allHistory: Flow<List<HistoryItem>> = dao.getAllHistory()

    suspend fun insertHistory(item: HistoryItem) = dao.insertHistory(item)

    suspend fun deleteHistory(id: Int) = dao.deleteHistory(id)

    suspend fun clearAllHistory() = dao.clearAllHistory()

    // --- BOOKMARKS ---
    val allBookmarks: Flow<List<Bookmark>> = dao.getAllBookmarks()

    suspend fun insertBookmark(bookmark: Bookmark) = dao.insertBookmark(bookmark)

    suspend fun deleteBookmarkByUrl(url: String) = dao.deleteBookmarkByUrl(url)

    fun isBookmarked(url: String): Flow<Boolean> = dao.isBookmarked(url)

    // --- DOWNLOADS ---
    val allDownloads: Flow<List<DownloadItem>> = dao.getAllDownloads()

    suspend fun getDownloadById(id: Int): DownloadItem? = dao.getDownloadById(id)

    suspend fun insertDownload(download: DownloadItem): Long = dao.insertDownload(download)

    suspend fun updateDownload(download: DownloadItem) = dao.updateDownload(download)

    suspend fun deleteDownload(id: Int) = dao.deleteDownload(id)

    // --- EXTENSIONS ---
    val allExtensions: Flow<List<BrowserExtension>> = dao.getAllExtensions()

    suspend fun insertExtension(extension: BrowserExtension): Long = dao.insertExtension(extension)

    suspend fun updateExtension(extension: BrowserExtension) = dao.updateExtension(extension)

    suspend fun deleteExtension(extension: BrowserExtension) = dao.deleteExtension(extension)

    // --- APP SETTINGS ---
    val appSettings: Flow<AppSettings?> = dao.getSettings()

    suspend fun getSettingsDirect(): AppSettings? = dao.getSettingsDirect()

    suspend fun updateSettings(settings: AppSettings) = dao.insertSettings(settings)
}
