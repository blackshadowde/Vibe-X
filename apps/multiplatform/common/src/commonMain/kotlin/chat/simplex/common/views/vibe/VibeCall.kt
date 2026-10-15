package chat.simplex.common.views.vibe

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The middle of a Vibe voice call: their big picture with rings rippling
 * out while it rings, and a soft green glow that breathes once you're
 * connected (like Discord's speaking ring), then their name.
 */
@Composable
fun VibeCallHero(image: String?, name: String, connected: Boolean) {
  val inf = rememberInfiniteTransition(label = "callHero")
  val ripple by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing)), label = "ripple")
  val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
  val size = 168.dp
  Box(Modifier.size(size + 80.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
      val r0 = size.toPx() / 2
      val c = center
      if (connected) {
        drawCircle(VibeColors.green.copy(alpha = 0.18f + 0.18f * breathe), r0 + 10.dp.toPx() + 8.dp.toPx() * breathe, c)
        drawCircle(VibeColors.green, r0 + 5.dp.toPx(), c, style = Stroke(3.dp.toPx()))
      } else {
        for (k in 0..2) {
          val t = (ripple + k / 3f) % 1f
          drawCircle(Color.White.copy(alpha = 0.22f * (1f - t)), r0 + 40.dp.toPx() * t, c, style = Stroke(2.dp.toPx()))
        }
      }
    }
    VibeAvatar(image, name, size)
  }
  Spacer(Modifier.height(8.dp))
  Text(name, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
  Spacer(Modifier.height(4.dp))
}
