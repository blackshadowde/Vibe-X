package chat.simplex.common.views.vibe

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.chatModel
import chat.simplex.common.platform.showToast
import chat.simplex.common.views.chat.clearChatDialog
import chat.simplex.common.views.chat.deleteContactDialog
import chat.simplex.common.views.chat.group.deleteGroupDialog
import chat.simplex.common.views.chat.group.leaveGroupDialog
import chat.simplex.common.views.chatlist.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val deleteRed = Color(0xFFDA373C)

/**
 * Long-press a chat (rail picture or chat row): Pin, Mute and Unread as
 * three round buttons, then Open, Clear, Delete / Leave and Mark as read,
 * in a pane that slides up, like Vibe.
 */
object VibeChatActions {
  fun show(chat: Chat, onOpen: () -> Unit) {
    VibeSheets.show(background = { vibePage() }) { close -> Sheet(chat, onOpen, close) }
  }
}

@Composable
private fun Sheet(chat0: Chat, onOpen: () -> Unit, close: () -> Unit) {
  val haptic = rememberVibeHaptic()
  LaunchedEffect(Unit) { haptic(HapticFeedbackType.LongPress) }
  // live copy: pinning / muting updates the model, the buttons follow
  val chat = chatModel.chats.value.firstOrNull { it.id == chat0.id } ?: chat0
  val info = chat.chatInfo
  val pinned = info.chatSettings?.favorite == true
  val ntfs = info.chatSettings?.enableNtfs ?: MsgFilter.All
  val muted = ntfs != MsgFilter.All
  val unread = chat.chatStats.unreadCount > 0 || chat.chatStats.unreadChat
  var pinnedUi by remember { mutableStateOf(pinned) }
  var mutedUi by remember { mutableStateOf(muted) }
  var unreadUi by remember { mutableStateOf(unread) }
  var busy by remember { mutableStateOf(false) }
  val name = info.displayName
  val scope = rememberCoroutineScope()

  fun run(flip: () -> Unit, save: () -> Unit, done: String) {
    if (busy) return
    busy = true
    haptic(HapticFeedbackType.TextHandleMove)
    flip()
    save()
    showToast(done)
    scope.launch { delay(260); close() }
  }

  Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
      VibeChatAvatar(info, 44.dp)
      Spacer(Modifier.width(12.dp))
      Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = VibeColors.onSurface())
    }
    val canToggle = info is ChatInfo.Direct || info is ChatInfo.Group
    if (canToggle) {
      Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundAction(VibeIcons.PushPin, pinnedUi, if (pinnedUi) "Unpin" else "Pin") {
          run({ pinnedUi = !pinnedUi }, { toggleChatFavorite(chat.remoteHostId, info, pinnedUi, chatModel) }, if (pinnedUi) "Pinned $name" else "Unpinned $name")
        }
        RoundAction(
          if (mutedUi) painterIcon(MR.images.ic_notifications_off) else painterIcon(MR.images.ic_notifications),
          mutedUi, if (mutedUi) "Unmute" else "Mute"
        ) {
          run({ mutedUi = !mutedUi }, { toggleNotifications(chat.remoteHostId, info, if (mutedUi) MsgFilter.None else MsgFilter.All, chatModel) }, if (mutedUi) "Muted $name" else "Notifications on for $name")
        }
        RoundAction(painterIcon(MR.images.ic_mark_chat_unread), unreadUi, if (unreadUi) "Read" else "Unread") {
          run({ unreadUi = !unreadUi }, { if (unreadUi) markChatUnread(chat, chatModel) else markChatRead(chat) }, if (unreadUi) "Marked unread" else "Marked as read")
        }
      }
      Spacer(Modifier.height(14.dp))
    }
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard())) {
      ListRow(VibeIcons.Chat, "Open chat") { close(); onOpen() }
      if (info is ChatInfo.Direct || info is ChatInfo.Group || info is ChatInfo.Local) {
        ListRow(painterIcon(MR.images.ic_settings_backup_restore), "Clear chat history", subtitle = "Messages go, the chat stays") { close(); clearChatDialog(chat) }
      }
      when (info) {
        is ChatInfo.Direct -> ListRow(VibeIcons.Delete, "Delete chat", color = deleteRed) { close(); deleteContactDialog(chat, chatModel) }
        is ChatInfo.Group -> {
          val gi = info.groupInfo
          if (gi.membership.memberCurrentOrPending && !(gi.useRelays && gi.isOwner)) {
            ListRow(painterIcon(MR.images.ic_logout), "Leave", color = deleteRed) { close(); leaveGroupDialog(chat.remoteHostId, gi, chatModel) }
          }
          if (gi.canDelete) ListRow(VibeIcons.Delete, "Delete group", color = deleteRed) { close(); deleteGroupDialog(chat, gi, chatModel) }
        }
        else -> {}
      }
    }
    Spacer(Modifier.height(16.dp))
  }
}

@Composable
private fun painterIcon(res: dev.icerock.moko.resources.ImageResource) = painterResource(res)

@Composable
private fun ListRow(icon: Any, title: String, subtitle: String? = null, color: Color? = null, onClick: () -> Unit) {
  val t1 = color ?: VibeColors.onSurface()
  Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
    when (icon) {
      is ImageVector -> Icon(icon, null, Modifier.size(24.dp), tint = t1)
      is androidx.compose.ui.graphics.painter.Painter -> Icon(icon, null, Modifier.size(24.dp), tint = t1)
    }
    Spacer(Modifier.width(16.dp))
    Column {
      Text(title, fontSize = 16.sp, color = t1)
      if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = VibeColors.onVariant())
    }
  }
}

/** Round grey button that fills in (and its icon inverts) when switched on, with a little spring. */
@Composable
private fun RoundAction(icon: Any, on: Boolean, label: String, onClick: () -> Unit) {
  val dark = VibeColors.isDark()
  val grey = if (dark) Color(0xFF3A393F) else Color(0xFFE3E5E8)
  val ink = if (dark) Color(0xFFF2F3F5) else Color(0xFF060607)
  val bg by animateColorAsState(if (on) ink else grey, tween(220), label = "bg")
  val fg = if (on) (if (dark) Color(0xFF1A191E) else Color.White) else ink
  val press = remember { MutableInteractionSource() }
  val pressed by press.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
  Column(
    Modifier.width(88.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clickable(press, null, onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(Modifier.size(60.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
      AnimatedContent(on, transitionSpec = { scaleIn(spring(dampingRatio = 0.5f)) togetherWith scaleOut() }, label = "icon") { _ ->
        when (icon) {
          is ImageVector -> Icon(icon, null, Modifier.size(26.dp), tint = fg)
          is androidx.compose.ui.graphics.painter.Painter -> Icon(icon, null, Modifier.size(26.dp), tint = fg)
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.onVariant())
  }
}
