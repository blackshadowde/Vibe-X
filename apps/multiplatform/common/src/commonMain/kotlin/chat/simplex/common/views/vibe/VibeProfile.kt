package chat.simplex.common.views.vibe

import androidx.compose.ui.unit.constrainWidth

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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.AppLock
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.views.call.CallMediaType
import chat.simplex.common.views.chat.startChatCall
import chat.simplex.common.views.helpers.ModalManager
import chat.simplex.common.views.usersettings.SettingsView
import chat.simplex.common.views.usersettings.UserAddressView
import chat.simplex.common.views.usersettings.UserProfileView
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * "About this person" (Discord style, as in Vibe): their photo as a bleed
 * header that fades into the pane, name, Message / call buttons, about
 * and "connected since".
 */
object VibeProfileSheet {
  fun showContact(chat: Chat, onMore: () -> Unit) {
    val info = chat.chatInfo as? ChatInfo.Direct ?: return
    val c = info.contact
    VibeSheets.show(handle = false, corner = 24.dp, background = { VibeColors.surface() }) { close ->
      PersonSheet(
        name = c.displayName,
        image = c.image,
        subtitle = c.profile.contactLink?.let { simplexChatLink(it) },
        fullName = c.fullName.takeIf { it.isNotBlank() && it != c.displayName },
        about = VibeStatus.bioOf(c.profile.description),
        status = c.profile.shortDescr?.ifBlank { null },
        timeZone = VibeStatus.timeZoneOf(c.profile.description),
        since = c.createdAt,
        sinceLabel = vt("Connected since"),
        close = close,
        message = { close(); openVibeChatById(chat) },
        call = if (c.ready) ({ close(); startChatCall(chat.remoteHostId, info, CallMediaType.Audio) }) else null,
        video = if (c.ready) ({ close(); startChatCall(chat.remoteHostId, info, CallMediaType.Video) }) else null,
        more = { close(); onMore() },
      )
    }
  }

  fun showMember(groupInfo: GroupInfo, member: GroupMember, onMore: () -> Unit) {
    VibeSheets.show(handle = false, corner = 24.dp, background = { VibeColors.surface() }) { close ->
      PersonSheet(
        name = member.displayName,
        image = member.image,
        subtitle = member.memberProfile.contactLink?.let { simplexChatLink(it) },
        fullName = member.fullName.takeIf { it.isNotBlank() && it != member.displayName },
        about = VibeStatus.bioOf(member.memberProfile.description),
        status = member.memberProfile.shortDescr?.ifBlank { null },
        timeZone = VibeStatus.timeZoneOf(member.memberProfile.description),
        since = null,
        sinceLabel = "Member of ${groupInfo.displayName} since",
        close = close,
        message = { close(); onMore() },
        call = null,
        video = null,
        more = { close(); onMore() },
      )
    }
  }

  private fun openVibeChatById(chat: Chat) {
    val c = chatModel.chats.value.firstOrNull { it.id == chat.id } ?: chat
    if (chatModel.chatId.value != c.id) openVibeChat(c, kotlinx.coroutines.MainScope())
  }
}

@Composable
private fun BleedHeader(name: String, image: String?, height: Dp, onBack: () -> Unit) {
  val surface = VibeColors.surface()
  val bitmap = remember(image) { image?.let { runCatching { base64ToBitmap(it) }.getOrNull() } }
  Box(Modifier.fillMaxWidth().height(height)) {
    if (bitmap != null) {
      Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
      VibeMattePicture(name)
    }
    Box(
      Modifier.fillMaxSize().background(
        Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to surface)
      )
    )
    Box(
      Modifier.padding(start = 12.dp, top = 14.dp).size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.38f)).clickable(onClick = onBack),
      contentAlignment = Alignment.Center
    ) {
      Icon(VibeIcons.ArrowBack, vt("Back"), Modifier.size(22.dp), tint = Color.White)
    }
  }
}

private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun dateText(ts: Instant): String {
  val d = ts.toLocalDateTime(TimeZone.currentSystemDefault())
  return "${months[d.monthNumber - 1]} ${d.dayOfMonth}, ${d.year}"
}

@Composable
private fun PersonSheet(
  name: String,
  image: String?,
  subtitle: String?,
  fullName: String?,
  about: String?,
  status: String? = null,
  timeZone: String? = null,
  since: Instant?,
  sinceLabel: String,
  close: () -> Unit,
  message: () -> Unit,
  call: (() -> Unit)?,
  video: (() -> Unit)?,
  more: () -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  val muted = VibeColors.onVariant()
  val soft = if (VibeColors.isDark()) Color(0xFF2B2D31) else Color(0xFFEBEDEF)
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    BleedHeader(name, image, 280.dp, close)
    Column(Modifier.padding(horizontal = 20.dp)) {
      if (status != null) VibeStatusBubble(status, Modifier.padding(bottom = 8.dp))
      Text(name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface(), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 28.sp)
      if (fullName != null) Text(fullName, fontSize = 15.sp, color = muted)
      if (subtitle != null) {
        Row(
          Modifier.padding(top = 2.dp).clickable { clipboard.setText(AnnotatedString(subtitle)); showToast(vt("Copied")) },
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(subtitle, fontSize = 14.5.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
          Spacer(Modifier.width(6.dp))
          Icon(VibeIcons.Copy, null, Modifier.size(14.dp), tint = muted)
        }
      }
      Spacer(Modifier.height(16.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
          Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).background(VibeColors.blurple).clickable(onClick = message),
          horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(VibeIcons.Chat, null, Modifier.size(18.dp), tint = Color.White)
          Spacer(Modifier.width(8.dp))
          Text(vt("Message"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        if (call != null) {
          Spacer(Modifier.width(8.dp))
          SquareButton(VibeIcons.Call, soft, call)
        }
        if (video != null) {
          Spacer(Modifier.width(8.dp))
          SquareButton(VibeIcons.Videocam, soft, video)
        }
      }
      val label = Modifier.padding(top = 18.dp, bottom = 4.dp)
      val local = timeZone?.let { VibeStatus.localTime(it) }
      if (local != null) {
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(VibeIcons.Schedule, null, Modifier.size(17.dp), tint = muted)
          Spacer(Modifier.width(8.dp))
          Text("$local for ${name.substringBefore(' ')}", fontSize = 15.sp, color = VibeColors.onSurface())
        }
      }
      if (about != null) {
        Text(vt("About me"), label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = muted)
        Text(about, fontSize = 15.sp, color = VibeColors.onSurface())
      }
      if (since != null) {
        Text(sinceLabel, label, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(VibeIcons.Chat, null, Modifier.size(17.dp), tint = muted)
          Spacer(Modifier.width(8.dp))
          Text(dateText(since), fontSize = 15.sp, color = VibeColors.onSurface())
        }
      }
      Spacer(Modifier.height(18.dp))
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(soft).clickable(onClick = more).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(VibeIcons.Lock, null, Modifier.size(20.dp), tint = VibeColors.onSurface())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(vt("Security and more"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.onSurface())
          Text(vt("Verify, alias, disappearing messages, servers"), fontSize = 12.5.sp, color = muted)
        }
      }
      Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
  }
}

@Composable
private fun SquareButton(icon: ImageVector, bg: Color, onClick: () -> Unit) {
  Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    Icon(icon, null, Modifier.size(20.dp), tint = VibeColors.onSurface())
  }
}

/**
 * Your own profile, like Vibe: bleed header with the photo, camera /
 * pencil / gear buttons, name, your Vibe X address and Edit profile.
 */
object VibeOwnProfile {
  fun show() {
    VibeSheets.show(handle = false, corner = 32.dp, background = { VibeColors.surface() }) { close -> OwnSheet(close) }
  }
}

@Composable
private fun OwnSheet(close: () -> Unit) {
  val user = chatModel.currentUser.value ?: return
  val clipboard = LocalClipboardManager.current
  val muted = VibeColors.onVariant()
  val address = chatModel.userAddress.value?.connLinkContact?.simplexChatUri(short = true)
  fun editProfile() {
    close()
    ModalManager.start.showModalCloseable(cardScreen = true) { c -> UserProfileView(chatModel, c) }
  }
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
    Box(Modifier.fillMaxWidth().height(236.dp)) {
      // the photo bleeds into the banner
      val bitmap = remember(user.image) { user.image?.let { runCatching { base64ToBitmap(it) }.getOrNull() } }
      Box(Modifier.fillMaxWidth().height(128.dp).background(Color(0xFF1F2624))) {
        if (bitmap != null) {
          Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
          Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xCC1F2624))))
        }
      }
      CircleButton(VibeIcons.Close, Modifier.padding(start = 14.dp, top = 16.dp), close)
      Row(Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 16.dp)) {
        CircleButton(VibeIcons.Camera, Modifier, ::editProfile)
        Spacer(Modifier.width(10.dp))
        CircleButton(VibeIcons.Edit, Modifier, ::editProfile)
        Spacer(Modifier.width(10.dp))
        CircleButton(VibeIcons.Settings, Modifier) {
          close()
          ModalManager.start.showModalCloseable(cardScreen = true) { c -> SettingsView(chatModel, AppLock::setPerformLA, c) }
        }
      }
      Box(Modifier.padding(start = 16.dp, top = 80.dp).clip(CircleShape).background(VibeColors.surface()).padding(5.dp)) {
        VibeAvatar(user.image, user.displayName, 120.dp)
        Box(
          Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp).size(26.dp).clip(CircleShape).background(VibeColors.surface()).padding(5.dp)
            .clip(CircleShape).background(if (chatModel.chatRunning.value == true) VibeColors.green else Color(0xFF80848E))
        )
      }
    }
    Column(Modifier.padding(horizontal = 20.dp)) {
      Text(user.displayName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface(), maxLines = 2, overflow = TextOverflow.Ellipsis)
      if (user.fullName.isNotBlank() && user.fullName != user.displayName) Text(user.fullName, fontSize = 15.sp, color = muted)
      if (address != null) {
        Row(
          Modifier.padding(top = 2.dp).clickable { clipboard.setText(AnnotatedString(address)); showToast(vt("Copied your address")) },
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(address, fontSize = 14.5.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
          Spacer(Modifier.width(6.dp))
          Icon(VibeIcons.Copy, null, Modifier.size(14.dp), tint = muted)
        }
      }
      Spacer(Modifier.height(16.dp))
      Row(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(VibeColors.blurple).clickable(onClick = ::editProfile),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(VibeIcons.Edit, null, Modifier.size(18.dp), tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(vt("Edit profile"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
      }
      Spacer(Modifier.height(14.dp))
      Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard())) {
        Row(
          Modifier.fillMaxWidth().clickable { close(); VibeStatus.showEditor() }.padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(VibeIcons.Mood, null, Modifier.size(22.dp), tint = VibeColors.onSurface())
          Spacer(Modifier.width(16.dp))
          Column {
            Text(user.profile.shortDescr?.ifBlank { null } ?: vt("Set a status"), fontSize = 16.sp, color = VibeColors.onSurface(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(vt("What you're up to, and your local time"), fontSize = 13.sp, color = muted)
          }
        }
        Row(
          Modifier.fillMaxWidth().clickable {
            close()
            ModalManager.start.showModalCloseable(cardScreen = true) { c -> UserAddressView(chatModel, shareViaProfile = user.addressShared, close = c) }
          }.padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(VibeIcons.QrCode, null, Modifier.size(22.dp), tint = VibeColors.onSurface())
          Spacer(Modifier.width(16.dp))
          Column {
            Text(if (address != null) vt("Your Vibe X address") else vt("Create your Vibe X address"), fontSize = 16.sp, color = VibeColors.onSurface())
            Text(vt("Share it so people can reach you"), fontSize = 13.sp, color = muted)
          }
        }
      }
      Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
  }
}

@Composable
private fun CircleButton(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
  Box(
    modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
      .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape).clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Icon(icon, null, Modifier.size(21.dp), tint = Color.White)
  }
}

/** A person (or group) without a picture: matte grey with a soft light and their letter. */
@Composable
fun VibeMattePicture(name: String, modifier: Modifier = Modifier) {
  val matte = VibeColors.matte()
  Box(
    modifier.fillMaxSize().background(matte).background(
      Brush.radialGradient(listOf(Color.White.copy(alpha = if (VibeColors.isDark()) 0.07f else 0.22f), Color.Transparent))
    ),
    contentAlignment = Alignment.Center
  ) {
    Text(name.trim().take(1).uppercase().ifEmpty { "@" }, fontSize = 96.sp, fontWeight = FontWeight.Bold, color = VibeColors.onMatte())
  }
}

/** How far the content of an info page starts below the top of the screen (status bar + top bar). */
@Composable
fun vibeInfoTopInset(): Dp {
  val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  return if (appPreferences.oneHandUI.state.value) status + chat.simplex.common.ui.theme.DEFAULT_PADDING + 5.dp
  else status + chat.simplex.common.views.helpers.AppBarHeight * chat.simplex.common.views.helpers.fontSizeSqrtMultiplier
}

/** Stretches the element up by [top] and sideways by [horizontal], out of its parent's padding. */
fun Modifier.vibeBleed(top: Dp, horizontal: Dp): Modifier = this.then(
  Modifier.layout { measurable, constraints ->
    val t = top.roundToPx()
    val hz = horizontal.roundToPx()
    val wide = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.maxWidth + 2 * hz, maxWidth = constraints.maxWidth + 2 * hz) else constraints
    val p = measurable.measure(wide)
    layout(constraints.constrainWidth(p.width - 2 * hz), maxOf(0, p.height - t)) { p.place(-hz, -t) }
  }
)

/**
 * The picture at the top of an info page, as in Vibe: full width, behind the
 * status bar and the top bar, fading into the page. People without a
 * picture get matte grey with their letter.
 */
@Composable
fun VibeInfoBleedHeader(name: String, image: String?, sidePadding: Dp, height: Dp = 260.dp) {
  val top = vibeInfoTopInset()
  val bg = androidx.compose.material.MaterialTheme.colors.background
  val bitmap = remember(image) { image?.let { runCatching { base64ToBitmap(it) }.getOrNull() } }
  val enter = remember { androidx.compose.animation.core.Animatable(1.08f) }
  LaunchedEffect(Unit) { enter.animateTo(1f, VibeSprings.of(0.7f, 1f)) }
  Box(
    Modifier
      .fillMaxWidth()
      .vibeBleed(top, sidePadding)
      .height(height + top)
      .clipToBounds()
  ) {
    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = enter.value; scaleY = enter.value }) {
      if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
      else VibeMattePicture(name)
    }
    // keeps the back arrow readable, and fades the picture into the page
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.22f to Color.Transparent, 0.6f to Color.Transparent, 1f to bg)))
  }
  Spacer(Modifier.height(12.dp))
}
