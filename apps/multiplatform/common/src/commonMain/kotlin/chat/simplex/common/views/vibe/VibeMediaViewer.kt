package chat.simplex.common.views.vibe

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import chat.simplex.common.platform.base64ToBitmap
import chat.simplex.common.views.chat.item.ImageGalleryProvider
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Google Messages style media viewer: photos and videos side by side with a
 * gap, the neighbours shrinking away as you swipe, a strip of thumbnails at
 * the bottom to jump around.
 */
@Composable
fun VibeMediaViewer(provider: ImageGalleryProvider, pager: PagerState, close: () -> Unit, page: @Composable (Int) -> Unit) {
  val scope = rememberCoroutineScope()
  val drag = remember { Animatable(0f) }
  val enter = remember { Animatable(0f) }
  LaunchedEffect(Unit) { enter.animateTo(1f, VibeSprings.page()) }
  val fade = (1f - (drag.value.absoluteValue / 900f)).coerceIn(0f, 1f) * enter.value
  Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = fade))) {
    HorizontalPager(
      state = pager,
      pageSpacing = 16.dp,
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          translationY = drag.value
          val s = 1f - (drag.value.absoluteValue / 4000f).coerceIn(0f, 0.15f)
          scaleX = s * (0.94f + 0.06f * enter.value); scaleY = s * (0.94f + 0.06f * enter.value)
        }
    ) { index ->
      Box(
        Modifier.fillMaxSize().graphicsLayer {
          // neighbours shrink and dim a little while they slide in
          val off = ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
          val s = 1f - 0.12f * off
          scaleX = s; scaleY = s
          alpha = 1f - 0.35f * off
        }
      ) { page(index) }
    }
    // close
    Box(
      Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp).graphicsLayer { alpha = fade }
        .size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable { close() },
      contentAlignment = Alignment.Center
    ) { Icon(VibeIcons.Close, "Close", tint = Color.White) }
    // thumbnails
    val strip = remember { provider.vibeStrip() }
    if (strip.size > 1) {
      val currentId = remember(pager.settledPage) { provider.vibeItemAt(pager.settledPage) }
      val list = rememberLazyListState()
      val currentIndex = strip.indexOfFirst { it.first == currentId }
      LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) list.animateScrollToItem((currentIndex - 3).coerceAtLeast(0))
      }
      LazyRow(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp).graphicsLayer { alpha = fade },
        state = list,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        itemsIndexed(strip, key = { _, t -> t.first }) { _, (id, preview) ->
          val bmp = remember(id) { runCatching { base64ToBitmap(preview) }.getOrNull() }
          val on = id == currentId
          val size by androidx.compose.animation.core.animateDpAsState(if (on) 56.dp else 46.dp, VibeSprings.pop(), label = "thumb")
          Box(
            Modifier.size(size).clip(RoundedCornerShape(10.dp))
              .then(if (on) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
              .background(Color.DarkGray)
              .clickable {
                val to = provider.vibePageFor(id, pager.currentPage) ?: return@clickable
                VibeHaptics.selection()
                scope.launch { pager.animateScrollToPage(to) }
              }
          ) {
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().graphicsLayer { alpha = if (on) 1f else 0.7f }, contentScale = ContentScale.Crop)
          }
        }
      }
    }
  }
}
