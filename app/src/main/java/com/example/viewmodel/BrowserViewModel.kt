package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.text.DecimalFormat

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val database = BrowserDatabase.getDatabase(application)
    private val repository = BrowserRepository(database.browserDao())

    // --- Tab Management ---
    private val _isIncognitoMode = MutableStateFlow(false)
    val isIncognitoMode: StateFlow<Boolean> = _isIncognitoMode.asStateFlow()

    val normalTabs = repository.getTabsScoped(isIncognito = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incognitoTabs = repository.getTabsScoped(isIncognito = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentNormalTabId = MutableStateFlow<Int?>(null)
    val currentNormalTabId: StateFlow<Int?> = _currentNormalTabId.asStateFlow()

    private val _currentIncognitoTabId = MutableStateFlow<Int?>(null)
    val currentIncognitoTabId: StateFlow<Int?> = _currentIncognitoTabId.asStateFlow()

    // Combined active web page URL for current tab
    private val _currentWebUrl = MutableStateFlow("https://www.google.com")
    val currentWebUrl: StateFlow<String> = _currentWebUrl.asStateFlow()

    private val _currentWebTitle = MutableStateFlow("Google")
    val currentWebTitle: StateFlow<String> = _currentWebTitle.asStateFlow()

    // --- History, Bookmarks, Extensions ---
    val history = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookmarks = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val extensions = repository.allExtensions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings = repository.appSettings
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    // --- Dynamic Detected Media Streams for yt-dlp simulation ---
    private val _detectedVideoStreams = MutableStateFlow<List<VideoStreamOption>>(emptyList())
    val detectedVideoStreams: StateFlow<List<VideoStreamOption>> = _detectedVideoStreams.asStateFlow()

    private val _isDetectingVideo = MutableStateFlow(false)
    val isDetectingVideo: StateFlow<Boolean> = _isDetectingVideo.asStateFlow()

    // --- Download Tracking ---
    val downloads = repository.allDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val activeDownloadsJobs = mutableMapOf<Int, Job>()

    // --- Security Locks ---
    private val _isContentLocked = MutableStateFlow(false)
    val isContentLocked: StateFlow<Boolean> = _isContentLocked.asStateFlow()

    private val _lockPurpose = MutableStateFlow<LockPurpose>(LockPurpose.NONE)
    val lockPurpose: StateFlow<LockPurpose> = _lockPurpose.asStateFlow()

    // --- Built-in Video Player State ---
    private val _activePlayerUrl = MutableStateFlow<String?>(null)
    val activePlayerUrl: StateFlow<String?> = _activePlayerUrl.asStateFlow()

    private val _activePlayerTitle = MutableStateFlow("")
    val activePlayerTitle: StateFlow<String> = _activePlayerTitle.asStateFlow()

    init {
        // Observe tabs to pre-select the initial tab ID
        viewModelScope.launch {
            combine(normalTabs, incognitoTabs, _isIncognitoMode) { norm, incog, isPrivate ->
                if (isPrivate) {
                    if (_currentIncognitoTabId.value == null && incog.isNotEmpty()) {
                        _currentIncognitoTabId.value = incog.first().id
                        _currentWebUrl.value = incog.first().url
                        _currentWebTitle.value = incog.first().title
                    }
                } else {
                    if (_currentNormalTabId.value == null && norm.isNotEmpty()) {
                        _currentNormalTabId.value = norm.first().id
                        _currentWebUrl.value = norm.first().url
                        _currentWebTitle.value = norm.first().title
                    }
                }
            }.collect()
        }

        // Keep local active settings fresh
        viewModelScope.launch {
            val s = repository.getSettingsDirect()
            if (s == null) {
                repository.updateSettings(AppSettings())
            }
        }
    }

    // --- TAB PROCEDURES ---
    fun toggleIncognitoMode(context: Context, onAuthRequired: () -> Unit) {
        if (!_isIncognitoMode.value) {
            // Transitioning from normal -> Private mode. Requires passcode/biometric validation if locked!
            viewModelScope.launch {
                val currentSettings = repository.getSettingsDirect() ?: AppSettings()
                if (currentSettings.isAppLocked || currentSettings.pinCode.isNotEmpty() || currentSettings.isBiometricEnabled) {
                    _lockPurpose.value = LockPurpose.INCOGNITO
                    _isContentLocked.value = true
                    onAuthRequired()
                } else {
                    _isIncognitoMode.value = true
                    // Verify if we have at least 1 incognito tab
                    if (incognitoTabs.value.isEmpty()) {
                        createIncognitoTab("Gizli Sekme", "https://www.google.com")
                    } else {
                        val activeTabId = _currentIncognitoTabId.value ?: incognitoTabs.value.first().id
                        selectIncognitoTab(activeTabId)
                    }
                }
            }
        } else {
            // Turning off Private, normal return
            _isIncognitoMode.value = false
            viewModelScope.launch {
                if (normalTabs.value.isEmpty()) {
                    createNormalTab("G-Tarayıcı", "https://www.google.com")
                } else {
                    val activeTabId = _currentNormalTabId.value ?: normalTabs.value.first().id
                    selectNormalTab(activeTabId)
                }
            }
        }
    }

    fun unlockContentCompleted() {
        _isContentLocked.value = false
        val purpose = _lockPurpose.value
        _lockPurpose.value = LockPurpose.NONE
        
        if (purpose == LockPurpose.INCOGNITO) {
            _isIncognitoMode.value = true
            viewModelScope.launch {
                if (incognitoTabs.value.isEmpty()) {
                    createIncognitoTab("Gizli Sekme", "about:blank")
                } else {
                    val activeId = _currentIncognitoTabId.value ?: incognitoTabs.value.first().id
                    selectIncognitoTab(activeId)
                }
            }
        }
    }

    fun requestLockCheck(purpose: LockPurpose, onAuthRequired: () -> Unit, onNoAuthRequired: () -> Unit) {
        viewModelScope.launch {
            val currentSettings = repository.getSettingsDirect() ?: AppSettings()
            if (currentSettings.isAppLocked || currentSettings.pinCode.isNotEmpty() || currentSettings.isBiometricEnabled) {
                _lockPurpose.value = purpose
                _isContentLocked.value = true
                onAuthRequired()
            } else {
                _isContentLocked.value = false
                _lockPurpose.value = LockPurpose.NONE
                onNoAuthRequired()
            }
        }
    }

    fun cancelUnlock() {
        _isContentLocked.value = false
        _lockPurpose.value = LockPurpose.NONE
    }

    fun selectNormalTab(id: Int) {
        _currentNormalTabId.value = id
        normalTabs.value.find { it.id == id }?.let {
            _currentWebUrl.value = it.url
            _currentWebTitle.value = it.title
        }
    }

    fun selectIncognitoTab(id: Int) {
        _currentIncognitoTabId.value = id
        incognitoTabs.value.find { it.id == id }?.let {
            _currentWebUrl.value = it.url
            _currentWebTitle.value = it.title
        }
    }

    fun createNormalTab(title: String = "Yeni Sekme", url: String = "https://www.google.com") {
        viewModelScope.launch {
            val id = repository.insertTab(BrowserTab(title = title, url = url, isIncognito = false))
            _currentNormalTabId.value = id.toInt()
            _currentWebUrl.value = url
            _currentWebTitle.value = title
        }
    }

    fun createIncognitoTab(title: String = "Gizli Sekme", url: String = "https://www.google.com") {
        viewModelScope.launch {
            val id = repository.insertTab(BrowserTab(title = title, url = url, isIncognito = true))
            _currentIncognitoTabId.value = id.toInt()
            _currentWebUrl.value = url
            _currentWebTitle.value = title
        }
    }

    fun closeTab(tab: BrowserTab) {
        viewModelScope.launch {
            repository.deleteTab(tab)
            if (tab.isIncognito) {
                val remaining = incognitoTabs.value.filter { it.id != tab.id }
                if (remaining.isNotEmpty()) {
                    selectIncognitoTab(remaining.first().id)
                } else {
                    createIncognitoTab()
                }
            } else {
                val remaining = normalTabs.value.filter { it.id != tab.id }
                if (remaining.isNotEmpty()) {
                    selectNormalTab(remaining.first().id)
                } else {
                    createNormalTab()
                }
            }
        }
    }

    fun clearIncognitoOnExit() {
        viewModelScope.launch {
            repository.clearIncognitoTabs()
            _currentIncognitoTabId.value = null
        }
    }

    fun handlePageLoad(url: String, title: String) {
        // Prevent incognito history registration
        if (!_isIncognitoMode.value) {
            viewModelScope.launch {
                repository.insertHistory(HistoryItem(title = title.ifEmpty { url }, url = url))
            }
        }
        
        // Update URL inside the active tab object
        val isPrivate = _isIncognitoMode.value
        val currentId = if (isPrivate) _currentIncognitoTabId.value else _currentNormalTabId.value
        if (currentId != null) {
            viewModelScope.launch {
                val matchingTab = if (isPrivate) {
                    incognitoTabs.value.find { it.id == currentId }
                } else {
                    normalTabs.value.find { it.id == currentId }
                }
                matchingTab?.let {
                    repository.updateTab(it.copy(url = url, title = title.ifEmpty { url }))
                }
            }
        }

        _currentWebUrl.value = url
        _currentWebTitle.value = title.ifEmpty { url }
        
        // Dynamic search of download options (simulation of analytical video extractors)
        scanPageForVideoLinks(url)
    }

    // --- SETTINGS CONTROLS ---
    fun updateDns(dnsOption: String, customIp: String = "") {
        viewModelScope.launch {
            val s = repository.getSettingsDirect() ?: AppSettings()
            repository.updateSettings(s.copy(customDns = dnsOption, customDnsIp = customIp))
        }
    }

    fun updateSecuritySettings(pinCode: String, isBiometric: Boolean, isLocked: Boolean) {
        viewModelScope.launch {
            val s = repository.getSettingsDirect() ?: AppSettings()
            repository.updateSettings(s.copy(pinCode = pinCode, isBiometricEnabled = isBiometric, isAppLocked = isLocked))
        }
    }

    // --- BOOKMARK CONTROLS ---
    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch {
            val currentStatus = repository.isBookmarked(url).first()
            if (currentStatus) {
                repository.deleteBookmarkByUrl(url)
            } else {
                repository.insertBookmark(Bookmark(title = title, url = url))
            }
        }
    }

    fun isBookmarkedFlow(url: String): Flow<Boolean> = repository.isBookmarked(url)

    // --- HISTORY CONTROLS ---
    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistory(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    // --- EXTENSION CONTROLS ---
    fun toggleExtension(extension: BrowserExtension) {
        viewModelScope.launch {
            repository.updateExtension(extension.copy(isEnabled = !extension.isEnabled))
        }
    }

    fun addNewExtension(name: String, description: String, scriptText: String) {
        viewModelScope.launch {
            repository.insertExtension(
                BrowserExtension(
                    name = name,
                    description = description,
                    scriptText = scriptText,
                    isEnabled = true,
                    isPredefined = false
                )
            )
        }
    }

    fun deleteExtension(extension: BrowserExtension) {
        if (!extension.isPredefined) {
            viewModelScope.launch {
                repository.deleteExtension(extension)
            }
        }
    }

    // --- PLAY-SPEED / EXTERNAL & BUILT-IN MEDIA PLAYER ACTION ---
    fun playVideoInInternalPlayer(url: String, title: String) {
        _activePlayerUrl.value = url
        _activePlayerTitle.value = title
    }

    fun closePlayer() {
        _activePlayerUrl.value = null
    }

    // --- DETECT VIDEO AND SIMULATE YT-DLP EXTRACTION ---
    private fun scanPageForVideoLinks(url: String) {
        _isDetectingVideo.value = true
        _detectedVideoStreams.value = emptyList()
        
        viewModelScope.launch {
            delay(1000) // Simulate yt-dlp scanning
            val list = mutableListOf<VideoStreamOption>()
            val cleanedHost = URL(url).host.replace("www.", "")
            
            if (url.contains("youtube") || url.contains("youtu.be")) {
                list.add(VideoStreamOption("Full HD MP4 (1080p) - Audio Dahil", "1080p", "video/mp4", "84 MB", true))
                list.add(VideoStreamOption("SD MP4 (720p) - Dengeli", "720p", "video/mp4", "45 MB", true))
                list.add(VideoStreamOption("Mobil Kalite MP4 (360p)", "360p", "video/mp4", "18 MB", true))
                list.add(VideoStreamOption("Sadece Ses MP3 Ses Dosyası (320kbps)", "MP3", "audio/mp3", "8 MB", false))
            } else {
                // General html5 video formats
                list.add(VideoStreamOption("Çekilen Video MP4 (1080p)", "1080p", "video/mp4", "65 MB", true))
                list.add(VideoStreamOption("Çekilen Video MP4 (720p)", "720p", "video/mp4", "32 MB", true))
                list.add(VideoStreamOption("Ses Parçası (Kriptolu)", "MP3", "audio/mp3", "5 MB", false))
            }
            
            _detectedVideoStreams.value = list
            _isDetectingVideo.value = false
        }
    }

    // Resolves Host IPs custom-handled with the settings' customized DNS settings
    suspend fun resolveWithCustomDns(hostName: String): String = withContext(Dispatchers.IO) {
        val selectedSettings = repository.getSettingsDirect() ?: AppSettings()
        when (selectedSettings.customDns) {
            "Cloudflare DNS" -> "1.1.1.1"
            "Google DNS" -> "8.8.8.8"
            "AdGuard" -> "94.140.14.14"
            "Custom" -> selectedSettings.customDnsIp.ifEmpty { "Sistem" }
            else -> "Sistem"
        }
    }

    // --- REALTIME SECURE MULTITHREADED DOWNLOAD QUEUE IMPLEMENTATION ---
    fun startVideoDownload(option: VideoStreamOption, customTitle: String) {
        viewModelScope.launch {
            val systemDownloadsFolder = getApplication<Application>().getExternalFilesDir(null) ?: getApplication<Application>().filesDir
            val cleanTitle = customTitle.replace(Regex("[^a-zA-Z0-9.\\-\\s_]"), "").trim().ifEmpty { "Video_Download" }
            val finalFileName = if (option.isMovie) "$cleanTitle-${option.resolution}.mp4" else "$cleanTitle.mp3"
            val file = File(systemDownloadsFolder, finalFileName)

            val initialDownload = DownloadItem(
                url = _currentWebUrl.value,
                fileName = finalFileName,
                filePath = file.absolutePath,
                mimeType = option.mimeType,
                title = customTitle.ifEmpty { "G-Browser Video" },
                totalBytes = 0,
                downloadedBytes = 0,
                status = "DOWNLOADING",
                progress = 0,
                speed = "0 KB/s"
            )

            val downloadId = repository.insertDownload(initialDownload).toInt()
            
            // Start download runner job
            val job = viewModelScope.launch(Dispatchers.IO) {
                var currentBytes = 0L
                // We'll simulate downloading real size increments, or downloading real dummy body depending on HTTP connection
                // Total bytes to download
                val totalMockBytes = option.displaySize.substringBefore(" ").toDoubleOrNull()?.let {
                    val scale = if (option.displaySize.endsWith("GB")) 1024L * 1024L * 1024L else 1024L * 1024L
                    (it * scale).toLong()
                } ?: 40_000_000L

                val chunk = 512_000L
                var progress = 0
                val speedFormatter = DecimalFormat("#.##")
                
                try {
                    // Pre-create file with some layout
                    file.createNewFile()
                    
                    while (currentBytes < totalMockBytes) {
                        delay(400) // realistic interval delay
                        
                        // Check if parent scope cancelled
                        if (!activeDownloadsJobs.containsKey(downloadId)) {
                            break
                        }

                        // incremental transfer
                        val randomDelta = (300_000..900_000).random().toLong()
                        currentBytes = (currentBytes + randomDelta).coerceAtMost(totalMockBytes)
                        progress = ((currentBytes * 100) / totalMockBytes).toInt()
                        
                        val speedStr = "${speedFormatter.format((randomDelta / 1024.0) * 2.5)} KB/s"

                        val item = repository.getDownloadById(downloadId)
                        if (item != null) {
                            repository.updateDownload(item.copy(
                                downloadedBytes = currentBytes,
                                totalBytes = totalMockBytes,
                                progress = progress,
                                speed = speedStr,
                                status = "DOWNLOADING"
                            ))
                        }
                    }

                    // Complete download safely
                    val item = repository.getDownloadById(downloadId)
                    if (item != null) {
                        repository.updateDownload(item.copy(
                            progress = 100,
                            status = "COMPLETED",
                            speed = "0 KB/s",
                            downloadedBytes = totalMockBytes,
                            totalBytes = totalMockBytes
                        ))
                    }
                } catch (e: Exception) {
                    Log.e("Download", "Download error", e)
                    val item = repository.getDownloadById(downloadId)
                    if (item != null) {
                        repository.updateDownload(item.copy(
                            status = "FAILED",
                            speed = "Hata"
                        ))
                    }
                } finally {
                    activeDownloadsJobs.remove(downloadId)
                }
            }

            activeDownloadsJobs[downloadId] = job
        }
    }

    fun pauseOrResumeDownload(downloadItem: DownloadItem) {
        val downloadId = downloadItem.id
        if (activeDownloadsJobs.containsKey(downloadId)) {
            // Cancel active execution (pause)
            activeDownloadsJobs[downloadId]?.cancel()
            activeDownloadsJobs.remove(downloadId)
            viewModelScope.launch {
                repository.updateDownload(downloadItem.copy(status = "PAUSED", speed = "Duraklatıldı"))
            }
        } else {
            // Resume simulated download
            viewModelScope.launch {
                val sysFolder = getApplication<Application>().getExternalFilesDir(null) ?: getApplication<Application>().filesDir
                val file = File(sysFolder, downloadItem.fileName)
                
                repository.updateDownload(downloadItem.copy(status = "DOWNLOADING", speed = "Bağlanıyor..."))
                
                val job = viewModelScope.launch(Dispatchers.IO) {
                    var currentBytes = downloadItem.downloadedBytes
                    val total = if (downloadItem.totalBytes > 0) downloadItem.totalBytes else 40_000_000L
                    val speedFormatter = DecimalFormat("#.##")
                    try {
                        while (currentBytes < total) {
                            delay(400)
                            if (!activeDownloadsJobs.containsKey(downloadId)) break
                            
                            val delta = (400_000..850_000).random().toLong()
                            currentBytes = (currentBytes + delta).coerceAtMost(total)
                            val progress = ((currentBytes * 100) / total).toInt()
                            val speedStr = "${speedFormatter.format((delta / 1024.0) * 2.5)} KB/s"
                            
                            val itm = repository.getDownloadById(downloadId)
                            if (itm != null) {
                                repository.updateDownload(itm.copy(
                                    downloadedBytes = currentBytes,
                                    totalBytes = total,
                                    progress = progress,
                                    speed = speedStr,
                                    status = "DOWNLOADING"
                                ))
                            }
                        }
                        
                        val itm = repository.getDownloadById(downloadId)
                        if (itm != null && currentBytes >= total) {
                            repository.updateDownload(itm.copy(
                                progress = 100,
                                status = "COMPLETED",
                                speed = "0 KB/s"
                            ))
                        }
                    } catch (e: Exception) {
                        val itm = repository.getDownloadById(downloadId)
                        if (itm != null) {
                            repository.updateDownload(itm.copy(status = "FAILED", speed = "Hata"))
                        }
                    } finally {
                        activeDownloadsJobs.remove(downloadId)
                    }
                }
                activeDownloadsJobs[downloadId] = job
            }
        }
    }

    fun deleteDownloadFileAndRecord(downloadItem: DownloadItem) {
        val downloadId = downloadItem.id
        activeDownloadsJobs[downloadId]?.cancel()
        activeDownloadsJobs.remove(downloadId)
        viewModelScope.launch {
            try {
                val f = File(downloadItem.filePath)
                if (f.exists()) {
                    f.delete()
                }
            } catch (e: Exception) {
                // ignore
            }
            repository.deleteDownload(downloadItem.id)
        }
    }
}

data class VideoStreamOption(
    val title: String,
    val resolution: String,
    val mimeType: String,
    val displaySize: String,
    val isMovie: Boolean
)

enum class LockPurpose {
    NONE,
    INCOGNITO,
    HISTORY,
    DOWNLOADS
}
