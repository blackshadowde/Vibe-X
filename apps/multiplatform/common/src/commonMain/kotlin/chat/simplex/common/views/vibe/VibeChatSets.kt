package chat.simplex.common.views.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.appPreferences
import chat.simplex.common.platform.chatModel
import chat.simplex.common.platform.showToast
import chat.simplex.common.views.chatlist.TagListEditor
import chat.simplex.common.views.chatlist.TagListView
import chat.simplex.common.views.helpers.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Chats you keep out of sight, stored on this phone only:
 * - archived: hidden from the rail and the list; long-press your picture to open them,
 * - locked: the same, but they open only after your fingerprint / face / phone PIN,
 *   and their notifications don't show the message.
 */
object VibeChatSets {
  private val locked = mutableStateOf<Set<String>?>(null)
  private val archived = mutableStateOf<Set<String>?>(null)

  private fun read(raw: String?): Set<String> =
    raw?.split('\n')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

  private fun lockedSet(): Set<String> = locked.value ?: read(appPreferences.vibeLockedChats.get()).also { locked.value = it }
  private fun archivedSet(): Set<String> = archived.value ?: read(appPreferences.vibeArchivedChats.get()).also { archived.value = it }

  private fun key(chatId: String) = "${chatModel.currentUser.value?.userId ?: 0}:$chatId"

  fun isLocked(chatId: String) = key(chatId) in lockedSet()
  fun isArchived(chatId: String) = key(chatId) in archivedSet()
  fun hidden(chat: Chat) = isLocked(chat.id) || isArchived(chat.id)

  private fun setLocked(chatId: String, on: Boolean) {
    val s = lockedSet().let { if (on) it + key(chatId) else it - key(chatId) }
    locked.value = s
    appPreferences.vibeLockedChats.set(s.joinToString("\n"))
  }

  fun setArchived(chatId: String, on: Boolean) {
    val s = archivedSet().let { if (on) it + key(chatId) else it - key(chatId) }
    archived.value = s
    appPreferences.vibeArchivedChats.set(s.joinToString("\n"))
  }

  fun archivedChats(): List<Chat> = chatModel.chats.value.filter { isArchived(it.id) && !isLocked(it.id) }
  fun lockedChats(): List<Chat> = chatModel.chats.value.filter { isLocked(it.id) }

  /** Locking (and unlocking) asks for your screen lock first. */
  fun toggleLock(chat: Chat) {
    val on = !isLocked(chat.id)
    unlock(if (on) vt("Lock this chat") else vt("Unlock this chat")) {
      setLocked(chat.id, on)
      if (on && chatModel.chatId.value == chat.id) chatModel.chatId.value = null
      if (on && VibeRail.selected.value == chat.id) VibeRail.selected.value = null
      VibeHaptics.success()
      showToast(if (on) "${chat.chatInfo.displayName} is locked" else "${chat.chatInfo.displayName} is unlocked")
    }
  }

  private fun unlock(title: String, then: () -> Unit) {
    authenticate(title, vt("Use your fingerprint, face or phone PIN"), oneTime = true) { r ->
      when (r) {
        is LAResult.Success -> then()
        is LAResult.Unavailable -> AlertManager.shared.showAlertMsg(
          vt("Set a screen lock first"),
          "Locked chats open with your phone's fingerprint, face or PIN (or Vibe X's own lock in Privacy). Set one up, then try again."
        )
        else -> {}
      }
    }
  }

  fun showArchived() {
    VibeSheets.show(background = { vibePage() }) { close -> ListSheet(vt("Archived chats"), vt("Hidden from your list. Long-press one to unarchive it."), { archivedChats() }, close) }
  }

  fun showLocked() {
    unlock(vt("Open locked chats")) {
      VibeSheets.show(background = { vibePage() }) { close -> ListSheet(vt("Locked chats"), vt("Only you can open these. Long-press one to unlock it."), { lockedChats() }, close) }
    }
  }

  /** Long-press your own picture: archived and locked chats, folders, switch profile. */
  fun showMore(userPickerState: MutableStateFlow<AnimatedViewState>) {
    VibeHaptics.medium()
    VibeSheets.show(background = { vibePage() }) { close ->
      Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard())) {
          Row(VibeIcons.Archive, vt("Archived chats"), archivedChats().size.takeIf { it > 0 }?.toString()) { close(); showArchived() }
          Row(VibeIcons.Lock, vt("Locked chats"), null) { close(); showLocked() }
          Row(VibeIcons.Folder, vt("Folders"), chatModel.userTags.value.size.takeIf { it > 0 }?.toString()) {
            close()
            val rh = chatModel.remoteHostId()
            ModalManager.start.showModalCloseable { c ->
              if (chatModel.userTags.value.isEmpty()) TagListEditor(rhId = rh, close = c)
              else TagListView(rhId = rh, close = c, reorderMode = true)
            }
          }
          Row(VibeIcons.Groups, vt("Switch profile"), null) { close(); userPickerState.value = AnimatedViewState.VISIBLE }
        }
      }
    }
  }

  @Composable
  private fun Row(icon: ImageVector, title: String, badge: String?, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
      Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(icon, null, Modifier.size(24.dp), tint = VibeColors.onSurface())
      Spacer(Modifier.width(16.dp))
      Text(title, Modifier.weight(1f), fontSize = 16.sp, color = VibeColors.onSurface())
      if (badge != null) Text(badge, color = VibeColors.onVariant(), fontWeight = FontWeight.SemiBold)
    }
  }

  @Composable
  private fun ListSheet(title: String, hint: String, chats: () -> List<Chat>, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    val list = chats()
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
      Text(title, Modifier.padding(start = 20.dp), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      Text(hint, Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp), fontSize = 13.sp, color = VibeColors.onVariant())
      if (list.isEmpty()) {
        Text(vt("Nothing here yet."), Modifier.padding(20.dp), color = VibeColors.onVariant())
      }
      LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(list, key = { it.id }) { chat ->
          androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().combinedClickable(
              onClick = { close(); openVibeChat(chat, scope) },
              onLongClick = { close(); VibeChatActions.show(chat) { openVibeChat(chat, scope) } }
            ).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            VibeChatAvatar(chat.chatInfo, 42.dp)
            Spacer(Modifier.width(14.dp))
            Text(chat.chatInfo.displayName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = VibeColors.onSurface())
            val unread = chat.chatStats.unreadCount
            if (unread > 0) {
              Box(Modifier.defaultMinSize(minWidth = 20.dp).height(20.dp).clip(CircleShape).background(VibeColors.red).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Text(if (unread > 99) "99+" else "$unread", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }
}

/** The folders (SimpleX's chat lists) a chat is in. */
fun Chat.vibeTags(): List<Long> = when (val i = chatInfo) {
  is ChatInfo.Direct -> i.contact.chatTags
  is ChatInfo.Group -> i.groupInfo.chatTags
  else -> emptyList()
}
