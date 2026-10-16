package chat.simplex.common.views.vibe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Base64
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import chat.simplex.common.platform.androidAppContext
import java.io.ByteArrayOutputStream
import java.net.URI
import kotlin.math.max
import kotlin.math.roundToInt

actual fun vibePreviewBitmap(uri: URI, maxPx: Int): ImageBitmap? {
  val u = android.net.Uri.parse(uri.toString())
  val cr = androidAppContext.contentResolver
  if (Build.VERSION.SDK_INT >= 29) {
    runCatching { return cr.loadThumbnail(u, Size(maxPx, maxPx), null).asImageBitmap() }
  }
  if (Build.VERSION.SDK_INT >= 28) {
    runCatching {
      val src = ImageDecoder.createSource(cr, u)
      return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        val scale = maxPx.toFloat() / max(w, h)
        if (scale < 1f) decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
      }.asImageBitmap()
    }
  }
  return runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    cr.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
  }.getOrNull()
}

/**
 * A profile picture as big and sharp as SimpleX's ~12.5 KB limit allows:
 * a little more JPEG compression buys a lot more pixels, which matters when
 * the picture fills the top of a page.
 */
actual fun vibeEncodeAvatar(image: ImageBitmap, maxChars: Int): String {
  val src = image.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
  val png = src.hasAlpha()
  fun enc(b: Bitmap, q: Int): String {
    val out = ByteArrayOutputStream()
    b.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, q, out)
    return "data:image/${if (png) "png" else "jpg"};base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
  }
  val qualities = if (png) listOf(100) else listOf(72, 62, 52)
  var best: String? = null
  var bestSide = 0
  for (q in qualities) {
    var side = minOf(480, max(src.width, src.height))
    while (side >= 96) {
      val w = if (src.width >= src.height) side else (side * src.width.toFloat() / src.height).roundToInt()
      val h = if (src.height >= src.width) side else (side * src.height.toFloat() / src.width).roundToInt()
      val scaled = if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, max(1, w), max(1, h), true)
      val s = enc(scaled, q)
      if (s.length <= maxChars) {
        if (side > bestSide * 1.15) { best = s; bestSide = side }
        break
      }
      side = (side * 0.88f).toInt()
    }
  }
  return best ?: chat.simplex.common.platform.resizeImageToStrSize(image, maxChars.toLong())
}
