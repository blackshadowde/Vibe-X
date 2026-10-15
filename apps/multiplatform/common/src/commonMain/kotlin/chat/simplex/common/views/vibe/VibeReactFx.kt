package chat.simplex.common.views.vibe

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import chat.simplex.common.ui.theme.EmojiFont
import kotlin.math.*
import kotlin.random.Random

/**
 * Vibe's reaction animations (ported from Vibe's VibeHeartHost):
 * - ❤️ (double tap, or someone hearts your message): a big heart pops at the
 *   tap, tiny hearts fly out, then it flies down into the ❤ chip,
 *   Google Messages style.
 * - any other reaction: the message flashes in the emoji's colours, a
 *   shockwave ring, themed particles (tears, confetti, flames, sparkles…),
 *   and the emoji pops up big, wobbles, and flies into its chip.
 * Positions are in root coordinates.
 */
object VibeReactFx {
  sealed class Fx(val id: Long)
  class Heart(id: Long, val origin: Offset, val target: Offset) : Fx(id)
  class Reaction(id: Long, val emoji: String, val rect: Rect, val origin: Offset, val target: Offset) : Fx(id)

  val list = mutableStateListOf<Fx>()
  private var next = 0L
  private var lastHeart = 0L

  fun heart(origin: Offset, target: Offset) {
    lastHeart = System.currentTimeMillis()
    if (list.size > 4) list.removeAt(0)
    list.add(Heart(next++, origin, target))
  }

  /** A heart from the menu / someone else, unless a double tap just played one. */
  fun heartIfQuiet(origin: Offset, target: Offset) {
    if (System.currentTimeMillis() - lastHeart > 3000) heart(origin, target)
  }

  fun reaction(emoji: String, rect: Rect) {
    if (list.size > 4) list.removeAt(0)
    val origin = Offset(rect.left + rect.width * 0.5f, rect.top + rect.height * 0.4f)
    val target = Offset(rect.left + 86f * density, rect.bottom - 20f * density)
    list.add(Reaction(next++, emoji, rect, origin, target))
  }

  /** Set by the overlay (px per dp). */
  internal var density = 3f
}

private val heartColors = listOf(Color(0xFFFF5A5F), Color(0xFFFF7A7A), Color(0xFFFFB3B3), Color(0xFFFF4D6D), Color(0xFFFFC2D1))

private val unitHeart: List<Offset> = (0..72).map { i ->
  val a = i / 72f * 2f * PI.toFloat()
  val x = 16f * sin(a).pow(3)
  val y = 13f * cos(a) - 5f * cos(2 * a) - 2f * cos(3 * a) - cos(4 * a)
  Offset(x / 17f, -y / 17f)
}

private fun heartPath(c: Offset, size: Float): Path = Path().apply {
  unitHeart.forEachIndexed { i, o ->
    val p = c + o * (size / 2f)
    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
  }
  close()
}

private fun easeOutBack(t: Float): Float {
  val c1 = 1.70158f
  val c3 = c1 + 1f
  return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
}

private fun easeInOutCubic(t: Float) = if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
private fun easeOutCubic(t: Float) = 1 - (1 - t).pow(3)
private fun easeIn(t: Float) = t * t

@Composable
fun VibeReactFxLayer(origin: Offset) {
  for (fx in VibeReactFx.list) {
    key(fx.id) {
      when (fx) {
        is VibeReactFx.Heart -> HeartView(fx, origin)
        is VibeReactFx.Reaction -> ReactionView(fx, origin)
      }
    }
  }
}

private class Tiny(val angle: Float, val speed: Float, val size: Float, val delay: Float, val color: Color)

@Composable
private fun HeartView(fx: VibeReactFx.Heart, root: Offset) {
  val t = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    t.animateTo(1f, tween(1700, easing = LinearEasing))
    VibeReactFx.list.remove(fx)
  }
  val d = VibeReactFx.density
  val tiny = remember {
    List(16) {
      Tiny(
        -PI.toFloat() / 2 + (Random.nextFloat() - 0.5f) * PI.toFloat() * 1.7f,
        (70f + Random.nextFloat() * 110f) * d,
        (8f + Random.nextFloat() * 9f) * d,
        0.1f + Random.nextFloat() * 0.12f,
        heartColors[Random.nextInt(heartColors.size)]
      )
    }
  }
  Canvas(Modifier.fillMaxSize()) {
    val v = t.value
    val origin = fx.origin - root
    val target = fx.target - root
    for (h in tiny) {
      val u = ((v - h.delay) / 0.55f).coerceIn(0f, 1f)
      if (u <= 0f || u >= 1f) continue
      val e = easeOutCubic(u)
      val pos = origin + Offset(cos(h.angle), sin(h.angle)) * (h.speed * e) + Offset(0f, 40f * d * u * u)
      drawPath(heartPath(pos, h.size * (1 - 0.3f * u)), h.color.copy(alpha = (1 - easeIn(u)).coerceIn(0f, 1f)))
    }
    val pos: Offset
    val scale: Float
    var alpha = 1f
    when {
      v < 0.15f -> { pos = origin; scale = easeOutBack(v / 0.15f) }
      v < 0.42f -> { pos = origin; scale = 1f }
      v < 0.78f -> {
        val u = easeInOutCubic((v - 0.42f) / 0.36f)
        pos = lerp(origin, target, u); scale = 1f - 0.8f * u
      }
      else -> {
        val u = (v - 0.78f) / 0.22f
        pos = target; scale = 0.2f + 0.05f * sin(u * PI.toFloat()); alpha = (1 - easeIn(u)).coerceIn(0f, 1f)
      }
    }
    if (scale <= 0f || alpha <= 0f) return@Canvas
    val size = 96f * d * scale
    drawPath(heartPath(pos, size * 1.25f), Color(0xFFFF6B6B).copy(alpha = 0.25f * alpha))
    drawPath(heartPath(pos, size), Color(0xFFFF5A5F).copy(alpha = alpha))
    drawPath(heartPath(pos, size * 0.62f), Color(0xFFFFB3B8).copy(alpha = alpha))
  }
}

private enum class PKind { Dot, Star, Drop, Confetti, Glyph, Ember }

private class Theme(val colors: List<Color>, val kinds: List<PKind>, val glyphs: List<String> = emptyList(), val rise: Boolean = false)

private fun themeFor(e: String): Theme {
  fun any(chars: String) = chars.split(" ").any { e.contains(it) }
  return when {
    any("😂 🤣 😆 😹") -> Theme(listOf(Color(0xFFFFC83D), Color(0xFF4FC3F7), Color(0xFF29B6F6)), listOf(PKind.Drop, PKind.Drop, PKind.Glyph))
    any("😢 😭 🥲 😿 💔") -> Theme(listOf(Color(0xFF64B5F6), Color(0xFF90CAF9), Color(0xFF1E88E5)), listOf(PKind.Drop, PKind.Drop, PKind.Dot))
    any("😮 😲 😯 🤯 😱 🙀") -> Theme(listOf(Color(0xFFB9F6CA), Color(0xFFFFF59D), Color(0xFF80DEEA)), listOf(PKind.Star, PKind.Star, PKind.Glyph), listOf("❗", "❕"))
    any("🔥 💥 🌶 🚀") -> Theme(listOf(Color(0xFFFF7043), Color(0xFFFFB300), Color(0xFFFF3D00)), listOf(PKind.Ember, PKind.Ember, PKind.Glyph), rise = true)
    any("🎉 🥳 🎊 🎂 🍾") -> Theme(listOf(Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB)), listOf(PKind.Confetti, PKind.Confetti, PKind.Confetti, PKind.Glyph))
    any("😡 🤬 😠 👿 💢") -> Theme(listOf(Color(0xFFFF5252), Color(0xFFFF8A65), Color(0xFFD50000)), listOf(PKind.Dot, PKind.Glyph), listOf("💢"))
    any("👍 👏 🙏 💪 ✅ 👌 🤝") -> Theme(listOf(Color(0xFFFFB74D), Color(0xFFF48FB1), Color(0xFFFFD54F)), listOf(PKind.Glyph, PKind.Glyph, PKind.Star))
    any("👎") -> Theme(listOf(Color(0xFF90A4AE), Color(0xFFB0BEC5), Color(0xFF78909C)), listOf(PKind.Glyph, PKind.Dot))
    any("😍 🥰 😘 💕 💖 💗 💘 💞 🧡 💛 💚 💙 💜 🤍 🖤 ❤") -> Theme(listOf(Color(0xFFFF80AB), Color(0xFFFF4081), Color(0xFFFFC1E3)), listOf(PKind.Glyph, PKind.Glyph, PKind.Dot), listOf("💕", "💖", "💗"))
    else -> Theme(listOf(Color(0xFF8C96F6), Color(0xFFFFD54F), Color(0xFF80DEEA)), listOf(PKind.Glyph, PKind.Star, PKind.Dot))
  }
}

private class P(val kind: PKind, val angle: Float, val speed: Float, val size: Float, val delay: Float, val spin: Float, val color: Color, val glyph: String?)

@Composable
private fun ReactionView(fx: VibeReactFx.Reaction, root: Offset) {
  val t = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    t.animateTo(1f, tween(1900, easing = LinearEasing))
    VibeReactFx.list.remove(fx)
  }
  val theme = remember { themeFor(fx.emoji) }
  val d = VibeReactFx.density
  val ps = remember {
    val glyphs = if (theme.glyphs.isEmpty()) listOf(fx.emoji) else theme.glyphs
    List(22) {
      val kind = theme.kinds[Random.nextInt(theme.kinds.size)]
      val glyph = if (kind == PKind.Glyph) glyphs[Random.nextInt(glyphs.size)] else null
      val size = if (kind == PKind.Glyph) 18f + Random.nextFloat() * 14f else 6f + Random.nextFloat() * 8f
      val spread = if (theme.rise) PI.toFloat() * 0.9f else PI.toFloat() * 2f
      val base = if (theme.rise) -PI.toFloat() / 2f else 0f
      P(
        kind, base + (Random.nextFloat() - 0.5f) * spread, (90f + Random.nextFloat() * 120f), size,
        0.08f + Random.nextFloat() * 0.14f, (Random.nextFloat() - 0.5f) * 10f,
        theme.colors[Random.nextInt(theme.colors.size)], glyph
      )
    }
  }
  val measurer = rememberTextMeasurer()
  val bigLayout = remember { measurer.measure(fx.emoji, TextStyle(fontFamily = EmojiFont, fontSize = 84.sp)) }
  val glyphLayouts = remember {
    ps.mapNotNull { p -> p.glyph?.let { g -> (g + p.size) to measurer.measure(g, TextStyle(fontFamily = EmojiFont, fontSize = (p.size).sp)) } }.toMap()
  }
  Canvas(Modifier.fillMaxSize()) {
    val v = t.value
    val origin = fx.origin - root
    val target = fx.target - root
    val rect = fx.rect.translate(-root)
    // 1. bubble flash (Google Messages)
    val flash = when {
      v < 0.12f -> v / 0.12f
      v < 0.5f -> 1f
      else -> (1f - (v - 0.5f) / 0.3f).coerceIn(0f, 1f)
    }
    if (flash > 0f) {
      val r = rect.deflate(4f * VibeReactFx.density)
      drawRoundRect(
        Brush.linearGradient(
          listOf(theme.colors[0].copy(alpha = 0.30f * flash), theme.colors[1 % theme.colors.size].copy(alpha = 0.22f * flash)),
          start = r.topLeft, end = r.bottomRight
        ),
        topLeft = r.topLeft, size = r.size, cornerRadius = CornerRadius(14f * VibeReactFx.density)
      )
    }
    // 2. shockwave ring
    val ringT = ((v - 0.08f) / 0.35f).coerceIn(0f, 1f)
    if (ringT > 0f && ringT < 1f) {
      val e = easeOutCubic(ringT)
      drawCircle(
        theme.colors[0].copy(alpha = 0.7f * (1 - e)), (30f + 90f * e) * d, origin,
        style = Stroke(width = (4f * (1 - e) + 0.5f) * VibeReactFx.density)
      )
    }
    // 3. particles
    for (p in ps) {
      val u = ((v - p.delay) / 0.6f).coerceIn(0f, 1f)
      if (u <= 0f || u >= 1f) continue
      val e = easeOutCubic(u)
      val gravity = (if (theme.rise) -50f else 70f) * u * u * d
      val pos = origin + Offset(cos(p.angle), sin(p.angle)) * (p.speed * d * e) + Offset(0f, gravity)
      val alpha = (1 - easeIn(u)).coerceIn(0f, 1f)
      val c = p.color.copy(alpha = alpha)
      val sz = p.size * d
      when (p.kind) {
        PKind.Dot -> drawCircle(c, sz * 0.45f * (1 - 0.4f * u), pos)
        PKind.Star -> drawPath(star(pos, sz * (1 - 0.3f * u) + 2f), c)
        PKind.Drop -> drawPath(drop(pos, sz * 0.5f), c)
        PKind.Ember -> drawCircle(c.copy(alpha = alpha * (0.7f + 0.3f * sin(v * 40f + p.spin))), sz * 0.5f * (1 - 0.5f * u), pos)
        PKind.Confetti -> rotate(p.spin * u * 57.3f, pos) {
          drawRect(c, topLeft = Offset(pos.x - sz / 2, pos.y - sz * 0.225f), size = Size(sz, sz * 0.45f))
        }
        PKind.Glyph -> {
          val layout = glyphLayouts[(p.glyph ?: "") + p.size] ?: continue
          val sc = 0.6f + 0.4f * easeOutBack(e).coerceAtMost(1.2f)
          withTransform({
            translate(pos.x, pos.y)
            rotate(p.spin * 0.08f * u * 57.3f, Offset.Zero)
            scale(sc, sc, Offset.Zero)
          }) {
            drawText(layout, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f), alpha = alpha)
          }
        }
      }
    }
    // 4. the big emoji: pop, wobble, fly into the chip
    var pos: Offset
    var scale: Float
    var alpha = 1f
    var rot = 0f
    when {
      v < 0.16f -> { pos = origin; scale = easeOutBack(v / 0.16f) }
      v < 0.46f -> {
        val u = (v - 0.16f) / 0.30f
        pos = origin - Offset(0f, 6f * VibeReactFx.density * sin(u * PI.toFloat()))
        scale = 1f + 0.06f * sin(u * PI.toFloat() * 3)
        rot = 0.12f * sin(u * PI.toFloat() * 4) * (1 - u)
      }
      v < 0.8f -> {
        val u = easeInOutCubic((v - 0.46f) / 0.34f)
        pos = lerp(origin, target, u) - Offset(0f, 40f * VibeReactFx.density * sin(u * PI.toFloat()))
        scale = 1f - 0.78f * u
      }
      else -> {
        val u = (v - 0.8f) / 0.2f
        pos = target; scale = 0.22f + 0.06f * sin(u * PI.toFloat()); alpha = (1 - easeIn(u)).coerceIn(0f, 1f)
      }
    }
    if (scale <= 0f || alpha <= 0f) return@Canvas
    drawCircle(theme.colors[0].copy(alpha = 0.25f * alpha), bigLayout.size.width * 0.6f * scale, pos)
    withTransform({
      translate(pos.x, pos.y)
      rotate(rot * 57.3f, Offset.Zero)
      scale(scale, scale, Offset.Zero)
    }) {
      drawText(bigLayout, topLeft = Offset(-bigLayout.size.width / 2f, -bigLayout.size.height / 2f), alpha = alpha)
    }
  }
}

private fun star(c: Offset, r: Float) = Path().apply {
  moveTo(c.x, c.y - r)
  quadraticBezierTo(c.x, c.y, c.x + r, c.y)
  quadraticBezierTo(c.x, c.y, c.x, c.y + r)
  quadraticBezierTo(c.x, c.y, c.x - r, c.y)
  quadraticBezierTo(c.x, c.y, c.x, c.y - r)
  close()
}

private fun drop(c: Offset, r: Float) = Path().apply {
  moveTo(c.x, c.y - r * 1.6f)
  quadraticBezierTo(c.x + r * 1.1f, c.y - r * 0.2f, c.x + r, c.y + r * 0.3f)
  quadraticBezierTo(c.x, c.y + r * 1.4f, c.x - r, c.y + r * 0.3f)
  quadraticBezierTo(c.x - r * 1.1f, c.y - r * 0.2f, c.x, c.y - r * 1.6f)
  close()
}
