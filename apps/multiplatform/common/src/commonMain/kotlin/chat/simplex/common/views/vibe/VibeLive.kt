package chat.simplex.common.views.vibe

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.ui.theme.VibeBlurple
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Live typing: SimpleX sends what you type as you type it (a "live
 * message"), in steps. This plays each step back as typing: new letters
 * rise in with a blurple glow that cools down, deleted ones are
 * backspaced, and a caret blinks at the end.
 */
@Composable
fun VibeLiveText(text: String, modifier: Modifier = Modifier) {
  var shown by remember { mutableStateOf(text) }
  LaunchedEffect(text) {
    when {
      text.startsWith(shown) && text.length > shown.length -> {
        val start = shown.length
        val add = text.length - start
        // the other side sends about once a second: spread the new letters over it
        val step = (1000L / max(add, 1)).coerceIn(12L, 70L)
        for (i in start + 1..text.length) {
          shown = text.substring(0, i)
          delay(step)
        }
      }
      shown.startsWith(text) && shown.length > text.length -> {
        val step = (420L / max(shown.length - text.length, 1)).coerceIn(10L, 45L)
        while (shown.length > text.length) {
          shown = shown.dropLast(1)
          delay(step)
        }
      }
      else -> shown = text
    }
  }
  val inf = rememberInfiniteTransition(label = "caret")
  val caret by inf.animateFloat(
    1f, 0f,
    infiniteRepeatable(keyframes { durationMillis = 900; 1f at 0; 1f at 420; 0f at 520; 0f at 860 }),
    label = "caretAlpha"
  )
  val base = MaterialTheme.colors.onBackground
  val glowLen = 6
  val styled = buildAnnotatedString {
    val cut = max(0, shown.length - glowLen)
    append(shown.substring(0, cut))
    val tail = shown.substring(cut)
    tail.forEachIndexed { i, ch ->
      // 0 = oldest of the glowing letters, last = just typed
      val heat = (i + 1).toFloat() / glowLen
      withStyle(
        SpanStyle(
          color = lerp(base, VibeBlurple, heat * 0.9f).copy(alpha = 1f - heat * 0.45f),
          baselineShift = BaselineShift(-0.18f * heat * heat),
        )
      ) { append(ch) }
    }
    withStyle(SpanStyle(color = VibeBlurple.copy(alpha = caret), fontWeight = FontWeight.Bold)) { append("▍") }
  }
  Text(styled, modifier, fontSize = 16.sp, color = base)
}

/** Small "typing live" label with a breathing dot, above a live message. */
@Composable
fun VibeLivePill(modifier: Modifier = Modifier) {
  val inf = rememberInfiniteTransition(label = "livePill")
  val breathe by inf.animateFloat(
    0.55f, 1f,
    infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "breathe"
  )
  Row(
    modifier
      .padding(top = 2.dp, bottom = 2.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(VibeBlurple.copy(alpha = 0.14f))
      .padding(horizontal = 7.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      Modifier
        .size(7.dp)
        .graphicsLayer { scaleX = breathe; scaleY = breathe; alpha = breathe }
        .clip(CircleShape)
        .background(VibeBlurple)
    )
    Spacer(Modifier.width(5.dp))
    Text("typing live", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = VibeBlurple)
    LiveDots(VibeBlurple)
  }
}

@Composable
private fun LiveDots(color: Color) {
  val inf = rememberInfiniteTransition(label = "dots")
  val t by inf.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "t")
  Row(Modifier.padding(start = 2.dp)) {
    for (i in 0..2) {
      val lift = ((t - i).let { if (it < 0) it + 3 else it }).let { if (it < 1f) kotlin.math.sin(it * Math.PI).toFloat() else 0f }
      Text(".", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.graphicsLayer { translationY = -lift * 5f })
    }
  }
}
