package chat.simplex.common.views.vibe

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import chat.simplex.common.model.ChatItem
import chat.simplex.common.model.MsgContent
import chat.simplex.common.model.decryptCryptoFile
import chat.simplex.common.platform.*
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

actual fun vibeSaveToGallery(item: ChatItem) {
  val mc = item.content.msgContent
  val video = mc is MsgContent.MCVideo
  if (mc !is MsgContent.MCImage && !video) return
  if (item.chatDir.sent) return
  val file = item.file ?: return
  val path = getLoadedFilePath(file) ?: return
  val name = file.fileName
  val resolver = androidAppContext.contentResolver
  val values = ContentValues().apply {
    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
    put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else imageMimeType(name))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Vibe X")
      put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
  }
  val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
  val uri = resolver.insert(collection, values) ?: return
  try {
    resolver.openOutputStream(uri)?.use { out ->
      val args = file.fileSource?.cryptoArgs
      if (args != null) {
        val tmp = File.createTempFile("vibe-save", ".tmp", tmpDir)
        try {
          decryptCryptoFile(path, args, tmp.absolutePath)
          tmp.inputStream().use { it.copyTo(out) }
        } finally {
          tmp.delete()
        }
      } else {
        File(path).inputStream().use { it.copyTo(out) }
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }
  } catch (e: Throwable) {
    Log.e(TAG, "Vibe save to gallery: ${e.message}")
    runCatching { resolver.delete(uri, null, null) }
  }
}

// the app's cache (emoji animations live in it) and downloaded updates
actual fun vibeCacheDirs(): List<File> = listOf(androidAppContext.cacheDir, File(androidAppContext.filesDir, "vibe-update"))

private val crashFile: File get() = File(androidAppContext.filesDir, "vibe-crash.txt")
private const val SEEN_EXIT = "vibe_seen_exit"
private var watching = false

actual fun vibeWatchCrashes() {
  if (watching) return
  watching = true
  val previous = Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler { thread, e ->
    runCatching {
      val sw = StringWriter()
      e.printStackTrace(PrintWriter(sw))
      crashFile.writeText(
        "Vibe X ${VibeUpdater.versionLabel} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "Thread: ${thread.name}\n\n" + sw.toString().take(12_000)
      )
    }
    previous?.uncaughtException(thread, e)
  }
}

actual fun vibeTakeCrashReport(): String? {
  // a Kotlin / Java crash we caught ourselves
  if (crashFile.exists()) {
    val text = runCatching { crashFile.readText() }.getOrNull()
    crashFile.delete()
    if (!text.isNullOrBlank()) return text
  }
  // otherwise ask Android how the app ended last time (native crashes, freezes)
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
  return runCatching {
    val am = androidAppContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val last = am.getHistoricalProcessExitReasons(null, 0, 1).firstOrNull() ?: return null
    val prefs = androidAppContext.getSharedPreferences("vibe", Context.MODE_PRIVATE)
    if (prefs.getLong(SEEN_EXIT, 0L) >= last.timestamp) return null
    prefs.edit().putLong(SEEN_EXIT, last.timestamp).apply()
    val kind = when (last.reason) {
      ApplicationExitInfo.REASON_CRASH -> "crash"
      ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
      ApplicationExitInfo.REASON_ANR -> "freeze (not responding)"
      else -> return null
    }
    val trace = runCatching { last.traceInputStream?.bufferedReader()?.use { it.readText().take(12_000) } }.getOrNull()
    "Vibe X ${VibeUpdater.versionLabel} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
        "Last time: $kind at ${java.util.Date(last.timestamp)}\n${last.description ?: ""}\n\n${trace ?: ""}"
  }.getOrNull()
}
