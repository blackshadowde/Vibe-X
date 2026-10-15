package chat.simplex.common.views.vibe

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.chatModel
import chat.simplex.common.ui.theme.VibeBlurple
import chat.simplex.common.views.chatlist.*
import chat.simplex.common.views.helpers.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * While you're in one chat, someone in another chat typing (live) or
 * writing to you peeks in from the left edge, next to the message box,
 * instead of a notification popping over the screen. Tap to go there.
 */
@Composable
fun VibePeek(currentChatId: String, modifier: Modifier = Modifier) {
  var tick by remember { mutableStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) { delay(1000); tick++ }
  }
  val dismissed = remember { mutableStateMapOf<String, Long>() }
  val peekers = remember(tick, chatModel.chats.value) {
    chatModel.chats.value.mapNotNull { chat ->
      if (chat.id == currentChatId) return@mapNotNull null
      val last = chat.chatItems.lastOrNull() ?: return@mapNotNull null
      if (last.chatDir.sent) return@mapNotNull null
      if (dismissed[chat.id] == last.id) return@mapNotNull null
      val muted = chat.chatInfo.chatSettings?.enableNtfs?.let { it == MsgFilter.None } ?: false
      when {
        last.meta.isLive -> Triple(chat, last, true)
        !muted && last.isRcvNew && last.vibeJustArrived(8) -> Triple(chat, last, false)
        else -> null
      }
    }.take(3)
  }
  val scope = rememberCoroutineScope()
  Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    for ((chat, item, typing) in peekers) {
      key(chat.id) {
        val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(
          visibleState = shown,
          enter = slideInHorizontally(spring(dampingRatio = 0.6f, stiffness = 300f)) { -it } + fadeIn(),
          exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
          PeekChip(chat, item, typing) {
            dismissed[chat.id] = item.id
            scope.launch {
              when (val info = chat.chatInfo) {
                is ChatInfo.Direct -> directChatAction(chat.remoteHostId, info.contact, chatModel)
                is ChatInfo.Group -> groupChatAction(chat.remoteHostId, info.groupInfo, chatModel)
                else -> {}
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PeekChip(chat: Chat, item: ChatItem, typing: Boolean, onClick: () -> Unit) {
  val inf = rememberInfiniteTransition(label = "peek")
  val bob by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
  Row(
    Modifier
      .graphicsLayer { translationY = -bob * 3f }
      .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
      .background(MaterialTheme.colors.surface)
      .clickable(onClick = onClick)
      .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box {
      Box(Modifier.size(34.dp).clip(CircleShape).border(2.dp, if (typing) VibeBlurple else MaterialTheme.colors.surface, CircleShape)) {
        ChatInfoImage(chat.chatInfo, size = 34.dp)
      }
      if (typing) {
        Box(
          Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(VibeBlurple)
            .graphicsLayer { scaleX = 0.8f + bob * 0.3f; scaleY = 0.8f + bob * 0.3f }
        )
      }
    }
    Spacer(Modifier.width(6.dp))
    Column(Modifier.widthIn(max = 150.dp)) {
      Text(chat.chatInfo.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colors.onBackground)
      Text(
        if (typing) "typing…" else VibeEffect.strip(item.text),
        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = if (typing) VibeBlurple else MaterialTheme.colors.secondary
      )
    }
  }
}
