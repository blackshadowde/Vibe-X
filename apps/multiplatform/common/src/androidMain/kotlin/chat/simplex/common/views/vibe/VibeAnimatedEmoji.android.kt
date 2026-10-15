package chat.simplex.common.views.vibe

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import chat.simplex.common.platform.androidAppContext
import chat.simplex.common.platform.appPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private object NotoCache {
  val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()
  val locks = ConcurrentHashMap<String, Mutex>()
  val dir: File by lazy { File(androidAppContext.cacheDir, "noto").apply { mkdirs() } }

  /** The animation file for [emoji], downloading it once; null if there's none. */
  suspend fun file(emoji: String): File? {
    for (code in notoCodes(emoji)) {
      if (code in missing) continue
      val f = File(dir, "$code.webp")
      if (f.length() > 0) return f
      val lock = locks.getOrPut(code) { Mutex() }
      val got = lock.withLock {
        if (f.length() > 0) return@withLock f
        try {
          val c = URL("https://fonts.gstatic.com/s/e/notoemoji/latest/$code/512.webp").openConnection() as HttpURLConnection
          c.connectTimeout = 8000
          c.readTimeout = 15000
          if (c.responseCode == 200) {
            val tmp = File(dir, "$code.part")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (tmp.length() > 0) tmp.renameTo(f)
            f.takeIf { it.length() > 0 }
          } else {
            if (c.responseCode == 404) missing.add(code)
            null
          }
        } catch (e: Exception) {
          null
        }
      }
      if (got != null) return got
    }
    return null
  }
}

@Composable
actual fun VibeAnimatedEmoji(emoji: String, size: Dp, modifier: Modifier) {
  var drawable by remember(emoji) { mutableStateOf<Drawable?>(null) }
  val proxied = appPreferences.networkUseSocksProxy.get() || appPreferences.vibeLowData.state.value
  LaunchedEffect(emoji, proxied) {
    if (Build.VERSION.SDK_INT < 28 || proxied) return@LaunchedEffect
    drawable = withContext(Dispatchers.IO) {
      try {
        val f = NotoCache.file(emoji) ?: return@withContext null
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(f))
      } catch (e: Throwable) {
        null
      }
    }
  }
  val d = drawable
  if (d == null) {
    VibePlainEmoji(emoji, size, modifier)
    return
  }
  AndroidView(
    factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
    update = { iv ->
      if (iv.drawable !== d) {
        iv.setImageDrawable(d)
        if (Build.VERSION.SDK_INT >= 28) (d as? AnimatedImageDrawable)?.apply { repeatCount = AnimatedImageDrawable.REPEAT_INFINITE; start() }
      }
    },
    modifier = modifier.size(size)
  )
}
