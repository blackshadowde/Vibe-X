package chat.simplex.common.views.vibe

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.BackHandler
import kotlinx.coroutines.launch

/**
 * Vibe's panes: everything that used to be a pop-up menu slides up from the
 * bottom instead, on the same critically damped spring Vibe uses (fast out,
 * settles without lifting off the edge), and closes with a quick ease-in.
 * Drag it down (or flick) to close; a scrolled list hands its pull-down
 * over to the pane once it's at the top.
 */
object VibeSheets {
  class Entry(
    val id: Long,
    val background: (@Composable () -> Color)?,
    val handle: Boolean,
    val maxHeight: Float,
    val corner: Dp,
    val content: @Composable (close: () -> Unit) -> Unit,
  ) {
    val closing = mutableStateOf(false)
  }

  val stack = mutableStateListOf<Entry>()
  private var next = 0L

  /** Shows a pane; [content] gets a function that closes it. */
  fun show(
    handle: Boolean = true,
    maxHeight: Float = 0.92f,
    corner: Dp = 28.dp,
    background: (@Composable () -> Color)? = null,
    content: @Composable (close: () -> Unit) -> Unit,
  ) {
    stack.add(Entry(next++, background, handle, maxHeight, corner, content))
  }

  fun closeAll() {
    stack.forEach { it.closing.value = true }
  }
}

private val openSpec = spring<Float>(dampingRatio = 1f, stiffness = 380f)
private val closeSpec = tween<Float>(220, easing = CubicBezierEasing(0.32f, 0f, 0.67f, 0f))

@Composable
fun VibeSheetHost() {
  for (e in VibeSheets.stack) {
    key(e.id) { SheetView(e) }
  }
}

@Composable
private fun SheetView(e: VibeSheets.Entry) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
  var heightPx by remember { mutableStateOf(0f) }
  val progress = remember { Animatable(0f) }
  val drag = remember { Animatable(0f) }
  val isTop = VibeSheets.stack.lastOrNull() === e

  fun close() {
    e.closing.value = true
  }
  LaunchedEffect(e.closing.value) {
    if (e.closing.value) {
      progress.animateTo(0f, closeSpec)
      VibeSheets.stack.remove(e)
    } else {
      progress.animateTo(1f, openSpec)
    }
  }
  BackHandler(enabled = isTop && !e.closing.value) { close() }

  fun settle(velocity: Float) {
    scope.launch {
      val h = if (heightPx > 0f) heightPx else screenH
      if (velocity > 1200f || drag.value > h * 0.3f) {
        // carry the flick into the close
        e.closing.value = true
      } else {
        drag.animateTo(0f, spring(dampingRatio = 1f, stiffness = 420f), initialVelocity = velocity)
      }
    }
  }

  val nested = remember {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // scrolling the list back up while the pane is pulled down: lift the pane first
        if (available.y < 0f && drag.value > 0f) {
          val used = maxOf(available.y, -drag.value)
          scope.launch { drag.snapTo(drag.value + used) }
          return Offset(0f, used)
        }
        return Offset.Zero
      }

      override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y > 0f && source == NestedScrollSource.UserInput) {
          scope.launch { drag.snapTo(drag.value + available.y) }
          return Offset(0f, available.y)
        }
        return Offset.Zero
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        if (drag.value > 0f) {
          settle(available.y)
          return available
        }
        return Velocity.Zero
      }
    }
  }

  Box(Modifier.fillMaxSize()) {
    // dim behind; tap to close
    Box(
      Modifier
        .fillMaxSize()
        .graphicsLayer { alpha = progress.value }
        .background(Color.Black.copy(alpha = 0.5f))
        .clickable(remember { MutableInteractionSource() }, indication = null) { close() }
    )
    val bg = e.background?.invoke() ?: VibeColors.container()
    Column(
      Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .heightIn(max = with(density) { (screenH * e.maxHeight).toDp() })
        .onSizeChanged { heightPx = it.height.toFloat() }
        .graphicsLayer {
          val h = if (heightPx > 0f) heightPx else screenH
          translationY = (1f - progress.value) * h + drag.value
        }
        .clip(RoundedCornerShape(topStart = e.corner, topEnd = e.corner))
        .background(bg)
        .nestedScroll(nested)
        .draggable(
          rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } },
          Orientation.Vertical,
          onDragStopped = { v -> settle(v) },
        )
        .imePadding()
    ) {
      if (e.handle) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
          Box(Modifier.size(width = 32.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(VibeColors.onVariant().copy(alpha = 0.4f)))
        }
      }
      e.content { close() }
    }
  }
}

/** Grey card used inside panes (Vibe's settings card colour). */
@Composable fun vibeCard() = if (VibeColors.isDark()) Color(0xFF26252B) else Color(0xFFF2F3F5)

/** The pane's page colour (Vibe's settings page colour). */
@Composable fun vibePage() = if (VibeColors.isDark()) Color(0xFF1C1B20) else Color(0xFFFFFFFF)

/** Light haptic tick, unless haptics are turned off in Change your style. */
@Composable
fun rememberVibeHaptic(): (androidx.compose.ui.hapticfeedback.HapticFeedbackType) -> Unit {
  val h = androidx.compose.ui.platform.LocalHapticFeedback.current
  val on = chat.simplex.common.platform.appPreferences.vibeHaptics.state.value
  return { type -> if (on) h.performHapticFeedback(type) }
}
