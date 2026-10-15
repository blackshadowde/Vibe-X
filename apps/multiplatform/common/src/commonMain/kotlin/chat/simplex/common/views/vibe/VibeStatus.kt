package chat.simplex.common.views.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.newchat.planAndConnect
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/* ---------------------------------------------------------------------------
 * Custom status and local time, as in Vibe. SimpleX profiles have a short
 * description, which Vibe X uses for the status ("☕ Taking a break"), and
 * your local time zone rides as a last line of your bio, so other Vibe X
 * users see "2:14 AM for you" (other SimpleX apps just see the line).
 * ------------------------------------------------------------------------ */

object VibeStatus {
  private val tzLine = Regex("""(?m)^🕓 Time zone: ([A-Za-z_]+(?:/[A-Za-z_\-+0-9]+)*)\s*$""")

  fun timeZoneOf(description: String?): String? = description?.let { tzLine.find(it)?.groupValues?.get(1) }

  /** The bio without the time zone line. */
  fun bioOf(description: String?): String? = description?.replace(tzLine, "")?.trim()?.ifBlank { null }

  /** "2:14 AM" in [tz], or null if it isn't a real time zone. */
  fun localTime(tz: String): String? {
    val zone = TimeZone.getTimeZone(tz)
    if (zone.id == "GMT" && tz != "GMT") return null
    return SimpleDateFormat("h:mm a", Locale.getDefault()).apply { timeZone = zone }.format(Date())
  }

  /** Clears a status whose "Clear after" has passed. */
  fun clearIfExpired() {
    val at = appPreferences.vibeStatusClearAt.get()
    if (at <= 0L || System.currentTimeMillis() < at) return
    val user = chatModel.currentUser.value ?: return
    appPreferences.vibeStatusClearAt.set(0L)
    withBGApi { save(user, null, VibeStatus.timeZoneOf(user.profile.description) != null) }
  }

  private suspend fun save(user: User, status: String?, shareTime: Boolean): Boolean {
    val p = user.profile.toProfile()
    val bio = bioOf(p.description)
    val tz = if (shareTime) java.util.TimeZone.getDefault().id else null
    val description = listOfNotNull(bio, tz?.let { "🕓 Time zone: $it" }).joinToString("\n").ifBlank { null }
    val updated = chatModel.controller.apiUpdateProfile(user.remoteHostId, p.copy(shortDescr = status?.trim()?.ifBlank { null }, description = description))
      ?: return false
    withContext(Dispatchers.Main) { chatModel.updateCurrentUser(user.remoteHostId, updated.first) }
    return true
  }

  private val presets = listOf("☕ Taking a break", "💼 At work", "📚 Studying", "🎮 Gaming", "🌙 Sleeping", "🚗 Driving", "🏋️ At the gym", "📵 Busy")
  private val clearAfter = listOf("Don't clear" to 0L, "1 hour" to 60 * 60_000L, "4 hours" to 4 * 60 * 60_000L, "Today" to -1L, "This week" to 7 * 24 * 60 * 60_000L)

  fun showEditor() {
    VibeSheets.show(background = { vibePage() }) { close -> Editor(close) }
  }

  @Composable
  private fun Editor(close: () -> Unit) {
    val user = chatModel.currentUser.value ?: return
    var text by remember { mutableStateOf(user.profile.shortDescr ?: "") }
    var clear by remember { mutableStateOf(0L) }
    var share by remember { mutableStateOf(timeZoneOf(user.profile.description) != null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
      Text("Set a status", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      Spacer(Modifier.height(12.dp))
      // the thought bubble
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(vibeCard()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("💭", fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
          if (text.isEmpty()) Text("What's happening?", color = VibeColors.onVariant(), fontSize = 16.sp)
          BasicTextField(
            text, { if (it.length <= 100) text = it }, singleLine = true,
            textStyle = TextStyle(fontSize = 16.sp, color = VibeColors.onSurface()),
            cursorBrush = SolidColor(VibeColors.blurple), modifier = Modifier.fillMaxWidth()
          )
        }
        if (text.isNotEmpty()) {
          Box(Modifier.size(28.dp).clip(CircleShape).clickable { text = "" }, contentAlignment = Alignment.Center) {
            Icon(VibeIcons.Close, null, Modifier.size(18.dp), tint = VibeColors.onVariant())
          }
        }
      }
      Spacer(Modifier.height(10.dp))
      Row(Modifier.horizontalScroll(rememberScrollState())) {
        for (p in presets) Chip(p, text == p) { text = p }
      }
      Text("Clear after", Modifier.padding(top = 14.dp, bottom = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VibeColors.onVariant())
      Row(Modifier.horizontalScroll(rememberScrollState())) {
        for ((label, ms) in clearAfter) Chip(label, clear == ms) { clear = ms }
      }
      Row(Modifier.fillMaxWidth().padding(top = 10.dp).clickable { share = !share }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Show my local time", fontSize = 16.sp, color = VibeColors.onSurface())
          Text("People see what time it is for you (${java.util.TimeZone.getDefault().id})", fontSize = 13.sp, color = VibeColors.onVariant())
        }
        Switch(share, { share = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VibeColors.blurple, checkedTrackAlpha = 1f))
      }
      Spacer(Modifier.height(16.dp))
      Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(VibeColors.blurple).clickable(enabled = !busy) {
          busy = true
          scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { save(user, text, share) }.getOrDefault(false) }
            busy = false
            if (ok) {
              val until = when {
                text.isBlank() || clear == 0L -> 0L
                clear == -1L -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
                else -> System.currentTimeMillis() + clear
              }
              appPreferences.vibeStatusClearAt.set(until)
              VibeHaptics.success()
              showToast(if (text.isBlank()) "Status cleared" else "Status set")
              close()
            }
          }
        },
        contentAlignment = Alignment.Center
      ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
        else Text("Save", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
      }
    }
  }

  @Composable
  private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
      label,
      Modifier.padding(end = 8.dp).clip(RoundedCornerShape(16.dp))
        .then(if (on) Modifier.background(VibeColors.blurple) else Modifier.border(1.dp, VibeColors.divider(), RoundedCornerShape(16.dp)))
        .clickable { VibeHaptics.selection(); onClick() }.padding(horizontal = 12.dp, vertical = 7.dp),
      color = if (on) Color.White else VibeColors.onSurface(), fontSize = 14.sp
    )
  }
}

/** Discord-style thought bubble: two small dots trailing to the bubble with the status. */
@Composable
fun VibeStatusBubble(status: String, modifier: Modifier = Modifier) {
  Row(modifier, verticalAlignment = Alignment.Bottom) {
    Box(Modifier.padding(bottom = 2.dp).size(6.dp).clip(CircleShape).background(VibeColors.container()))
    Spacer(Modifier.width(3.dp))
    Box(Modifier.padding(bottom = 6.dp).size(10.dp).clip(CircleShape).background(VibeColors.container()))
    Spacer(Modifier.width(3.dp))
    Text(
      status, Modifier.clip(RoundedCornerShape(16.dp)).background(VibeColors.container()).padding(horizontal = 12.dp, vertical = 8.dp),
      fontSize = 14.sp, color = VibeColors.onSurface(), maxLines = 2, overflow = TextOverflow.Ellipsis
    )
  }
}

/* ---------------------------------------------------------------------------
 * Find public groups: SimpleX's directory is a bot you message. Vibe X
 * opens it, and sends what you search for so it answers with groups.
 * ------------------------------------------------------------------------ */

object VibeDirectory {
  const val LINK = "simplex:/contact#/?v=1-4&smp=smp%3A%2F%2Fu2dS9sG8nMNURyZwqASV4yROM28Er0luVTx5X1CsMrU%3D%40smp4.simplex.im%2FeXSPwqTkKyDO3px4fLf1wx3MvPdjdLW3%23%2F%3Fv%3D1-2%26dh%3DMCowBQYDK2VuAyEAaiv6MkMH44L2TcYrt_CsX3ZvM11WgbMEUn0hkIKTOho%253D%26srv%3Do5vmywmrnaxalvz6wi3zicyftgio6psuvyniis6gco6bp6ekl4cqj4id.onion"

  fun chat(): Chat? = chatModel.chats.value.firstOrNull {
    val i = it.chatInfo
    i is ChatInfo.Direct && i.contact.ready && i.displayName.contains("Directory", ignoreCase = true)
  }

  fun show() {
    VibeSheets.show(background = { vibePage() }) { close -> Sheet(close) }
  }

  @Composable
  private fun Sheet(close: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    val dir = chat()
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
      Text("Find public groups", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      Text(
        "SimpleX's directory lists public groups. Search it, and it answers with groups you can join.",
        Modifier.padding(top = 4.dp, bottom = 14.dp), fontSize = 14.sp, color = VibeColors.onVariant()
      )
      if (dir == null) {
        Box(
          Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(VibeColors.blurple).clickable {
            close()
            scope.launch { planAndConnect(chatModel.remoteHostId(), LINK, close = null) }
          },
          contentAlignment = Alignment.Center
        ) { Text("Connect to the directory", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Text("It takes a moment the first time. Then come back here to search.", Modifier.padding(top = 8.dp), fontSize = 13.sp, color = VibeColors.onVariant())
      } else {
        Row(
          Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(VibeColors.lowest()).padding(horizontal = 14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(VibeIcons.Search, null, Modifier.size(20.dp), tint = VibeColors.onVariant())
          Spacer(Modifier.width(10.dp))
          Box(Modifier.weight(1f)) {
            if (q.isEmpty()) Text("e.g. music, India, Android", color = VibeColors.onVariant())
            BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(fontSize = 16.sp, color = VibeColors.onSurface()), cursorBrush = SolidColor(VibeColors.blurple), modifier = Modifier.fillMaxWidth())
          }
        }
        Spacer(Modifier.height(12.dp))
        Box(
          Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(VibeColors.blurple).clickable {
            val text = q.trim()
            close()
            scope.launch {
              if (text.isNotEmpty()) withContext(Dispatchers.IO) {
                val info = dir.chatInfo
                val sent = chatModel.controller.apiSendMessages(dir.remoteHostId, info.chatType, info.apiId, null, composedMessages = listOf(ComposedMessage(null, null, MsgContent.MCText(text), emptyMap())))
                withContext(Dispatchers.Main) { sent?.forEach { chatModel.chatsContext.addChatItem(dir.remoteHostId, it.chatInfo, it.chatItem) } }
              }
              openVibeChat(dir, scope)
            }
          },
          contentAlignment = Alignment.Center
        ) { Text(if (q.isBlank()) "Open the directory" else "Search", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
      }
    }
  }
}
