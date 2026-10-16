package chat.simplex.common.views.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.chatModel
import chat.simplex.common.views.helpers.ModalManager
import chat.simplex.common.views.newchat.*
import chat.simplex.common.views.usersettings.UserAddressView
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.launch

/**
 * The + pane, as in Vibe: big round actions (invite, scan or paste, group,
 * channel, your address), a box that takes a name or a link, and the people
 * you can message.
 */
object VibeNewChat {
  fun show() {
    VibeHaptics.medium()
    VibeSheets.show(maxHeight = 0.9f, background = { vibePage() }) { close -> Sheet(close) }
  }

  private fun closeAll() = ModalManager.start.closeModals()

  private fun open(close: () -> Unit, block: () -> Unit) {
    close()
    block()
  }

  @Composable
  private fun Sheet(close: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    val isLink = q.trim().let { it.startsWith("simplex:") || it.startsWith("https://simplex.chat") || it.contains("/a#") || it.contains("/c#") || it.contains("/i#") || it.contains("/g#") }
    val people = chatModel.chats.value.filter {
      val i = it.chatInfo
      i is ChatInfo.Direct && i.contact.ready && !i.contact.chatDeleted && !VibeChatSets.isLocked(it.id) &&
          (q.isBlank() || isLink || i.displayName.contains(q.trim(), ignoreCase = true))
    }.sortedBy { it.chatInfo.displayName.lowercase() }
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
      Text(vt("New chat or group"), Modifier.padding(start = 20.dp, bottom = 14.dp), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Action(painterResource(MR.images.ic_add_link), "Invite") {
          open(close) { ModalManager.start.showModalCloseable { _ -> NewChatView(chatModel.currentRemoteHost.value, NewChatOption.INVITE, close = ::closeAll) } }
        }
        Action(painterResource(MR.images.ic_qr_code), "Scan / paste") {
          open(close) { ModalManager.start.showModalCloseable { _ -> NewChatView(chatModel.currentRemoteHost.value, NewChatOption.CONNECT, showQRCodeScanner = true, close = ::closeAll) } }
        }
        Action(painterResource(MR.images.ic_group), vt("Group")) {
          open(close) { ModalManager.start.showCustomModal { c -> AddGroupView(chatModel, chatModel.currentRemoteHost.value, c, ::closeAll) } }
        }
        Action(painterResource(MR.images.ic_bigtop_updates), vt("Channel")) {
          open(close) { ModalManager.start.showCustomModal { c -> AddChannelView(chatModel, chatModel.currentRemoteHost.value, c, ::closeAll) } }
        }
      }
      Spacer(Modifier.height(16.dp))
      // a name to find, or a link to connect to
      Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(VibeColors.lowest()).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(VibeIcons.Search, null, Modifier.size(20.dp), tint = VibeColors.onVariant())
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
          if (q.isEmpty()) Text("Search people, or paste a Vibe X link", color = VibeColors.onVariant(), maxLines = 1)
          BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = VibeColors.onSurface()), cursorBrush = SolidColor(VibeColors.blurple), modifier = Modifier.fillMaxWidth())
        }
      }
      if (isLink) {
        Row(
          Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(VibeColors.blurple)
            .clickable {
              val link = q.trim()
              close()
              scope.launch { planAndConnect(chatModel.remoteHostId(), link, close = null) }
            },
          horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) { Text("Connect", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
      }
      Row(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(vibeCard())
          .clickable {
            open(close) {
              val user = chatModel.currentUser.value ?: return@open
              ModalManager.start.showModalCloseable(cardScreen = true) { c -> UserAddressView(chatModel, shareViaProfile = user.addressShared, close = c) }
            }
          }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(VibeIcons.QrCode, null, Modifier.size(22.dp), tint = VibeColors.blurple)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(vt("Your Vibe X address"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.onSurface())
          Text(vt("Share it so people can reach you"), fontSize = 13.sp, color = VibeColors.onVariant())
        }
      }
      if (people.isNotEmpty()) {
        Text("PEOPLE — ${people.size}", Modifier.padding(start = 20.dp, top = 6.dp, bottom = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = VibeColors.onVariant())
      }
      LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(people, key = { it.id }) { chat ->
          Row(
            Modifier.fillMaxWidth().clickable { close(); openVibeChat(chat, scope) }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            VibeChatAvatar(chat.chatInfo, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              Text(chat.chatInfo.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.onSurface(), maxLines = 1, overflow = TextOverflow.Ellipsis)
              val status = (chat.chatInfo as? ChatInfo.Direct)?.contact?.profile?.shortDescr?.takeIf { it.isNotBlank() }
              if (status != null) Text(status, fontSize = 13.sp, color = VibeColors.onVariant(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    }
  }

  @Composable
  private fun Action(icon: Painter, label: String, onClick: () -> Unit) {
    Column(Modifier.width(80.dp).clip(RoundedCornerShape(16.dp)).clickable { VibeHaptics.light(); onClick() }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(Modifier.size(56.dp).clip(CircleShape).background(VibeColors.blurple), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(26.dp), tint = Color.White)
      }
      Spacer(Modifier.height(6.dp))
      Text(label, fontSize = 12.5.sp, color = VibeColors.onSurface(), textAlign = TextAlign.Center, maxLines = 1)
    }
  }
}
