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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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

enum class VibeEmit { Burst, Rise, Fall, Cannons }
enum class VibeParticle { Glyph, Confetti, Ember, Spark, Dot }

private fun c(vararg v: Long) = v.map { Color(it) }

/**
 * Vibe's send effects (long-press send). [code] is what travels with the
 * message (see [mark]); the look is Vibe's: the big animated emoji pops up
 * in the middle while particles fill the screen in the effect's own way.
 */
enum class VibeEffect(
  val code: Int,
  val label: String,
  val emoji: String,
  val emit: VibeEmit,
  val glyphs: List<String>,
  val colors: List<Color>,
  val kinds: List<VibeParticle>,
  val count: Int,
  val pickable: Boolean = true,
) {
  Like(5, "Like", "👍", VibeEmit.Burst, listOf("👍", "👍", "✨"), c(0xFFFFD54F, 0xFFFFB74D, 0xFF8C96F6),
    listOf(VibeParticle.Glyph, VibeParticle.Glyph, VibeParticle.Spark, VibeParticle.Dot), 36),
  Love(2, "Love", "❤️", VibeEmit.Rise, listOf("❤️", "💕", "💖", "💗"), c(0xFFFF5A5F, 0xFFFF80AB, 0xFFFFC1E3),
    listOf(VibeParticle.Glyph, VibeParticle.Glyph, VibeParticle.Glyph, VibeParticle.Dot), 30),
  Fire(6, "Fire", "🔥", VibeEmit.Rise, listOf("🔥"), c(0xFFFF7043, 0xFFFFB300, 0xFFFF3D00),
    listOf(VibeParticle.Ember, VibeParticle.Ember, VibeParticle.Ember, VibeParticle.Glyph), 48),
  Party(1, "Party", "🎉", VibeEmit.Cannons, listOf("🎉", "🎊"), c(0xFFFF5252, 0xFFFFD740, 0xFF69F0AE, 0xFF40C4FF, 0xFFE040FB),
    listOf(VibeParticle.Confetti, VibeParticle.Confetti, VibeParticle.Confetti, VibeParticle.Glyph), 70),
  Lol(7, "LOL", "😂", VibeEmit.Fall, listOf("😂", "🤣"), c(0xFF4FC3F7, 0xFFFFC83D),
    listOf(VibeParticle.Glyph, VibeParticle.Glyph, VibeParticle.Dot), 30),
  Dislike(8, "Dislike", "👎", VibeEmit.Fall, listOf("👎"), c(0xFF90A4AE, 0xFFB0BEC5),
    listOf(VibeParticle.Glyph, VibeParticle.Glyph, VibeParticle.Dot), 22),
  Poop(9, "Poop", "💩", VibeEmit.Fall, listOf("💩"), c(0xFF8D6E63, 0xFFA1887F),
    listOf(VibeParticle.Glyph, VibeParticle.Glyph, VibeParticle.Dot), 24),
  Balloons(4, "Balloons", "🎈", VibeEmit.Rise, listOf("🎈"), c(0xFFFF5252, 0xFF40C4FF, 0xFFFFD740),
    listOf(VibeParticle.Glyph), 20),
  // sent by earlier Vibe X versions
  Fireworks(3, "Fireworks", "🎆", VibeEmit.Burst, listOf("🎆", "✨"), c(0xFF5865F2, 0xFFEB459E, 0xFFFEE75C, 0xFF57F287),
    listOf(VibeParticle.Spark, VibeParticle.Spark, VibeParticle.Dot, VibeParticle.Glyph), 60, pickable = false);

  companion object {
    private const val MARK = "\u2063\u2063"
    private const val TICK = '\u200B'
    private val tail = Regex("\u2063\u2063(\u200B{1,12})$")

    fun of(text: String): VibeEffect? {
      val m = tail.find(text) ?: return null
      val code = m.groupValues[1].length
      return entries.firstOrNull { it.code == code }
    }

    fun strip(text: String): String = text.replace(tail, "")

    val picker: List<VibeEffect> get() = entries.filter { it.pickable }
  }

  fun mark(text: String): String = strip(text) + MARK + TICK.toString().repeat(code)
}

object VibeEffects {
  class Play(val effect: VibeEffect, val id: Long, val now: Boolean)
  val current = mutableStateOf<Play?>(null)
  private val played = HashSet<Long>()

  /** Plays [effect] over the chat; it waits a moment for the message to slide in, unless [now]. */
  fun play(effect: VibeEffect, now: Boolean = false) {
    current.value = Play(effect, System.nanoTime(), now)
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
    VibeHaptics.light()
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
      key(fx.id) {
        EffectView(fx.effect, size, fx.now) { if (VibeEffects.current.value === fx) VibeEffects.current.value = null }
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

private class Particle(
  val kind: VibeParticle,
  val sx: Float, val sy: Float,
  val vx: Float, val vy: Float, // px per run
  val size: Float, // dp
  val delay: Float, val life: Float,
  val spin: Float, val sway: Float,
  val color: Color,
  val glyph: String?,
)

private fun easeOutBack(t: Float): Float {
  val c1 = 1.70158f
  val c3 = c1 + 1f
  return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
}

private fun easeIn(t: Float) = t * t * t

private fun makeParticles(e: VibeEffect, area: androidx.compose.ui.geometry.Rect, o: Offset, d: Float): List<Particle> {
  val r = Random
  val w = area.width
  val h = area.height
  return List(e.count) { i ->
    val kind = e.kinds[r.nextInt(e.kinds.size)]
    var size = 6f + r.nextFloat() * 8f
    var glyph: String? = null
    if (kind == VibeParticle.Glyph) {
      size = 20f + r.nextFloat() * 20f
      glyph = e.glyphs[r.nextInt(e.glyphs.size)]
    }
    var sx = o.x
    var sy = o.y
    var vx = 0f
    var vy = 0f
    var delay = r.nextFloat() * 0.25f
    var life = 0.55f + r.nextFloat() * 0.3f
    when (e.emit) {
      VibeEmit.Burst -> {
        val a = r.nextFloat() * PI.toFloat() * 2
        val speed = (120f + r.nextFloat() * 220f) * d
        vx = cos(a) * speed
        vy = sin(a) * speed
        delay = 0.06f + r.nextFloat() * 0.12f
      }
      VibeEmit.Rise -> {
        sx = area.left + r.nextFloat() * w
        sy = area.bottom + 30f * d
        vx = (r.nextFloat() - 0.5f) * 80f * d
        vy = -(h * (0.55f + r.nextFloat() * 0.5f))
        delay = r.nextFloat() * 0.35f
      }
      VibeEmit.Fall -> {
        sx = area.left + r.nextFloat() * w
        sy = area.top - 40f * d
        vx = (r.nextFloat() - 0.5f) * 60f * d
        vy = h * (0.7f + r.nextFloat() * 0.5f)
        delay = r.nextFloat() * 0.4f
      }
      VibeEmit.Cannons -> {
        val left = i % 2 == 0
        sx = if (left) area.left - 10f * d else area.right + 10f * d
        sy = area.top + h * 0.85f
        val a = if (left) -PI.toFloat() / 2 + 0.25f + r.nextFloat() * 0.6f else -PI.toFloat() / 2 - 0.25f - r.nextFloat() * 0.6f
        val speed = h * (0.55f + r.nextFloat() * 0.45f)
        vx = cos(a) * speed
        vy = sin(a) * speed
        delay = r.nextFloat() * 0.12f
        life = 0.7f + r.nextFloat() * 0.25f
      }
    }
    Particle(kind, sx, sy, vx, vy, size, delay, life, (r.nextFloat() - 0.5f) * 12f, r.nextFloat() * PI.toFloat() * 2, e.colors[r.nextInt(e.colors.size)], glyph)
  }
}

/**
 * Vibe's effect player: the big animated emoji pops up in the middle of the
 * chat, wobbles and shrinks away, while the particles fill the screen.
 * Starts once the message has slid into the timeline, so the two never fight.
 */
@Composable
private fun EffectView(effect: VibeEffect, size: IntSize, now: Boolean, done: () -> Unit) {
  val dens = LocalDensity.current
  val d = dens.density
  val p = remember { Animatable(0f) }
  var started by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    if (!now) delay(520)
    started = true
    VibeHaptics.medium()
    launch { delay(160); VibeHaptics.light() }
    p.animateTo(1f, tween(2600, easing = LinearEasing))
    done()
  }
  val w = size.width.toFloat()
  val h = size.height.toFloat()
  // between the top bar and the message box
  val area = remember(size) { androidx.compose.ui.geometry.Rect(0f, h * 0.1f, w, h * 0.86f) }
  val origin = area.center
  val ps = remember(size) { makeParticles(effect, area, origin, d) }
  val measurer = rememberTextMeasurer()
  val layouts = remember { HashMap<String, androidx.compose.ui.text.TextLayoutResult>() }
  val gravity = when (effect.emit) {
    VibeEmit.Burst -> 160f
    VibeEmit.Cannons -> 520f
    VibeEmit.Rise -> -40f
    VibeEmit.Fall -> 120f
  } * d
  val glow = effect.colors.first()
  if (!started) return
  Canvas(Modifier.fillMaxSize()) {
    val t = p.value
    val g = if (t < 0.15f) t / 0.15f else (1f - (t - 0.15f) / 0.6f).coerceIn(0f, 1f)
    if (g > 0f) {
      drawCircle(
        Brush.radialGradient(listOf(glow.copy(alpha = 0.32f * g), Color.Transparent), center = origin, radius = 160f * d),
        radius = 160f * d, center = origin
      )
    }
    for (pt in ps) {
      val u = ((t - pt.delay) / pt.life).coerceIn(0f, 1f)
      if (u <= 0f || u >= 1f) continue
      val x = pt.sx + pt.vx * u + 18f * d * sin(pt.sway + u * 6f)
      val y = pt.sy + pt.vy * u + gravity * u * u
      val fadeIn = (u / 0.08f).coerceIn(0f, 1f)
      val fadeOut = 1f - easeIn(((u - 0.65f) / 0.35f).coerceIn(0f, 1f))
      val a = (fadeIn * fadeOut).coerceIn(0f, 1f)
      if (a <= 0f) continue
      val sz = pt.size * d
      when (pt.kind) {
        VibeParticle.Dot -> drawCircle(pt.color.copy(alpha = a), sz * 0.4f, Offset(x, y))
        VibeParticle.Spark -> {
          val rr = sz * (1f - 0.3f * u) + 2f * d
          val path = Path().apply {
            moveTo(x, y - rr)
            quadraticTo(x, y, x + rr, y)
            quadraticTo(x, y, x, y + rr)
            quadraticTo(x, y, x - rr, y)
            quadraticTo(x, y, x, y - rr)
            close()
          }
          drawPath(path, pt.color.copy(alpha = a))
        }
        VibeParticle.Ember -> {
          val flicker = 0.65f + 0.35f * sin(t * 50f + pt.spin)
          val rr = sz * 0.55f * (1f - 0.5f * u) * 1.6f
          drawCircle(
            Brush.radialGradient(listOf(pt.color.copy(alpha = a * flicker), pt.color.copy(alpha = 0f)), center = Offset(x, y), radius = rr),
            radius = rr, center = Offset(x, y)
          )
        }
        VibeParticle.Confetti -> {
          rotate(pt.spin * u * 57.3f, Offset(x, y)) {
            val sy = abs(cos(pt.sway + u * 14f)) * 0.8f + 0.2f
            val cw = sz * 1.2f
            val ch = sz * 0.5f * sy
            drawRect(pt.color.copy(alpha = a), topLeft = Offset(x - cw / 2, y - ch / 2), size = Size(cw, ch))
          }
        }
        VibeParticle.Glyph -> {
          val gl = pt.glyph ?: continue
          val key = gl + pt.size.toInt()
          val layout = layouts.getOrPut(key) {
            measurer.measure(gl, TextStyle(fontFamily = EmojiFont, fontSize = (pt.size / dens.fontScale).sp))
          }
          val sc = 0.7f + 0.3f * easeOutBack(fadeIn)
          rotate(0.25f * sin(pt.sway + u * 5f) * 57.3f, Offset(x, y)) {
            scale(sc, Offset(x, y)) {
              drawText(layout, topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f), alpha = a)
            }
          }
        }
      }
    }
  }
  // the big animated emoji
  val big = 150.dp
  val bigPx = with(dens) { big.toPx() }
  VibeAnimatedEmoji(
    effect.emoji, big,
    Modifier.graphicsLayer {
      val t = p.value
      var scale: Float
      var alpha = 1f
      if (t < 0.14f) {
        scale = easeOutBack(t / 0.14f)
      } else if (t < 0.62f) {
        scale = 1f + 0.05f * sin((t - 0.14f) / 0.48f * PI.toFloat() * 3f)
      } else {
        val u = ((t - 0.62f) / 0.22f).coerceIn(0f, 1f)
        scale = 1f - 0.5f * u * u
        alpha = 1f - u
      }
      translationX = origin.x - bigPx / 2
      translationY = origin.y - bigPx / 2
      scaleX = scale.coerceIn(0f, 2f)
      scaleY = scale.coerceIn(0f, 2f)
      this.alpha = alpha.coerceIn(0f, 1f)
    }
  )
}

/** The small effect emoji next to a message sent with one: tap to play it again. */
@Composable
fun VibeEffectBadge(effect: VibeEffect, modifier: Modifier = Modifier) {
  Text(
    effect.emoji,
    modifier
      .clip(androidx.compose.foundation.shape.CircleShape)
      .clickable { VibeEffects.play(effect, now = true) }
      .padding(start = 8.dp, top = 6.dp, end = 6.dp, bottom = 2.dp),
    style = burstEmojiStyle, fontSize = 13.sp
  )
}

/** Plays the effect for a message that just came in. */
@Composable
fun VibeEffectTrigger(item: ChatItem) {
  LaunchedEffect(item.id, item.meta.isLive, item.content.text) {
    delay(150)
    VibeEffects.maybePlay(item)
  }
}
