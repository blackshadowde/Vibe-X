package chat.simplex.common.views.vibe

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.ui.theme.VibeBlurple
import kotlinx.coroutines.delay

private val chipShape = RoundedCornerShape(10.dp)

/** Discord's reaction pill: blurple outline when it's yours. */
@Composable
fun Modifier.vibeReactionChip(mine: Boolean): Modifier {
  val bg by animateColorAsState(
    if (mine) VibeBlurple.copy(alpha = 0.22f) else MaterialTheme.colors.onBackground.copy(alpha = 0.07f),
    tween(220), label = "chipBg"
  )
  val edge by animateColorAsState(
    if (mine) VibeBlurple else MaterialTheme.colors.onBackground.copy(alpha = 0.0f),
    tween(220), label = "chipEdge"
  )
  return this
    .padding(end = 6.dp, top = 4.dp)
    .vibePopIn(0)
    .clip(chipShape)
    .background(bg)
    .border(1.dp, edge, chipShape)
}

/** Springs in from nothing, [index] staggers a row of them. */
fun Modifier.vibePopIn(index: Int): Modifier = composed {
  val s = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    delay(index * 28L)
    s.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
  }
  graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = s.value.coerceIn(0f, 1f) }
}

/**
 * Fires a burst of [emoji] from this element whenever [total] goes up
 * (you or someone else just reacted), and bumps the element.
 */
fun Modifier.vibeBurstOnIncrease(emoji: String, total: Int): Modifier = composed {
  var center by remember { mutableStateOf(Offset.Zero) }
  var last by remember { mutableStateOf(total) }
  val bump = remember { Animatable(1f) }
  LaunchedEffect(total) {
    if (total > last && center != Offset.Zero) {
      VibeBurst.fire(emoji, center)
      bump.snapTo(1.35f)
      bump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
    }
    last = total
  }
  this
    .onGloballyPositioned { center = it.boundsInRoot().center }
    .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
}

/** Remembers where an element is, for bursts started by a tap on it. */
fun Modifier.vibeTrackCenter(into: MutableState<Offset>): Modifier =
  onGloballyPositioned { into.value = it.boundsInRoot().center }

/** The count in a reaction pill, rolling up or down when it changes. */
@Composable
fun VibeReactionCount(count: Int, mine: Boolean) {
  AnimatedContent(
    targetState = count,
    transitionSpec = {
      if (targetState > initialState) {
        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
      } else {
        (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
      }
    },
    label = "reactionCount"
  ) { n ->
    Text(
      "$n",
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      color = if (mine) VibeBlurple else MaterialTheme.colors.secondary,
    )
  }
}
