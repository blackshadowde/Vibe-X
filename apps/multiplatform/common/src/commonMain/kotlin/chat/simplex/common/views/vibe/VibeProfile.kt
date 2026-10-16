package chat.simplex.common.views.vibe

import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.interaction.collectIsPressedAsState
import chat.simplex.common.views.helpers.withBGApi
import chat.simplex.common.views.helpers.GetImageBottomSheet
import chat.simplex.common.views.isValidDisplayName
import chat.simplex.common.views.CreateProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

  /** You, as others see you (tapping your own picture in a chat): picture across the top, name, @name, about, member since. */
  fun showSelf() {
    val user = chatModel.currentUser.value ?: return
    VibeHaptics.light()
    val since = (chatModel.chats.value.firstOrNull { it.chatInfo is ChatInfo.Local }?.chatInfo as? ChatInfo.Local)?.noteFolder?.createdAt
    VibeSheets.show(handle = false, corner = 24.dp, background = { VibeColors.surface() }) { close ->
      PersonSheet(
        name = user.fullName.trim().ifEmpty { user.displayName },
        image = user.image,
        subtitle = "@" + user.displayName,
        fullName = null,
        about = VibeStatus.bioOf(user.profile.description),
        status = user.profile.shortDescr?.ifBlank { null },
        timeZone = null,
        since = since,
        sinceLabel = vt("Member since"),
        close = close,
        message = null,
        call = null,
        video = null,
        more = null,
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
  val bitmap = remember(image) { vibeBitmap(image) }
  Box(Modifier.fillMaxWidth().height(height)) {
    if (bitmap != null) {
      VibeHeaderPhoto(bitmap)
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
  message: (() -> Unit)?,
  call: (() -> Unit)?,
  video: (() -> Unit)?,
  more: (() -> Unit)?,
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
      if (message != null) {
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
      if (more != null) {
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
 * Your own profile, Discord style: banner, your picture with a status
 * thought bubble, your name and @name, bio, Share my Vibe X ID and Edit
 * profile, Member since, and a floating bar (Settings, Chat, Desktop).
 */
object VibeOwnProfile {
  fun show() {
    VibeHaptics.light()
    VibeSheets.show(handle = false, maxHeight = 0.96f, corner = 32.dp, background = { VibeColors.surface() }) { close -> OwnSheet(close) }
  }

  /** The chat bubble in the floating bar: your chat profiles and chat preferences, in one pane. */
  fun showChatPane() {
    VibeHaptics.light()
    VibeSheets.show(maxHeight = 0.94f, background = { vibePage() }) { close -> ChatPane(close) }
  }
}

/** Opens a page under the panes: the panes slide away while the page rises. */
private fun openPage(block: () -> Unit) {
  VibeSheets.closeAll()
  block()
}

@Composable
private fun OwnSheet(close: () -> Unit) {
  val user = chatModel.currentUser.value ?: return
  val muted = VibeColors.onVariant()
  fun shareAddress() {
    openPage { ModalManager.start.showModalCloseable(cardScreen = true) { c -> UserAddressView(chatModel, shareViaProfile = user.addressShared, close = c) } }
  }
  val status = user.profile.shortDescr?.trim()?.takeIf { it.isNotEmpty() }
  val bio = VibeStatus.bioOf(user.profile.description)
  val big = user.fullName.trim().takeIf { it.isNotEmpty() } ?: user.displayName
  val since = remember { (chatModel.chats.value.firstOrNull { it.chatInfo is ChatInfo.Local }?.chatInfo as? ChatInfo.Local)?.noteFolder?.createdAt }
  Box(Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
      Box(Modifier.fillMaxWidth().height(250.dp)) {
        VibeBanner(user.image, Modifier.fillMaxWidth().height(150.dp))
        CircleButton(VibeIcons.Close, Modifier.padding(start = 14.dp, top = 16.dp), close)
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
              .clickable { VibeStatus.showEditor() }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (status == null) {
              Box(Modifier.size(22.dp).clip(CircleShape).background(VibeColors.onSurface()), contentAlignment = Alignment.Center) {
                Icon(VibeIcons.Add, null, Modifier.size(16.dp), tint = VibeColors.surface())
              }
              Spacer(Modifier.width(10.dp))
            }
            Text(
              status ?: vt("What are you up to?"), fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
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
        VibePressable(onClick = ::shareAddress) { m ->
          Row(
            m.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(VibeColors.blurple),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(VibeIcons.QrCode, null, Modifier.size(20.dp), tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(if (chatModel.userAddress.value != null) "Share my Vibe X ID" else vt("Create your Vibe X address"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }
        Spacer(Modifier.height(12.dp))
        // Edit profile: a full-screen pane slides up over this one
        VibePressable(onClick = { VibeEditProfile.show() }) { m ->
          Row(
            m.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, VibeColors.divider(), RoundedCornerShape(26.dp)),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(VibeIcons.Edit, null, Modifier.size(19.dp), tint = VibeColors.blurple)
            Spacer(Modifier.width(10.dp))
            Text(vt("Edit profile"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VibeColors.blurple)
          }
        }
        if (since != null) {
          Text(vt("Member since"), Modifier.padding(top = 22.dp, bottom = 6.dp), fontSize = 15.sp, color = VibeColors.onSurface())
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
        openPage { ModalManager.start.showModalCloseable(cardScreen = true) { c -> SettingsView(chatModel, AppLock::setPerformLA, c) } }
      }
      BarButton(VibeIcons.Chat, vt("Chat")) { VibeOwnProfile.showChatPane() }
      BarButton(painterResource(MR.images.ic_desktop), vt("Desktop")) {
        openPage { ModalManager.start.showCustomModal { c -> ConnectDesktopView(c) } }
      }
    }
  }
}

/** Shrinks a little under your finger and springs back, like Vibe's buttons. */
@Composable
private fun VibePressable(onClick: () -> Unit, content: @Composable (Modifier) -> Unit) {
  val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.96f else 1f, VibeSprings.press(), label = "press")
  content(
    Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
      .clickable(source, indication = null) { VibeHaptics.light(); onClick() }
  )
}

/**
 * The banner at the top of your profile: your picture, softly blurred and
 * dimmed, so the small profile picture SimpleX allows never looks blocky.
 */
@Composable
fun VibeBanner(image: String?, modifier: Modifier) {
  val bitmap = remember(image) { vibeBitmap(image) }
  Box(modifier.background(Color(0xFF1F2624)).clipToBounds()) {
    if (bitmap != null) {
      Image(
        bitmap, null,
        Modifier.fillMaxSize().graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }.blur(26.dp, BlurredEdgeTreatment.Rectangle),
        contentScale = ContentScale.Crop, alpha = 0.7f
      )
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.15f), 1f to Color.Black.copy(alpha = 0.45f))))
    }
  }
}

/* ------------------------------------------------------------------------
 * Edit profile: a pane that covers the whole screen, with your picture,
 * display name, username, about me and local time, and a Save changes
 * button that only lights up when something changed.
 * ------------------------------------------------------------------------ */
object VibeEditProfile {
  fun show() {
    VibeHaptics.light()
    VibeSheets.show(handle = false, maxHeight = 1f, corner = 28.dp, background = { VibeColors.surface() }) { close -> EditPane(close) }
  }
}

@Composable
private fun EditPane(close: () -> Unit) {
  val user = chatModel.currentUser.value ?: return
  val start = remember { user.profile.toProfile() }
  val startBio = remember { VibeStatus.bioOf(start.description) ?: "" }
  val startShare = remember { VibeStatus.timeZoneOf(start.description) != null }
  var fullName by remember { mutableStateOf(start.fullName.takeIf { it.trim() != start.displayName.trim() } ?: "") }
  var username by remember { mutableStateOf(start.displayName) }
  var bio by remember { mutableStateOf(startBio) }
  var share by remember { mutableStateOf(startShare) }
  var image by remember { mutableStateOf(start.image) }
  // your picture in full, kept on this phone (contacts get the small one SimpleX allows)
  var hd by remember { mutableStateOf<ByteArray?>(null) }
  var picker by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  val chosen = rememberSaveable { mutableStateOf<java.net.URI?>(null) }
  val scope = rememberCoroutineScope()
  val muted = VibeColors.onVariant()

  val nameOk = username.trim().isNotEmpty() && (username == start.displayName || isValidDisplayName(username.trim()))
  val startFull = start.fullName.takeIf { it.trim() != start.displayName.trim() } ?: ""
  val changed = username.trim() != start.displayName || fullName.trim() != startFull.trim() || bio.trim() != startBio.trim() ||
      share != startShare || image != start.image
  val canSave = changed && nameOk && !busy

  fun save() {
    if (!canSave) return
    busy = true
    VibeHaptics.medium()
    scope.launch {
      val tz = if (share) java.util.TimeZone.getDefault().id else null
      val description = listOfNotNull(bio.trim().ifBlank { null }, tz?.let { "🕓 Time zone: $it" }).joinToString("\n").ifBlank { null }
      val name = username.trim()
      val p = start.copy(displayName = name, fullName = fullName.trim().ifBlank { name }, description = description, image = image)
      val updated = withContext(Dispatchers.IO) { runCatching { chatModel.controller.apiUpdateProfile(user.remoteHostId, p) }.getOrNull() }
      busy = false
      if (updated != null) {
        chatModel.updateCurrentUser(user.remoteHostId, updated.first)
        val saved = updated.first.image
        withContext(Dispatchers.IO) {
          if (saved == null) VibeMyHd.clear()
          else if (saved != start.image) hd?.let { VibeMyHd.save(saved, it) }
        }
        appPreferences.vibeShareTimeOff.set(!share)
        VibeHaptics.success()
        showToast(vt("Profile saved"))
        close()
      } else {
        VibeHaptics.heavy()
      }
    }
  }

  Box(Modifier.fillMaxWidth().fillMaxHeight()) {
    Column(Modifier.fillMaxSize()) {
      // top bar: close, title
      Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(vibeCard()).clickable(onClick = close), contentAlignment = Alignment.Center) {
          Icon(VibeIcons.Close, vt("Close"), Modifier.size(20.dp), tint = VibeColors.onSurface())
        }
        Text(vt("Edit profile"), Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.size(42.dp))
      }
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()) {
        // banner and picture with a camera badge
        Box(Modifier.fillMaxWidth().height(206.dp)) {
          VibeBanner(image, Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(130.dp).clip(RoundedCornerShape(22.dp)))
          Box(
            Modifier.align(Alignment.BottomCenter).clip(CircleShape).background(VibeColors.surface()).padding(5.dp)
              .clip(CircleShape).clickable { VibeHaptics.light(); picker = !picker }
          ) {
            VibeAvatar(image, username.ifBlank { "?" }, 112.dp)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
              Icon(VibeIcons.Camera, vt("Change photo"), Modifier.size(30.dp), tint = Color.White)
            }
          }
        }
        // camera / gallery: always composed (so the result comes back), shown when you tap the picture
        Column(
          Modifier.padding(horizontal = 16.dp).fillMaxWidth().animateContentSize(VibeSprings.of(0.4f, 0.9f))
            .then(if (picker) Modifier.padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).background(vibeCard()) else Modifier.height(0.dp))
            .clipToBounds()
        ) {
          GetImageBottomSheet(
            chosen,
            onImageChange = { bitmap ->
              scope.launch {
                val square = withContext(Dispatchers.Default) { runCatching { cropToSquare(bitmap) }.getOrNull() } ?: return@launch
                val encoded = withContext(Dispatchers.Default) { runCatching { vibeEncodeAvatar(square) }.getOrNull() }
                if (encoded != null) {
                  image = encoded
                  hd = withContext(Dispatchers.Default) { VibeMyHd.encode(square) }
                }
              }
            },
            hideBottomSheet = { picker = false }
          )
          if (image != null) {
            Text(
              vt("Remove photo"),
              Modifier.fillMaxWidth().clickable { image = null; picker = false }.padding(bottom = 16.dp, top = 2.dp),
              color = VibeColors.red, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        }
        Spacer(Modifier.height(18.dp))
        EditField(vt("Display name"), fullName, { fullName = it }, placeholder = username.ifBlank { vt("Your name") }, hint = vt("The big name people see on your profile"))
        EditField(
          vt("Username"), username, { username = it.replace("\n", "") }, prefix = "@",
          hint = if (nameOk) vt("Your name in chats. No spaces at the start or end.") else vt("Use letters and numbers, without spaces at the start or end"),
          error = !nameOk
        )
        EditField(vt("About me"), bio, { if (it.length <= 300) bio = it }, placeholder = vt("Say a little about yourself"), singleLine = false, hint = "${bio.length}/300")
        Row(
          Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(vibeCard())
            .clickable { VibeHaptics.selection(); share = !share }.padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(VibeIcons.Schedule, null, Modifier.size(22.dp), tint = VibeColors.onSurface())
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f)) {
            Text(vt("Show my local time"), fontSize = 16.sp, color = VibeColors.onSurface())
            Text(java.util.TimeZone.getDefault().id, fontSize = 13.sp, color = muted)
          }
          androidx.compose.material.Switch(
            share, { VibeHaptics.selection(); share = it },
            colors = androidx.compose.material.SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VibeColors.blurple, checkedTrackAlpha = 1f)
          )
        }
        Text(
          vt("Your profile is stored on your phone and shared only with your contacts."),
          Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontSize = 13.sp, color = muted
        )
        Spacer(Modifier.height(110.dp))
      }
    }
    // Save changes: grey until something changes, then blurple
    val lit by androidx.compose.animation.animateColorAsState(if (canSave || busy) VibeColors.blurple else vibeCard(), androidx.compose.animation.core.tween(220), label = "save")
    val pop by androidx.compose.animation.core.animateFloatAsState(if (changed) 1f else 0.97f, VibeSprings.pop(), label = "savePop")
    Box(
      Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        .graphicsLayer { scaleX = pop; scaleY = pop }
        .height(54.dp).shadow(if (canSave) 10.dp else 0.dp, RoundedCornerShape(27.dp)).clip(RoundedCornerShape(27.dp)).background(lit)
        .clickable(enabled = canSave) { save() },
      contentAlignment = Alignment.Center
    ) {
      if (busy) androidx.compose.material.CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
      else Text(
        if (changed) vt("Save changes") else vt("No changes yet"), fontSize = 16.sp, fontWeight = FontWeight.Bold,
        color = if (canSave) Color.White else muted
      )
    }
  }
}

@Composable
private fun EditField(
  label: String,
  value: String,
  onChange: (String) -> Unit,
  placeholder: String = "",
  prefix: String? = null,
  hint: String? = null,
  error: Boolean = false,
  singleLine: Boolean = true,
) {
  var focused by remember { mutableStateOf(false) }
  val border by androidx.compose.animation.animateColorAsState(
    when { error -> VibeColors.red; focused -> VibeColors.blurple; else -> Color.Transparent }, androidx.compose.animation.core.tween(180), label = "field"
  )
  Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
    Text(label.uppercase(), Modifier.padding(start = 4.dp, bottom = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = VibeColors.onVariant())
    Row(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(vibeCard()).border(1.5.dp, border, RoundedCornerShape(16.dp))
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
    ) {
      if (prefix != null) Text(prefix, fontSize = 16.sp, color = VibeColors.onVariant())
      Box(Modifier.weight(1f)) {
        if (value.isEmpty()) Text(placeholder, fontSize = 16.sp, color = VibeColors.onVariant().copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        androidx.compose.foundation.text.BasicTextField(
          value, onChange, singleLine = singleLine, maxLines = if (singleLine) 1 else 6,
          textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = VibeColors.onSurface()),
          cursorBrush = androidx.compose.ui.graphics.SolidColor(VibeColors.blurple),
          modifier = Modifier.fillMaxWidth().then(if (singleLine) Modifier else Modifier.heightIn(min = 72.dp))
            .onFocusChanged { focused = it.isFocused }
        )
      }
    }
    if (hint != null) Text(hint, Modifier.padding(start = 4.dp, top = 5.dp), fontSize = 12.5.sp, color = if (error) VibeColors.red else VibeColors.onVariant())
  }
}

/* ------------------------------------------------------------------------
 * The Chat pane: your chat profiles and your chat preferences together.
 * ------------------------------------------------------------------------ */
@Composable
private fun ChatPane(close: () -> Unit) {
  val user = chatModel.currentUser.value ?: return
  val scope = rememberCoroutineScope()
  var prefs by remember(user.userId) { mutableStateOf(user.fullPreferences) }
  fun apply(p: FullChatPreferences) {
    val before = prefs
    prefs = p
    VibeHaptics.selection()
    scope.launch {
      val newProfile = user.profile.toProfile().copy(preferences = p.toPreferences())
      val updated = withContext(Dispatchers.IO) { runCatching { chatModel.controller.apiUpdateProfile(user.remoteHostId, newProfile) }.getOrNull() }
      if (updated != null) {
        val (updatedProfile, updatedContacts) = updated
        chatModel.updateCurrentUser(user.remoteHostId, updatedProfile, p)
        updatedContacts.forEach { chatModel.chatsContext.updateContact(user.remoteHostId, it) }
      } else prefs = before
    }
  }
  val users = chatModel.users.map { it.user }.filter { !it.hidden }
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 20.dp)) {
    Text(vt("Chat"), Modifier.padding(start = 20.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())

    PaneLabel(vt("Chat profiles"))
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard())) {
      for (u in users) {
        Row(
          Modifier.fillMaxWidth().clickable {
            if (!u.activeUser) {
              VibeHaptics.medium()
              VibeSheets.closeAll()
              withBGApi {
                chatModel.controller.showProgressIfNeeded {
                  ModalManager.closeAllModalsEverywhere()
                  chatModel.controller.changeActiveUser(u.remoteHostId, u.userId, null)
                }
              }
            }
          }.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          VibeAvatar(u.image, u.displayName, 40.dp)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(u.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.onSurface(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (u.activeUser) Text(vt("Using now"), fontSize = 13.sp, color = VibeColors.green)
          }
          if (u.activeUser) Icon(VibeIcons.CheckCircle, null, Modifier.size(22.dp), tint = VibeColors.blurple)
        }
      }
      PaneAction(VibeIcons.Add, vt("Add a profile")) {
        doWithAuth(generalGetString(MR.strings.auth_open_chat_profiles), generalGetString(MR.strings.auth_log_in_using_credential)) {
          openPage { ModalManager.start.showModalCloseable { c -> CreateProfile(chatModel, c) } }
        }
      }
      PaneAction(painterResource(MR.images.ic_manage_accounts), vt("Manage profiles"), vt("Hide, mute or delete profiles")) {
        openPage {
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
      }
    }

    Spacer(Modifier.height(18.dp))
    PaneLabel(vt("Chat preferences"))
    Text(
      vt("What your contacts may do in chats with you. Changes are sent to your contacts."),
      Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), fontSize = 13.sp, color = VibeColors.onVariant()
    )
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard())) {
      // disappearing messages: on / off, then how long
      val timedOn = prefs.timedMessages.allow != FeatureAllowed.NO
      PrefHeader(ChatFeature.TimedMessages) {
        androidx.compose.material.Switch(
          timedOn, { on -> apply(prefs.copy(timedMessages = TimedMessagesPreference(allow = if (on) FeatureAllowed.YES else FeatureAllowed.NO, ttl = prefs.timedMessages.ttl))) },
          colors = androidx.compose.material.SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VibeColors.blurple, checkedTrackAlpha = 1f)
        )
      }
      if (timedOn) {
        Row(Modifier.padding(start = 52.dp, end = 12.dp, bottom = 12.dp).horizontalScroll(rememberScrollState())) {
          for (ttl in TimedMessagesPreference.profileLevelTTLValues) {
            PrefChip(timeText(ttl), prefs.timedMessages.ttl == ttl) { apply(prefs.copy(timedMessages = prefs.timedMessages.copy(ttl = ttl))) }
          }
        }
      }
      PrefRow(ChatFeature.FullDelete, prefs.fullDelete.allow) { apply(prefs.copy(fullDelete = SimpleChatPreference(it))) }
      PrefRow(ChatFeature.Reactions, prefs.reactions.allow) { apply(prefs.copy(reactions = SimpleChatPreference(it))) }
      PrefRow(ChatFeature.Voice, prefs.voice.allow) { apply(prefs.copy(voice = SimpleChatPreference(it))) }
      PrefRow(ChatFeature.Calls, prefs.calls.allow) { apply(prefs.copy(calls = SimpleChatPreference(it))) }
    }
  }
}

@Composable
private fun PaneLabel(text: String) {
  Text(text.uppercase(), Modifier.padding(start = 20.dp, bottom = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = VibeColors.onVariant())
}

@Composable
private fun PaneAction(icon: Any, title: String, sub: String? = null, onClick: () -> Unit) {
  Row(Modifier.fillMaxWidth().clickable { VibeHaptics.light(); onClick() }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
      when (icon) {
        is ImageVector -> Icon(icon, null, Modifier.size(22.dp), tint = VibeColors.onSurface())
        is androidx.compose.ui.graphics.painter.Painter -> Icon(icon, null, Modifier.size(22.dp), tint = VibeColors.onSurface())
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(title, fontSize = 16.sp, color = VibeColors.onSurface())
      if (sub != null) Text(sub, fontSize = 13.sp, color = VibeColors.onVariant())
    }
  }
}

@Composable
private fun PrefHeader(feature: ChatFeature, trailing: @Composable () -> Unit = {}) {
  Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(feature.icon, null, Modifier.size(22.dp), tint = VibeColors.onSurface())
    Spacer(Modifier.width(16.dp))
    Text(feature.text, Modifier.weight(1f), fontSize = 16.sp, color = VibeColors.onSurface())
    trailing()
  }
}

@Composable
private fun PrefRow(feature: ChatFeature, allow: FeatureAllowed, onSelect: (FeatureAllowed) -> Unit) {
  PrefHeader(feature)
  Row(Modifier.padding(start = 52.dp, end = 12.dp)) {
    for (a in listOf(FeatureAllowed.ALWAYS, FeatureAllowed.YES, FeatureAllowed.NO)) PrefChip(a.text, allow == a) { if (allow != a) onSelect(a) }
  }
  Text(feature.allowDescription(allow), Modifier.padding(start = 52.dp, end = 14.dp, top = 6.dp, bottom = 12.dp), fontSize = 12.5.sp, color = VibeColors.onVariant())
}

@Composable
private fun PrefChip(label: String, on: Boolean, onClick: () -> Unit) {
  val bg by androidx.compose.animation.animateColorAsState(if (on) VibeColors.blurple else Color.Transparent, androidx.compose.animation.core.tween(180), label = "chip")
  Text(
    label,
    Modifier.padding(end = 8.dp).clip(RoundedCornerShape(16.dp)).background(bg)
      .then(if (on) Modifier else Modifier.border(1.dp, VibeColors.divider(), RoundedCornerShape(16.dp)))
      .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
    color = if (on) Color.White else VibeColors.onSurface(), fontSize = 14.sp
  )
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
  val bitmap = remember(image) { vibeBitmap(image) }
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
      if (bitmap != null) VibeHeaderPhoto(bitmap)
      else VibeMattePicture(name)
    }
    // keeps the back arrow readable, and fades the picture into the page
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.22f to Color.Transparent, 0.6f to Color.Transparent, 1f to bg)))
  }
  Spacer(Modifier.height(12.dp))
}

/**
 * A profile picture stretched across the top of a page. SimpleX keeps
 * profile pictures tiny (about 12 KB), so stretched they show JPEG blocks;
 * a touch of blur scaled to how far it's stretched smooths those away
 * without making the picture look out of focus.
 */
@Composable
fun VibeHeaderPhoto(bitmap: androidx.compose.ui.graphics.ImageBitmap) {
  androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val stretch = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() } / bitmap.width.coerceAtLeast(1)
    // about one source pixel of blur once it's stretched more than 2x
    val radius = with(androidx.compose.ui.platform.LocalDensity.current) { (stretch * 0.9f).coerceIn(0f, 14f).toDp() }
    Image(
      bitmap, null,
      Modifier.fillMaxSize().then(if (stretch > 2f) Modifier.blur(radius, BlurredEdgeTreatment.Rectangle) else Modifier),
      contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High
    )
  }
}
