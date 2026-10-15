package chat.simplex.common.views.vibe

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import chat.simplex.common.model.CryptoFile
import chat.simplex.common.model.readCryptoFile
import chat.simplex.common.platform.CryptoMediaSource
import chat.simplex.common.platform.Log
import chat.simplex.common.platform.TAG
import chat.simplex.common.platform.getAppFilePath
import java.io.File
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Decodes the voice note and measures its loudness: the real waveform. */
actual fun vibeDecodeWaveform(fileSource: CryptoFile): List<Float>? {
  val path = if (fileSource.isAbsolutePath) fileSource.filePath else getAppFilePath(fileSource.filePath)
  if (!File(path).exists()) return null
  val ex = MediaExtractor()
  var codec: MediaCodec? = null
  return try {
    val args = fileSource.cryptoArgs
    if (args != null) ex.setDataSource(CryptoMediaSource(readCryptoFile(path, args))) else ex.setDataSource(path)
    val track = (0 until ex.trackCount).firstOrNull {
      ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    } ?: return null
    ex.selectTrack(track)
    val format = ex.getTrackFormat(track)
    val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
    val c = MediaCodec.createDecoderByType(mime)
    codec = c
    c.configure(format, null, null, 0)
    c.start()
    val info = MediaCodec.BufferInfo()
    val levels = ArrayList<Float>()
    var inDone = false
    var outDone = false
    var guard = 0
    while (!outDone && guard++ < 100_000) {
      if (!inDone) {
        val i = c.dequeueInputBuffer(10_000)
        if (i >= 0) {
          val buf = c.getInputBuffer(i)
          val n = if (buf == null) -1 else ex.readSampleData(buf, 0)
          if (n < 0) {
            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inDone = true
          } else {
            c.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
            ex.advance()
          }
        }
      }
      val o = c.dequeueOutputBuffer(info, 10_000)
      if (o >= 0) {
        val out = c.getOutputBuffer(o)
        if (out != null && info.size > 1) {
          out.position(info.offset)
          out.limit(info.offset + info.size)
          val sb = out.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
          val count = sb.remaining()
          var sum = 0.0
          while (sb.hasRemaining()) {
            val v = sb.get() / 32768.0
            sum += v * v
          }
          if (count > 0) levels.add(sqrt(sum / count).toFloat())
        }
        c.releaseOutputBuffer(o, false)
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
      }
    }
    levels.ifEmpty { null }
  } catch (e: Throwable) {
    Log.e(TAG, "Vibe waveform: ${e.message}")
    null
  } finally {
    runCatching { codec?.stop() }
    runCatching { codec?.release() }
    runCatching { ex.release() }
  }
}
