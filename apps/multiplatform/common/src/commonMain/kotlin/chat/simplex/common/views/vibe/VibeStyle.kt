package chat.simplex.common.views.vibe

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import chat.simplex.common.model.ChatItem
import chat.simplex.common.platform.appPlatform
import kotlinx.datetime.Clock

/** Vibe X look on phones; the desktop app keeps SimpleX's own look. */
val vibeStyle: Boolean get() = appPlatform.isAndroid

/** True inside a Vibe message row: no bubble, left aligned, no tail. */
val LocalVibeRows = staticCompositionLocalOf { false }

/** Items that already played their arrival animation (so scrolling back doesn't replay it). */
internal object VibeSeen {
  private val ids = HashSet<Long>()
  fun firstTime(id: Long): Boolean = synchronized(ids) { ids.add(id) }
}

/** Arrived in the last few seconds, worth an entrance. */
fun ChatItem.vibeJustArrived(seconds: Int = 6): Boolean =
  id >= 0 && (Clock.System.now() - meta.createdAt).inWholeSeconds.let { it >= -5 && it <= seconds }

/**
 * Google Messages style entrance: the new message sweeps up from the input
 * bar with a little spring, leaning in from its sender's side.
 */
fun Modifier.vibeSweepIn(item: ChatItem): Modifier = composed {
  // your own live message shows only once it's sent, so time it by its last update
  // a live message (or typing dots) becomes the real one when it's sent, so time it by its last update
  val fresh = item.id >= 0 && (Clock.System.now() - item.meta.updatedAt).inWholeSeconds in -5L..6L
  val animate = remember(item.id) { fresh && VibeSeen.firstTime(item.id) }
  if (!animate) return@composed this
  val p = remember(item.id) { Animatable(0f) }
  LaunchedEffect(item.id) {
    p.animateTo(1f, VibeSprings.message())
  }
  val density = LocalDensity.current
  val rise = with(density) { 64.dp.toPx() }
  val lean = with(density) { 28.dp.toPx() }
  val sent = item.chatDir.sent
  this.graphicsLayer {
    val v = p.value
    val inv = 1f - v
    alpha = v.coerceIn(0f, 1f)
    translationY = rise * inv
    translationX = (if (sent) lean else -lean) * inv
    scaleX = 0.86f + 0.14f * v
    scaleY = 0.86f + 0.14f * v
    rotationZ = (if (sent) 2.5f else -2.5f) * inv
    transformOrigin = TransformOrigin(if (sent) 1f else 0f, 1f)
  }
}

/**
 * Vibe-flutter's soft shadow under chat text in light mode, so names and
 * messages stand out on white (none in dark mode).
 */
@Composable
fun vibeTextShadow(): androidx.compose.ui.graphics.Shadow? {
  if (!androidx.compose.material.MaterialTheme.colors.isLight) return null
  val d = LocalDensity.current
  return with(d) { androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color(0x40000000), androidx.compose.ui.geometry.Offset(0f, 1.dp.toPx()), 4.dp.toPx()) }
}
