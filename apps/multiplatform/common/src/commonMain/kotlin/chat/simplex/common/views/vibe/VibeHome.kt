package chat.simplex.common.views.vibe

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.views.call.CallMediaType
import chat.simplex.common.views.chat.startChatCall
import chat.simplex.common.views.chatlist.*
import chat.simplex.common.views.helpers.*
import kotlinx.coroutines.launch

/**
 * Vibe X home, Discord style:
 * - a rail of round pictures on the left (your profile, then your chats),
 * - the middle pane: your chat list, or the picked chat's pane
 *   ("# | chat", "# | audio-call", "# | video-call"),
 * - the chat itself slides over both (SimpleX's own chat screen).
 */
object VibeRail {
  /** The chat whose pane is open in the middle (null = the chat list). */
  val selected = mutableStateOf<String?>(null)
}

private val RailWidth = 72.dp

@Composable
fun VibeHome(chatList: @Composable () -> Unit) {
  val dark = !MaterialTheme.colors.isLight
  val railColor = if (dark) Color(0xFF121214) else Color(0xFFE3E5E8)
  val selectedId = VibeRail.selected.value
  val selectedChat = selectedId?.let { id -> chatModel.chats.value.firstOrNull { it.id == id } }
  if (selectedId != null && selectedChat == null) {
    // the chat was deleted or left
    LaunchedEffect(selectedId) { VibeRail.selected.value = null }
  }
  BackHandler(enabled = selectedChat != null && chatModel.chatId.value == null) {
    VibeRail.selected.value = null
  }
  Row(Modifier.fillMaxSize().background(railColor)) {
    VibeRailColumn(Modifier.width(RailWidth).fillMaxHeight())
    Box(
      Modifier
        .weight(1f)
        .fillMaxHeight()
        .clip(RoundedCornerShape(topStart = 24.dp))
        .background(MaterialTheme.colors.background)
    ) {
      AnimatedContent(
        targetState = selectedChat?.id,
        transitionSpec = {
          (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 }) togetherWith fadeOut(tween(160))
        },
        label = "vibe-pane",
      ) { id ->
        val chat = id?.let { cid -> chatModel.chats.value.firstOrNull { it.id == cid } }
        if (chat == null) chatList() else VibeChatPane(chat)
      }
    }
  }
}

/** Chats shown in the rail: people and groups, pinned (favourite) first. */
private fun railChats(): List<Chat> {
  val all = chatModel.chats.value.filter {
    it.chatInfo is ChatInfo.Direct || it.chatInfo is ChatInfo.Group || it.chatInfo is ChatInfo.Local
  }
  return all.filter { it.chatInfo.chatSettings?.favorite == true } +
    all.filter { it.chatInfo.chatSettings?.favorite != true }
}

@Composable
private fun VibeRailColumn(modifier: Modifier) {
  val chats = railChats()
  val user = chatModel.currentUser.value
  Column(modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    // You: back to the chat list
    Box(
      Modifier
        .padding(vertical = 6.dp)
        .size(48.dp)
        .clip(CircleShape)
        .clickable { VibeRail.selected.value = null },
      contentAlignment = Alignment.Center,
    ) {
      if (user != null) {
        ProfileImage(size = 48.dp, image = user.image)
      }
    }
    Box(
      Modifier
        .padding(vertical = 6.dp)
        .width(32.dp)
        .height(2.dp)
        .clip(RoundedCornerShape(1.dp))
        .background(MaterialTheme.colors.onBackground.copy(alpha = 0.12f))
    )
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      contentPadding = PaddingValues(bottom = 16.dp),
    ) {
      items(chats, key = { it.remoteHostId.toString() + it.id }) { chat ->
        VibeRailItem(chat)
      }
    }
  }
}

@Composable
private fun VibeRailItem(chat: Chat) {
  val selected = VibeRail.selected.value == chat.id
  val unread = chat.chatStats.unreadCount
  val muted = chat.chatInfo.chatSettings?.enableNtfs?.let { it != MsgFilter.All } ?: false
  val showMenu = remember { mutableStateOf(false) }
  val inProgress = remember { mutableStateOf(false) }
  val pill by animateDpAsState(
    when {
      selected -> 32.dp
      unread > 0 -> 8.dp
      else -> 0.dp
    },
    spring(dampingRatio = 0.75f, stiffness = 500f),
    label = "pill",
  )
  Box(Modifier.fillMaxWidth().padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
    // Discord's white pill at the edge: selected (tall) or unread (short)
    Box(
      Modifier
        .align(Alignment.CenterStart)
        .width(4.dp)
        .height(pill)
        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
        .background(MaterialTheme.colors.onBackground)
    )
    Box(
      Modifier
        .size(48.dp)
        .clip(if (chat.chatInfo is ChatInfo.Group) RoundedCornerShape(16.dp) else CircleShape)
        .combinedClickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = { VibeRail.selected.value = chat.id },
          onLongClick = { showMenu.value = true },
        ),
      contentAlignment = Alignment.Center,
    ) {
      ChatInfoImage(chat.chatInfo, size = 48.dp)
    }
    if (unread > 0) {
      Box(
        Modifier
          .align(Alignment.BottomEnd)
          .padding(end = 8.dp)
          .defaultMinSize(minWidth = 20.dp)
          .height(20.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(if (muted) Color(0xFF80848E) else Color(0xFFF23F43))
          .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          if (unread > 99) "99+" else unread.toString(),
          color = Color.White,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    DefaultDropdownMenu(showMenu) {
      val showMarkRead = chat.chatStats.unreadCount > 0 || chat.chatStats.unreadChat
      when (val info = chat.chatInfo) {
        is ChatInfo.Direct -> ContactMenuItems(chat, info.contact, chatModel, showMenu, showMarkRead)
        is ChatInfo.Group -> GroupMenuItems(chat, info.groupInfo, chatModel, showMenu, inProgress, showMarkRead)
        is ChatInfo.Local -> NoteFolderMenuItems(chat, showMenu, showMarkRead)
        else -> {}
      }
    }
  }
}

/** The middle pane for one chat: big picture, name, "# | rows". */
@Composable
private fun VibeChatPane(chat: Chat) {
  val scope = rememberCoroutineScope()
  val info = chat.chatInfo
  val openChat: () -> Unit = {
    scope.launch {
      when (info) {
        is ChatInfo.Direct -> directChatAction(chat.remoteHostId, info.contact, chatModel)
        is ChatInfo.Group -> groupChatAction(chat.remoteHostId, info.groupInfo, chatModel)
        is ChatInfo.Local -> noteFolderChatAction(chat.remoteHostId, info.noteFolder)
        else -> {}
      }
    }
  }
  Column(
    Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp)
      .statusBarsPadding()
      .padding(top = 20.dp, bottom = 24.dp)
  ) {
    ChatInfoImage(info, size = 88.dp)
    Spacer(Modifier.height(14.dp))
    Text(
      info.displayName,
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colors.onBackground,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    if (info.fullName.isNotEmpty() && info.fullName != info.displayName) {
      Text(info.fullName, fontSize = 14.sp, color = MaterialTheme.colors.secondary)
    }
    Spacer(Modifier.height(18.dp))
    VibeHashRow(
      label = "chat",
      subtitle = "End-to-end encrypted",
      unread = chat.chatStats.unreadCount,
      onClick = openChat,
    )
    if (info is ChatInfo.Direct && info.contact.ready) {
      VibeHashRow(
        label = "audio-call",
        icon = "📞",
        onClick = { startChatCall(chat.remoteHostId, info, CallMediaType.Audio) },
      )
      VibeHashRow(
        label = "video-call",
        icon = "📹",
        onClick = { startChatCall(chat.remoteHostId, info, CallMediaType.Video) },
      )
    }
    if (info is ChatInfo.Group) {
      Spacer(Modifier.height(12.dp))
      Text(
        "MEMBERS",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colors.secondary,
        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
      )
      VibeHashRow(
        label = "members",
        subtitle = "Open the chat, then tap its name",
        onClick = openChat,
      )
    }
  }
}

/** "#  |  label", with an optional line under it and an unread counter. */
@Composable
fun VibeHashRow(
  label: String,
  subtitle: String? = null,
  icon: String? = null,
  unread: Int = 0,
  onClick: () -> Unit,
) {
  val lit = unread > 0
  val muted = if (lit) MaterialTheme.colors.onBackground else MaterialTheme.colors.secondary
  Row(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 6.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
      Text(icon ?: "#", fontSize = if (icon != null) 18.sp else 21.sp, color = muted)
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(
        buildString { append("|  "); append(label) },
        fontSize = 16.sp,
        color = muted,
        fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Normal,
      )
      if (subtitle != null) {
        Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    if (lit) {
      Box(
        Modifier
          .defaultMinSize(minWidth = 20.dp)
          .height(20.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFFF23F43))
          .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(if (unread > 99) "99+" else unread.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}
