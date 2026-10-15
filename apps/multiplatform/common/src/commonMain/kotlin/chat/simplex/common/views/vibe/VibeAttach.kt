package chat.simplex.common.views.vibe

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.chatModel
import chat.simplex.common.views.chat.ComposeState
import chat.simplex.common.views.chat.processPickedMedia
import chat.simplex.common.views.helpers.AttachmentOption
import chat.simplex.common.views.helpers.SharedContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI

/** A photo or video on the phone. */
class VibeMediaItem(val uri: URI, val video: Boolean, val durationMs: Long, val id: Long)

/** Recent photos and videos, and access to them. */
class VibeGallery(val items: List<VibeMediaItem>, val granted: Boolean, val request: () -> Unit)

@Composable
expect fun rememberVibeGallery(): VibeGallery

@Composable
expect fun VibeMediaThumb(item: VibeMediaItem, modifier: Modifier)

/** Set when "Send N" is tapped: the chat sends the attached media as soon as it's ready. */
object VibeAttachSend {
  val pending = mutableStateOf(false)
}

/**
 * The "+" pane, like Vibe's: a grid of your recent photos and videos (tap
 * to pick, then Send), and a floating bar with Photos, Camera, Files and
 * Address. (No location: SimpleX doesn't support sharing it.)
 */
object VibeAttachSheet {
  fun show(attachmentOption: MutableState<AttachmentOption?>, composeState: MutableState<ComposeState>) {
    VibeSheets.show(maxHeight = 0.62f, background = { VibeColors.container() }) { close ->
      Sheet(attachmentOption, composeState, close)
    }
  }
}

@Composable
private fun Sheet(attachmentOption: MutableState<AttachmentOption?>, composeState: MutableState<ComposeState>, close: () -> Unit) {
  // the keyboard goes away as the pane comes up, as in Vibe
  val view = chat.simplex.common.platform.LocalMultiplatformView()
  val focus = androidx.compose.ui.platform.LocalFocusManager.current
  LaunchedEffect(Unit) {
    focus.clearFocus(force = true)
    chat.simplex.common.platform.hideKeyboard(view, clearFocus = true)
  }
  val gallery = rememberVibeGallery()
  val selected = remember { mutableStateListOf<VibeMediaItem>() }
  val grid = rememberLazyGridState()
  val scope = rememberCoroutineScope()
  Box(Modifier.fillMaxWidth().fillMaxHeight()) {
    if (!gallery.granted) {
      Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(vt("Allow access to photos to attach media"), color = VibeColors.onSurface())
        Spacer(Modifier.height(8.dp))
        Text(vt("Allow"), Modifier.clip(RoundedCornerShape(12.dp)).clickable { gallery.request() }.padding(horizontal = 16.dp, vertical = 8.dp), color = VibeColors.blurple, fontWeight = FontWeight.SemiBold)
      }
    } else {
      LazyVerticalGrid(
        GridCells.Fixed(4), state = grid,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        item {
          Box(
            Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(VibeColors.highest())
              .clickable { close(); attachmentOption.value = AttachmentOption.CameraPhoto },
            contentAlignment = Alignment.Center
          ) {
            Icon(VibeIcons.Camera, vt("Camera"), Modifier.size(40.dp), tint = VibeColors.onVariant())
          }
        }
        itemsIndexed(gallery.items, key = { _, it -> it.id }) { _, m ->
          val picked = selected.any { it.id == m.id }
          Box(
            Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(VibeColors.highest())
              .clickable { if (picked) selected.removeAll { it.id == m.id } else if (selected.size < 10) selected.add(m) }
          ) {
            VibeMediaThumb(m, Modifier.fillMaxSize())
            if (m.video) {
              val s = m.durationMs / 1000
              Row(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(VibeIcons.Videocam, null, Modifier.size(13.dp), tint = Color.White)
                Spacer(Modifier.width(3.dp))
                Text("${s / 60}:${(s % 60).toString().padStart(2, '0')}", fontSize = 11.sp, color = Color.White)
              }
            }
            Box(
              Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape)
                .background(if (picked) VibeColors.blurple else Color.Black.copy(alpha = 0.38f))
                .border(1.5.dp, Color.White, CircleShape),
              contentAlignment = Alignment.Center
            ) {
              if (picked) Text("${selected.indexOfFirst { it.id == m.id } + 1}", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
      AnimatedContent(selected.isNotEmpty(), transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() }, label = "attachBar") { sending ->
        if (sending) {
          Row(
            Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(30.dp)).background(VibeColors.blurple).clickable {
              val uris = selected.map { it.uri }
              close()
              VibeAttachSend.pending.value = true
              CoroutineScope(Dispatchers.IO).launch { composeState.processPickedMedia(uris, null) }
            },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(VibeIcons.Send, null, Modifier.size(22.dp), tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text("Send ${selected.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        } else {
          Row(
            Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)).background(VibeColors.high()).padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            Action(VibeIcons.PhotoLibrary, vt("Photos"), active = true) { close(); attachmentOption.value = AttachmentOption.GalleryImage }
            Action(VibeIcons.Videocam, vt("Videos")) { close(); attachmentOption.value = AttachmentOption.GalleryVideo }
            Action(VibeIcons.AttachFile, vt("Files")) { close(); attachmentOption.value = AttachmentOption.File }
            Action(VibeIcons.QrCode, vt("Address")) {
              close()
              chatModel.sharedContent.value = SharedContent.MyAddress
            }
          }
        }
      }
    }
  }
}

@Composable
private fun Action(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
  val color = if (active) VibeColors.onSurface() else VibeColors.onVariant()
  Column(
    Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Icon(icon, null, Modifier.size(30.dp), tint = color)
    Spacer(Modifier.height(4.dp))
    Text(label, fontSize = 15.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = color)
  }
}
