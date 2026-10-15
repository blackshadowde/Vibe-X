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

  /** Matte grey behind people without a picture. */
  @Composable fun matte() = if (isDark()) Color(0xFF4A4B52) else Color(0xFFC4C7CE)
  @Composable fun onMatte() = if (isDark()) Color(0xFFE3E5E8) else Color.White

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
      Box(Modifier.fillMaxSize().background(VibeColors.matte()), contentAlignment = Alignment.Center) {
        val letter = name.trim().firstOrNull()?.uppercase() ?: "?"
        val fs = with(LocalDensity.current) { (size * 0.42f).toSp() }
        Text(letter, color = VibeColors.onMatte(), fontSize = fs, fontWeight = FontWeight.SemiBold)
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
  val Block = icon("block", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM4,12c0,-4.42 3.58,-8 8,-8 1.85,0 3.55,0.63 4.9,1.69L5.69,16.9C4.63,15.55 4,13.85 4,12zM12,20c-1.85,0 -3.55,-0.63 -4.9,-1.69L18.31,7.1C19.37,8.45 20,10.15 20,12c0,4.42 -3.58,8 -8,8z")
  val Bookmark = icon("bookmark", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z")
  val BookmarkBorder = icon("bookmarkBorder", "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z")
  val ArrowBack = icon("arrowBack", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z")
  val DarkMode = icon("darkMode", "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z")
  val LightMode = icon("lightMode", "M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5 5,-2.24 5,-5 -2.24,-5 -5,-5zM2,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM20,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1h-2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM11,20v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1 -0.55,0 -1,0.45 -1,1zM5.99,4.58c-0.39,-0.39 -1.03,-0.39 -1.41,0 -0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0s0.39,-1.03 0,-1.41L5.99,4.58zM18.36,16.95c-0.39,-0.39 -1.03,-0.39 -1.41,0 -0.39,0.39 -0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0 0.39,-0.39 0.39,-1.03 0,-1.41l-1.06,-1.06zM19.42,5.99c0.39,-0.39 0.39,-1.03 0,-1.41 -0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0l1.06,-1.06zM7.05,18.36c0.39,-0.39 0.39,-1.03 0,-1.41 -0.39,-0.39 -1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0l1.06,-1.06z")
  val Check = icon("check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z")
  val PushPin = icon("pushPin", "M16,9V4l1,0c0.55,0 1,-0.45 1,-1v0c0,-0.55 -0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3v0c0,0.55 0.45,1 1,1l1,0v5c0,1.66 -1.34,3 -3,3h0v2h5.97v7l1,1l1,-1v-7H19v-2h0C17.34,12 16,10.66 16,9z")
  val MarkRead = icon("markRead", "M18,7l-1.41,-1.41 -6.34,6.34 1.41,1.41L18,7zM22.24,5.59L11.66,16.17 7.48,12l-1.41,1.41L11.66,19l12,-12 -1.42,-1.41z")
  val PhotoLibrary = icon("photoLibrary", "M22,16V4c0,-1.1 -0.9,-2 -2,-2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2zM11,12l2.03,2.71L16,11l4,5H8l3,-4zM2,6v14c0,1.1 0.9,2 2,2h14v-2H4V6H2z")
  val Camera = icon("camera", "M12,15.2A3.2,3.2 0 1,1 12,8.8A3.2,3.2 0 1,1 12,15.2zM9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5 -5,2.24 -5,5 2.24,5 5,5z")
  val AttachFile = icon("attachFile", "M16.5,6v11.5c0,2.21 -1.79,4 -4,4s-4,-1.79 -4,-4V5c0,-1.38 1.12,-2.5 2.5,-2.5s2.5,1.12 2.5,2.5v10.5c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V6H10v9.5c0,1.38 1.12,2.5 2.5,2.5s2.5,-1.12 2.5,-2.5V5c0,-2.21 -1.79,-4 -4,-4S7,2.79 7,5v12.5c0,3.04 2.46,5.5 5.5,5.5s5.5,-2.46 5.5,-5.5V6h-1.5z")
  val QrCode = icon("qr", "M3,11h8V3H3V11zM5,5h4v4H5V5zM3,21h8v-8H3V21zM5,15h4v4H5V15zM13,3v8h8V3H13zM19,9h-4V5h4V9zM19,19h2v2h-2zM13,13h2v2h-2zM15,15h2v2h-2zM13,17h2v2h-2zM15,19h2v2h-2zM17,17h2v2h-2zM17,13h2v2h-2zM19,15h2v2h-2z")
  val Copy = icon("copy", "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z")
  val Chat = icon("chat", "M20,2H4c-1.1,0 -1.99,0.9 -1.99,2L2,22l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z")
  val Edit = icon("edit", "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z")
  val Groups = icon("groups", "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3zM8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3zM8,13c-2.33,0 -7,1.17 -7,3.5V19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5zM16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45V19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z")
  val RocketLaunch = icon("rocketLaunch", "M9.19,6.35c-2.04,2.29 -3.44,5.58 -3.57,5.89L2,10.69l4.05,-4.05c0.47,-0.47 1.15,-0.68 1.81,-0.55L9.19,6.35L9.19,6.35zM11.17,17c0,0 3.74,-1.55 5.89,-3.7c5.4,-5.4 4.5,-9.62 4.21,-10.57c-0.95,-0.3 -5.17,-1.19 -10.57,4.21C8.55,9.09 7,12.83 7,12.83L11.17,17zM17.65,14.81c-2.29,2.04 -5.58,3.44 -5.89,3.57L13.31,22l4.05,-4.05c0.47,-0.47 0.68,-1.15 0.55,-1.81L17.65,14.81L17.65,14.81zM9,18c0,0.83 -0.34,1.58 -0.88,2.12C6.94,21.3 2,22 2,22s0.7,-4.94 1.88,-6.12C4.42,15.34 5.17,15 6,15C7.66,15 9,16.34 9,18zM13,9c0,-1.1 0.9,-2 2,-2s2,0.9 2,2s-0.9,2 -2,2S13,10.1 13,9z")
  val SystemUpdate = icon("systemUpdate", "M17,1.01L7,1c-1.1,0 -1.99,0.9 -1.99,2v18c0,1.1 0.89,2 1.99,2h10c1.1,0 2,-0.9 2,-2V3c0,-1.1 -0.9,-1.99 -2,-1.99zM17,19H7V5h10v14zM16,13h-3V8h-2v5H8l4,4 4,-4z")
  val SystemUpdateAlt = icon("systemUpdateAlt", "M12,16.5l4,-4h-3v-9h-2v9H8l4,4zM21,3.5h-6v1.99h6v14.03H3V5.49h6V3.5H3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2v-14c0,-1.1 -0.9,-2 -2,-2z")
  val Sync = icon("sync", "M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46C19.54,15.03 20,13.57 20,12c0,-4.42 -3.58,-8 -8,-8zM12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z")
  val CloudOff = icon("cloudOff", "M19.35,10.04C18.67,6.59 15.64,4 12,4c-1.48,0 -2.85,0.43 -4.01,1.17l1.46,1.46C10.21,6.23 11.08,6 12,6c3.04,0 5.5,2.46 5.5,5.5v0.5H19c1.66,0 3,1.34 3,3 0,1.13 -0.64,2.11 -1.56,2.62l1.45,1.45C23.16,18.16 24,16.68 24,15c0,-2.64 -2.05,-4.78 -4.65,-4.96zM3,5.27l2.75,2.74C2.56,8.15 0,10.77 0,14c0,3.31 2.69,6 6,6h11.73l2,2L21,20.73 4.27,4 3,5.27zM7.73,10l8,8H6c-2.21,0 -4,-1.79 -4,-4s1.79,-4 4,-4h1.73z")
  val CheckCircle = icon("checkCircle", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z")
  val HelpOutline = icon("helpOutline", "M11,18h2v-2h-2v2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,6c-2.21,0 -4,1.79 -4,4h2c0,-1.1 0.9,-2 2,-2s2,0.9 2,2c0,2 -3,1.75 -3,5h2c0,-2.25 3,-2.5 3,-5 0,-2.21 -1.79,-4 -4,-4z")
  val OpenInNew = icon("openInNew", "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7zM14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z")
  val Info = icon("info", "M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z")
  val DeleteOutline = icon("deleteOutline", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM8,9h8v10H8V9zM15.5,4l-1,-1h-5l-1,1H5v2h14V4z")
  val PauseCircleOutline = icon("pauseCircleOutline", "M9,16h2V8H9v8zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM13,16h2V8h-2v8z")
  val PlayCircleOutline = icon("playCircleOutline", "M10,16.5l6,-4.5 -6,-4.5v9zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z")
  val PlayArrow = icon("playArrow", "M8,5v14l11,-7z")
  val Pause = icon("pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z")
  val MicNone = icon("micNone", "M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM11,5c0,-0.55 0.45,-1 1,-1s1,0.45 1,1v6c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V5zM17,11c0,2.76 -2.24,5 -5,5s-5,-2.24 -5,-5H5c0,3.53 2.61,6.43 6,6.92V21h2v-3.08c3.39,-0.49 6,-3.39 6,-6.92h-2z")
  val Refresh = icon("refresh", "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z")
  val SendOutlined = icon("sendOutlined", "M4.01,6.03l7.51,3.22 -7.52,-1 0.01,-2.22M11.51,14.75L4,17.97v-2.22l7.51,-1M2.01,3L2,10l15,2 -15,2 0.01,7L23,12 2.01,3z")
  val Delete = icon("delete", "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z")
}
