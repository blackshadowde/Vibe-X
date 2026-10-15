package chat.simplex.common.views.vibe

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.chatModel
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource

/** What the message menu can do with this message (null = not offered). */
class VibeMsgActions(
  val chatInfo: ChatInfo,
  val item: ChatItem,
  val react: ((MsgReaction) -> Unit)?,
  val reply: (() -> Unit)?,
  val edit: (() -> Unit)?,
  val forward: (() -> Unit)?,
  val copy: (() -> Unit)?,
  val share: (() -> Unit)?,
  val download: (() -> Unit)?,
  val info: (() -> Unit)?,
  val select: (() -> Unit)?,
  val moderate: (() -> Unit)?,
  val deleteForEveryone: (() -> Unit)?,
  val deleteForMe: () -> Unit,
)

private val red = Color(0xFFF87171)

/** Long-press a message: quick reactions, the message, then the actions. Slides up from the bottom. */
object VibeMessageMenu {
  fun show(a: VibeMsgActions) {
    VibeHaptics.medium()
    VibeSheets.show(background = { VibeColors.high() }, corner = 32.dp) { close -> Menu(a, close) }
  }
}

/** Vibe's quick reactions, in the order SimpleX allows them. */
private val quick = listOf(MREmojiChar.Heart, MREmojiChar.ThumbsUp, MREmojiChar.Laugh, MREmojiChar.Launch, MREmojiChar.Sad)

@Composable
private fun Menu(a: VibeMsgActions, close: () -> Unit) {
  val haptic = rememberVibeHaptic()
  val item = a.item
  var more by remember { mutableStateOf(false) }
  val inner = Modifier
    .clip(RoundedCornerShape(24.dp))
    .background(VibeColors.lowest())
    .border(1.dp, VibeColors.divider(), RoundedCornerShape(24.dp))
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
    val react = a.react
    if (react != null) {
      val mine = item.reactions.filter { it.userReacted }.map { it.reaction.text }.toSet()
      Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp).fillMaxWidth().then(inner).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val list = if (more) MsgReaction.supported else quick.map { MsgReaction.Emoji(it) }
        for ((i, r) in list.withIndex()) {
          val used = r.text in mine
          Box(
            Modifier.vibePopIn(i).size(48.dp).clip(CircleShape).background(VibeColors.high())
              .clickable(enabled = !used) { haptic(HapticFeedbackType.LongPress); close(); react(r) },
            contentAlignment = Alignment.Center
          ) {
            VibeAnimatedEmoji(r.text, 32.dp, Modifier.alpha(if (used) 0.33f else 1f))
          }
        }
        if (!more) {
          Box(Modifier.size(48.dp).clip(CircleShape).background(VibeColors.high()).clickable { more = true }, contentAlignment = Alignment.Center) {
            Icon(painterResource(MR.images.ic_add_reaction), null, Modifier.size(22.dp), tint = VibeColors.onSurface())
          }
        }
      }
    }
    // the message itself
    val (name, image) = vibeSender(a.chatInfo, item)
    Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth().then(inner).padding(horizontal = 14.dp, vertical = 10.dp)) {
      Icon(VibeIcons.Reply, null, Modifier.padding(top = 8.dp).size(20.dp), tint = VibeColors.onVariant())
      Spacer(Modifier.width(14.dp))
      VibeAvatar(image, name, 38.dp)
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = VibeColors.onSurface())
        Text(VibeEffect.strip(item.text).ifBlank { "Attachment" }, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = VibeColors.onSurface())
      }
      Spacer(Modifier.width(8.dp))
      Text(vibeTime(item.meta.itemTs), fontSize = 12.sp, color = VibeColors.onVariant())
    }
    @Composable
    fun row(icon: ImageVector?, painter: Painter?, label: String, onClick: () -> Unit, color: Color? = null, subtitle: String? = null) {
      Row(
        Modifier.fillMaxWidth().clickable { haptic(HapticFeedbackType.TextHandleMove); close(); onClick() }.padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        val tint = color ?: VibeColors.onVariant()
        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = tint)
        else if (painter != null) Icon(painter, null, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.width(16.dp))
        Column {
          Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = color ?: VibeColors.onSurface())
          if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = VibeColors.onVariant())
        }
      }
    }
    a.reply?.let { row(VibeIcons.Reply, null, "Reply", it) }
    a.edit?.let { row(null, painterResource(MR.images.ic_edit), "Edit", it, color = VibeColors.blurple) }
    a.forward?.let { row(null, painterResource(MR.images.ic_forward), "Forward", it) }
    a.copy?.let { row(null, painterResource(MR.images.ic_content_copy), "Copy Text", it) }
    a.share?.let { row(null, painterResource(MR.images.ic_share), "Share", it) }
    a.download?.let { row(null, painterResource(MR.images.ic_arrow_downward), "Download", it) }
    val starred = VibeStarred.isStarred(a.chatInfo.id, item.id)
    row(if (starred) VibeIcons.Bookmark else VibeIcons.BookmarkBorder, null, if (starred) "Remove Bookmark" else "Bookmark Message", { VibeStarred.toggle(a.chatInfo, item) })
    a.info?.let { row(null, painterResource(MR.images.ic_info), "Info", it) }
    a.select?.let { row(null, painterResource(MR.images.ic_check_circle), "Select", it) }
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(VibeColors.divider()))
    a.moderate?.let { row(null, painterResource(MR.images.ic_flag), "Remove for everyone (moderator)", it, color = red) }
    a.deleteForEveryone?.let { row(VibeIcons.Block, null, "Delete for Everyone", it, color = red) }
    row(null, painterResource(MR.images.ic_delete), "Delete for me", a.deleteForMe, color = red, subtitle = "Other people still see it.")
    Spacer(Modifier.height(8.dp))
  }
}

/** Name and picture of whoever sent [item]. */
fun vibeSender(chatInfo: ChatInfo, item: ChatItem): Pair<String, String?> {
  val user = chatModel.currentUser.value
  return when (val d = item.chatDir) {
    is CIDirection.GroupRcv -> d.groupMember.displayName to d.groupMember.image
    is CIDirection.GroupSnd -> if (chatInfo is ChatInfo.Group) chatInfo.groupInfo.membership.displayName to chatInfo.groupInfo.membership.image else (user?.displayName ?: "You") to user?.image
    is CIDirection.ChannelRcv -> chatInfo.displayName to chatInfo.image
    else -> if (d.sent) (user?.displayName ?: "You") to user?.image else chatInfo.displayName to chatInfo.image
  }
}
