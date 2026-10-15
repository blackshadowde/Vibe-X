package chat.simplex.common.views.vibe

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.chatModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------------------------------------------ top bar --- */

/** Avatar 40 + name (19, bold) + a line under it while connecting. */
@Composable
fun VibeChatTitle(info: ChatInfo, onAvatar: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    VibeChatAvatar(info, 40.dp, Modifier.clickable(onClick = onAvatar))
    Spacer(Modifier.width(12.dp))
    Column {
      Text(info.displayName, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = VibeColors.onSurface())
      val sub = chatModel.chatSubStatus.value
      val line = when {
        info is ChatInfo.Direct && !info.contact.ready -> "connecting…"
        info is ChatInfo.Direct && sub != null && sub != SubscriptionStatus.Active -> "Connecting to server…"
        else -> null
      }
      AnimatedVisibility(line != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.dp, color = VibeColors.onVariant())
          Spacer(Modifier.width(4.dp))
          Text(line ?: "", fontSize = 12.sp, color = VibeColors.onVariant())
        }
      }
      // connected: their local time, in the same spot
      if (line == null && info is ChatInfo.Direct) VibeTheirTime(info.contact.profile.description)
    }
  }
}

/* ------------------------------------------------- top of the chat --- */

/** "Welcome to your chat with X!" at the very top of a chat. */
@Composable
fun VibeChatIntro(info: ChatInfo) {
  val dm = info is ChatInfo.Direct
  val name = info.displayName
  Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 8.dp)) {
    Box(Modifier.size(84.dp).clip(CircleShape).background(VibeColors.highest()), contentAlignment = Alignment.Center) {
      Text(if (dm) "@" else "#", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface())
    }
    Spacer(Modifier.height(20.dp))
    Text(
      if (dm) "Welcome to your chat with $name!" else "Welcome to $name!",
      fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface(), lineHeight = 34.sp
    )
    Spacer(Modifier.height(10.dp))
    Text(
      if (dm) "This is the start of your direct encrypted conversation with $name. End-to-end encrypted with double ratchet and post-quantum keys."
      else "This is the start of the $name conversation. End-to-end encrypted.",
      fontSize = 16.sp, lineHeight = 21.sp, color = VibeColors.onVariant()
    )
    Spacer(Modifier.height(28.dp))
    DividerLabel("LIVE ENCRYPTED STREAM", 13.sp, FontWeight.Bold, 1.sp, 16.dp)
  }
}

@Composable
private fun DividerLabel(text: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, spacing: androidx.compose.ui.unit.TextUnit, gap: androidx.compose.ui.unit.Dp) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.weight(1f).height(1.dp).background(VibeColors.divider()))
    Text(text, Modifier.padding(horizontal = gap), fontSize = size, fontWeight = weight, letterSpacing = spacing, color = VibeColors.onVariant())
    Box(Modifier.weight(1f).height(1.dp).background(VibeColors.divider()))
  }
}

fun vibeDayLabel(ts: Instant): String {
  val tz = TimeZone.currentSystemDefault()
  val d = ts.toLocalDateTime(tz).date
  val today = Clock.System.now().toLocalDateTime(tz).date
  return when (d) {
    today -> "Today"
    today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
    else -> DateFormat.getDateInstance(DateFormat.LONG).format(Date(ts.toEpochMilliseconds()))
  }
}

/** "──── Today ────" between days. */
@Composable
fun VibeDateDivider(ts: Instant) {
  Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
    DividerLabel(vibeDayLabel(ts), 16.sp, FontWeight.SemiBold, 0.sp, 20.dp)
  }
}

/** Discord style: just the time today, "Yesterday at", or the date. */
fun vibeTime(ts: Instant): String {
  val tz = TimeZone.currentSystemDefault()
  val d = ts.toLocalDateTime(tz).date
  val today = Clock.System.now().toLocalDateTime(tz).date
  val t = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts.toEpochMilliseconds()))
  return when (d) {
    today -> t
    today.minus(1, DateTimeUnit.DAY) -> "Yesterday at $t"
    else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ts.toEpochMilliseconds())) + " $t"
  }
}

/* --------------------------------------------------------- the reply --- */

private fun quoteSender(quote: CIQuote, info: ChatInfo): Pair<String, String?> {
  val user = chatModel.currentUser.value
  return when (val d = quote.chatDir) {
    is CIDirection.GroupRcv -> d.groupMember.displayName to d.groupMember.image
    is CIDirection.DirectRcv -> info.displayName to info.image
    is CIDirection.ChannelRcv -> info.displayName to info.image
    null -> (quote.sender(null) ?: "") to null
    else -> (if (info is ChatInfo.Group) info.groupInfo.membership.displayName else user?.displayName ?: "You") to user?.image
  }
}

/**
 * The line above a reply: a curve from the avatar up and right to a tiny
 * picture, "@name" and the start of the message, like Discord.
 */
@Composable
fun VibeReplyStrip(quote: CIQuote, info: ChatInfo, onClick: () -> Unit) {
  val (name, image) = quoteSender(quote, info)
  val own = quote.chatDir?.sent == true
  val line = VibeColors.onVariant().copy(alpha = 0.45f)
  Row(Modifier.fillMaxWidth().height(22.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier.width(40.dp).fillMaxHeight().drawBehind {
        val cx = size.width / 2
        val mid = size.height / 2
        val r = 8.dp.toPx()
        val p = Path().apply {
          moveTo(cx, size.height + 2.dp.toPx())
          lineTo(cx, mid + r)
          quadraticBezierTo(cx, mid, cx + r, mid)
          lineTo(size.width, mid)
        }
        drawPath(p, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
      }
    )
    Spacer(Modifier.width(6.dp))
    VibeAvatar(image, name, 16.dp)
    Spacer(Modifier.width(6.dp))
    Text(
      "@$name", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
      color = if (own) VibeColors.ownName() else VibeColors.otherName(), modifier = Modifier.widthIn(max = 140.dp)
    )
    Spacer(Modifier.width(6.dp))
    val preview = VibeEffect.strip(quote.text).ifBlank { quote.content.text }.replace('\n', ' ')
    Text(
      preview.ifBlank { "Attachment" }, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
      color = VibeColors.onVariant(), modifier = Modifier.weight(1f),
      fontStyle = if (preview.isBlank()) FontStyle.Italic else FontStyle.Normal
    )
  }
}

/**
 * Discord style swipe to reply: the message slides left, a blurple circle
 * with a reply arrow grows in at the right edge and pops (with a short
 * vibration) once it's far enough. Let go to reply.
 */
fun Modifier.vibeSwipeReply(enabled: Boolean, onReply: () -> Unit): Modifier = composed {
  if (!enabled) return@composed this
  val density = LocalDensity.current
  val threshold = with(density) { 72.dp.toPx() }
  val maxDrag = with(density) { 110.dp.toPx() }
  val dx = remember { Animatable(0f) }
  var armed by remember { mutableStateOf(false) }
  val haptic = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val reply by rememberUpdatedState(onReply)
  this
    .pointerInput(Unit) {
      detectHorizontalDragGestures(
        onDragEnd = {
          val fire = armed
          armed = false
          scope.launch {
            dx.animateTo(0f, VibeSprings.of(0.38f, 0.72f))
          }
          if (fire) scope.launch { delay(140); reply() }
        },
        onDragCancel = {
          armed = false
          scope.launch { dx.animateTo(0f, VibeSprings.of(0.38f, 0.72f)) }
        },
        onHorizontalDrag = { change, amount ->
          var next = dx.value + amount
          if (next > 0f) next = 0f
          if (next < -threshold) next = dx.value + amount * 0.45f
          next = next.coerceIn(-maxDrag, 0f)
          if (next != dx.value) change.consume()
          val now = next <= -threshold
          if (now && !armed) VibeHaptics.medium()
          armed = now
          scope.launch { dx.snapTo(next) }
        }
      )
    }
    .drawBehind {
      val v = dx.value
      if (v < -1f) {
        val progress = (-v / threshold).coerceIn(0f, 1f)
        val s = if (armed) 1.15f else 0.55f + 0.45f * progress
        val r = 19.dp.toPx() * s
        val c = Offset(size.width - 16.dp.toPx() - 19.dp.toPx(), size.height / 2)
        if (armed) drawCircle(VibeColors.blurple.copy(alpha = 0.3f * progress), r + 6.dp.toPx(), c)
        drawCircle(VibeColors.blurple.copy(alpha = progress), r, c)
        // arrow
        val a = 7.dp.toPx() * s
        val p = Path().apply {
          moveTo(c.x + a, c.y + a * 0.8f)
          quadraticBezierTo(c.x + a * 0.6f, c.y - a * 0.2f, c.x - a * 0.5f, c.y - a * 0.2f)
        }
        drawPath(p, Color.White.copy(alpha = progress), style = Stroke(2.dp.toPx() * s, cap = StrokeCap.Round))
        val h = Path().apply {
          moveTo(c.x - a * 0.1f, c.y - a * 0.75f)
          lineTo(c.x - a * 0.65f, c.y - a * 0.2f)
          lineTo(c.x - a * 0.1f, c.y + a * 0.35f)
        }
        drawPath(h, Color.White.copy(alpha = progress), style = Stroke(2.dp.toPx() * s, cap = StrokeCap.Round))
      }
    }
    .graphicsLayer { translationX = dx.value }
}

/* --------------------------------------------------------- ticks --- */

/** Sending / sent ✓ / delivered ✓✓ (blurple), right after the text. */
@Composable
fun VibeTicks(ci: ChatItem, modifier: Modifier = Modifier) {
  if (!ci.chatDir.sent) return
  val dim = VibeColors.onVariant()
  when (val s = ci.meta.itemStatus) {
    is CIStatus.SndNew -> Text(if (ci.meta.isLive) "" else "Sending…", modifier, fontSize = 12.sp, color = dim)
    is CIStatus.SndSent -> Icon(VibeIcons.Done, null, modifier.size(18.dp), tint = dim)
    is CIStatus.SndRcvd -> Icon(VibeIcons.DoneAll, null, modifier.size(18.dp), tint = VibeColors.blurple)
    is CIStatus.SndErrorAuth, is CIStatus.CISSndError -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
      Icon(VibeIcons.Error, null, Modifier.size(16.dp), tint = VibeColors.red)
      Spacer(Modifier.width(4.dp))
      Text("Could not be sent", fontSize = 12.sp, color = VibeColors.red)
    }
    is CIStatus.SndWarning -> Icon(VibeIcons.Error, null, modifier.size(16.dp), tint = Color(0xFFFAA61A))
    else -> {}
  }
}

/* ------------------------------------------------------ input bar --- */

/** "Replying to NAME" / "Editing message" with a round ✕, inside the input pill. */
@Composable
fun VibeReplyDisplay(label: String?, bold: String?, onClose: () -> Unit) {
  AnimatedVisibility(label != null || bold != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically(tween(0))) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, end = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        buildAnnotatedString {
          if (label != null) withStyle(SpanStyle(color = VibeColors.onVariant())) { append(label) }
          if (bold != null) withStyle(SpanStyle(color = VibeColors.onSurface(), fontWeight = FontWeight.Bold)) { append(bold) }
        },
        Modifier.weight(1f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
      )
      Box(Modifier.size(38.dp).clip(CircleShape).background(VibeColors.highest()).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Icon(VibeIcons.Close, "Close", Modifier.size(20.dp), tint = VibeColors.onSurface())
      }
    }
  }
}

/** The ⚡ in the input pill: live typing on (blurple) / off (grey). */
@Composable
fun VibeLiveToggle() {
  val pref = chat.simplex.common.platform.appPreferences.vibeLiveTyping
  val on = pref.state.value
  val tint by animateColorAsState(if (on) VibeColors.blurple else VibeColors.onVariant().copy(alpha = 0.6f), label = "live")
  val scale = remember { Animatable(1f) }
  val scope = rememberCoroutineScope()
  Box(
    Modifier.size(40.dp).clip(CircleShape).clickable {
      pref.set(!on)
      VibeHaptics.medium()
      scope.launch { scale.snapTo(1.3f); scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) }
      chat.simplex.common.platform.showToast(if (!on) "Live typing on: they see your words as you type" else "Live typing off: they see typing dots")
    },
    contentAlignment = Alignment.Center
  ) {
    Icon(VibeIcons.Bolt, "Live typing", Modifier.size(22.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }, tint = tint)
  }
}
