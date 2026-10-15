package chat.simplex.common.views.vibe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.chatModel
import chat.simplex.common.views.chat.clearChatDialog
import chat.simplex.common.views.chat.deleteContactDialog
import chat.simplex.common.views.chat.group.deleteGroupDialog
import chat.simplex.common.views.chat.group.leaveGroupDialog

/** How well this chat is connected right now, in four dots. */
private class Signal(val lit: Int, val color: Color, val title: String, val detail: String, val busy: Boolean = false)

@Composable
private fun signalOf(info: ChatInfo): Signal {
  val amber = Color(0xFFFAA61A)
  val grey = VibeColors.onVariant()
  val online = chatModel.networkInfo.value.online
  val running = chatModel.chatRunning.value
  return when {
    !online -> Signal(0, grey, "No internet", "Messages will go out when your phone is back online.")
    running == null -> Signal(1, VibeColors.blurple, "Starting…", "Vibe X is connecting to its servers.", busy = true)
    running == false -> Signal(0, grey, "Chat is stopped", "Start it again in Settings › Database.")
    info is ChatInfo.Direct -> {
      val c = info.contact
      val sub = chatModel.chatSubStatus.value
      when {
        !c.active -> Signal(0, VibeColors.red, "Contact deleted", "This contact deleted the connection.")
        !c.ready -> Signal(1, amber, "Waiting for them", "They haven't accepted the invitation yet.")
        sub is SubscriptionStatus.Removed -> Signal(1, VibeColors.red, "Connection error", sub.subError)
        sub == SubscriptionStatus.NoSub -> Signal(1, VibeColors.red, "Not subscribed", "The server isn't delivering this chat's messages. It retries by itself.")
        sub == SubscriptionStatus.Pending -> Signal(2, VibeColors.blurple, "Connecting…", "Reaching your receiving server.", busy = true)
        sub == SubscriptionStatus.Active -> Signal(4, VibeColors.green, "Connected", "Private end-to-end encrypted connection, ready to send and receive.")
        else -> Signal(3, VibeColors.green, "Connected", "Checking the connection with your server…")
      }
    }
    info is ChatInfo.Group -> {
      val m = info.groupInfo.membership
      when {
        !m.memberActive -> Signal(1, amber, "Joining", "You'll get messages once you've joined.")
        else -> Signal(4, VibeColors.green, "Connected", "Messages to the group go to each member over their own encrypted connection.")
      }
    }
    else -> Signal(4, VibeColors.green, "On this phone", "Private notes stay on this phone.")
  }
}

/** Four dots, growing like signal bars: green when connected, moving while it connects. */
@Composable
fun VibeSignalDots(info: ChatInfo) {
  val s = signalOf(info)
  val inf = rememberInfiniteTransition(label = "signal")
  val sweep by inf.animateFloat(0f, 4f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "sweep")
  val off = VibeColors.onVariant().copy(alpha = 0.28f)
  Row(
    Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).clickable { VibeHaptics.light(); showSignal(s) }.padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    for (i in 0 until 4) {
      val on = if (s.busy) i <= sweep.toInt() % 4 else i < s.lit
      val color by animateColorAsState(if (on) s.color else off, tween(250), label = "dot$i")
      Box(
        Modifier.padding(horizontal = 1.5.dp).align(Alignment.Bottom).padding(bottom = 17.dp)
          .size((5 + i * 1.5f).dp).clip(CircleShape).background(color)
      )
    }
  }
}

private fun showSignal(s: Signal) {
  VibeSheets.show(background = { vibePage() }) { close ->
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 4) {
          Box(Modifier.padding(end = 4.dp).size((8 + i * 2).dp).clip(CircleShape).background(if (i < s.lit) s.color else VibeColors.onVariant().copy(alpha = 0.28f)))
        }
        Spacer(Modifier.width(10.dp))
        Text(s.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      }
      Text(s.detail, Modifier.padding(top = 8.dp), fontSize = 15.sp, color = VibeColors.onVariant())
    }
  }
}

/** The chat's ⋮ menu: details, search, clear, delete / leave (media, files and links are in the middle pane). */
object VibeChatMenu {
  fun show(info: ChatInfo, onDetails: () -> Unit, onSearch: () -> Unit) {
    VibeHaptics.medium()
    VibeSheets.show(background = { vibePage() }) { close ->
      val chat = chatModel.getChat(info.id)
      val red = Color(0xFFF87171)
      Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
          VibeChatAvatar(info, 40.dp)
          Spacer(Modifier.width(12.dp))
          Text(info.displayName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface(), maxLines = 1)
        }
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard())) {
          if (info !is ChatInfo.Local) Row(VibeIcons.Info, "Chat details", VibeColors.onSurface()) { close(); onDetails() }
          Row(VibeIcons.Search, "Search messages", VibeColors.onSurface()) { close(); onSearch() }
        }
        if (chat != null) {
          Spacer(Modifier.height(12.dp))
          Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard())) {
            if (info is ChatInfo.Direct || info is ChatInfo.Group || info is ChatInfo.Local) {
              Row(VibeIcons.Delete, "Clear chat", VibeColors.onSurface()) { close(); clearChatDialog(chat) }
            }
            when (info) {
              is ChatInfo.Direct -> Row(VibeIcons.Delete, "Delete chat", red) { close(); deleteContactDialog(chat, chatModel) }
              is ChatInfo.Group -> {
                val gi = info.groupInfo
                if (gi.membership.memberCurrentOrPending && !(gi.useRelays && gi.isOwner)) {
                  Row(VibeIcons.ArrowBack, "Leave group", red) { close(); leaveGroupDialog(chat.remoteHostId, gi, chatModel) }
                }
                if (gi.canDelete) Row(VibeIcons.Delete, "Delete group", red) { close(); deleteGroupDialog(chat, gi, chatModel) }
              }
              else -> {}
            }
          }
        }
      }
    }
  }

  @Composable
  private fun Row(icon: ImageVector, title: String, color: Color, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
      Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(icon, null, Modifier.size(22.dp), tint = color)
      Spacer(Modifier.width(16.dp))
      Text(title, fontSize = 16.sp, color = color)
    }
  }
}
