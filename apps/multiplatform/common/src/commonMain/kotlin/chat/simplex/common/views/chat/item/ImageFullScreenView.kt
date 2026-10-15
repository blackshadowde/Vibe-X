package chat.simplex.common.views.chat.item

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import chat.simplex.common.model.CryptoFile
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.CurrentColors
import chat.simplex.common.views.chat.ProviderMedia
import chat.simplex.common.views.helpers.*
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.net.URI
import kotlin.math.absoluteValue

interface ImageGalleryProvider {
  val initialIndex: Int
  val totalMediaSize: MutableState<Int>
  fun getMedia(index: Int): ProviderMedia?
  fun currentPageChanged(index: Int)
  fun scrollToStart()
  fun onDismiss(index: Int)
  /** Vibe X: preview pictures (item id, base64) of the chat's photos and videos, oldest first. */
  fun vibeStrip(): List<Pair<Long, String>> = emptyList()
  /** Vibe X: the item shown on [page]. */
  fun vibeItemAt(page: Int): Long? = null
  /** Vibe X: the page that shows [itemId], counted from [currentPage]. */
  fun vibePageFor(itemId: Long, currentPage: Int): Int? = null
}

@Composable
fun ImageFullScreenView(imageProvider: () -> ImageGalleryProvider, close: () -> Unit) {
  val provider = remember { imageProvider() }
  val pagerState = rememberPagerState(
    initialPage = provider.initialIndex,
    initialPageOffsetFraction = 0f
  ) {
    provider.totalMediaSize.value
  }
  val firstValidPageBeforeScrollingToStart = remember { mutableStateOf(0) }
  val goBack = { provider.onDismiss(pagerState.currentPage); close() }
  BackHandler(onBack = goBack)
  // Pager doesn't ask previous page at initialization step who knows why. By not doing this, prev page is not checked and can be blank,
  // which makes this blank page visible for a moment. Prevent it by doing the check ourselves
  LaunchedEffect(Unit) {
    if (provider.getMedia(provider.initialIndex - 1) == null) {
      firstValidPageBeforeScrollingToStart.value = provider.initialIndex
      provider.scrollToStart()
      pagerState.scrollToPage(0)
      firstValidPageBeforeScrollingToStart.value = 0
    }
  }
  val scope = rememberCoroutineScope()
  val playersToRelease = rememberSaveable { mutableSetOf<URI>() }
  DisposableEffectOnGone(
    always = {
      platform.androidSetStatusAndNavigationBarAppearance(false, false, blackNavBar = true)
      chatModel.fullscreenGalleryVisible.value = true
    },
    whenDispose = {
      val c = CurrentColors.value.colors
      platform.androidSetStatusAndNavigationBarAppearance(c.isLight, c.isLight)
      chatModel.fullscreenGalleryVisible.value = false
    },
    whenGone = {
      playersToRelease.forEach { VideoPlayerHolder.release(it, true, true) }
    }
  )

  @Composable
  fun Content(index: Int) {
    // Index can be huge but in reality at that moment pager state scrolls to 0 and that page should have index 0 too if it's the first one.
    // Or index 1 if it's the second page
    val index = index - firstValidPageBeforeScrollingToStart.value
    Column(
      Modifier
        .fillMaxSize()
        .background(Color.Black)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = goBack)
    ) {
      var settledCurrentPage by remember { mutableStateOf(pagerState.currentPage) }
      LaunchedEffect(pagerState) {
        snapshotFlow {
          if (!pagerState.isScrollInProgress) pagerState.currentPage else settledCurrentPage
        }.collect {
          settledCurrentPage = it
        }
      }
      LaunchedEffect(settledCurrentPage) {
        // Make this pager with infinity scrolling with only 3 pages at a time when left and right pages constructs in real time
        if (settledCurrentPage != provider.initialIndex)
          provider.currentPageChanged(index)
      }
      val media = provider.getMedia(index)
      if (media == null) {
        // No such image. Let's shrink total pages size or scroll to start of the list of pages to remove blank page automatically
        SideEffect {
          scope.launch {
            when (settledCurrentPage) {
              index - 1 -> provider.totalMediaSize.value = settledCurrentPage + 1
              index + 1 -> {
                provider.scrollToStart()
                pagerState.scrollToPage(0)
              }
              // Current media was deleted or moderated, close gallery
              index -> close()
            }
          }
        }
      } else {
        var scale by remember { mutableStateOf(1f) }
        var translationX by remember { mutableStateOf(0f) }
        var translationY by remember { mutableStateOf(0f) }
        var viewWidth by remember { mutableStateOf(0) }
        var allowTranslate by remember { mutableStateOf(true) }
        LaunchedEffect(settledCurrentPage) {
          scale = 1f
          translationX = 0f
          translationY = 0f
        }
        // Vibe X: flick a photo down (or up) to close it, unless it's zoomed in
        val dismiss = remember { androidx.compose.animation.core.Animatable(0f) }
        val vibeDismiss = if (!chat.simplex.common.views.vibe.vibeStyle) Modifier else Modifier.pointerInput(Unit) {
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (scale != 1f) return@awaitEachGesture
            var dx = 0f
            var dy = 0f
            var dragging = false
            while (true) {
              val ev = awaitPointerEvent(PointerEventPass.Initial)
              if (ev.changes.size > 1) break
              val c = ev.changes.firstOrNull { it.id == down.id } ?: break
              if (!c.pressed) break
              dx += c.position.x - c.previousPosition.x
              dy += c.position.y - c.previousPosition.y
              if (!dragging) {
                if (dy.absoluteValue > viewConfiguration.touchSlop && dy.absoluteValue > dx.absoluteValue * 1.5f) dragging = true
                else if (dx.absoluteValue > viewConfiguration.touchSlop) break
              }
              if (dragging) {
                c.consume()
                val y = dy
                scope.launch { dismiss.snapTo(y) }
              }
            }
            if (dragging) {
              if (dy.absoluteValue > 140.dp.toPx()) {
                chat.simplex.common.views.vibe.VibeHaptics.light()
                goBack()
              } else scope.launch { dismiss.animateTo(0f, chat.simplex.common.views.vibe.VibeSprings.release()) }
            }
          }
        }
        val modifier = vibeDismiss
          .graphicsLayer {
            this.translationY = dismiss.value
            val s = 1f - (dismiss.value.absoluteValue / 3000f).coerceIn(0f, 0.2f)
            scaleX = s; scaleY = s
          }
          .onGloballyPositioned {
            viewWidth = it.size.width
          }
          .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = translationX,
            translationY = translationY,
          )
          .pointerInput(Unit) {
            detectTransformGestures(
              { allowTranslate },
              onGesture = { _, pan, gestureZoom, _ ->
                scale = (scale * gestureZoom).coerceIn(1f, 20f)
                allowTranslate = viewWidth * (scale - 1f) - ((translationX + pan.x * scale).absoluteValue * 2) > 0
                if (scale > 1 && allowTranslate) {
                  translationX += pan.x * scale
                  translationY += pan.y * scale
                } else if (allowTranslate) {
                  translationX = 0f
                  translationY = 0f
                }
              }
            )
          }
          .fillMaxSize()
        // LALAL
        // https://github.com/JetBrains/compose-multiplatform/pull/2015/files#diff-841b3825c504584012e1d1c834d731bae794cce6acad425d81847c8bbbf239e0R24
        if (media is ProviderMedia.Image) {
          val (data: ByteArray, imageBitmap: ImageBitmap) = media
          FullScreenImageView(modifier, data, imageBitmap)
        } else if (media is ProviderMedia.Video) {
          val preview = remember(media.uri.path) { base64ToBitmap(media.preview) }
          val uriDecrypted = remember(media.uri.path) { mutableStateOf(if (media.fileSource?.cryptoArgs == null) media.uri else media.fileSource.decryptedGet()) }
          val decrypted = uriDecrypted.value
          if (decrypted != null) {
            // settledCurrentPage finishes **only** when fully swiped
            // So we use pagerState.currentPage that changes right away as the screen is being dragged
            val isCurrentPage = index == pagerState.currentPage && kotlin.math.abs(pagerState.currentPageOffsetFraction) < 0.3f
            VideoView(modifier, decrypted, preview, isCurrentPage, close)
            DisposableEffect(Unit) {
              onDispose { playersToRelease.add(decrypted) }
            }
          } else if (media.fileSource != null) {
            VideoViewEncrypted(uriDecrypted, media.fileSource, preview, close)
          }
        }
      }
    }
  }
  if (appPlatform.isAndroid && chat.simplex.common.views.vibe.vibeStyle) {
    chat.simplex.common.views.vibe.VibeMediaViewer(provider, pagerState, goBack) { index -> Content(index) }
  } else if (appPlatform.isAndroid) {
    HorizontalPager(state = pagerState) { index -> Content(index) }
  } else {
    Content(pagerState.currentPage)
  }
}

@Composable
expect fun FullScreenImageView(modifier: Modifier, data: ByteArray, imageBitmap: ImageBitmap)

@Composable
private fun VideoViewEncrypted(uriUnencrypted: MutableState<URI?>, fileSource: CryptoFile, defaultPreview: ImageBitmap, close: () -> Unit) {
  LaunchedEffect(Unit) {
    withBGApi {
      uriUnencrypted.value = fileSource.decryptedGetOrCreate()
      if (uriUnencrypted.value == null) {
        close()
      }
    }
  }
  Box(contentAlignment = Alignment.Center) {
    VideoPreviewImageViewFullScreen(defaultPreview, {}, {})
    VideoDecryptionProgress() {}
  }
}

@Composable
private fun VideoView(modifier: Modifier, uri: URI, defaultPreview: ImageBitmap, currentPage: Boolean, close: () -> Unit) {
  val player = remember(uri) { VideoPlayerHolder.getOrCreate(uri, true, defaultPreview, 0L, true) }
  val isCurrentPage = rememberUpdatedState(currentPage)
  val play = {
    player.play(true)
  }
  val stop = {
    player.stop()
  }
  LaunchedEffect(Unit) {
    snapshotFlow { isCurrentPage.value }
      .distinctUntilChanged()
      .collect {
        if (it) play() else stop()
        player.enableSound(true)
      }
  }

  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    FullScreenVideoView(player, modifier, close)
  }
}

@Composable
expect fun FullScreenVideoView(player: VideoPlayer, modifier: Modifier, close: () -> Unit)
