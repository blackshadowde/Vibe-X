package chat.simplex.common.views.vibe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.LinkPreview
import chat.simplex.common.platform.base64ToBitmap
import chat.simplex.common.views.helpers.openUriCatching

/**
 * Discord-style link card under a message: a coloured bar on the left, the
 * site, the title in link blue, a few lines of description, and the
 * picture (small on the right when it's square, big under it otherwise).
 */
@Composable
fun VibeLinkCard(preview: LinkPreview, onLongClick: () -> Unit) {
  val uri = LocalUriHandler.current
  val image = remember(preview.image) { runCatching { base64ToBitmap(preview.image) }.getOrNull()?.takeIf { it.width > 2 && it.height > 2 } }
  val square = image != null && image.width * 1.3f >= image.height && image.height * 1.3f >= image.width
  val host = remember(preview.uri) { runCatching { java.net.URI(preview.uri).host?.removePrefix("www.") }.getOrNull() ?: preview.uri }
  val bar = VibeColors.blurple
  Row(
    Modifier
      .padding(top = 6.dp, bottom = 2.dp)
      .widthIn(max = 420.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(VibeColors.container())
      .combinedClickable(onClick = { uri.openUriCatching(preview.uri) }, onLongClick = onLongClick)
      .height(IntrinsicSize.Min)
  ) {
    Box(Modifier.width(4.dp).fillMaxHeight().background(bar))
    Column(Modifier.weight(1f, false).padding(12.dp)) {
      Row {
        Column(Modifier.weight(1f, false)) {
          Text(host, fontSize = 12.sp, color = VibeColors.onVariant(), maxLines = 1, overflow = TextOverflow.Ellipsis)
          if (preview.title.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(preview.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.sendFg, maxLines = 2, overflow = TextOverflow.Ellipsis)
          }
          if (preview.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(preview.description, fontSize = 13.5.sp, lineHeight = 18.sp, color = VibeColors.onSurface(), maxLines = 4, overflow = TextOverflow.Ellipsis)
          }
        }
        if (image != null && square) {
          Spacer(Modifier.width(12.dp))
          Image(image, null, Modifier.size(72.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
        }
      }
      if (image != null && !square) {
        Spacer(Modifier.height(10.dp))
        Image(image, null, Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
      }
    }
  }
}
