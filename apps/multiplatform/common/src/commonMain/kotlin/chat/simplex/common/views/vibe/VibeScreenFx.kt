package chat.simplex.common.views.vibe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import chat.simplex.common.ui.theme.EmojiFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

/* ---------------------------------------------------------------------------
 * Hot words: some words fill the whole screen when they're sent or arrive,
 * like in Google Messages and iMessage, each with its own little scene.
 * ------------------------------------------------------------------------ */

enum class VibeScene(val seconds: Float) {
  Laugh(2.8f),      // hahaha: big "HA"s pop all over and shake with laughter
  Congrats(3.2f),   // congratulations: a gold shimmer sweeps by, streamers curl down
  Kiss(2.8f),       // kiss: lips fly in, stamp the middle, hearts puff out
  Hug(3.0f),        // hug: two warm arms close around the middle and squeeze
  Party(3.4f),      // party: disco ball, sweeping coloured beams, confetti
  Birthday(3.6f),   // happy birthday: balloons float up past a glowing cake
  GoodNight(3.4f),  // good night: the screen dims to night, moon, stars, Zz
  Love(3.0f),       // love you: a big heart beats twice and breaks into little ones
}

object VibeHotWords {
  private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)
  private val rules = listOf(
    VibeScene.Birthday to r("""happy\s*b(irth)?day|\bhbd\b|🎂|janmadin|जन्मदिन|పుట్టినరోజు"""),
    VibeScene.Congrats to r("""congrat|\bcongo\b|badhai|बधाई|అభినందన"""),
    VibeScene.GoodNight to r("""good\s*night|\bgud\s*n(i|y)te?\b|\bgn8\b|shubh\s*ratri|शुभ\s*रात्रि|శుభ\s*రాత్రి|😴|🌙"""),
    VibeScene.Love to r("""\bi\s*love\s*(you|u)\b|\blove\s*(you|u)\b|\bily\b|\bluv\s*(you|u)\b|❤️❤️|💖|💗"""),
    VibeScene.Kiss to r("""\bkiss(es|y)?\b|\bmwa+h*\b|\bumm?ah+\b|😘|💋|😚"""),
    VibeScene.Hug to r("""\bhugs?\b|\*hug|\bjhappi\b|🤗|🫂"""),
    VibeScene.Party to r("""\bpart(y|ee)+\b|\bcelebrat|🥳|🪩"""),
    VibeScene.Laugh to r("""\b(ha){3,}h?\b|\b(he){3,}\b|\blo+l+\b|\blmf?a+o+\b|\brofl\b|😂|🤣"""),
  )

  fun match(text: String): VibeScene? {
    val t = VibeEffect.strip(text).trim()
    if (t.isEmpty() || t.length > 400) return null
    return rules.firstOrNull { it.second.containsMatchIn(t) }?.first
  }
}

object VibeScreenFx {
  class Play(val scene: VibeScene, val id: Long)
  val current = mutableStateOf<Play?>(null)

  fun play(scene: VibeScene) {
    current.value = Play(scene, System.nanoTime())
  }
}

@Composable
fun VibeScreenFxHost() {
  val p = VibeScreenFx.current.value ?: return
  key(p.id) {
    Scene(p.scene) { if (VibeScreenFx.current.value === p) VibeScreenFx.current.value = null }
  }
}

private val emojiStyle = TextStyle(fontFamily = EmojiFont)

private class Glyphs(val m: TextMeasurer, val fontScale: Float) {
  private val cache = HashMap<String, TextLayoutResult>()
  fun get(text: String, sizeSp: Float, bold: Boolean = false, color: Color = Color.Unspecified): TextLayoutResult =
    cache.getOrPut("$text|$sizeSp|$bold|${color.value}") {
      m.measure(
        text,
        if (bold) TextStyle(fontSize = (sizeSp / fontScale).sp, fontWeight = FontWeight.Black, color = color)
        else emojiStyle.copy(fontSize = (sizeSp / fontScale).sp)
      )
    }
}

private fun DrawScope.glyph(g: TextLayoutResult, at: Offset, alpha: Float = 1f, s: Float = 1f, deg: Float = 0f) {
  if (alpha <= 0f || s <= 0f) return
  rotate(deg, at) {
    scale(s, at) {
      drawText(g, topLeft = Offset(at.x - g.size.width / 2f, at.y - g.size.height / 2f), alpha = alpha.coerceIn(0f, 1f))
    }
  }
}

private fun heart(c: Offset, w: Float): Path = Path().apply {
  moveTo(c.x, c.y + w * 0.36f)
  cubicTo(c.x - w * 0.62f, c.y - w * 0.02f, c.x - w * 0.36f, c.y - w * 0.6f, c.x, c.y - w * 0.24f)
  cubicTo(c.x + w * 0.36f, c.y - w * 0.6f, c.x + w * 0.62f, c.y - w * 0.02f, c.x, c.y + w * 0.36f)
  close()
}

private fun sparkle(c: Offset, r: Float): Path = Path().apply {
  moveTo(c.x, c.y - r)
  quadraticTo(c.x, c.y, c.x + r, c.y)
  quadraticTo(c.x, c.y, c.x, c.y + r)
  quadraticTo(c.x, c.y, c.x - r, c.y)
  quadraticTo(c.x, c.y, c.x, c.y - r)
  close()
}

private fun backOut(t: Float): Float {
  val c1 = 1.9f
  val c3 = c1 + 1f
  return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
}

private fun smooth(t: Float) = t * t * (3f - 2f * t)
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun fadeInOut(t: Float, inEnd: Float, outStart: Float) = min(seg(t, 0f, inEnd), 1f - seg(t, outStart, 1f))

@Composable
private fun Scene(scene: VibeScene, done: () -> Unit) {
  val p = remember { Animatable(0f) }
  val dens = LocalDensity.current
  val measurer = rememberTextMeasurer(cacheSize = 32)
  val g = remember { Glyphs(measurer, dens.fontScale) }
  LaunchedEffect(Unit) {
    // haptics that match the scene
    launch {
      when (scene) {
        VibeScene.Laugh -> repeat(5) { VibeHaptics.light(); delay(140) }
        VibeScene.Kiss -> { delay(900); VibeHaptics.medium() }
        VibeScene.Hug -> { delay(500); VibeHaptics.light(); delay(450); VibeHaptics.heavy() }
        VibeScene.Party -> repeat(8) { VibeHaptics.selection(); delay(260) }
        VibeScene.Love -> repeat(2) { VibeHaptics.light(); delay(150); VibeHaptics.medium(); delay(550) }
        else -> VibeHaptics.medium()
      }
    }
    p.animateTo(1f, tween((scene.seconds * 1000).toInt(), easing = LinearEasing))
    done()
  }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val d = dens.density
    val w = constraints.maxWidth.toFloat()
    val h = constraints.maxHeight.toFloat()
    val seed = remember { Random.nextInt() }
    Canvas(Modifier.fillMaxSize()) {
      val t = p.value
      val rnd = Random(seed)
      when (scene) {
        VibeScene.Laugh -> laugh(t, rnd, g, w, h, d)
        VibeScene.Congrats -> congrats(t, rnd, g, w, h, d)
        VibeScene.Kiss -> kiss(t, rnd, g, w, h, d)
        VibeScene.Hug -> hug(t, rnd, g, w, h, d)
        VibeScene.Party -> party(t, rnd, g, w, h, d)
        VibeScene.Birthday -> birthday(t, rnd, g, w, h, d)
        VibeScene.GoodNight -> goodNight(t, rnd, g, w, h, d)
        VibeScene.Love -> love(t, rnd, g, w, h, d)
      }
    }
  }
}

/* --- hahaha: "HA"s pop all over, shake with laughter and float away; 😂 bounce up from the bottom --- */
private fun DrawScope.laugh(t: Float, r: Random, g: Glyphs, w: Float, h: Float, d: Float) {
  val colors = listOf(Color(0xFFFFC83D), Color(0xFFFF8A3D), Color(0xFFFF5FA2), Color(0xFF8C96F6), Color(0xFF4FC3F7))
  repeat(14) { i ->
    val x = w * (0.12f + r.nextFloat() * 0.76f)
    val y = h * (0.16f + r.nextFloat() * 0.62f)
    val size = 34f + r.nextFloat() * 34f
    val tilt = (r.nextFloat() - 0.5f) * 36f
    val c = colors[r.nextInt(colors.size)]
    val word = if (r.nextInt(4) == 0) "HA!" else "HA"
    val u = seg(t, i * 0.035f, i * 0.035f + 0.62f)
    if (u <= 0f || u >= 1f) return@repeat
    val pop = backOut(seg(u, 0f, 0.22f))
    val shake = sin(u * 40f + i) * 8f * (1f - u)
    val a = 1f - seg(u, 0.7f, 1f)
    glyph(g.get(word, size, bold = true, color = c), Offset(x, y - u * 40f * d), a, pop, tilt + shake)
  }
  // a few 😂 bouncing like balls
  repeat(4) { i ->
    val x0 = w * (0.15f + 0.23f * i)
    val u = seg(t, 0.05f + i * 0.08f, 0.95f)
    if (u <= 0f || u >= 1f) return@repeat
    // three bounces, each lower
    val k = u * 3f
    val n = floor(k)
    val f = k - n
    val peak = h * 0.38f * 0.55f.pow(n)
    val y = h * 0.86f - peak * 4f * f * (1f - f)
    val squash = if (f < 0.08f || f > 0.92f) 0.85f else 1f
    glyph(g.get("😂", 44f), Offset(x0 + u * 30f * d * (if (i % 2 == 0) 1 else -1), y), 1f - seg(u, 0.8f, 1f), squash, sin(u * 12f) * 14f)
  }
}

/* --- congratulations: a gold shimmer sweeps across, curly streamers fall, sparkles twinkle --- */
private fun DrawScope.congrats(t: Float, r: Random, g: Glyphs, w: Float, h: Float, d: Float) {
  val gold = Color(0xFFFFD54F)
  // the shimmer band, corner to corner
  val s = seg(t, 0.05f, 0.55f)
  if (s > 0f && s < 1f) {
    val x = -w * 0.6f + s * w * 2.2f
    drawRect(
      Brush.linearGradient(
        listOf(Color.Transparent, gold.copy(alpha = 0.0f), gold.copy(alpha = 0.38f), Color.White.copy(alpha = 0.45f), gold.copy(alpha = 0.38f), Color.Transparent),
        start = Offset(x, 0f), end = Offset(x + w * 0.5f, h * 0.35f)
      ),
      size = Size(w, h)
    )
  }
  val colors = listOf(Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFE040FB), gold)
  // streamers: wavy ribbons falling and twisting
  repeat(16) { i ->
    val x0 = w * r.nextFloat()
    val delayT = r.nextFloat() * 0.3f
    val len = (60f + r.nextFloat() * 60f) * d
    val c = colors[r.nextInt(colors.size)]
    val phase = r.nextFloat() * 6f
    val u = seg(t, delayT, delayT + 0.7f)
    if (u <= 0f || u >= 1f) return@repeat
    val y0 = -len + u * (h + len * 2f)
    val path = Path()
    val steps = 14
    for (k in 0..steps) {
      val q = k / steps.toFloat()
      val x = x0 + sin(phase + q * 7f + u * 10f) * 12f * d
      val y = y0 + q * len
      if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, c.copy(alpha = 1f - seg(u, 0.8f, 1f)), style = Stroke(width = 5f * d, cap = StrokeCap.Round))
  }
  // twinkling gold sparkles
  repeat(18) { i ->
    val c = Offset(w * r.nextFloat(), h * (0.1f + r.nextFloat() * 0.8f))
    val start = r.nextFloat() * 0.6f
    val u = seg(t, start, start + 0.3f)
    if (u <= 0f || u >= 1f) return@repeat
    val tw = sin(u * PI.toFloat())
    drawPath(sparkle(c, (6f + 10f * tw) * d), (if (i % 3 == 0) Color.White else gold).copy(alpha = tw))
  }
  // the trophy pops in the middle
  val u = seg(t, 0.12f, 0.85f)
  if (u > 0f && u < 1f) {
    val pop = backOut(seg(u, 0f, 0.25f))
    glyph(g.get("🏆", 96f), Offset(w / 2, h * 0.45f), 1f - seg(u, 0.75f, 1f), pop, sin(u * 10f) * 4f)
  }
}

/* --- kiss: lips fly in on a curve, stamp the middle with a pink ring, hearts puff out --- */
private fun DrawScope.kiss(t: Float, r: Random, g: Glyphs, w: Float, h: Float, d: Float) {
  val center = Offset(w / 2, h * 0.45f)
  val fly = seg(t, 0f, 0.32f)
  val start = Offset(-60f * d, h * 0.92f)
  val ctrl = Offset(w * 0.1f, h * 0.2f)
  val e = 1f - (1f - fly).pow(3)
  val pos = Offset(
    (1 - e) * (1 - e) * start.x + 2 * (1 - e) * e * ctrl.x + e * e * center.x,
    (1 - e) * (1 - e) * start.y + 2 * (1 - e) * e * ctrl.y + e * e * center.y
  )
  val stamp = seg(t, 0.32f, 0.42f)
  val lipsScale = if (fly < 1f) 0.6f + 0.5f * fly else 1.6f - 0.6f * smooth(stamp)
  val lipsAlpha = 1f - seg(t, 0.8f, 1f)
  // the ring when it lands
  val ring = seg(t, 0.34f, 0.7f)
  if (ring > 0f && ring < 1f) {
    drawCircle(Color(0xFFFF4F8B).copy(alpha = 0.55f * (1f - ring)), (40f + 160f * ring) * d, center, style = Stroke(width = (10f * (1f - ring) + 1f) * d))
    drawCircle(Brush.radialGradient(listOf(Color(0x55FF4F8B), Color.Transparent), center, 180f * d * ring + 1f), 180f * d * ring + 1f, center)
  }
  glyph(g.get("💋", 88f), pos, lipsAlpha, lipsScale, if (fly < 1f) (1f - fly) * -40f else 0f)
  // little hearts puff out in a ring and drift up
  repeat(12) { i ->
    val a = i / 12f * 2f * PI.toFloat() + r.nextFloat() * 0.3f
    val speed = (110f + r.nextFloat() * 90f) * d
    val u = seg(t, 0.38f, 0.38f + 0.5f + r.nextFloat() * 0.1f)
    if (u <= 0f || u >= 1f) return@repeat
    val k = 1f - (1f - u).pow(2)
    val c = Offset(center.x + cos(a) * speed * k, center.y + sin(a) * speed * k - 60f * d * u * u)
    val col = if (i % 2 == 0) Color(0xFFFF4F8B) else Color(0xFFFF8FB1)
    drawPath(heart(c, (14f + 8f * sin(u * PI.toFloat())) * d), col.copy(alpha = 1f - seg(u, 0.6f, 1f)))
  }
}

/* --- hug: warm glow, two arms sweep in from the sides and close round the middle, then a squeeze --- */
private fun DrawScope.hug(t: Float, r: Random, g: Glyphs, w: Float, h: Float, d: Float) {
  val center = Offset(w / 2, h * 0.45f)
  val warm = fadeInOut(t, 0.2f, 0.75f)
  drawRect(Brush.radialGradient(listOf(Color(0x66FFB74D), Color(0x33FF8A65), Color.Transparent), center, max(w, h) * 0.75f), alpha = warm)
  val close = smooth(seg(t, 0.1f, 0.45f))
  val squeeze = seg(t, 0.45f, 0.62f)
  val sq = 1f - 0.12f * sin(squeeze * PI.toFloat())
  val radius = (150f - 30f * close) * d * sq
  val arms = 1f - seg(t, 0.78f, 0.95f)
  if (arms > 0f) {
    val brushL = Brush.linearGradient(listOf(Color(0xFFFFB74D), Color(0xFFFF7A9A)), Offset(center.x - radius, center.y), Offset(center.x, center.y + radius))
    val rect = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    val sweep = 150f * close
    // left arm comes round from the left, right arm from the right
    drawArc(brushL, 180f, -sweep, false, rect.topLeft, rect.size, alpha = arms, style = Stroke(width = 22f * d, cap = StrokeCap.Round))
    drawArc(brushL, 0f, sweep, false, rect.topLeft, rect.size, alpha = arms, style = Stroke(width = 22f * d, cap = StrokeCap.Round))
    drawArc(brushL, 180f, sweep * 0.9f, false, rect.topLeft, rect.size, alpha = arms * 0.5f, style = Stroke(width = 12f * d, cap = StrokeCap.Round))
    drawArc(brushL, 0f, -sweep * 0.9f, false, rect.topLeft, rect.size, alpha = arms * 0.5f, style = Stroke(width = 12f * d, cap = StrokeCap.Round))
  }
  val face = seg(t, 0.05f, 0.9f)
  if (face > 0f && face < 1f) {
    val pop = backOut(seg(face, 0f, 0.3f))
    glyph(g.get("🤗", 92f), center, 1f - seg(face, 0.8f, 1f), pop * sq)
  }
  // warm little hearts rise after the squeeze
  repeat(10) { i ->
    val u = seg(t, 0.55f + r.nextFloat() * 0.1f, 1f)
    val x = center.x + (r.nextFloat() - 0.5f) * w * 0.7f
    val hs = (12f + r.nextFloat() * 10f) * d
    if (u <= 0f || u >= 1f) return@repeat
    val y = center.y + 80f * d - u * h * 0.4f
    drawPath(heart(Offset(x + sin(u * 8f + i) * 10f * d, y), hs), Color(0xFFFF8A65).copy(alpha = 1f - u))
  }
}

/* --- party: lights dim, a disco ball spins, coloured beams sweep, spots dance, confetti falls --- */
private fun DrawScope.party(t: Float, r: Random, g: Glyphs, w: Float, h: Float, d: Float) {
  val on = fadeInOut(t, 0.12f, 0.82f)
  drawRect(Color.Black.copy(alpha = 0.32f * on), size = Size(w, h))
  val ball = Offset(w / 2, h * 0.12f)
  val beams = listOf(Color(0xFFFF4081), Color(0xFF40C4FF), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFFE040FB), Color(0xFFFF6E40))
  beams.forEachIndexed { i, c ->
    val ang = (i * 60f + t * 260f) * (PI.toFloat() / 180f)
    val tip = Offset(ball.x + cos(ang) * h, ball.y + abs(sin(ang)) * h + h * 0.2f)
    val spread = 0.12f
    val l = Offset(ball.x + cos(ang - spread) * h, ball.y + abs(sin(ang - spread)) * h + h * 0.2f)
    val rr = Offset(ball.x + cos(ang + spread) * h, ball.y + abs(sin(ang + spread)) * h + h * 0.2f)
    val path = Path().apply { moveTo(ball.x, ball.y); lineTo(l.x, l.y); lineTo(rr.x, rr.y); close() }
    drawPath(path, Brush.linearGradient(listOf(c.copy(alpha = 0.42f * on), Color.Transparent), ball, tip))
    // the spot where the beam lands
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.5f * on), Color.Transparent), tip, 70f * d), 70f * d, tip)
  }
  // the disco ball, its facets glittering
  if (on > 0f) {
    val br = 30f * d
    drawLine(Color.White.copy(alpha = 0.6f * on), Offset(ball.x, 0f), Offset(ball.x, ball.y - br), strokeWidth = 2f * d)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF5F5F5), Color(0xFF9E9E9E)), Offset(ball.x - br * 0.3f, ball.y - br * 0.3f), br * 1.4f), br, ball, alpha = on)
    repeat(14) { k ->
      val a = k * 0.9f + t * 9f
      val fx = ball.x + cos(a) * br * 0.65f * ((k % 3) / 3f + 0.4f)
      val fy = ball.y + sin(a * 1.3f) * br * 0.65f
      drawPath(sparkle(Offset(fx, fy), (3f + 3f * abs(sin(a * 2f + t * 20f))) * d), Color.White.copy(alpha = on))
    }
  }
  // confetti
  val colors = beams
  repeat(40) {
    val x0 = w * r.nextFloat()
    val start = r.nextFloat() * 0.5f
    val c = colors[r.nextInt(colors.size)]
    val spin = r.nextFloat() * 10f
    val u = seg(t, start, start + 0.55f)
    if (u <= 0f || u >= 1f) return@repeat
    val x = x0 + sin(u * 8f + spin) * 18f * d
    val y = -20f * d + u * (h + 40f * d)
    rotate(spin * 40f + u * 600f, Offset(x, y)) {
      val sy = abs(cos(spin + u * 14f)) * 0.8f + 0.2f
      drawRect(c, Offset(x - 6f * d, y - 3f * d * sy), Size(12f * d, 6f * d * sy), alpha = 1f - seg(u, 0.85f, 1f))
    }
  }
  val u = seg(t, 0.15f, 0.8f)
  if (u > 0f && u < 1f) glyph(g.get("🥳", 96f), Offset(w / 2, h * 0.5f), 1f - seg(u, 0.75f, 1f), backOut(seg(u, 0f, 0.25f)), sin(u * 14f) * 10f)
}

/* --- happy birthday: balloons on strings float up past a cake whose candles glow --- */
private fun DrawScope.birthday(t: Float, r: Random, g: Glyphs, w: Float, h: Float, d: Float) {
  val center = Offset(w / 2, h * 0.5f)
  val cake = seg(t, 0.05f, 0.92f)
  if (cake > 0f && cake < 1f) {
    val glow = 0.6f + 0.4f * sin(t * 40f)
    drawCircle(Brush.radialGradient(listOf(Color(0x66FFC107).copy(alpha = 0.4f * glow), Color.Transparent), Offset(center.x, center.y - 30f * d), 160f * d), 160f * d, Offset(center.x, center.y - 30f * d), alpha = 1f - seg(cake, 0.8f, 1f))
    glyph(g.get("🎂", 104f), center, 1f - seg(cake, 0.8f, 1f), backOut(seg(cake, 0f, 0.25f)))
  }
  val colors = listOf(Color(0xFFFF5252), Color(0xFF40C4FF), Color(0xFFFFD740), Color(0xFF69F0AE), Color(0xFFE040FB), Color(0xFFFF8A65))
  repeat(11) { i ->
    val x0 = w * (0.06f + r.nextFloat() * 0.88f)
    val start = r.nextFloat() * 0.35f
    val c = colors[i % colors.size]
    val bw = (38f + r.nextFloat() * 18f) * d
    val u = seg(t, start, start + 0.62f)
    if (u <= 0f || u >= 1f) return@repeat
    val x = x0 + sin(u * 5f + i) * 22f * d
    val y = h + bw * 2f - u * (h + bw * 5f)
    // string
    val s = Path().apply {
      moveTo(x, y + bw * 0.62f)
      for (k in 1..6) lineTo(x + sin(u * 10f + k) * 5f * d, y + bw * 0.62f + k * 14f * d)
    }
    drawPath(s, Color.White.copy(alpha = 0.7f), style = Stroke(width = 1.5f * d))
    // balloon, its knot and a highlight
    drawOval(c, Offset(x - bw / 2, y - bw * 0.62f), Size(bw, bw * 1.22f))
    drawOval(Color.White.copy(alpha = 0.35f), Offset(x - bw * 0.3f, y - bw * 0.48f), Size(bw * 0.22f, bw * 0.36f))
    val knot = Path().apply { moveTo(x - 5f * d, y + bw * 0.66f); lineTo(x + 5f * d, y + bw * 0.66f); lineTo(x, y + bw * 0.56f); close() }
    drawPath(knot, c)
  }
}

/* --- good night: the screen goes to night, a moon rises, stars twinkle, Zz drift up --- */
private fun DrawScope.goodNight(t: Float, r: Random, g: Glyphs, w: Float, h: Float, d: Float) {
  val night = fadeInOut(t, 0.18f, 0.8f)
  drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1033).copy(alpha = 0.78f), Color(0xFF1A1F4A).copy(alpha = 0.45f), Color.Transparent)), alpha = night, size = Size(w, h))
  repeat(34) {
    val c = Offset(w * r.nextFloat(), h * r.nextFloat() * 0.75f)
    val ph = r.nextFloat() * 6f
    val tw = 0.5f + 0.5f * sin(t * 18f + ph)
    val rr = (1.5f + r.nextFloat() * 2.5f) * d
    if (r.nextInt(4) == 0) drawPath(sparkle(c, rr * 3f * tw), Color.White.copy(alpha = night * tw))
    else drawCircle(Color.White.copy(alpha = night * (0.4f + 0.6f * tw)), rr, c)
  }
  // the crescent moon rises a little
  val rise = smooth(seg(t, 0.05f, 0.45f))
  val mc = Offset(w * 0.72f, h * 0.28f - 30f * d * rise)
  val mr = 48f * d
  drawCircle(Brush.radialGradient(listOf(Color(0x55FFF3B0), Color.Transparent), mc, mr * 2.4f), mr * 2.4f, mc, alpha = night)
  val moon = Path.combine(
    PathOperation.Difference,
    Path().apply { addOval(Rect(mc, mr)) },
    Path().apply { addOval(Rect(Offset(mc.x + mr * 0.45f, mc.y - mr * 0.25f), mr * 0.9f)) }
  )
  drawPath(moon, Color(0xFFFFF3B0).copy(alpha = night))
  // Zz drifting up from the moon
  repeat(4) { i ->
    val u = seg(t, 0.25f + i * 0.12f, 0.25f + i * 0.12f + 0.45f)
    if (u <= 0f || u >= 1f) return@repeat
    val p = Offset(mc.x - mr * 0.8f - u * 60f * d + sin(u * 6f) * 10f * d, mc.y + mr - u * 110f * d)
    glyph(g.get(if (i % 2 == 0) "Z" else "z", 26f + i * 6f, bold = true, color = Color.White), p, sin(u * PI.toFloat()) * night, 1f, -12f)
  }
}

/* --- love you: a big heart beats twice (lub-dub), sends heart ripples, then breaks into little hearts --- */
private fun DrawScope.love(t: Float, r: Random, g: Glyphs, w: Float, h: Float, d: Float) {
  val center = Offset(w / 2, h * 0.45f)
  fun beat(at: Float, strength: Float): Float {
    val u = seg(t, at, at + 0.09f)
    return strength * sin(u * PI.toFloat())
  }
  val appear = backOut(seg(t, 0f, 0.14f))
  val pulse = beat(0.16f, 0.12f) + beat(0.24f, 0.2f) + beat(0.42f, 0.12f) + beat(0.50f, 0.22f)
  val burst = seg(t, 0.62f, 0.72f)
  val size = 150f * d * appear * (1f + pulse) * (1f - burst)
  if (size > 1f) {
    drawCircle(Brush.radialGradient(listOf(Color(0x66FF3B5C), Color.Transparent), center, size * 1.6f), size * 1.6f, center)
    drawPath(heart(center, size), Brush.verticalGradient(listOf(Color(0xFFFF6B8B), Color(0xFFE91E63)), center.y - size * 0.6f, center.y + size * 0.4f))
    drawPath(heart(Offset(center.x - size * 0.16f, center.y - size * 0.12f), size * 0.26f), Color.White.copy(alpha = 0.25f))
  }
  // heart-shaped ripples on each dub
  for (at in listOf(0.24f, 0.50f)) {
    val u = seg(t, at, at + 0.35f)
    if (u > 0f && u < 1f) drawPath(heart(center, 150f * d * (1f + u * 1.3f)), Color(0xFFFF3B5C).copy(alpha = 0.5f * (1f - u)), style = Stroke(width = 3f * d))
  }
  // breaks into little hearts that float up
  repeat(18) { i ->
    val a = r.nextFloat() * 2f * PI.toFloat()
    val sp = (60f + r.nextFloat() * 150f) * d
    val u = seg(t, 0.64f, 0.64f + 0.3f + r.nextFloat() * 0.06f)
    val hs = (16f + r.nextFloat() * 14f) * d
    if (u <= 0f || u >= 1f) return@repeat
    val k = 1f - (1f - u).pow(2)
    val c = Offset(center.x + cos(a) * sp * k, center.y + sin(a) * sp * k - 90f * d * u)
    drawPath(heart(c, hs), (if (i % 3 == 0) Color(0xFFFF8FB1) else Color(0xFFFF3B5C)).copy(alpha = 1f - u))
  }
}
