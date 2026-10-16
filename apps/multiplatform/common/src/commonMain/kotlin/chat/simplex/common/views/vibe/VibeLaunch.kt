package chat.simplex.common.views.vibe

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.chatModel
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Opening the app: the Vibe X icon, the bubble stays still and its three
 * dots hop once, like someone typing, then it opens. The app loads behind
 * it the whole time; the dots only hop again if loading takes longer.
 */
object VibeLaunch {
  /** Show the opening on the next frame (a fresh launch). */
  val pending = mutableStateOf(true)
}

// the dots in the 384 px icon: centres and radius
private val dotX = floatArrayOf(120f, 184.5f, 249f)
private const val dotY = 179f
private const val dotR = 18.5f

@Composable
fun VibeLaunchOverlay() {
  if (!VibeLaunch.pending.value) return
  val hop = remember { List(3) { Animatable(0f) } }
  val out = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    var rounds = 0
    while (true) {
      // one wave across the dots: each rises and lands, a beat after the one before
      val jobs = hop.mapIndexed { i, a ->
        launch {
          delay(i * 70L)
          a.animateTo(1f, tween(150, easing = CubicBezierEasing(0.2f, 0f, 0.3f, 1f)))
          a.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 900f))
        }
      }
      jobs.forEach { it.join() }
      rounds++
      val ready = chatModel.chatDbStatus.value != null
      // at most ~3 s, then open whatever the state
      if (ready || rounds >= 6) break
      delay(120)
    }
    out.animateTo(1f, tween(220, easing = FastOutLinearInEasing))
    VibeLaunch.pending.value = false
  }
  val bg = VibeColors.surface()
  Box(
    Modifier.fillMaxSize().graphicsLayer { alpha = 1f - out.value }.background(bg),
    contentAlignment = Alignment.Center
  ) {
    Box(
      Modifier.size(150.dp).graphicsLayer {
        val s = 1f + 0.12f * out.value
        scaleX = s; scaleY = s
      }
    ) {
      Image(painterResource(MR.images.vibe_logo_nodots), null, Modifier.fillMaxSize())
      Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 384f
        for (i in 0 until 3) {
          val lift = hop[i].value * 16f * k
          drawCircle(Color.White, dotR * k, Offset(dotX[i] * k, dotY * k - lift))
        }
      }
    }
  }
}
