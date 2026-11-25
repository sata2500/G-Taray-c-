package com.gtarayici.browser.media

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** Ayrı indirilen görüntü ve ses izlerini yeniden kodlamadan tek bir MP4'te birleştirir. */
object TrackMuxer {
    @SuppressLint("WrongConstant")
    fun mux(videoFile: File, audioFile: File, output: File) {
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val extractors = mutableListOf<Pair<MediaExtractor, Int>>()
        try {
            listOf(videoFile to "video/", audioFile to "audio/").forEach { (file, prefix) ->
                val extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
                extractors += extractor to -1
                val track = (0 until extractor.trackCount).first {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(prefix)
                }
                extractor.selectTrack(track)
                extractors[extractors.lastIndex] = extractor to muxer.addTrack(extractor.getTrackFormat(track))
            }
            muxer.start()
            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            extractors.forEach { (extractor, outTrack) ->
                while (true) {
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    info.set(0, size, extractor.sampleTime, flags)
                    muxer.writeSampleData(outTrack, buffer, info)
                    extractor.advance()
                }
            }
            muxer.stop()
        } finally {
            extractors.forEach { runCatching { it.first.release() } }
            runCatching { muxer.release() }
        }
    }
}
