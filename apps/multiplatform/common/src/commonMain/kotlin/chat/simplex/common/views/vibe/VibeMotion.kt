package chat.simplex.common.views.vibe

import androidx.compose.animation.core.*
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.*
import androidx.compose.material.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import chat.simplex.common.platform.appPreferences
import kotlinx.coroutines.*

/* ---------------------------------------------------------------------------
 * Motion and touch, the way Vibe does it (Apple's "Designing Fluid
 * Interfaces"): everything moves on springs, keeps the finger's speed, and
 * settles like a real object; every tap is felt.
 * ------------------------------------------------------------------------ */

/** Haptics, as in Vibe (selection / light / medium / heavy); off with Settings > Appearance > Haptic feedback. */
object VibeHaptics {
  enum class Kind { Selection, Light, Medium, Heavy, Success }

  private var last = 0L

  val enabled: Boolean get() = runCatching { appPreferences.vibeHaptics.get() }.getOrDefault(true)

  private fun fire(kind: Kind) {
    if (!enabled) return
    val now = System.currentTimeMillis()
    // two within 70 ms are one touch
    if (now - last < 70) return
    last = now
    runCatching { vibePerformHaptic(kind) }
  }

  fun selection() = fire(Kind.Selection)
  fun light() = fire(Kind.Light)
  fun medium() = fire(Kind.Medium)
  fun heavy() = fire(Kind.Heavy)
  fun success() = fire(Kind.Success)
}

expect fun vibePerformHaptic(kind: VibeHaptics.Kind)

/** Apple-like springs: response (seconds) and damping, as in SwiftUI's .spring(response:dampingFraction:). */
object VibeSprings {
  /** stiffness for a spring that settles in about [response] seconds */
  private fun k(response: Float) = (2f * Math.PI.toFloat() / response).let { it * it }

  fun <T> of(response: Float, damping: Float): SpringSpec<T> = spring(dampingRatio = damping, stiffness = k(response))

  /** pages and panes: smooth, no overshoot (UIKit's push) */
  fun <T> page(): SpringSpec<T> = of(0.42f, 1f)
  /** panes rising from the bottom: a hint of life as they land */
  fun <T> sheet(): SpringSpec<T> = of(0.40f, 0.9f)
  /** things that pop in (bubbles, reactions, badges) */
  fun <T> pop(): SpringSpec<T> = of(0.45f, 0.62f)
  /** messages entering the timeline */
  fun <T> message(): SpringSpec<T> = of(0.62f, 0.78f)
  /** a pressed control springing back */
  fun <T> release(): SpringSpec<T> = of(0.32f, 0.52f)
  /** a control being pressed */
  fun <T> press(): SpringSpec<T> = of(0.16f, 1f)
}

/**
 * Every clickable in Vibe X: the ripple, plus Vibe's touch. It squeezes a
 * little while held and springs back on release, with a tick you feel. Like
 * Telegram, the squeeze waits for the finger to stay put a moment, so
 * scrolling a list never squeezes (or buzzes) the row under your finger.
 */
private class VibeIndication(private val ripple: IndicationNodeFactory) : IndicationNodeFactory {
  override fun create(interactionSource: InteractionSource): DelegatableNode =
    VibeIndicationNode(interactionSource, ripple.create(interactionSource))

  override fun equals(other: Any?) = other is VibeIndication && other.ripple == ripple
  override fun hashCode() = ripple.hashCode()
}

private class VibeIndicationNode(
  private val source: InteractionSource,
  rippleNode: DelegatableNode,
) : DelegatingNode(), DrawModifierNode {
  init { delegate(rippleNode) }

  private val squeeze = Animatable(1f)
  private var pressJob: Job? = null

  private fun animate(to: Float, spec: AnimationSpec<Float>) {
    coroutineScope.launch { squeeze.animateTo(to, spec) { invalidateDraw() } }
  }

  override fun onAttach() {
    coroutineScope.launch {
      source.interactions.collect { i ->
        when (i) {
          is PressInteraction.Press -> {
            pressJob?.cancel()
            pressJob = coroutineScope.launch {
              delay(70)
              VibeHaptics.selection()
              squeeze.animateTo(0.965f, VibeSprings.press()) { invalidateDraw() }
            }
          }
          is PressInteraction.Release -> {
            val quick = pressJob?.isActive == true
            pressJob?.cancel()
            pressJob = null
            if (quick) {
              // a quick tap: still felt, and a brief squeeze so it looks pressed
              VibeHaptics.light()
              coroutineScope.launch {
                squeeze.animateTo(0.965f, tween(70)) { invalidateDraw() }
                squeeze.animateTo(1f, VibeSprings.release()) { invalidateDraw() }
              }
            } else animate(1f, VibeSprings.release())
          }
          is PressInteraction.Cancel -> {
            pressJob?.cancel()
            pressJob = null
            animate(1f, VibeSprings.release())
          }
        }
      }
    }
  }

  override fun ContentDrawScope.draw() {
    val s = squeeze.value
    if (s != 1f) scale(s) { this@draw.drawContent() } else drawContent()
  }
}

/** Gives everything under it Vibe's touch (see [VibeIndication]). */
@Composable
fun VibeMotionRoot(content: @Composable () -> Unit) {
  val indication = remember { VibeIndication(ripple()) }
  CompositionLocalProvider(LocalIndication provides indication) { content() }
}

/**
 * Vibe's press for things that aren't plain clickables (icons in a row,
 * avatars, cards with their own gestures): squeezes while held, springs
 * back, and ticks. Doesn't take the touch: taps and long presses still work.
 */
fun Modifier.vibePressable(scale: Float = 0.94f): Modifier = composed {
  val s = remember { Animatable(1f) }
  val scope = rememberCoroutineScope()
  this
    .pointerInput(Unit) {
      awaitPointerEventScope {
        while (true) {
          val down = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
          val first = down.changes.firstOrNull() ?: continue
          if (!first.pressed || first.previousPressed) continue
          val start = first.position
          val job = scope.launch {
            delay(70)
            VibeHaptics.selection()
            s.animateTo(scale, VibeSprings.press())
          }
          var moved = false
          while (true) {
            val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val c = e.changes.firstOrNull { it.id == first.id } ?: break
            if ((c.position - start).getDistance() > 8 * density) moved = true
            if (moved || !c.pressed) break
          }
          val quick = job.isActive
          job.cancel()
          scope.launch {
            if (quick && !moved) {
              VibeHaptics.light()
              s.animateTo(scale, tween(70))
            }
            s.animateTo(1f, VibeSprings.release())
          }
        }
      }
    }
    .graphicsLayer { scaleX = s.value; scaleY = s.value }
}
