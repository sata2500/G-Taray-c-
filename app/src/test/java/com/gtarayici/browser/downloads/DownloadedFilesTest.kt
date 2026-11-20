package com.gtarayici.browser.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadedFilesTest {
    private val root = "/storage/emulated/0"

    @Test
    fun relativePathOfSharedFolders() {
        assertEquals("Download/", DownloadedFiles.relativePath("/storage/emulated/0/Download", root))
        assertEquals("Download/", DownloadedFiles.relativePath("/storage/emulated/0/Download/", root))
        assertEquals("Download/Telegram/", DownloadedFiles.relativePath("/storage/emulated/0/Download/Telegram", "$root/"))
    }

    @Test
    fun outsideSharedStorageIsNull() {
        assertNull(DownloadedFiles.relativePath("/data/user/0/com.gtarayici.browser/files", root))
        assertNull(DownloadedFiles.relativePath("/storage/emulated/0", root))
        assertNull(DownloadedFiles.relativePath("/storage/emulated/01/Download", root))
    }
}
