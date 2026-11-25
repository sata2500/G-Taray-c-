package com.gtarayici.browser.vault

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

/** Oynatıcıya kasadaki dosyayı, istenen konumdan şifresini anlık çözerek veren kaynak. */
@UnstableApi
class VaultDataSource(private val vault: Vault) : BaseDataSource(false) {
    private var reader: VaultFormat.Reader? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        val id = dataSpec.uri.lastPathSegment ?: throw IOException("Geçersiz adres")
        transferInitializing(dataSpec)
        val opened = vault.reader(id)
        reader = opened
        uri = dataSpec.uri
        position = dataSpec.position
        if (position > opened.size) throw IOException("Konum dosyanın dışında")
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else opened.size - position
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val r = reader ?: return C.RESULT_END_OF_INPUT
        val n = r.read(position, buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (n <= 0) return C.RESULT_END_OF_INPUT
        position += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        val opened = reader
        reader = null
        uri = null
        if (opened != null) {
            runCatching { opened.close() }
            transferEnded()
        }
    }

    companion object {
        fun uri(id: String): Uri = Uri.parse("gtvault://item/$id")
    }
}
