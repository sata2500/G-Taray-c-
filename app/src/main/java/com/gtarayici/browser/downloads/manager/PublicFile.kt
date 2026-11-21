package com.gtarayici.browser.downloads.manager

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel

/**
 * İndirilen dosyanın doğrudan yazıldığı "İndirilenler" kaydı. Android 10+ dosya, tamamlanana kadar diğer
 * uygulamalara görünmeyen (IS_PENDING) bir MediaStore kaydıdır; parçalar dosyadaki konumlarına doğrudan yazılır,
 * geçici bir kopya oluşmaz. Android 9'da uygulamanın kendi İndirilenler klasörü kullanılır.
 */
class PublicFile private constructor(
    private val context: Context,
    val uri: Uri?,
    private val file: File?,
    private val descriptor: ParcelFileDescriptor?,
    private val channel: FileChannel,
    private val requestedName: String,
) {
    val sink: DownloadSink = object : DownloadSink {
        override fun write(position: Long, buffer: ByteArray, length: Int) {
            val data = ByteBuffer.wrap(buffer, 0, length)
            var at = position
            while (data.hasRemaining()) at += channel.write(data, at)
        }
    }

    /** Sırayla yazmak için (HLS). */
    fun outputStream(): OutputStream = Channels.newOutputStream(channel.position(0))

    fun reset() {
        channel.truncate(0)
    }

    /** Dosyayı tamamlar ve görünür yapar; klasör yolu ile sistemin verdiği gerçek adı döndürür. */
    fun commit(): Pair<String, String> {
        channel.force(true)
        channel.close()
        descriptor?.close()
        val target = uri
        if (target != null) {
            val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(target, values, null, null)
            val saved = context.contentResolver.query(target, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: requestedName
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath to saved
        }
        return file!!.parentFile!!.absolutePath to file.name
    }

    /** Yarım kalan dosyayı siler. */
    fun discard() {
        runCatching { channel.close() }
        runCatching { descriptor?.close() }
        uri?.let { target -> runCatching { context.contentResolver.delete(target, null, null) } }
        file?.delete()
    }

    companion object {
        fun create(context: Context, name: String, mime: String?): PublicFile {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    mime?.substringBefore(';')?.trim()?.takeIf { it.contains('/') }?.let { put(MediaStore.Downloads.MIME_TYPE, it) }
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("İndirilenler klasöründe dosya oluşturulamadı")
                val descriptor = context.contentResolver.openFileDescriptor(uri, "rw") ?: error("Dosya açılamadı")
                val channel = FileOutputStream(descriptor.fileDescriptor).channel
                return PublicFile(context, uri, null, descriptor, channel, name)
            }
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            var target = File(dir, name)
            var n = 1
            while (target.exists()) {
                val base = name.substringBeforeLast('.', name)
                val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
                target = File(dir, "$base (${n++})$ext")
            }
            return PublicFile(context, null, target, null, RandomAccessFile(target, "rw").channel, target.name)
        }
    }
}
