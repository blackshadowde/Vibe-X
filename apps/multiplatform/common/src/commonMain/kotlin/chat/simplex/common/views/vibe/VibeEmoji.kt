package chat.simplex.common.views.vibe

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatItem
import chat.simplex.common.ui.theme.EmojiFont
import kotlinx.coroutines.launch

private enum class Motion { Beat, Shake, Flicker, Wave, Bounce, Nod, Sink, Pulse, Rocket, Float }

private fun motionOf(text: String): Motion {
  val t = text.trim()
  fun any(vararg s: String) = s.any { t.startsWith(it) }
  return when {
    any("❤", "💖", "💕", "💗", "💓", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "❣", "💘", "💝", "♥", "💞") -> Motion.Beat
    any("😂", "🤣", "😆", "😹", "😅", "😄", "😁", "😀", "😃") -> Motion.Shake
    any("🔥", "✨", "⚡") -> Motion.Flicker
    any("👋", "🖐", "✋", "🤚") -> Motion.Wave
    any("🎉", "🥳", "🎊", "🤩", "🙌") -> Motion.Bounce
    any("👍", "👎", "👌", "✌", "🤞", "👏", "🙏", "✅") -> Motion.Nod
    any("😢", "😭", "🥺", "😞", "😔", "😿", "💔") -> Motion.Sink
    any("😍", "🥰", "😘", "😻", "😊", "☺") -> Motion.Pulse
    any("🚀") -> Motion.Rocket
    else -> Motion.Float
  }
}

private val bigEmojiStyle = TextStyle(fontFamily = EmojiFont)

/** A message that is only a few emoji: big, alive, and it pops when it arrives or is tapped. */
@Composable
fun VibeBigEmoji(item: ChatItem) {
  val text = VibeEffect.strip(item.content.text).trim()
  val motion = remember(text) { motionOf(text) }
  val small = text.codePoints().count() < 4
  val scope = rememberCoroutineScope()
  val pop = remember(item.id) { Animatable(if (item.vibeJustArrived() && VibeSeen.firstTime(-1_000_000_000L - item.id)) 0f else 1f) }
  var bounds by remember { mutableStateOf(Offset.Zero) }
  val fresh = remember(item.id) { pop.value == 0f }
  LaunchedEffect(item.id) {
    if (fresh) {
      pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 220f))
    }
  }
  LaunchedEffect(item.id, bounds) {
    if (fresh && bounds != Offset.Zero) VibeBurst.fire(firstGlyph(text), bounds, big = true)
  }
  val inf = rememberInfiniteTransition(label = "emoji")
  val phase by inf.animateFloat(
    0f, 1f,
    infiniteRepeatable(
      tween(
        when (motion) {
          Motion.Beat -> 1100
          Motion.Shake -> 520
          Motion.Flicker -> 360
          Motion.Wave -> 900
          Motion.Bounce -> 760
          Motion.Nod -> 900
          Motion.Sink -> 2200
          Motion.Pulse -> 1300
          Motion.Rocket -> 600
          Motion.Float -> 1900
        },
        easing = LinearEasing
      )
    ),
    label = "phase"
  )
  Box(
    Modifier
      .onGloballyPositioned { bounds = it.boundsInRoot().center }
      .clickable(remember { MutableInteractionSource() }, indication = null) {
        scope.launch {
          pop.snapTo(0.55f)
          pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 300f))
        }
        VibeBurst.fire(firstGlyph(text), bounds, big = true)
      }
  ) {
    Text(
      text,
      style = bigEmojiStyle,
      fontSize = if (small) 58.sp else 40.sp,
      modifier = Modifier.graphicsLayer {
        val tau = phase * 2f * Math.PI.toFloat()
        val s = kotlin.math.sin(tau)
        var sc = pop.value
        var rot = 0f
        var ty = 0f
        var sx = 1f
        var sy = 1f
        var origin = TransformOrigin.Center
        when (motion) {
          Motion.Beat -> {
            // lub-dub
            val b = when {
              phase < 0.12f -> phase / 0.12f
              phase < 0.24f -> 1f - (phase - 0.12f) / 0.12f
              phase < 0.36f -> (phase - 0.24f) / 0.12f * 0.7f
              phase < 0.5f -> 0.7f - (phase - 0.36f) / 0.14f * 0.7f
              else -> 0f
            }
            sc *= 1f + 0.16f * b
          }
          Motion.Shake -> { rot = s * 11f; ty = -kotlin.math.abs(s) * 6f }
          Motion.Flicker -> { sy = 1f + 0.08f * s; sx = 1f - 0.04f * s; origin = TransformOrigin(0.5f, 1f) }
          Motion.Wave -> { rot = 14f * s + 6f; origin = TransformOrigin(0.8f, 0.9f) }
          Motion.Bounce -> {
            ty = -kotlin.math.abs(s) * 26f
            val squash = if (kotlin.math.abs(s) < 0.15f) 0.1f * (1f - kotlin.math.abs(s) / 0.15f) else 0f
            sx = 1f + squash; sy = 1f - squash
            origin = TransformOrigin(0.5f, 1f)
          }
          Motion.Nod -> { rot = s * 9f; origin = TransformOrigin(0.5f, 1f) }
          Motion.Sink -> { ty = (s + 1f) * 5f; rot = s * 4f }
          Motion.Pulse -> { sc *= 1f + 0.07f * (s + 1f) / 2f; rot = s * 5f }
          Motion.Rocket -> { ty = -6f - s * 6f; rot = 0f + kotlin.math.sin(tau * 3) * 2.5f }
          Motion.Float -> { ty = s * 7f; sc *= 1f + 0.03f * s }
        }
        scaleX = sc * sx
        scaleY = sc * sy
        rotationZ = rot
        translationY = ty
        transformOrigin = origin
      }
    )
  }
}

/** The first emoji (grapheme) of [text]. */
fun firstGlyph(text: String): String {
  val it = java.text.BreakIterator.getCharacterInstance()
  it.setText(text)
  val end = it.next()
  return if (end > 0) text.substring(0, end) else text
}
