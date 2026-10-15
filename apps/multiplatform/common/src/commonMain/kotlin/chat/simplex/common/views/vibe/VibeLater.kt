package chat.simplex.common.views.vibe

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.Log
import chat.simplex.common.platform.TAG
import chat.simplex.common.platform.appPreferences
import chat.simplex.common.platform.chatModel
import chat.simplex.common.platform.showToast
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.text.SimpleDateFormat
import java.util.*

/* ---------------------------------------------------------------------------
 * Send later: long-press send, pick a time. The message waits on this phone
 * and is sent at that time (or as soon as Vibe X runs again, if it wasn't
 * running then).
 * ------------------------------------------------------------------------ */

@Serializable
data class VibeScheduledMsg(
  val id: Long,
  val userId: Long,
  val rh: Long? = null,
  val chatId: String,
  val text: String,
  val at: Long,
  val quoted: Long? = null,
)

object VibeScheduled {
  val list = mutableStateOf<List<VibeScheduledMsg>?>(null)
  private val ser = ListSerializer(VibeScheduledMsg.serializer())
  private var job: Job? = null
  private val sending = HashSet<Long>()

  fun all(): List<VibeScheduledMsg> = list.value ?: runCatching {
    appPreferences.vibeScheduled.get()?.let { json.decodeFromString(ser, it) }
  }.getOrNull().orEmpty().also { list.value = it }

  private fun save(l: List<VibeScheduledMsg>) {
    list.value = l
    appPreferences.vibeScheduled.set(json.encodeToString(ser, l))
  }

  fun forChat(chatId: String): List<VibeScheduledMsg> {
    val uid = chatModel.currentUser.value?.userId ?: return emptyList()
    return all().filter { it.chatId == chatId && it.userId == uid }.sortedBy { it.at }
  }

  fun add(chat: Chat, text: String, at: Long, quoted: Long?) {
    val uid = chatModel.currentUser.value?.userId ?: return
    save(all() + VibeScheduledMsg(System.currentTimeMillis(), uid, chat.remoteHostId, chat.id, text, at, quoted))
    VibeHaptics.success()
    showToast("Will send ${whenText(at)}")
    start()
  }

  fun remove(id: Long) = save(all().filter { it.id != id })

  fun sendNow(m: VibeScheduledMsg) {
    CoroutineScope(Dispatchers.Default).launch { send(m) }
  }

  /** Checks every 20 seconds for messages that are due (while the app runs). */
  fun start() {
    if (job?.isActive == true) return
    job = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
      while (isActive) {
        val now = System.currentTimeMillis()
        val uid = chatModel.currentUser.value?.userId
        if (chatModel.chatRunning.value == true && uid != null) {
          all().filter { it.at <= now && it.userId == uid }.forEach { send(it) }
        }
        if (all().isEmpty()) break
        delay(20_000)
      }
    }
  }

  private suspend fun send(m: VibeScheduledMsg) {
    synchronized(sending) { if (!sending.add(m.id)) return }
    try {
      val chat = chatModel.chats.value.firstOrNull { it.id == m.chatId } ?: run {
        // the chat is gone: nothing to send to
        withContext(Dispatchers.Main) { remove(m.id) }
        return
      }
      val info = chat.chatInfo
      val sent = chatModel.controller.apiSendMessages(
        m.rh, info.chatType, info.apiId, info.groupChatScope(),
        composedMessages = listOf(ComposedMessage(null, m.quoted, MsgContent.MCText(m.text), emptyMap()))
      )
      if (!sent.isNullOrEmpty()) {
        withContext(Dispatchers.Main) {
          remove(m.id)
          sent.forEach { chatModel.chatsContext.addChatItem(m.rh, it.chatInfo, it.chatItem) }
        }
      }
    } catch (e: Throwable) {
      Log.e(TAG, "Vibe send later: ${e.message}")
    } finally {
      synchronized(sending) { sending.remove(m.id) }
    }
  }

  fun whenText(at: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance()
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(at))
    return when {
      c.get(Calendar.YEAR) == today.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "today at $time"
      c.get(Calendar.YEAR) == today.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) + 1 -> "tomorrow at $time"
      else -> SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(at))
    }
  }

  /** The choices in the long-press pane. */
  fun presets(): List<Pair<String, Long>> {
    val now = System.currentTimeMillis()
    fun at(daysAhead: Int, hour: Int): Long = Calendar.getInstance().apply {
      add(Calendar.DAY_OF_YEAR, daysAhead); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val out = mutableListOf(
      vt("In 30 minutes") to now + 30 * 60_000L,
      vt("In 1 hour") to now + 60 * 60_000L,
      vt("In 3 hours") to now + 3 * 60 * 60_000L,
    )
    if (at(0, 21) - now > 60 * 60_000L) out.add(vt("Tonight at 9 PM") to at(0, 21))
    out.add(vt("Tomorrow at 8 AM") to at(1, 8))
    out.add(vt("Tomorrow at 6 PM") to at(1, 18))
    return out
  }
}

/** "🕓 2 scheduled" above the input; tap to see, send now or delete them. */
@Composable
fun VibeScheduledBar(chat: Chat) {
  VibeScheduled.list.value // follow changes
  val items = VibeScheduled.forChat(chat.id)
  if (items.isEmpty()) return
  LaunchedEffect(Unit) { VibeScheduled.start() }
  Row(
    Modifier.padding(start = 16.dp, top = 6.dp).clip(RoundedCornerShape(14.dp)).background(VibeColors.blurple.copy(alpha = 0.14f))
      .clickable { showScheduled(chat) }.padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(VibeIcons.Schedule, null, Modifier.size(16.dp), tint = VibeColors.blurple)
    Spacer(Modifier.width(6.dp))
    Text(
      if (items.size == 1) "Scheduled ${VibeScheduled.whenText(items[0].at)}" else "${items.size} scheduled messages",
      fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.blurple
    )
  }
}

private fun showScheduled(chat: Chat) {
  VibeSheets.show(background = { vibePage() }) { close ->
    VibeScheduled.list.value
    val items = VibeScheduled.forChat(chat.id)
    if (items.isEmpty()) LaunchedEffect(Unit) { close() }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
      Text(vt("Scheduled"), Modifier.padding(start = 20.dp, bottom = 10.dp), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      for (m in items) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(vibeCard()).padding(14.dp)) {
          Text(VibeScheduled.whenText(m.at).replaceFirstChar { it.uppercase() }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VibeColors.blurple)
          Spacer(Modifier.height(4.dp))
          Text(m.text, maxLines = 4, overflow = TextOverflow.Ellipsis, color = VibeColors.onSurface())
          Row(Modifier.padding(top = 8.dp)) {
            Text(vt("Send now"), Modifier.clip(RoundedCornerShape(12.dp)).clickable { VibeScheduled.sendNow(m) }.padding(horizontal = 10.dp, vertical = 6.dp), color = VibeColors.blurple, fontWeight = FontWeight.SemiBold)
            Text(vt("Delete"), Modifier.clip(RoundedCornerShape(12.dp)).clickable { VibeScheduled.remove(m.id) }.padding(horizontal = 10.dp, vertical = 6.dp), color = VibeColors.red, fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }
  }
}

/* ---------------------------------------------------------------------------
 * Activity pill: while you're inside a chat, a message in another chat
 * slides a pill down from the top (picture, name, the message). Tap it to
 * go there; flick it up to dismiss.
 * ------------------------------------------------------------------------ */

object VibeActivity {
  class Ping(val id: Long, val chatId: String, val name: String, val image: String?, val text: String)
  val current = mutableStateOf<Ping?>(null)
  private var next = 0L

  fun onNew(cInfo: ChatInfo, item: ChatItem) {
    if (!vibeStyle) return
    if (item.chatDir.sent || !item.isRcvNew || item.vibeTypingDots() || item.vibeHidden()) return
    val open = chatModel.chatId.value ?: return // only inside a chat
    if (open == cInfo.id) return
    if (VibeChatSets.isLocked(cInfo.id)) return
    if (!cInfo.ntfsEnabled(item)) return
    val text = when {
      item.content.text.isNotBlank() -> VibeEffect.strip(item.content.text)
      item.content.msgContent is MsgContent.MCImage -> "📷 Photo"
      item.content.msgContent is MsgContent.MCVideo -> "🎬 Video"
      item.content.msgContent is MsgContent.MCVoice -> "🎤 Voice message"
      item.file != null -> "📎 File"
      else -> return
    }
    val sender = (item.chatDir as? CIDirection.GroupRcv)?.groupMember?.displayName
    current.value = Ping(next++, cInfo.id, cInfo.displayName, cInfo.image, if (sender != null) "$sender: $text" else text)
  }
}

@Composable
fun VibeActivityHost() {
  val ping = VibeActivity.current.value ?: return
  val scope = rememberCoroutineScope()
  val p = remember(ping.id) { Animatable(0f) }
  val drag = remember(ping.id) { Animatable(0f) }
  LaunchedEffect(ping.id) {
    VibeHaptics.light()
    p.animateTo(1f, VibeSprings.pop())
    delay(3800)
    p.animateTo(0f, VibeSprings.page())
    if (VibeActivity.current.value?.id == ping.id) VibeActivity.current.value = null
  }
  fun dismiss() = scope.launch {
    p.animateTo(0f, VibeSprings.page())
    if (VibeActivity.current.value?.id == ping.id) VibeActivity.current.value = null
  }
  Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
    Row(
      Modifier
        .graphicsLayer {
          translationY = -(1f - p.value) * 90.dp.toPx() + drag.value
          alpha = p.value.coerceIn(0f, 1f)
          scaleX = 0.9f + 0.1f * p.value; scaleY = 0.9f + 0.1f * p.value
        }
        .padding(horizontal = 16.dp)
        .widthIn(max = 420.dp)
        .shadow(10.dp, RoundedCornerShape(28.dp))
        .clip(RoundedCornerShape(28.dp))
        .background(VibeColors.high())
        .pointerInput(ping.id) {
          detectVerticalDragGestures(
            onDragEnd = { if (drag.value < -24.dp.toPx()) dismiss() else scope.launch { drag.animateTo(0f, VibeSprings.release()) } }
          ) { _, dy -> scope.launch { drag.snapTo((drag.value + dy).coerceAtMost(0f)) } }
        }
        .clickable {
          val chat = chatModel.chats.value.firstOrNull { it.id == ping.chatId }
          VibeActivity.current.value = null
          if (chat != null) openVibeChat(chat, scope)
        }
        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      VibeAvatar(ping.image, ping.name, 36.dp)
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f, false)) {
        Text(ping.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = VibeColors.onSurface())
        Text(ping.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = VibeColors.onVariant())
      }
      Spacer(Modifier.width(8.dp))
      Box(Modifier.size(8.dp).clip(CircleShape).background(VibeColors.blurple))
    }
  }
}
