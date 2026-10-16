package chat.simplex.common.views.vibe

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.platform.base64ToBitmap
import chat.simplex.common.views.chat.ComposePreview
import chat.simplex.common.views.helpers.UploadContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/** Biggest photo Vibe X sends in HD (and receives by itself). */
const val VIBE_HD_IMAGE_SIZE: Long = 2_621_440 // 2.5 MB

/** A preview of a photo on the phone, at most [maxPx] on its longer side. */
expect fun vibePreviewBitmap(uri: URI, maxPx: Int): ImageBitmap?

/** A profile picture encoded as sharp as fits in [maxChars] (SimpleX's profile limit). */
expect fun vibeEncodeAvatar(image: ImageBitmap, maxChars: Int = 12500): String

/**
 * Vibe's send pop-up: picked photos and videos come up big, with a caption
 * box and an HD switch, and go only when you tap Send.
 */
object VibeMediaSend {
  /** Read while the photos are saved for sending. */
  @Volatile var hd: Boolean = false

  class Req(
    val preview: ComposePreview.MediaPreview,
    val caption: String,
    val send: (caption: String, hd: Boolean) -> Unit,
    val cancel: () -> Unit,
  )

  val req = mutableStateOf<Req?>(null)

  fun open(preview: ComposePreview.MediaPreview, caption: String, send: (String, Boolean) -> Unit, cancel: () -> Unit) {
    if (req.value?.preview === preview) return
    req.value = Req(preview, caption, send, cancel)
  }
}

@Composable
fun VibeMediaSendHost() {
  val r = VibeMediaSend.req.value ?: return
  key(r) { Popup(r) }
}

@Composable
private fun Popup(r: VibeMediaSend.Req) {
  val scope = rememberCoroutineScope()
  val p = remember { Animatable(0f) }
  var leaving by remember { mutableStateOf(false) }
  var caption by remember { mutableStateOf(r.caption) }
  var hd by remember { mutableStateOf(appPrefs.vibeSendHd.get()) }
  val content = r.preview.content
  val videos = content.count { it is UploadContent.Video }
  val photos = content.size - videos
  LaunchedEffect(Unit) { VibeHaptics.light(); p.animateTo(1f, VibeSprings.pop()) }
  fun dismiss(action: () -> Unit) {
    if (leaving) return
    leaving = true
    scope.launch {
      p.animateTo(0f, androidx.compose.animation.core.tween(160))
      if (VibeMediaSend.req.value === r) VibeMediaSend.req.value = null
      action()
    }
  }
  BackHandler { dismiss(r.cancel) }
  Box(Modifier.fillMaxSize()) {
    Box(
      Modifier.fillMaxSize().graphicsLayer { alpha = p.value }.background(Color.Black.copy(alpha = 0.6f))
        .clickable(remember { MutableInteractionSource() }, indication = null) { dismiss(r.cancel) }
    )
    Column(
      Modifier.align(Alignment.Center).padding(horizontal = 20.dp).widthIn(max = 460.dp).fillMaxWidth()
        .graphicsLayer {
          val s = 0.88f + 0.12f * p.value
          scaleX = s; scaleY = s; alpha = p.value.coerceIn(0f, 1f)
        }
        .clip(RoundedCornerShape(28.dp)).background(vibePage())
        .clickable(remember { MutableInteractionSource() }, indication = null) {}
        .imePadding()
        .padding(vertical = 18.dp)
    ) {
      val title = when {
        videos == 0 && photos == 1 -> vt("Send photo")
        videos == 0 -> "Send $photos photos"
        photos == 0 && videos == 1 -> vt("Send video")
        else -> "Send ${content.size} items"
      }
      Text(title, Modifier.padding(horizontal = 20.dp), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      Spacer(Modifier.height(14.dp))
      LazyRow(
        Modifier.fillMaxWidth().height(if (content.size == 1) 300.dp else 240.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        itemsIndexed(content) { i, c ->
          Preview(c, r.preview.images.getOrNull(i), single = content.size == 1)
        }
      }
      Spacer(Modifier.height(14.dp))
      Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(vibeCard()).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(Modifier.weight(1f)) {
          if (caption.isEmpty()) Text(vt("Add a caption…"), color = VibeColors.onVariant(), fontSize = 16.sp)
          BasicTextField(
            caption, { caption = it }, maxLines = 4,
            textStyle = TextStyle(fontSize = 16.sp, color = VibeColors.onSurface()),
            cursorBrush = SolidColor(VibeColors.blurple), modifier = Modifier.fillMaxWidth()
          )
        }
      }
      if (photos > 0) {
        Spacer(Modifier.height(10.dp))
        Row(
          Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(vibeCard())
            .clickable { VibeHaptics.selection(); hd = !hd }.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // the HD badge lights up blurple when it's on
          Box(
            Modifier.clip(RoundedCornerShape(7.dp))
              .then(if (hd) Modifier.background(VibeColors.blurple) else Modifier.border(1.5.dp, VibeColors.onVariant(), RoundedCornerShape(7.dp)))
              .padding(horizontal = 7.dp, vertical = 3.dp)
          ) {
            Text("HD", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (hd) Color.White else VibeColors.onVariant())
          }
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(vt("Send in HD"), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.onSurface())
            Text(if (hd) vt("Full quality, up to 2.5 MB a photo") else vt("Smaller and faster to send"), fontSize = 13.sp, color = VibeColors.onVariant())
          }
          androidx.compose.material.Switch(
            hd, { VibeHaptics.selection(); hd = it },
            colors = androidx.compose.material.SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VibeColors.blurple, checkedTrackAlpha = 1f)
          )
        }
      }
      Spacer(Modifier.height(16.dp))
      Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
          Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp)).background(vibeCard()).clickable { dismiss(r.cancel) },
          contentAlignment = Alignment.Center
        ) { Text(vt("Cancel"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface()) }
        Row(
          Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp)).background(VibeColors.blurple).clickable {
            VibeHaptics.medium()
            appPrefs.vibeSendHd.set(hd)
            val c = caption
            val h = hd
            dismiss { r.send(c, h) }
          },
          horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(VibeIcons.Send, null, Modifier.size(19.dp), tint = Color.White)
          Spacer(Modifier.width(8.dp))
          Text(vt("Send"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
      }
    }
  }
}

@Composable
private fun Preview(c: UploadContent, thumb: String?, single: Boolean) {
  val uri = when (c) {
    is UploadContent.SimpleImage -> c.uri
    is UploadContent.AnimatedImage -> c.uri
    is UploadContent.Video -> null
  }
  val small = remember(thumb) { thumb?.let { runCatching { base64ToBitmap(it) }.getOrNull() } }
  var big by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(uri) {
    if (uri != null) big = withContext(Dispatchers.IO) { runCatching { vibePreviewBitmap(uri, 1280) }.getOrNull() }
  }
  val bmp = big ?: small
  val ratio = bmp?.let { (it.width.toFloat() / it.height.coerceAtLeast(1)).coerceIn(0.5f, 1.8f) } ?: 1f
  Box(
    Modifier.fillMaxHeight().aspectRatio(if (single) ratio else ratio.coerceAtMost(1.1f)).clip(RoundedCornerShape(18.dp)).background(Color.Black),
    contentAlignment = Alignment.Center
  ) {
    if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    if (c is UploadContent.Video) {
      Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        Icon(VibeIcons.PlayArrow, null, Modifier.size(30.dp), tint = Color.White)
      }
      val s = c.duration
      Text(
        "%d:%02d".format(s / 60, s % 60),
        Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color.White, fontSize = 12.sp
      )
    }
  }
}
