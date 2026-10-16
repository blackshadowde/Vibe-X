package chat.simplex.common.views.vibe

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Navigation glue for Vibe's gestures. */
object VibeNav {
  /** The open chat's own back action (set by the chat screen). */
  var back: (() -> Unit)? = null

  /** Open this chat's info page as soon as the chat opens ("Security and more"). */
  var infoFor: String? = null

  /** The last chat you opened: swiping left on Home brings it back. */
  var lastChatId: String? = null

  /** A finger is moving the chat in or out right now. */
  val dragging = mutableStateOf(false)

  /**
   * Opening the app fresh (from the launcher, after clearing it from
   * recents) starts on Home, not on whatever screen was open last time:
   * the process can outlive the screen because of the background service.
   */
  fun freshStart() {
    if (!vibeStyle) return
    runCatching {
      VibeSheets.stack.clear()
      VibeMediaSend.req.value = null
      chat.simplex.common.views.helpers.ModalManager.closeAllModalsEverywhere()
      VibeRail.selected.value = null
      infoFor = null
      back = null
      chat.simplex.common.platform.chatModel.chatId.value = null
      VibeLaunch.pending.value = true
    }
  }
}

/**
 * A horizontal drag in one direction ([toRight] or left) from anywhere,
 * decided early (14 px, mostly sideways) and then taken over so lists and
 * taps underneath don't also react. Works on raw pointer events, like
 * Vibe's back swipe, so it never fights with scrolling or swipe-to-reply
 * (which goes the other way).
 */
fun Modifier.vibeEdgeDrag(
  toRight: Boolean,
  enabled: () -> Boolean,
  onStart: () -> Boolean,
  onDelta: (Float) -> Unit,
  onEnd: (velocityX: Float) -> Unit,
): Modifier = pointerInput(toRight) {
  val slop = 14.dp.toPx()
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    if (!enabled()) return@awaitEachGesture
    val tracker = VelocityTracker()
    tracker.addPosition(down.uptimeMillis, down.position)
    var total = Offset.Zero
    var dragging = false
    while (true) {
      val ev = awaitPointerEvent(PointerEventPass.Initial)
      val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
      if (!ch.pressed) {
        if (dragging) onEnd(tracker.calculateVelocity().x)
        break
      }
      tracker.addPosition(ch.uptimeMillis, ch.position)
      val d = ch.position - ch.previousPosition
      if (!dragging) {
        if (ch.isConsumed) break
        total += d
        if (total.getDistance() < slop) continue
        val sideways = abs(total.x) > abs(total.y) * 1.6f
        val rightWay = if (toRight) total.x > 0 else total.x < 0
        if (sideways && rightWay && onStart()) {
          dragging = true
          ch.consume()
          onDelta(total.x)
        } else break
      } else {
        ch.consume()
        onDelta(d.x)
      }
    }
  }
}
