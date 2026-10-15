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

/** Stable name colour per person in groups, so you can tell people apart. */
private val nameColors = listOf(
  Color(0xFFF47B67), Color(0xFF3BA55C), Color(0xFFFAA61A), Color(0xFFEB459E),
  Color(0xFF45DDC0), Color(0xFF8B9CFF), Color(0xFFE67E22), Color(0xFF1ABC9C),
)

@Composable
fun VibeMessageRow(
  chatInfo: ChatInfo,
  item: ChatItem,
  header: Boolean,
  onAvatarClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val glow by animateColorAsState(
    if (item.isRcvNew) VibeBlurple.copy(alpha = 0.10f) else Color.Transparent,
    tween(900), label = "unreadGlow"
  )
  val user = chatModel.currentUser.value
  Row(
    modifier
      .fillMaxWidth()
      .background(glow)
      .padding(start = 12.dp, end = 12.dp, top = if (header) 12.dp else 1.dp, bottom = 1.dp)
  ) {
    Box(Modifier.width(40.dp)) {
      if (header) {
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onAvatarClick)) {
          when (val d = item.chatDir) {
            is CIDirection.GroupRcv -> MemberProfileImage(40.dp, d.groupMember, backgroundColor = MaterialTheme.colors.background)
            is CIDirection.GroupSnd -> if (chatInfo is ChatInfo.Group) MemberProfileImage(40.dp, chatInfo.groupInfo.membership, backgroundColor = MaterialTheme.colors.background) else ProfileImage(40.dp, user?.image)
            is CIDirection.ChannelRcv -> ChatInfoImage(chatInfo, 40.dp)
            else -> if (item.chatDir.sent) ProfileImage(40.dp, user?.image) else ChatInfoImage(chatInfo, 40.dp)
          }
        }
      } else if (item.isRcvNew) {
        // a small unread light where the avatar would be
        Box(Modifier.padding(top = 9.dp).align(Alignment.TopCenter).size(6.dp).clip(CircleShape).background(VibeBlurple))
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      if (header) {
        val (name, color) = when (val d = item.chatDir) {
          is CIDirection.GroupRcv -> d.groupMember.displayName to nameColors[(d.groupMember.memberId.hashCode() and 0x7fffffff) % nameColors.size]
          is CIDirection.GroupSnd -> (if (chatInfo is ChatInfo.Group) chatInfo.groupInfo.membership.displayName else user?.displayName ?: "You") to MaterialTheme.colors.onBackground
          is CIDirection.ChannelRcv -> chatInfo.displayName to MaterialTheme.colors.onBackground
          else -> (if (item.chatDir.sent) user?.displayName ?: "You" else chatInfo.displayName) to MaterialTheme.colors.onBackground
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 1.dp)) {
          Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
          Spacer(Modifier.width(8.dp))
          Text(vibeTimestamp(item.meta.itemTs), fontSize = 12.sp, color = MaterialTheme.colors.secondary, maxLines = 1, modifier = Modifier.padding(bottom = 2.dp))
        }
      }
      content()
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
