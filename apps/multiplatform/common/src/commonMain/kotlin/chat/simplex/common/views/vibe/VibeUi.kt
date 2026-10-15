package chat.simplex.common.views.vibe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import chat.simplex.common.model.*
import chat.simplex.common.platform.base64ToBitmap
import chat.simplex.common.platform.chatModel

/** Vibe-flutter's colour scheme (dark / light). */
object VibeColors {
  val blurple = Color(0xFF5865F2)
  val red = Color(0xFFF23F43)
  val green = Color(0xFF23A55A)
  val sendBg = Color(0xFFDCE4FF)
  val sendFg = Color(0xFF226DFD)

  @Composable fun surface() = if (isDark()) Color(0xFF1A191E) else Color(0xFFFFFFFF)
  @Composable fun lowest() = if (isDark()) Color(0xFF121214) else Color(0xFFE3E5E8)
  @Composable fun container() = if (isDark()) Color(0xFF252429) else Color(0xFFF2F3F5)
  @Composable fun high() = if (isDark()) Color(0xFF29282D) else Color(0xFFEBEDEF)
  @Composable fun highest() = if (isDark()) Color(0xFF323136) else Color(0xFFE3E5E8)
  @Composable fun secondaryContainer() = if (isDark()) Color(0xFF2E2D33) else Color(0xFFE3E5E8)
  @Composable fun onSurface() = if (isDark()) Color(0xFFF2F3F5) else Color(0xFF313338)
  @Composable fun onVariant() = if (isDark()) Color(0xFFB5BAC1) else Color(0xFF5C5E66)
  @Composable fun divider() = if (isDark()) Color(0xFF3A393F) else Color(0xFFD8D9DC)
  @Composable fun ownName() = if (isDark()) Color(0xFFB5BAC1) else Color(0xFF4E5058)
  @Composable fun otherName() = if (isDark()) Color(0xFFF2F3F5) else Color(0xFF060607)

  @Composable fun isDark() = !androidx.compose.material.MaterialTheme.colors.isLight
}

private val avatarColors = listOf(
  Color(0xFF5865F2), Color(0xFF3BA55C), Color(0xFFFAA61A), Color(0xFFED4245),
  Color(0xFFEB459E), Color(0xFF1ABC9C), Color(0xFF9B59B6), Color(0xFFE67E22),
)

fun vibeNameColor(name: String): Color = avatarColors[(name.hashCode() and 0x7fffffff) % avatarColors.size]

/**
 * Vibe's avatar: the picture, or the first letter on a colour picked from
 * the name (instead of SimpleX's grey silhouette). [shape] is round by
 * default; the rail uses rounded squares.
 */
@Composable
fun VibeAvatar(image: String?, name: String, size: Dp, modifier: Modifier = Modifier, shape: Shape = CircleShape) {
  val bitmap = remember(image) { image?.let { runCatching { base64ToBitmap(it) }.getOrNull() } }
  Box(modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
    if (bitmap != null) {
      Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
      Box(Modifier.fillMaxSize().background(vibeNameColor(name)), contentAlignment = Alignment.Center) {
        val letter = name.trim().firstOrNull()?.uppercase() ?: "?"
        val fs = with(LocalDensity.current) { (size * 0.42f).toSp() }
        Text(letter, color = Color.White, fontSize = fs, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}

/** Picture and name of a chat. */
@Composable
fun VibeChatAvatar(info: ChatInfo, size: Dp, modifier: Modifier = Modifier, shape: Shape = CircleShape) {
  when (info) {
    is ChatInfo.Local -> Box(modifier.size(size).clip(shape).background(VibeColors.highest()), contentAlignment = Alignment.Center) {
      androidx.compose.material.Icon(
        dev.icerock.moko.resources.compose.painterResource(chat.simplex.res.MR.images.ic_folder_filled), null,
        Modifier.size(size * 0.5f), tint = VibeColors.onVariant()
      )
    }
    else -> VibeAvatar(info.image, info.displayName, size, modifier, shape)
  }
}

@Composable
fun VibeMeAvatar(size: Dp, modifier: Modifier = Modifier) {
  val user = chatModel.currentUser.value
  VibeAvatar(user?.image, user?.displayName ?: "", size, modifier)
}

private fun icon(name: String, vararg paths: String): ImageVector {
  val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
  for (p in paths) b.addPath(addPathNodes(p), fill = SolidColor(Color.Black))
  return b.build()
}

/** Material icons Vibe-flutter uses that SimpleX doesn't ship. */
object VibeIcons {
  val Send = icon(
    "send",
    "M3.4,20.4l17.45,-7.48c0.81,-0.35 0.81,-1.49 0,-1.84L3.4,3.6c-0.66,-0.29 -1.39,0.2 -1.39,0.91L2,9.12c0,0.5 0.37,0.93 0.87,0.99L17,12 2.87,13.88c-0.5,0.07 -0.87,0.5 -0.87,1l0.01,4.61c0,0.71 0.73,1.2 1.39,0.91z"
  )
  val GraphicEq = icon("graphicEq", "M7,18h2V6H7v12zM11,22h2V2h-2v20zM3,14h2v-4H3v4zM15,18h2V6h-2v12zM19,10v4h2v-4h-2z")
  val Done = icon("done", "M9,16.2L4.8,12l-1.4,1.4L9,19 21,7l-1.4,-1.4L9,16.2z")
  val DoneAll = icon(
    "doneAll",
    "M18,7l-1.41,-1.41 -6.34,6.34 1.41,1.41L18,7zM22.24,5.59L11.66,16.17 7.48,12l-1.41,1.41L11.66,19l12,-12 -1.42,-1.41zM0.41,13.41L6,19l1.41,-1.41L1.83,12 0.41,13.41z"
  )
  val Reply = icon("reply", "M10,9V5l-7,7 7,7v-4.1c5,0 8.5,1.6 11,5.1 -1,-5 -4,-10 -11,-11z")
  val Add = icon("add", "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
  val Close = icon("close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z")
  val Settings = icon(
    "settings",
    "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z"
  )
  val Search = icon(
    "search",
    "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"
  )
  val Lock = icon(
    "lock",
    "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z"
  )
  val Call = icon(
    "call",
    "M20.01,15.38c-1.23,0 -2.42,-0.2 -3.53,-0.56 -0.35,-0.12 -0.74,-0.03 -1.01,0.24l-1.57,1.97c-2.83,-1.35 -5.48,-3.9 -6.89,-6.83l1.95,-1.66c0.27,-0.28 0.35,-0.67 0.24,-1.02 -0.37,-1.11 -0.56,-2.3 -0.56,-3.53 0,-0.54 -0.45,-0.99 -0.99,-0.99H4.19C3.65,3 3,3.24 3,3.99 3,13.28 10.73,21 20.01,21c0.71,0 0.99,-0.63 0.99,-1.18v-3.45c0,-0.54 -0.45,-0.99 -0.99,-0.99z"
  )
  val Videocam = icon("videocam", "M17,10.5V7c0,-0.55 -0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h12c0.55,0 1,-0.45 1,-1v-3.5l4,4v-11l-4,4z")
  val Error = icon("error", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-2h2v2zM13,13h-2V7h2v6z")
  val Pin = icon("pin", "M16,9V4l1,0c0.55,0 1,-0.45 1,-1v0c0,-0.55 -0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3v0c0,0.55 0.45,1 1,1l1,0v5c0,1.66 -1.34,3 -3,3h0v2h5.97v7l1,1l1,-1v-7H19v-2h0C17.34,12 16,10.66 16,9z")
  val Bolt = icon("bolt", "M11,21h-1l1,-7H7.5c-0.88,0 -0.33,-0.75 -0.31,-0.78C8.48,10.94 10.42,7.54 13.01,3h1l-1,7h3.51c0.4,0 0.62,0.19 0.4,0.66C12.97,17.55 11,21 11,21z")
  val Star = icon("star", "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z")
}
