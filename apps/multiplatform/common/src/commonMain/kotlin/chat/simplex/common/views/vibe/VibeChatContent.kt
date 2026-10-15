package chat.simplex.common.views.vibe

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.views.chatlist.openChat
import chat.simplex.common.views.helpers.formatBytes
import chat.simplex.common.views.helpers.withBGApi
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.datetime.Instant

/** What a chat's pane can list (Vibe's "# | media", "# | files" ...). */
enum class VibeContentKind(val title: String, val empty: String) {
  Media("media", vt("No photos or videos yet")),
  Files("files", vt("No files yet")),
  Links("links", vt("No links yet")),
  Voice("voice-messages", vt("No voice messages yet")),
  Starred("starred-messages", "Nothing bookmarked yet. Long-press a message, then Bookmark Message."),
}

object VibeChatContent {
  fun show(chat: Chat, kind: VibeContentKind) {
    VibeSheets.show(maxHeight = 0.85f, background = { vibePage() }) { close -> Sheet(chat, kind, close) }
  }

  /** Opens the chat at [itemId]. */
  fun openAt(chat: Chat, itemId: Long) {
    withBGApi { openChat(secondaryChatsCtx = null, chat.remoteHostId, chat.chatInfo.chatType, chat.chatInfo.apiId, itemId) }
  }
}

@Composable
private fun Sheet(chat: Chat, kind: VibeContentKind, close: () -> Unit) {
  val muted = VibeColors.onVariant()
  var items by remember { mutableStateOf<List<ChatItem>?>(null) }
  LaunchedEffect(kind) {
    if (kind == VibeContentKind.Starred) {
      items = emptyList()
      return@LaunchedEffect
    }
    val tags = when (kind) {
      VibeContentKind.Media -> listOf(MsgContentTag.Image, MsgContentTag.Video)
      VibeContentKind.Files -> listOf(MsgContentTag.File)
      VibeContentKind.Links -> listOf(MsgContentTag.Link)
      VibeContentKind.Voice -> listOf(MsgContentTag.Voice)
      VibeContentKind.Starred -> emptyList()
    }
    val all = mutableListOf<ChatItem>()
    for (t in tags) {
      val r = chatModel.controller.apiGetChat(chat.remoteHostId, chat.chatInfo.chatType, chat.chatInfo.apiId, chat.chatInfo.groupChatScope(), t, ChatPagination.Last(150))
      if (r != null) all += r.first.chatItems
    }
    items = all.filter { it.meta.itemDeleted == null }.sortedByDescending { it.meta.itemTs }
  }
  Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("#", fontSize = 24.sp, color = muted)
      Spacer(Modifier.width(12.dp))
      Text(kind.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface())
    }
    Text(chat.chatInfo.displayName, Modifier.padding(start = 20.dp, bottom = 12.dp), fontSize = 12.5.sp, color = muted)
    val list = items
    when {
      kind == VibeContentKind.Starred -> {
        val stars = VibeStarred.forChat(chat.chatInfo.id)
        if (stars.isEmpty()) Empty(kind.empty)
        else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
          items(stars, key = { it.itemId }) { s ->
            Row(
              Modifier.fillMaxWidth().clickable { close(); VibeChatContent.openAt(chat, s.itemId) }.padding(horizontal = 20.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(VibeIcons.Bookmark, null, Modifier.size(20.dp), tint = VibeColors.blurple)
              Spacer(Modifier.width(14.dp))
              Column(Modifier.weight(1f)) {
                Row {
                  Text(s.sender, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.onSurface())
                  Spacer(Modifier.width(6.dp))
                  Text(vibeTime(Instant.fromEpochMilliseconds(s.ts)), fontSize = 12.sp, color = muted)
                }
                Text(s.text.ifBlank { vt("Attachment") }, fontSize = 14.sp, color = VibeColors.onSurface(), maxLines = 2, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }
      }
      list == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = VibeColors.blurple, strokeWidth = 2.5.dp)
      }
      list.isEmpty() -> Empty(kind.empty)
      kind == VibeContentKind.Media -> LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        items(list, key = { it.id }) { ci ->
          val image = when (val mc = ci.content.msgContent) {
            is MsgContent.MCImage -> mc.image
            is MsgContent.MCVideo -> mc.image
            else -> null
          }
          val bitmap = remember(image) { image?.let { runCatching { base64ToBitmap(it) }.getOrNull() } }
          Box(
            Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(VibeColors.highest())
              .clickable { close(); VibeChatContent.openAt(chat, ci.id) }
          ) {
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            val mc = ci.content.msgContent
            if (mc is MsgContent.MCVideo) {
              Text(
                durationText(mc.duration), Modifier.align(Alignment.BottomEnd).padding(6.dp)
                  .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 11.sp, color = Color.White
              )
            }
          }
        }
      }
      else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        items(list, key = { it.id }) { ci ->
          val (sender, _) = vibeSender(chat.chatInfo, ci)
          val mc = ci.content.msgContent
          val (title, sub) = when (mc) {
            is MsgContent.MCLink -> (mc.preview.title.ifBlank { mc.preview.uri }) to mc.preview.uri
            is MsgContent.MCVoice -> "Voice message · ${durationText(mc.duration)}" to sender
            else -> (ci.file?.fileName ?: ci.text) to (ci.file?.fileSize?.let { formatBytes(it) + " · " } ?: "") + sender
          }
          Row(
            Modifier.fillMaxWidth().clickable { close(); VibeChatContent.openAt(chat, ci.id) }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(VibeColors.highest()), contentAlignment = Alignment.Center) {
              Icon(
                painterResource(
                  when (kind) {
                    VibeContentKind.Links -> MR.images.ic_link
                    VibeContentKind.Voice -> MR.images.ic_mic
                    else -> MR.images.ic_draft
                  }
                ), null, Modifier.size(20.dp), tint = VibeColors.onSurface()
              )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
              Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.onSurface(), maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(sub, fontSize = 13.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(vibeTime(ci.meta.itemTs), fontSize = 12.sp, color = muted)
          }
        }
      }
    }
    Spacer(Modifier.height(16.dp))
  }
}

@Composable
private fun Empty(text: String) {
  Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), contentAlignment = Alignment.Center) {
    Text(text, color = VibeColors.onVariant(), fontSize = 14.sp)
  }
}
