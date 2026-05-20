package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabs")
data class BrowserTab(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val isIncognito: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val fileName: String,
    val filePath: String,
    val mimeType: String = "video/mp4",
    val title: String,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: String = "PENDING", // PENDING, DOWNLOADING, COMPLETED, FAILED, PAUSED
    val progress: Int = 0,
    val speed: String = "0 KB/s",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "extensions")
data class BrowserExtension(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val scriptText: String,
    val isEnabled: Boolean = true,
    val isPredefined: Boolean = false
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val customDns: String = "Sistem", // Sistem, Cloudflare DNS, Google DNS, AdGuard, Custom
    val customDnsIp: String = "",
    val pinCode: String = "", // Used as password fallback for incognito and secret contents
    val isBiometricEnabled: Boolean = false,
    val isAppLocked: Boolean = false
)
