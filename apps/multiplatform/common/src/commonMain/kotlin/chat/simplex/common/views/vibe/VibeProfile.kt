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
import chat.simplex.common.views.helpers.ModalView
import chat.simplex.common.views.helpers.generalGetString
import chat.simplex.common.views.usersettings.doWithAuth
import chat.simplex.common.views.usersettings.UserProfilesView
import chat.simplex.common.views.usersettings.PreferencesView
import chat.simplex.common.views.remote.ConnectDesktopView
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.shadow
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
    VibeHaptics.light()
    VibeSheets.show(handle = false, maxHeight = 0.96f, corner = 32.dp, background = { VibeColors.surface() }) { close -> OwnSheet(close) }
  }

  /** The chat bubble in the floating bar: your chat profiles and chat preferences. */
  fun showChatSettings() {
    VibeSheets.show(background = { vibePage() }) { close ->
      Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(vt("Chat"), Modifier.padding(start = 20.dp, bottom = 12.dp), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard())) {
          PaneRow(painterResource(MR.images.ic_manage_accounts), "Chat profiles", "Switch, add or hide profiles") {
            close()
            ModalManager.start.showCustomModal(keyboardCoversBar = false) { c ->
              val search = rememberSaveable { mutableStateOf("") }
              val profileHidden = rememberSaveable { mutableStateOf(false) }
              val authorized = remember { stateGetOrPut("authorized") { false } }
              ModalView({ c() }, showSearch = true, searchAlwaysVisible = true, onSearchValueChanged = { search.value = it }) {
                UserProfilesView(chatModel, search, profileHidden) { block ->
                  if (authorized.value) block()
                  else doWithAuth(generalGetString(MR.strings.auth_open_chat_profiles), generalGetString(MR.strings.auth_log_in_using_credential)) {
                    authorized.value = true
                    block()
                  }
                }
              }
            }
          }
          PaneRow(painterResource(MR.images.ic_toggle_on), "Chat preferences", "Disappearing messages, reactions, voice, calls") {
            close()
            ModalManager.start.showCustomModal { c -> PreferencesView(chatModel, chatModel.currentUser.value ?: return@showCustomModal, c) }
          }
        }
      }
    }
  }
}

@Composable
private fun PaneRow(icon: androidx.compose.ui.graphics.painter.Painter, title: String, sub: String, onClick: () -> Unit) {
  Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, Modifier.size(22.dp), tint = VibeColors.onSurface())
    Spacer(Modifier.width(16.dp))
    Column {
      Text(title, fontSize = 16.sp, color = VibeColors.onSurface())
      Text(sub, fontSize = 13.sp, color = VibeColors.onVariant())
    }
  }
}

/**
 * Your profile, Discord style: banner with close / camera / pencil / gear,
 * your picture with a status thought bubble, your name and @name, bio,
 * Share my Vibe X ID and Set status, Member since, and a floating bar
 * (Settings, Chat, Desktop) whose panes slide up and swipe away.
 */
@Composable
private fun OwnSheet(close: () -> Unit) {
  val user = chatModel.currentUser.value ?: return
  val muted = VibeColors.onVariant()
  fun editProfile() {
    close()
    ModalManager.start.showModalCloseable(cardScreen = true) { c -> UserProfileView(chatModel, c) }
  }
  fun shareAddress() {
    close()
    ModalManager.start.showModalCloseable(cardScreen = true) { c -> UserAddressView(chatModel, shareViaProfile = user.addressShared, close = c) }
  }
  val status = user.profile.shortDescr?.trim()?.takeIf { it.isNotEmpty() }
  val bio = VibeStatus.bioOf(user.profile.description)
  val big = user.fullName.trim().takeIf { it.isNotEmpty() } ?: user.displayName
  val since = remember { (chatModel.chats.value.firstOrNull { it.chatInfo is ChatInfo.Local }?.chatInfo as? ChatInfo.Local)?.noteFolder?.createdAt }
  Box(Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
      Box(Modifier.fillMaxWidth().height(250.dp)) {
        val bitmap = remember(user.image) { user.image?.let { runCatching { base64ToBitmap(it) }.getOrNull() } }
        Box(Modifier.fillMaxWidth().height(150.dp).background(Color(0xFF1F2624))) {
          if (bitmap != null) {
            Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.5f)
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
        Box(Modifier.padding(start = 16.dp, top = 96.dp).clip(CircleShape).background(VibeColors.surface()).padding(5.dp)) {
          VibeAvatar(user.image, user.displayName, 124.dp)
          Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp).size(26.dp).clip(CircleShape).background(VibeColors.surface()).padding(5.dp)
              .clip(CircleShape).background(if (chatModel.chatRunning.value == true) VibeColors.green else Color(0xFF80848E))
          )
        }
        // the thought bubble: your status, or a nudge to set one
        Row(
          Modifier.padding(start = 140.dp, top = 120.dp, end = 14.dp),
          verticalAlignment = Alignment.Top
        ) {
          Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(VibeColors.container()))
          Spacer(Modifier.width(3.dp))
          Box(Modifier.padding(top = 14.dp).size(16.dp).clip(CircleShape).background(VibeColors.container()))
          Spacer(Modifier.width(2.dp))
          Row(
            Modifier.padding(top = 22.dp).clip(RoundedCornerShape(26.dp)).background(VibeColors.container())
              .clickable { close(); VibeStatus.showEditor() }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (status == null) {
              Box(Modifier.size(22.dp).clip(CircleShape).background(VibeColors.onSurface()), contentAlignment = Alignment.Center) {
                Icon(VibeIcons.Add, null, Modifier.size(16.dp), tint = VibeColors.surface())
              }
              Spacer(Modifier.width(10.dp))
            }
            Text(
              status ?: "What are you up to?", fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
              color = if (status == null) muted else VibeColors.onSurface(),
              fontStyle = if (status == null) androidx.compose.ui.text.font.FontStyle.Italic else null
            )
          }
        }
      }
      Column(Modifier.padding(horizontal = 20.dp)) {
        Text(big, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface(), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 34.sp)
        Text("@${user.displayName}", fontSize = 16.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (bio != null) {
          Text(vt("About me"), Modifier.padding(top = 18.dp, bottom = 4.dp), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = muted)
          Text(bio, fontSize = 15.sp, color = VibeColors.onSurface())
        }
        Spacer(Modifier.height(20.dp))
        // blurple bar: share my ID
        Row(
          Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(VibeColors.blurple).clickable(onClick = ::shareAddress),
          horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(VibeIcons.QrCode, null, Modifier.size(20.dp), tint = Color.White)
          Spacer(Modifier.width(10.dp))
          Text(if (chatModel.userAddress.value != null) "Share my Vibe X ID" else vt("Create your Vibe X address"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Row(
          Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, VibeColors.divider(), RoundedCornerShape(26.dp))
            .clickable { close(); VibeStatus.showEditor() },
          horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(VibeIcons.Mood, null, Modifier.size(20.dp), tint = VibeColors.blurple)
          Spacer(Modifier.width(10.dp))
          Text(if (status == null) vt("Set a status") else "Change status", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VibeColors.blurple)
        }
        if (since != null) {
          Text("Member since", Modifier.padding(top = 22.dp, bottom = 6.dp), fontSize = 15.sp, color = VibeColors.onSurface())
          Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(MR.images.vibe_logo), null, Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(10.dp))
            Text(dateText(since), fontSize = 17.sp, color = VibeColors.onSurface())
          }
        }
        // room for the floating bar
        Spacer(Modifier.navigationBarsPadding().height(120.dp))
      }
    }
    // the floating bar: Settings, Chat (profiles + preferences), Desktop
    Row(
      Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)
        .shadow(12.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)).background(VibeColors.high())
        .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      BarButton(VibeIcons.Settings, vt("Settings")) {
        close()
        ModalManager.start.showModalCloseable(cardScreen = true) { c -> SettingsView(chatModel, AppLock::setPerformLA, c) }
      }
      BarButton(VibeIcons.Chat, vt("Chat")) { close(); VibeOwnProfile.showChatSettings() }
      BarButton(painterResource(MR.images.ic_desktop), "Desktop") {
        close()
        ModalManager.start.showCustomModal { c -> ConnectDesktopView(c) }
      }
    }
  }
}

@Composable
private fun BarButton(icon: Any, label: String, onClick: () -> Unit) {
  Column(
    Modifier.width(84.dp).clip(RoundedCornerShape(20.dp)).clickable { VibeHaptics.light(); onClick() }.padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    when (icon) {
      is ImageVector -> Icon(icon, label, Modifier.size(26.dp), tint = VibeColors.onSurface())
      is androidx.compose.ui.graphics.painter.Painter -> Icon(icon, label, Modifier.size(26.dp), tint = VibeColors.onSurface())
    }
    Spacer(Modifier.height(4.dp))
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.onSurface())
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
