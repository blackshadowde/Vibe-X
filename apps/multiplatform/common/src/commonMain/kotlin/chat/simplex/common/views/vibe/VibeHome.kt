package chat.simplex.common.views.vibe

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.AppLock
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.views.call.CallMediaType
import chat.simplex.common.views.chat.startChatCall
import chat.simplex.common.views.chatlist.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.onboarding.*
import chat.simplex.common.views.usersettings.SettingsView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Vibe X home, a copy of Vibe's (Discord style):
 * - the rail of chats on the far left (your picture, then people, then groups),
 * - the middle pane: "Find or start a conversation", DIRECT MESSAGES and
 *   GROUPS, or the picked chat's pane ("# | chat", "# | audio-call" ...),
 * - your floating card at the bottom (picture, name, status, settings).
 */
object VibeRail {
  /** The chat whose pane is open in the middle (null = the list). */
  val selected = mutableStateOf<String?>(null)
  /** The folder (SimpleX chat list) shown in the middle, null = all chats. */
  val folder = mutableStateOf<Long?>(null)
}

private val RailWidth = 72.dp

@Composable
fun VibeHome(userPickerState: MutableStateFlow<AnimatedViewState>, stopped: Boolean) {
  val selectedId = VibeRail.selected.value
  val selectedChat = selectedId?.let { id -> chatModel.chats.value.firstOrNull { it.id == id } }
  if (selectedId != null && selectedChat == null) {
    LaunchedEffect(selectedId) { VibeRail.selected.value = null }
  }
  BackHandler(enabled = selectedChat != null && chatModel.chatId.value == null) {
    VibeRail.selected.value = null
  }
  // what SimpleX's chat list did when it opened
  LaunchedEffect(Unit) {
    val showUpdatedConditions = chatModel.conditions.value.conditionsAction?.shouldShowNotice ?: false
    // Vibe X: only the updated server conditions notice, not SimpleX's what's new / invest pages
    if (showUpdatedConditions) {
      delay(1000L)
      ModalManager.center.showCustomModal { close -> WhatsNewView(close = close, updatedConditions = showUpdatedConditions) }
    }
  }
  Box(Modifier.fillMaxSize().background(VibeColors.lowest())) {
    Row(Modifier.fillMaxSize()) {
      VibeRailColumn(userPickerState, Modifier.width(RailWidth).fillMaxHeight())
      Box(
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .clip(RoundedCornerShape(topStart = 24.dp))
          .background(VibeColors.surface())
      ) {
        AnimatedContent(
          targetState = selectedChat?.id,
          transitionSpec = {
            (fadeIn(VibeSprings.page()) + slideInHorizontally(VibeSprings.page()) { it / 12 }) togetherWith fadeOut(tween(160))
          },
          label = "vibe-pane",
        ) { id ->
          val chat = id?.let { cid -> chatModel.chats.value.firstOrNull { it.id == cid } }
          if (chat == null) VibeDmPane(stopped) else VibeChatPane(chat)
        }
      }
    }
    VibeUserBar(userPickerState, Modifier.align(Alignment.BottomCenter))
  }
  if (appPlatform.isAndroid) {
    val canEnableNotifications = remember { derivedStateOf { chatModel.chatRunning.value == true } }
    if (canEnableNotifications.value) SetNotificationsModeAdditions()
    tryOrShowError("UserPicker", error = {}) {
      UserPicker(chatModel = chatModel, userPickerState = userPickerState, setPerformLA = AppLock::setPerformLA)
    }
  }
}

fun openVibeChat(chat: Chat, scope: kotlinx.coroutines.CoroutineScope) {
  VibeNav.lastChatId = chat.id
  when (val info = chat.chatInfo) {
    is ChatInfo.Direct -> scope.launch { directChatAction(chat.remoteHostId, info.contact, chatModel) }
    is ChatInfo.Group -> scope.launch { groupChatAction(chat.remoteHostId, info.groupInfo, chatModel) }
    is ChatInfo.Local -> scope.launch { noteFolderChatAction(chat.remoteHostId, info.noteFolder) }
    is ChatInfo.ContactRequest -> contactRequestAlertDialog(chat.remoteHostId, info, chatModel)
    is ChatInfo.ContactConnection -> chatModel.chatId.value = chat.id
    else -> {}
  }
}

private fun isGroup(c: Chat) = c.chatInfo is ChatInfo.Group

/** People and groups in the rail, pinned first; groups after people (like Discord's servers). */
private fun railChats(): Pair<List<Chat>, List<Chat>> {
  val all = chatModel.chats.value.filter { (it.chatInfo is ChatInfo.Direct || it.chatInfo is ChatInfo.Group) && !VibeChatSets.hidden(it) }
  fun pinnedFirst(l: List<Chat>) = l.filter { it.chatInfo.chatSettings?.favorite == true } + l.filter { it.chatInfo.chatSettings?.favorite != true }
  return pinnedFirst(all.filter { !isGroup(it) }) to pinnedFirst(all.filter { isGroup(it) })
}

@Composable
private fun VibeRailColumn(userPickerState: MutableStateFlow<AnimatedViewState>, modifier: Modifier) {
  val (dms, groups) = railChats()
  val user = chatModel.currentUser.value
  Column(modifier.statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.height(10.dp))
    VibeAvatar(
      user?.image, user?.displayName ?: "", 48.dp,
      Modifier.combinedClickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null,
        onClick = { VibeRail.selected.value = null; VibeRail.folder.value = null },
        // archived and locked chats, folders, switch profile
        onLongClick = { VibeChatSets.showMore(userPickerState) },
      )
    )
    Box(Modifier.padding(vertical = 8.dp, horizontal = 20.dp).fillMaxWidth().height(1.dp).background(VibeColors.divider()))
    val pinnedCount = dms.takeWhile { it.chatInfo.chatSettings?.favorite == true }.size
    val list = dms + groups
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      contentPadding = PaddingValues(bottom = 110.dp),
    ) {
      itemsIndexed(list, key = { _, c -> c.remoteHostId.toString() + c.id }) { i, chat ->
        val line = (i == dms.size && dms.isNotEmpty() && groups.isNotEmpty()) || (i == pinnedCount && pinnedCount > 0 && i < dms.size)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          if (line) Box(Modifier.padding(vertical = 6.dp, horizontal = 24.dp).fillMaxWidth().height(1.dp).background(VibeColors.divider()))
          VibeRailItem(chat)
        }
      }
      // folders (SimpleX's chat lists), like Discord's servers
      val tags = chatModel.userTags.value
      if (tags.isNotEmpty()) {
        item("folders-line") { Box(Modifier.padding(vertical = 6.dp, horizontal = 24.dp).fillMaxWidth().height(1.dp).background(VibeColors.divider())) }
        itemsIndexed(tags, key = { _, t -> "tag" + t.chatTagId }) { _, t -> VibeFolderItem(t) }
      }
      item {
        Box(Modifier.padding(vertical = 8.dp).size(48.dp).clip(CircleShape).background(VibeColors.high()).clickable { showNewChatSheet() }, contentAlignment = Alignment.Center) {
          Icon(VibeIcons.Add, "New chat or group", tint = VibeColors.blurple, modifier = Modifier.size(24.dp))
        }
      }
    }
  }
}

@Composable
private fun VibeRailItem(chat: Chat) {
  val active = chat.id == (VibeRail.selected.value ?: chatModel.chatId.value)
  val unread = chat.chatStats.unreadCount
  val muted = chat.chatInfo.chatSettings?.enableNtfs?.let { it != MsgFilter.All } ?: false
  val showMenu = remember { mutableStateOf(false) }
  val inProgress = remember { mutableStateOf(false) }
  val railScope = rememberCoroutineScope()
  val pill by animateDpAsState(if (active) 30.dp else if (unread > 0) 8.dp else 0.dp, VibeSprings.pop(), label = "pill")
  val radius by animateDpAsState(if (active) 12.dp else 15.dp, VibeSprings.pop(), label = "radius")
  val ring by animateColorAsState(if (active) VibeColors.blurple else Color.Transparent, tween(280), label = "ring")
  val press = remember { MutableInteractionSource() }
  val pressed by press.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "press")
  Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
    Box(
      Modifier.align(Alignment.CenterStart).width(4.dp).height(pill)
        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(VibeColors.onSurface())
    )
    Box(
      Modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .size(56.dp)
        .border(2.dp, ring, RoundedCornerShape(radius + 3.dp))
        .combinedClickable(
          interactionSource = press, indication = null,
          onClick = { VibeRail.selected.value = chat.id },
          onLongClick = { VibeChatActions.show(chat) { openVibeChat(chat, railScope) } },
        ),
      contentAlignment = Alignment.Center,
    ) {
      VibeChatAvatar(chat.chatInfo, 46.dp, shape = RoundedCornerShape(radius))
      VibeTypingDot(chat, Modifier.align(Alignment.BottomEnd))
    }
    if (chat.chatInfo.chatSettings?.favorite == true) {
      Box(Modifier.align(Alignment.BottomStart).padding(start = 8.dp).size(18.dp).clip(CircleShape).background(VibeColors.lowest()), contentAlignment = Alignment.Center) {
        Icon(VibeIcons.Pin, null, Modifier.size(12.dp), tint = VibeColors.onVariant())
      }
    }
    if (unread > 0) {
      Box(
        Modifier.align(Alignment.TopEnd).padding(end = 6.dp)
          .defaultMinSize(minWidth = 18.dp).height(18.dp)
          .clip(RoundedCornerShape(9.dp))
          .background(if (muted) Color(0xFF80848E) else VibeColors.red)
          .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(if (unread > 99) "99+" else "$unread", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun VibeFolderItem(tag: ChatTag) {
  val active = VibeRail.folder.value == tag.chatTagId && VibeRail.selected.value == null
  val unread = chatModel.chats.value.count { tag.chatTagId in it.vibeTags() && !VibeChatSets.hidden(it) && (it.chatStats.unreadCount > 0 || it.chatStats.unreadChat) }
  val radius by animateDpAsState(if (active) 12.dp else 15.dp, VibeSprings.pop(), label = "fradius")
  val pill by animateDpAsState(if (active) 30.dp else if (unread > 0) 8.dp else 0.dp, VibeSprings.pop(), label = "fpill")
  Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
    Box(
      Modifier.align(Alignment.CenterStart).width(4.dp).height(pill)
        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(VibeColors.onSurface())
    )
    Box(
      Modifier.size(46.dp).clip(RoundedCornerShape(radius))
        .background(if (active) VibeColors.blurple else VibeColors.high())
        .clickable {
          VibeHaptics.selection()
          VibeRail.selected.value = null
          VibeRail.folder.value = if (VibeRail.folder.value == tag.chatTagId) null else tag.chatTagId
        },
      contentAlignment = Alignment.Center
    ) {
      val emoji = tag.chatTagEmoji
      if (emoji != null) Text(emoji, fontSize = 22.sp)
      else Text(tag.chatTagText.trim().take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else VibeColors.onSurface())
    }
  }
}

/** A little pen writing on the picture while someone is typing (live, or typing dots) there. */
@Composable
fun VibeTypingDot(chat: Chat, modifier: Modifier = Modifier) {
  val last = chat.chatItems.lastOrNull()
  if (last == null || !last.meta.isLive || last.chatDir.sent) return
  // a tiny pen writing, as in Vibe
  val inf = rememberInfiniteTransition(label = "typingPen")
  val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "t")
  Box(
    modifier.size(20.dp).clip(CircleShape).background(VibeColors.lowest()).padding(2.dp)
      .clip(CircleShape).background(VibeColors.blurple),
    contentAlignment = Alignment.Center
  ) {
    val a = t * 2f * Math.PI.toFloat()
    Icon(
      VibeIcons.Edit, null,
      Modifier.size(11.dp).graphicsLayer {
        translationX = kotlin.math.sin(a) * 1.6.dp.toPx()
        translationY = kotlin.math.abs(kotlin.math.cos(a)) * -1.2.dp.toPx()
        rotationZ = kotlin.math.sin(a * 2) * 8f
      },
      tint = Color.White
    )
  }
}

/* ---------------------------------------------------------------- pane --- */

@Composable
private fun VibeSearchField(value: String, onChange: (String) -> Unit, onClick: (() -> Unit)? = null) {
  Row(
    Modifier
      .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)
      .fillMaxWidth()
      .height(44.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(VibeColors.lowest())
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(VibeIcons.Search, null, Modifier.size(22.dp), tint = VibeColors.onVariant())
    Spacer(Modifier.width(10.dp))
    Box(Modifier.weight(1f)) {
      if (value.isEmpty()) Text("Find or start a conversation", fontSize = 15.sp, color = VibeColors.onVariant(), maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (onClick == null) {
        BasicTextField(
          value, onChange, singleLine = true,
          textStyle = TextStyle(fontSize = 15.sp, color = VibeColors.onSurface()),
          cursorBrush = SolidColor(VibeColors.blurple),
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}

@Composable
private fun SectionLabel(text: String, onAdd: (() -> Unit)? = null) {
  Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 2.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(text, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = VibeColors.onVariant())
    if (onAdd != null) {
      Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onAdd), contentAlignment = Alignment.Center) {
        Icon(VibeIcons.Add, null, Modifier.size(22.dp), tint = VibeColors.onVariant())
      }
    }
  }
}

@Composable
private fun VibeDmPane(stopped: Boolean) {
  var filter by remember { mutableStateOf("") }
  val q = filter.trim().lowercase()
  val folderId = VibeRail.folder.value
  val folder = folderId?.let { id -> chatModel.userTags.value.firstOrNull { it.chatTagId == id } }
  if (folderId != null && folder == null) LaunchedEffect(folderId) { VibeRail.folder.value = null }
  val chats = chatModel.chats.value.filter {
    !VibeChatSets.hidden(it) && (folder == null || folder.chatTagId in it.vibeTags()) &&
        (q.isEmpty() || it.chatInfo.displayName.lowercase().contains(q))
  }
  val requests = chats.filter { it.chatInfo is ChatInfo.ContactRequest }
  val notes = chats.firstOrNull { it.chatInfo is ChatInfo.Local }
  val people = chats.filter { it.chatInfo is ChatInfo.Direct || it.chatInfo is ChatInfo.ContactConnection }
  val groups = chats.filter { it.chatInfo is ChatInfo.Group }
  val pinned = (people + groups).filter { it.chatInfo.chatSettings?.favorite == true }
  val others = people.filter { it.chatInfo.chatSettings?.favorite != true }
  val otherGroups = groups.filter { it.chatInfo.chatSettings?.favorite != true }
  Column(Modifier.fillMaxSize().statusBarsPadding()) {
    VibeSearchField(filter, { filter = it })
    if (folder != null) {
      Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text((folder.chatTagEmoji?.let { "$it  " } ?: "") + folder.chatTagText, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.size(36.dp).clip(CircleShape).clickable { VibeRail.folder.value = null }, contentAlignment = Alignment.Center) {
          Icon(VibeIcons.Close, "All chats", Modifier.size(20.dp), tint = VibeColors.onVariant())
        }
      }
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 110.dp)) {
      if (notes != null && q.isEmpty() && folder == null) {
        item("notes") { NavTile(VibeIcons.Star, "Private notes", notes) }
      }
      if (requests.isNotEmpty()) {
        item("inv") { SectionLabel("INVITES — ${requests.size}") }
        itemsIndexed(requests, key = { _, c -> "inv" + c.id }) { i, c -> Stagger(i) { InviteTile(c) } }
      }
      if (pinned.isNotEmpty()) {
        item("pin") { SectionLabel("PINNED — ${pinned.size}") }
        itemsIndexed(pinned, key = { _, c -> "pin" + c.id }) { i, c -> Stagger(i) { DmTile(c) } }
      }
      item("dms") { SectionLabel("DIRECT MESSAGES — ${others.size}", onAdd = { showNewChatSheet() }) }
      itemsIndexed(others, key = { _, c -> "dm" + c.id }) { i, c -> Stagger(i + pinned.size) { DmTile(c) } }
      item("groups") { SectionLabel("GROUPS — ${otherGroups.size}", onAdd = { showNewChatSheet() }) }
      itemsIndexed(otherGroups, key = { _, c -> "gr" + c.id }) { i, c -> Stagger(i + pinned.size + others.size) { DmTile(c) } }
    }
  }
}

/** Rows rise in one after another when the list first shows. */
@Composable
private fun Stagger(index: Int, content: @Composable () -> Unit) {
  val p = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    delay((index.coerceAtMost(12) * 25).toLong())
    p.animateTo(1f, VibeSprings.page())
  }
  Box(Modifier.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 24f }) { content() }
}

@Composable
private fun NavTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, chat: Chat) {
  val scope = rememberCoroutineScope()
  Row(
    Modifier.fillMaxWidth().clickable { openVibeChat(chat, scope) }.padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, null, Modifier.size(24.dp), tint = VibeColors.onVariant())
    Spacer(Modifier.width(16.dp))
    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = VibeColors.onSurface())
  }
}

@Composable
private fun DmTile(chat: Chat) {
  val active = chat.id == chatModel.chatId.value
  val unread = chat.chatStats.unreadCount
  val showMenu = remember { mutableStateOf(false) }
  val inProgress = remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val press = remember { MutableInteractionSource() }
  val pressed by press.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "press")
  val info = chat.chatInfo
  val pending = info is ChatInfo.ContactConnection || (info is ChatInfo.Direct && !info.contact.ready)
  Box(
    Modifier
      .padding(horizontal = 8.dp, vertical = 1.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(RoundedCornerShape(12.dp))
      .background(if (active) VibeColors.secondaryContainer() else Color.Transparent)
      .combinedClickable(
        interactionSource = press, indication = LocalIndication.current,
        onClick = {
          if (info is ChatInfo.Direct || info is ChatInfo.Group) VibeRail.selected.value = chat.id else openVibeChat(chat, scope)
        },
        onLongClick = {
          if (info is ChatInfo.ContactConnection) showMenu.value = true
          else VibeChatActions.show(chat) { openVibeChat(chat, scope) }
        },
      )
  ) {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      Box {
        VibeChatAvatar(info, 42.dp)
        VibeTypingDot(chat, Modifier.align(Alignment.BottomEnd))
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            info.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.SemiBold, fontSize = 16.sp,
            color = VibeColors.onSurface(), modifier = Modifier.weight(1f, false)
          )
          if (info.chatSettings?.favorite == true) {
            Spacer(Modifier.width(4.dp))
            Icon(VibeIcons.Pin, null, Modifier.size(13.dp), tint = VibeColors.onVariant())
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(VibeIcons.Lock, null, Modifier.size(12.dp), tint = VibeColors.onVariant())
          Spacer(Modifier.width(4.dp))
          val sub = when {
            pending -> "connecting…"
            info is ChatInfo.Group -> if (info.groupInfo.useRelays) "Channel" else "Group"
            else -> "Encrypted Direct Message"
          }
          Text(sub, fontSize = 12.sp, color = VibeColors.onVariant(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      if (unread > 0) VibeUnreadBadge(unread)
    }
    DefaultDropdownMenu(showMenu) {
      val showMarkRead = chat.chatStats.unreadCount > 0 || chat.chatStats.unreadChat
      if (info is ChatInfo.ContactConnection) ContactConnectionMenuItems(chat.remoteHostId, info, chatModel, showMenu)
    }
  }
}

@Composable
private fun InviteTile(chat: Chat) {
  val info = chat.chatInfo as? ChatInfo.ContactRequest ?: return
  Row(
    Modifier.padding(horizontal = 8.dp, vertical = 2.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(VibeColors.high())
      .clickable { contactRequestAlertDialog(chat.remoteHostId, info, chatModel) }
      .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    VibeChatAvatar(info, 42.dp)
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(info.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = VibeColors.onSurface())
      Text("Wants to chat with you", fontSize = 12.sp, color = VibeColors.onVariant())
    }
    Box(Modifier.size(40.dp).clip(CircleShape).clickable { rejectContactRequest(chat.remoteHostId, info.apiId, chatModel) }, contentAlignment = Alignment.Center) {
      Icon(VibeIcons.Close, "Reject", tint = Color(0xFFFF5252))
    }
    Box(Modifier.size(40.dp).clip(CircleShape).clickable { acceptContactRequest(chat.remoteHostId, incognito = false, info.apiId, isCurrentUser = true, chatModel) }, contentAlignment = Alignment.Center) {
      Icon(VibeIcons.Done, "Accept", tint = VibeColors.onSurface())
    }
  }
}

@Composable
fun VibeUnreadBadge(count: Int) {
  Box(
    Modifier.defaultMinSize(minWidth = 20.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).background(VibeColors.red).padding(horizontal = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(if (count > 99) "99+" else "$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
  }
}

/* ------------------------------------------------------- chat's pane --- */

@Composable
private fun VibeChatPane(chat: Chat) {
  val scope = rememberCoroutineScope()
  val info = chat.chatInfo
  val openChat: () -> Unit = { openVibeChat(chat, scope) }
  Column(
    Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .verticalScroll(rememberScrollState())
      .padding(bottom = 120.dp)
  ) {
    VibeSearchField("", {}, onClick = { VibeRail.selected.value = null })
    Column(Modifier.padding(horizontal = 12.dp)) {
      Spacer(Modifier.height(12.dp))
      val openProfile: () -> Unit = { if (info is ChatInfo.Direct) VibeProfileSheet.showContact(chat) { openChat() } else openChat() }
      VibeChatAvatar(info, 62.dp, Modifier.padding(start = 6.dp).clickable(onClick = openProfile))
      Spacer(Modifier.height(10.dp))
      Text(
        info.displayName, Modifier.padding(start = 6.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold,
        color = VibeColors.onSurface(), maxLines = 1, overflow = TextOverflow.Ellipsis,
      )
      val sub = when {
        info is ChatInfo.Group -> if (info.groupInfo.useRelays) "Channel" else "Group"
        info is ChatInfo.Direct && !info.contact.ready -> "connecting…"
        info.fullName.isNotEmpty() && info.fullName != info.displayName -> info.fullName
        else -> null
      }
      if (sub != null) Text(sub, Modifier.padding(start = 6.dp, top = 2.dp), fontSize = 13.5.sp, color = VibeColors.onVariant())
      Spacer(Modifier.height(8.dp))
      VibeHashRow(
        label = "chat",
        subtitle = "End-to-end encrypted",
        subtitleIcon = VibeIcons.Lock,
        unread = chat.chatStats.unreadCount,
        onClick = openChat,
      )
      if (info is ChatInfo.Direct) {
        val missed = remember(chat.chatItems) { missedCalls(chat) }
        VibeHashRow(
          label = "audio-call", icon = VibeIcons.Call,
          unread = missed.first, subtitle = if (missed.first > 0) "Missed call" else null,
          onClick = { if (info.contact.ready) startChatCall(chat.remoteHostId, info, CallMediaType.Audio) },
        )
        VibeHashRow(
          label = "video-call", icon = VibeIcons.Videocam,
          unread = missed.second, subtitle = if (missed.second > 0) "Missed video call" else null,
          onClick = { if (info.contact.ready) startChatCall(chat.remoteHostId, info, CallMediaType.Video) },
        )
      }
      VibePaneHeader("INFO")
      VibeHashRow(label = "media", subtitle = "Photos and videos", onClick = { VibeChatContent.show(chat, VibeContentKind.Media) })
      VibeHashRow(label = "files", onClick = { VibeChatContent.show(chat, VibeContentKind.Files) })
      VibeHashRow(label = "links", onClick = { VibeChatContent.show(chat, VibeContentKind.Links) })
      VibeHashRow(label = "voice-messages", onClick = { VibeChatContent.show(chat, VibeContentKind.Voice) })
      VibeHashRow(label = "starred-messages", onClick = { VibeChatContent.show(chat, VibeContentKind.Starred) })
      if (info is ChatInfo.Group) {
        VibePaneHeader("MEMBERS")
        VibeHashRow(label = "members", subtitle = "Open the chat, then tap its name", onClick = openChat)
      }
      if (info is ChatInfo.Direct) VibeHashRow(label = "profile", onClick = openProfile)
    }
  }
}

/** Unseen missed audio / video calls (the call items that are still unread). */
private fun missedCalls(chat: Chat): Pair<Int, Int> {
  var audio = 0
  var video = 0
  for (ci in chat.chatItems) {
    val c = ci.content
    if (c is CIContent.RcvCall && ci.isRcvNew && (c.status == CICallStatus.Missed || c.status == CICallStatus.Rejected)) {
      audio++
    }
  }
  return audio to video
}

@Composable
fun VibePaneHeader(text: String) {
  Text(
    text, Modifier.padding(start = 6.dp, top = 16.dp, bottom = 4.dp),
    fontSize = 12.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, color = VibeColors.onVariant()
  )
}

/** "#   |  label", with an optional line under it; lights up with a counter when unread. */
@Composable
fun VibeHashRow(
  label: String,
  subtitle: String? = null,
  subtitleIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  unread: Int = 0,
  onClick: () -> Unit,
) {
  val lit = unread > 0
  val muted = if (lit) VibeColors.onSurface() else VibeColors.onVariant()
  val press = remember { MutableInteractionSource() }
  val pressed by press.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "press")
  Box {
    Row(
      Modifier
        .fillMaxWidth()
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(14.dp))
        .clickable(press, LocalIndication.current, onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, null, Modifier.size(21.dp), tint = muted)
        else Text("#", fontSize = 21.sp, color = muted)
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(
          buildAnnotatedString {
            withStyle(SpanStyle(color = muted.copy(alpha = 0.53f))) { append("|  ") }
            append(label)
          },
          fontSize = 16.sp,
          color = if (lit) VibeColors.onSurface() else muted,
          fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (subtitle != null) {
          Row(Modifier.padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            if (subtitleIcon != null) {
              Icon(subtitleIcon, null, Modifier.size(12.5.dp), tint = muted)
              Spacer(Modifier.width(4.dp))
            }
            Text(subtitle, fontSize = 13.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
      if (lit) {
        Spacer(Modifier.width(8.dp))
        VibeUnreadBadge(unread)
      }
    }
    if (lit) {
      Box(
        Modifier.align(Alignment.CenterStart).offset(x = (-10).dp).width(4.dp).height(8.dp)
          .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(VibeColors.onSurface())
      )
    }
  }
}

/* ------------------------------------------------------- user card --- */

@Composable
private fun VibeUserBar(userPickerState: MutableStateFlow<AnimatedViewState>, modifier: Modifier) {
  val user = chatModel.currentUser.value ?: return
  val running = chatModel.chatRunning.value
  // a quiet look for a new Vibe X now and then (at most every 6 hours)
  LaunchedEffect(Unit) {
    VibeScheduled.start()
    VibeCrash.checkOnStart()
    VibeUpdater.maybeCheck()
    // after the update check, so the two never stack
    kotlinx.coroutines.delay(4000)
    VibeSupport.maybeAsk()
  }
  val status = when (running) {
    true -> "Online"
    false -> "Chat is stopped"
    null -> "Connecting…"
  }
  Box(
    modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp)
      .height(76.dp)
  ) {
    Row(
      Modifier
        .padding(start = 34.dp, top = 8.dp, bottom = 8.dp)
        .fillMaxSize()
        .shadow(6.dp, RoundedCornerShape(50))
        .clip(RoundedCornerShape(50))
        .background(VibeColors.container())
        .padding(start = 50.dp, end = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { userPickerState.value = AnimatedViewState.VISIBLE }.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
      ) {
        Text(user.displayName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = VibeColors.onSurface())
        Text(status, fontSize = 13.sp, maxLines = 1, color = VibeColors.onVariant())
      }
      Box(
        Modifier.size(44.dp).clip(CircleShape).clickable {
          ModalManager.start.showModalCloseable(cardScreen = true) { close -> SettingsView(chatModel, AppLock::setPerformLA, close) }
        },
        contentAlignment = Alignment.Center
      ) {
        VibeUpdateDot { Icon(VibeIcons.Settings, "Settings", tint = VibeColors.onVariant()) }
      }
    }
    Box(Modifier.align(Alignment.TopStart).clip(CircleShape).background(VibeColors.surface()).padding(3.dp)) {
      VibeAvatar(
        user.image, user.displayName, 64.dp,
        Modifier.clickable { VibeOwnProfile.show() }
      )
      Box(
        Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(VibeColors.surface()).padding(3.dp)
          .clip(CircleShape).background(if (running == true) VibeColors.green else Color(0xFF80848E))
      )
    }
  }
}
