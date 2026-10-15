package chat.simplex.common.views.vibe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.chatModel
import chat.simplex.common.ui.theme.VibeBlurple
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.chat.group.MemberProfileImage
import kotlinx.datetime.*
import java.text.DateFormat
import java.util.Date

const val VIBE_HEADER_GAP_SECONDS = 7 * 60

/** Items shown as a Discord row (avatar + name); the rest keep SimpleX's centred look. */
fun ChatItem.isVibeRow(): Boolean =
  content.msgContent != null ||
    content is CIContent.SndCall || content is CIContent.RcvCall ||
    content is CIContent.SndDeleted || content is CIContent.RcvDeleted ||
    content is CIContent.SndModerated || content is CIContent.RcvModerated ||
    content is CIContent.RcvBlocked

private fun ChatItem.senderKey(): String = when (val d = chatDir) {
  is CIDirection.GroupRcv -> "m" + d.groupMember.groupMemberId
  is CIDirection.ChannelRcv -> "channel"
  else -> if (d.sent) "me" else "them"
}

/** Starts a new block (shows the avatar and name): new sender, a pause, or a non-message in between. */
fun vibeStartsBlock(item: ChatItem, older: ChatItem?): Boolean {
  if (older == null || !older.isVibeRow()) return true
  if (older.senderKey() != item.senderKey()) return true
  if ((item.meta.itemTs - older.meta.itemTs).inWholeSeconds >= VIBE_HEADER_GAP_SECONDS) return true
  val tz = TimeZone.currentSystemDefault()
  return item.meta.itemTs.toLocalDateTime(tz).date != older.meta.itemTs.toLocalDateTime(tz).date
}

fun vibeTimestamp(ts: Instant): String {
  val tz = TimeZone.currentSystemDefault()
  val day = ts.toLocalDateTime(tz).date
  val today = Clock.System.now().toLocalDateTime(tz).date
  val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ts.toEpochMilliseconds()))
  return when (day) {
    today -> "Today at $time"
    today.minus(1, DateTimeUnit.DAY) -> "Yesterday at $time"
    else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ts.toEpochMilliseconds())) + " $time"
  }
}

@Composable
private fun senderName(chatInfo: ChatInfo, item: ChatItem): Pair<String, String?> {
  val user = chatModel.currentUser.value
  return when (val d = item.chatDir) {
    is CIDirection.GroupRcv -> d.groupMember.displayName to d.groupMember.image
    is CIDirection.GroupSnd -> if (chatInfo is ChatInfo.Group) chatInfo.groupInfo.membership.displayName to chatInfo.groupInfo.membership.image else (user?.displayName ?: "You") to user?.image
    is CIDirection.ChannelRcv -> chatInfo.displayName to chatInfo.image
    else -> if (item.chatDir.sent) (user?.displayName ?: "You") to user?.image else chatInfo.displayName to chatInfo.image
  }
}

/**
 * One message, laid out like Vibe's: avatar (40) and a header (name, time)
 * on the first message of a block, text under it, left aligned, no bubble.
 * A reply starts a new block with the curved reply line above it.
 */
@Composable
fun VibeMessageRow(
  chatInfo: ChatInfo,
  item: ChatItem,
  first: Boolean,
  last: Boolean,
  onAvatarClick: () -> Unit,
  onReply: (() -> Unit)?,
  onQuoteClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val quote = item.quotedItem
  val header = first || quote != null
  val (name, image) = senderName(chatInfo, item)
  Column(
    modifier
      .fillMaxWidth()
      .vibeSwipeReply(onReply != null) { onReply?.invoke() }
      .padding(start = 12.dp, end = 12.dp, top = if (header) 10.dp else 0.dp, bottom = if (last) 4.dp else 0.dp)
  ) {
    if (quote != null) VibeReplyStrip(quote, chatInfo, onQuoteClick)
    Row {
      Box(Modifier.width(40.dp)) {
        if (header) VibeAvatar(image, name, 40.dp, Modifier.clickable(onClick = onAvatarClick))
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        if (header) {
          val color = when {
            item.chatDir.sent -> VibeColors.ownName()
            chatInfo is ChatInfo.Group -> vibeNameColor(name)
            else -> VibeColors.otherName()
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
            Spacer(Modifier.width(6.dp))
            Text(vibeTime(item.meta.itemTs), fontSize = 12.sp, color = VibeColors.onVariant(), maxLines = 1)
            if (item.meta.itemEdited) {
              Spacer(Modifier.width(6.dp))
              Text("(edited)", fontSize = 12.sp, color = VibeColors.onVariant())
            }
          }
        }
        content()
      }
    }
  }
}

/** Discord's red "NEW" line above the first unread message. */
@Composable
fun VibeNewDivider() {
  val red = Color(0xFFF23F43)
  Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.weight(1f).height(1.dp).background(red))
    Text(
      "NEW",
      fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
      modifier = Modifier.clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)).background(red).padding(horizontal = 5.dp, vertical = 1.dp)
    )
  }
}

/** Ends a block (bottom padding): the newer message starts a new one. */
fun vibeEndsBlock(item: ChatItem, newer: ChatItem?): Boolean =
  newer == null || !newer.isVibeRow() || newer.quotedItem != null || vibeStartsBlock(newer, item)

/** Items Vibe doesn't show (the intro at the top says it once). */
fun ChatItem.vibeHidden(): Boolean = when (content) {
  is CIContent.SndDirectE2EEInfo, is CIContent.RcvDirectE2EEInfo,
  is CIContent.SndGroupE2EEInfo, is CIContent.RcvGroupE2EEInfo,
  is CIContent.RcvChatFeature, is CIContent.SndChatFeature,
  is CIContent.RcvGroupFeature, is CIContent.SndGroupFeature -> true
  else -> false
}
