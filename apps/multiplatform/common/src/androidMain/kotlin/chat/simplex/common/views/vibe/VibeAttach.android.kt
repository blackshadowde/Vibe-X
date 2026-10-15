package chat.simplex.common.views.vibe

import android.Manifest
import android.content.ContentUris
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import chat.simplex.common.platform.androidAppContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

private val thumbs = LruCache<Long, Bitmap>(160)

private fun permissions(): List<String> =
  if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
  else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

/** Newest photos and videos first. */
private fun queryRecent(limit: Int): List<VibeMediaItem> {
  val out = mutableListOf<VibeMediaItem>()
  val uri = MediaStore.Files.getContentUri("external")
  val projection = arrayOf(
    MediaStore.Files.FileColumns._ID,
    MediaStore.Files.FileColumns.MEDIA_TYPE,
    MediaStore.Video.VideoColumns.DURATION,
  )
  val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
  val args = arrayOf(
    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
  )
  androidAppContext.contentResolver.query(uri, projection, selection, args, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")?.use { c ->
    val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
    val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
    val durCol = c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
    while (c.moveToNext() && out.size < limit) {
      val id = c.getLong(idCol)
      val video = c.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
      val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
      val content = ContentUris.withAppendedId(base, id)
      val dur = if (video && durCol >= 0) c.getLong(durCol) else 0L
      out.add(VibeMediaItem(URI(content.toString()), video, dur, id))
    }
  }
  return out
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun rememberVibeGallery(): VibeGallery {
  val perms = rememberMultiplePermissionsState(permissions())
  var items by remember { mutableStateOf<List<VibeMediaItem>>(emptyList()) }
  val granted = perms.permissions.any { it.status == com.google.accompanist.permissions.PermissionStatus.Granted }
  LaunchedEffect(granted) {
    if (granted) {
      items = withContext(Dispatchers.IO) { runCatching { queryRecent(240) }.getOrDefault(emptyList()) }
    }
  }
  return VibeGallery(items, granted) { perms.launchMultiplePermissionRequest() }
}

@Composable
actual fun VibeMediaThumb(item: VibeMediaItem, modifier: Modifier) {
  var bmp by remember(item.id) { mutableStateOf(thumbs.get(item.id)) }
  LaunchedEffect(item.id) {
    if (bmp != null) return@LaunchedEffect
    bmp = withContext(Dispatchers.IO) {
      runCatching {
        val cr = androidAppContext.contentResolver
        val b = if (Build.VERSION.SDK_INT >= 29) {
          cr.loadThumbnail(android.net.Uri.parse(item.uri.toString()), Size(260, 260), null)
        } else if (item.video) {
          @Suppress("DEPRECATION")
          MediaStore.Video.Thumbnails.getThumbnail(cr, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
        } else {
          @Suppress("DEPRECATION")
          MediaStore.Images.Thumbnails.getThumbnail(cr, item.id, MediaStore.Images.Thumbnails.MINI_KIND, null)
        }
        b?.also { thumbs.put(item.id, it) }
      }.getOrNull()
    }
  }
  val b = bmp
  if (b != null) Image(b.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
}
