package chat.simplex.common.views.vibe

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatItem
import chat.simplex.common.ui.theme.EmojiFont
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

/* ---------------------------------------------------------------------------
 * Send effects. The effect rides on the message as a few invisible characters
 * at the end of its text, so other SimpleX apps just show the message, and
 * Vibe X shows the effect full screen when it arrives.
 * ------------------------------------------------------------------------ */

enum class VibeEffect(val code: Int, val label: String, val emoji: String) {
  Confetti(1, "Confetti", "🎉"),
  Hearts(2, "Hearts", "💖"),
  Fireworks(3, "Fireworks", "🎆"),
  Balloons(4, "Balloons", "🎈");

  companion object {
    private const val MARK = "⁣⁣"
    private const val TICK = '​'
    private val tail = Regex("⁣⁣(​{1,8})$")

    fun of(text: String): VibeEffect? {
      val m = tail.find(text) ?: return null
      val code = m.groupValues[1].length
      return entries.firstOrNull { it.code == code }
    }

    fun strip(text: String): String = text.replace(tail, "")
  }

  fun mark(text: String): String = strip(text) + MARK + TICK.toString().repeat(code)
}

object VibeEffects {
  val current = mutableStateOf<Pair<VibeEffect, Long>?>(null)
  private val played = HashSet<Long>()

  fun play(effect: VibeEffect) {
    current.value = effect to System.nanoTime()
  }

  /** Play the effect of a message that just arrived (or was just sent), once. */
  fun maybePlay(item: ChatItem) {
    // your own effects play when you send them
    if (item.meta.isLive || item.id < 0 || item.chatDir.sent) return
    val effect = VibeEffect.of(item.content.text) ?: return
    // a live message is created when typing starts, so go by its last update
    if ((kotlinx.datetime.Clock.System.now() - item.meta.updatedAt).inWholeSeconds > 30) return
    if (synchronized(played) { !played.add(item.id) }) return
    play(effect)
  }
}

/* ---------------------------------------------------------------------------
 * Reaction bursts: a little firework of the emoji from where you tapped.
 * ------------------------------------------------------------------------ */

object VibeBurst {
  class Burst(val id: Long, val emoji: String, val at: Offset, val big: Boolean)
  val bursts = mutableStateListOf<Burst>()
  private var next = 0L

  /** [at] is in root coordinates. */
  fun fire(emoji: String, at: Offset, big: Boolean = false) {
    if (bursts.size > 6) bursts.removeAt(0)
    bursts.add(Burst(next++, emoji, at, big))
  }
}

private val burstEmojiStyle = TextStyle(fontFamily = EmojiFont)

/** Draws bursts and send effects over whatever it is placed on. */
@Composable
fun VibeOverlay(modifier: Modifier = Modifier) {
  var origin by remember { mutableStateOf(Offset.Zero) }
  var size by remember { mutableStateOf(IntSize.Zero) }
  Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot(); size = it.size }) {
    VibeReactFx.density = LocalDensity.current.density
    VibeReactFxLayer(origin)
    for (b in VibeBurst.bursts) {
      key(b.id) { BurstView(b, b.at - origin) { VibeBurst.bursts.remove(b) } }
    }
    val fx = VibeEffects.current.value
    if (fx != null && size.width > 0) {
      key(fx.second) {
        EffectView(fx.first, size) { if (VibeEffects.current.value == fx) VibeEffects.current.value = null }
      }
    }
  }
}

@Composable
private fun BurstView(b: VibeBurst.Burst, center: Offset, done: () -> Unit) {
  val p = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    p.animateTo(1f, tween(if (b.big) 1300 else 950, easing = LinearOutSlowInEasing))
    done()
  }
  val density = LocalDensity.current
  val count = if (b.big) 16 else 10
  val parts = remember {
    List(count) {
      val angle = (it.toFloat() / count) * 2 * PI.toFloat() + Random.nextFloat() * 0.5f
      Triple(angle, 0.6f + Random.nextFloat() * 0.6f, Random.nextFloat() * 50f - 25f)
    }
  }
  val reach = with(density) { (if (b.big) 150 else 78).sp.toPx() }
  val half = with(density) { 11.sp.toPx() }
  val v = p.value
  // the centre pop
  Text(
    b.emoji,
    style = burstEmojiStyle,
    fontSize = if (b.big) 44.sp else 26.sp,
    modifier = Modifier.graphicsLayer {
      val s = if (v < 0.25f) v / 0.25f * 1.5f else 1.5f - (v - 0.25f) * 1.2f
      translationX = center.x - half * 1.4f
      translationY = center.y - half * 1.4f - v * 40f
      scaleX = s; scaleY = s
      alpha = (1f - v * 1.3f).coerceIn(0f, 1f)
    }
  )
  for ((angle, speed, spin) in parts) {
    Text(
      b.emoji,
      style = burstEmojiStyle,
      fontSize = 16.sp,
      modifier = Modifier.graphicsLayer {
        val e = 1f - (1f - v).pow(3)
        val d = reach * speed * e
        translationX = center.x - half + cos(angle) * d
        translationY = center.y - half + sin(angle) * d + 120f * v * v
        rotationZ = spin * v * 6
        val s = 0.4f + 0.9f * sin(PI.toFloat() * min(1f, v * 1.4f))
        scaleX = s; scaleY = s
        alpha = (1f - v).coerceIn(0f, 1f)
      }
    )
  }
}

private val confettiColors = listOf(
  Color(0xFF5865F2), Color(0xFFEB459E), Color(0xFFFEE75C), Color(0xFF57F287), Color(0xFFED4245), Color(0xFF3BA5FF)
)

@Composable
private fun EffectView(effect: VibeEffect, size: IntSize, done: () -> Unit) {
  val duration = 3400
  val p = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    p.animateTo(1f, tween(duration, easing = LinearEasing))
    done()
  }
  val w = size.width.toFloat()
  val h = size.height.toFloat()
  when (effect) {
    VibeEffect.Confetti -> {
      val bits = remember {
        List(110) {
          floatArrayOf(
            Random.nextFloat() * w, // x
            -Random.nextFloat() * h * 0.6f, // start y
            0.55f + Random.nextFloat() * 0.7f, // fall speed
            Random.nextFloat() * 360f, // rotation
            (Random.nextFloat() - 0.5f) * 900f, // spin
            Random.nextFloat() * 6.28f, // sway phase
            Random.nextInt(confettiColors.size).toFloat(),
          )
        }
      }
      Canvas(Modifier.fillMaxSize()) {
        val v = p.value
        val fade = if (v > 0.8f) (1f - v) / 0.2f else 1f
        for (b in bits) {
          val y = b[1] + v * h * 1.5f * b[2]
          val x = b[0] + sin(b[5] + v * 9f) * 30f
          rotate(b[3] + b[4] * v, Offset(x, y)) {
            drawRect(
              confettiColors[b[6].toInt()].copy(alpha = fade),
              topLeft = Offset(x - 9f, y - 5f),
              size = Size(18f, 10f * abs(cos(v * 14f + b[5])) + 2f)
            )
          }
        }
      }
    }
    VibeEffect.Fireworks -> {
      val shells = remember {
        List(6) {
          floatArrayOf(
            w * (0.15f + Random.nextFloat() * 0.7f),
            h * (0.15f + Random.nextFloat() * 0.4f),
            it * 0.11f, // start
            Random.nextInt(confettiColors.size).toFloat(),
          )
        }
      }
      Canvas(Modifier.fillMaxSize()) {
        val v = p.value
        for (s in shells) {
          val t = ((v - s[2]) / 0.45f)
          if (t <= 0f || t >= 1f) continue
          val color = confettiColors[s[3].toInt()]
          val rise = (t / 0.3f).coerceAtMost(1f)
          if (t < 0.3f) {
            // the rocket going up
            drawCircle(color, 6f, Offset(s[0], h - (h - s[1]) * rise))
          } else {
            val e = (t - 0.3f) / 0.7f
            val r = 260f * (1f - (1f - e).pow(2))
            for (i in 0 until 26) {
              val a = i / 26f * 2 * PI.toFloat()
              val c = Offset(s[0] + cos(a) * r, s[1] + sin(a) * r + 90f * e * e)
              drawCircle(color.copy(alpha = (1f - e)), 7f * (1f - e * 0.6f), c)
              drawCircle(Color.White.copy(alpha = (1f - e) * 0.7f), 2.5f, c)
            }
          }
        }
      }
    }
    VibeEffect.Hearts, VibeEffect.Balloons -> {
      val emojis = if (effect == VibeEffect.Hearts) listOf("💖", "❤️", "💕", "💗", "💘") else listOf("🎈", "🎈", "🎈", "🎉")
      val items = remember {
        List(26) {
          floatArrayOf(
            Random.nextFloat() * w,
            Random.nextFloat() * 0.45f, // delay
            0.7f + Random.nextFloat() * 0.6f, // speed
            Random.nextFloat() * 6.28f,
            Random.nextInt(emojis.size).toFloat(),
            if (effect == VibeEffect.Hearts) 22f + Random.nextFloat() * 22f else 34f + Random.nextFloat() * 20f
          )
        }
      }
      val v = p.value
      for (it in items) {
        val t = ((v - it[1]) / (1f - it[1])).coerceIn(0f, 1f)
        if (t <= 0f) continue
        Text(
          emojis[it[4].toInt()],
          style = burstEmojiStyle,
          fontSize = it[5].sp,
          modifier = Modifier.graphicsLayer {
            translationX = it[0] + sin(it[3] + t * 7f) * 40f
            translationY = h - t * (h + 200f) * it[2]
            rotationZ = sin(it[3] + t * 5f) * 18f
            val pop = if (t < 0.12f) t / 0.12f else 1f
            scaleX = pop; scaleY = pop
            alpha = if (t > 0.8f) (1f - t) / 0.2f else 1f
          }
        )
      }
    }
  }
}

/** Plays the effect for a message that just came in. */
@Composable
fun VibeEffectTrigger(item: ChatItem) {
  LaunchedEffect(item.id, item.meta.isLive, item.content.text) {
    delay(150)
    VibeEffects.maybePlay(item)
  }
}
