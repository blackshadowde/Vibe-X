package chat.simplex.common.views.vibe

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.ColumnWithScrollBar
import chat.simplex.common.platform.chatModel
import chat.simplex.common.views.helpers.ModalManager
import chat.simplex.common.views.helpers.openUriCatching
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.launch

private const val GITHUB = "https://github.com/blackshadowde"
private const val SOURCE = "https://github.com/${VibeUpdater.REPO}"

private fun mb(bytes: Long) = if (bytes > 0) " · ${(bytes / 1_000_000.0).let { String.format("%.0f", it) }} MB" else ""

private fun ago(t: Long): String {
  val m = (System.currentTimeMillis() - t) / 60_000
  return when {
    m < 1 -> "just now"
    m < 60 -> "$m min ago"
    m < 24 * 60 -> "${m / 60} h ago"
    else -> "${m / (24 * 60)} d ago"
  }
}

/** Small red dot on [content] while an update is waiting (the settings gear, the Updates row). */
@Composable
fun VibeUpdateDot(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  LaunchedEffect(Unit) { VibeUpdater.load() }
  Box(modifier) {
    content()
    if (VibeUpdater.pending.value != null) {
      Box(
        Modifier.align(Alignment.TopEnd).size(12.dp).clip(CircleShape).background(VibeColors.surface()).padding(2.dp)
          .clip(CircleShape).background(VibeColors.red)
      )
    }
  }
}

/* ---------------------------------------------------------------- sheet --- */

/** "Update available": what's new, then Update now → download → Android's installer. */
@Composable
fun VibeUpdateSheet(r: VibeRelease, close: () -> Unit) {
  val uri = LocalUriHandler.current
  val stage = VibeUpdater.stage.value
  val muted = VibeColors.onVariant()
  Column(
    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp).navigationBarsPadding()
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(VibeColors.blurple.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(VibeIcons.RocketLaunch, null, tint = VibeColors.blurple)
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(vt("Update available"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
        Text("Vibe X ${r.label}${mb(r.size)}", color = muted)
      }
    }
    if (r.notes.isNotEmpty()) {
      Spacer(Modifier.height(18.dp))
      Text(vt("WHAT'S NEW"), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = muted)
      Spacer(Modifier.height(8.dp))
      for (line in r.notes) Text(line, Modifier.padding(vertical = 4.dp), fontSize = 15.sp, lineHeight = 20.sp, color = VibeColors.onSurface())
    }
    Spacer(Modifier.height(20.dp))
    Crossfade(stage, label = "updateStage") { s ->
      Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (s) {
          VibeUpdater.Stage.Info -> {
            BlurpleButton(vt("Update now"), VibeIcons.SystemUpdateAlt) { VibeHaptics.medium(); VibeUpdater.startDownload(r) }
            Spacer(Modifier.height(4.dp))
            TextAction(vt("Later")) { VibeUpdater.later(r); close() }
          }
          VibeUpdater.Stage.Downloading -> {
            val p = VibeUpdater.progress.value
            if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)), color = VibeColors.blurple, backgroundColor = VibeColors.blurple.copy(alpha = 0.2f))
            else LinearProgressIndicator(p, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)), color = VibeColors.blurple, backgroundColor = VibeColors.blurple.copy(alpha = 0.2f))
            Spacer(Modifier.height(10.dp))
            Text(if (p == null) vt("Starting download…") else "Downloading… ${(p * 100).toInt()}%", color = VibeColors.onSurface())
            Spacer(Modifier.height(4.dp))
            TextAction(vt("Cancel")) { VibeUpdater.cancel(); close() }
          }
          VibeUpdater.Stage.NeedsPermission -> {
            Text(
              vt("Android needs your OK once: allow Vibe X to install updates, then come back and tap Install."),
              textAlign = TextAlign.Center, color = muted
            )
            Spacer(Modifier.height(12.dp))
            OutlineButton("1. Allow in settings") { vibeAllowInstallApk() }
            Spacer(Modifier.height(8.dp))
            BlurpleButton("2. Install", null) { VibeUpdater.install() }
          }
          VibeUpdater.Stage.Installing -> {
            Text("Tap \"Update\" in the Android installer. Your chats stay.", textAlign = TextAlign.Center, color = muted)
            Spacer(Modifier.height(8.dp))
            TextAction(vt("Installer didn't open? Try again")) { VibeUpdater.install() }
          }
          VibeUpdater.Stage.Failed -> {
            Text(VibeUpdater.error.value ?: vt("Something went wrong."), textAlign = TextAlign.Center, color = VibeColors.red)
            Spacer(Modifier.height(10.dp))
            BlurpleButton(vt("Try again"), null) { VibeUpdater.startDownload(r) }
            TextAction(vt("Download in browser instead")) { uri.openUriCatching(r.apkUrl); close() }
          }
        }
      }
    }
  }
}

@Composable
private fun BlurpleButton(text: String, icon: ImageVector?, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(VibeColors.blurple).clickable(onClick = onClick),
    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      Icon(icon, null, Modifier.size(20.dp), tint = Color.White)
      Spacer(Modifier.width(8.dp))
    }
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun OutlineButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).border(1.dp, VibeColors.divider(), RoundedCornerShape(24.dp))
      .clickable(enabled = enabled, onClick = onClick),
    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      Icon(icon, null, Modifier.size(20.dp), tint = VibeColors.onSurface())
      Spacer(Modifier.width(8.dp))
    }
    Text(text, color = VibeColors.onSurface().copy(alpha = if (enabled) 1f else 0.5f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
  Text(
    text, Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    color = VibeColors.blurple, fontWeight = FontWeight.SemiBold
  )
}

/* -------------------------------------------------------------- Updates --- */

/** Settings → Updates: this version, update status, what's new and a refresh button. */
@Composable
fun VibeUpdatesView() {
  val uri = LocalUriHandler.current
  val scope = rememberCoroutineScope()
  var error by remember { mutableStateOf<String?>(null) }
  fun refresh() {
    if (VibeUpdater.checking.value) return
    error = null
    scope.launch {
      try {
        VibeUpdater.refresh()
      } catch (e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        error = vt("Could not reach GitHub. Are you online?")
      }
    }
  }
  LaunchedEffect(Unit) {
    VibeUpdater.load()
    // a fresh check when the page opens, unless we just checked
    val last = VibeUpdater.lastChecked.value
    if (last == null || System.currentTimeMillis() - last > 2 * 60_000) refresh()
  }
  val r = VibeUpdater.pending.value
  val busy = VibeUpdater.checking.value
  val last = VibeUpdater.lastChecked.value
  val muted = VibeColors.onVariant()
  val (icon, color, title, sub) = when {
    r != null -> Quad(VibeIcons.SystemUpdate, VibeColors.red, vt("Update available"), "Vibe X ${r.label}${mb(r.size)}")
    busy -> Quad(VibeIcons.Sync, VibeColors.blurple, vt("Checking for updates…"), vt("Asking GitHub for the latest release"))
    error != null -> Quad(VibeIcons.CloudOff, VibeColors.red, vt("Could not check"), error!!)
    last != null -> Quad(VibeIcons.CheckCircle, VibeColors.green, vt("You're up to date"), vt("This is the newest Vibe X"))
    else -> Quad(VibeIcons.HelpOutline, muted, vt("Not checked yet"), vt("Tap Check for updates"))
  }
  ColumnWithScrollBar {
    Text(vt("Updates"), Modifier.padding(start = 20.dp, top = 8.dp, bottom = 16.dp), fontSize = 28.sp, color = VibeColors.onSurface())
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(vibeCard()).padding(18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
          if (busy && r == null) CircularProgressIndicator(Modifier.size(24.dp), color = color, strokeWidth = 2.5.dp)
          else Icon(icon, null, Modifier.size(28.dp), tint = color)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
          Spacer(Modifier.height(2.dp))
          Text(sub, color = muted)
        }
      }
      Spacer(Modifier.height(16.dp))
      Box(Modifier.fillMaxWidth().height(1.dp).background(VibeColors.divider()))
      Spacer(Modifier.height(12.dp))
      InfoRow(vt("Installed version"), VibeUpdater.versionLabel)
      if (r != null) InfoRow(vt("New version"), r.label)
      InfoRow(vt("Last checked"), if (last == null) vt("Never") else ago(last))
      Spacer(Modifier.height(14.dp))
      if (!vibeCanSelfUpdate) {
        OutlineButton(vt("Open releases on GitHub"), VibeIcons.OpenInNew) { uri.openUriCatching(VibeUpdater.RELEASES_URL) }
      } else if (r != null) {
        BlurpleButton(vt("Update now"), VibeIcons.SystemUpdateAlt) { VibeUpdater.showSheet(r) }
      } else {
        OutlineButton(vt("Check for updates"), VibeIcons.Refresh, enabled = !busy) { VibeHaptics.light(); refresh() }
      }
    }
    val shown = r ?: VibeUpdater.latest.value?.takeIf { it.build == VibeUpdater.build }
    val notes = shown?.notes.orEmpty()
    if (notes.isNotEmpty()) {
      Text(
        if (r != null) "WHAT'S NEW IN ${r.label.uppercase()}" else "IN YOUR VERSION",
        Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = muted
      )
      Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(vibeCard()).padding(horizontal = 16.dp, vertical = 10.dp)) {
        for (line in notes) Text(line, Modifier.padding(vertical = 6.dp), fontSize = 15.sp, lineHeight = 20.sp, color = VibeColors.onSurface())
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(
      Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp))
        .clickable { uri.openUriCatching(r?.pageUrl ?: VibeUpdater.RELEASES_URL) }.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(VibeIcons.OpenInNew, null, Modifier.size(18.dp), tint = VibeColors.blurple)
      Spacer(Modifier.width(8.dp))
      Text(vt("All releases on GitHub"), color = VibeColors.blurple, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(32.dp))
  }
}

private data class Quad(val icon: ImageVector, val color: Color, val title: String, val sub: String)

@Composable
private fun InfoRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
    Text(label, Modifier.weight(1f), color = VibeColors.onVariant())
    Text(value, fontWeight = FontWeight.Bold, color = VibeColors.onSurface())
  }
}

/* ---------------------------------------------------------------- About --- */

/** About Vibe X, as in Vibe: logo, name, tagline, version, update status, buttons. */
@Composable
fun VibeAboutView() {
  val uri = LocalUriHandler.current
  val scope = rememberCoroutineScope()
  var status by remember { mutableStateOf("") }
  var checking by remember { mutableStateOf(false) }
  val muted = VibeColors.onVariant()
  fun check() {
    if (checking) return
    checking = true
    status = "Checking…"
    scope.launch {
      try {
        val r = VibeUpdater.refresh()
        if (r == null) {
          status = vt("You're on the latest version")
          VibeHaptics.success()
        } else {
          status = "Vibe X ${r.label} is available"
          VibeUpdater.showSheet(r)
        }
      } catch (e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        status = vt("No connection to GitHub")
      } finally {
        checking = false
      }
    }
  }
  ColumnWithScrollBar(horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.height(8.dp))
    Image(painterResource(MR.images.vibe_logo), null, Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)))
    Spacer(Modifier.height(14.dp))
    Text("Vibe X", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface())
    Spacer(Modifier.height(2.dp))
    Text(vt("A private messenger built on SimpleX"), fontSize = 15.sp, color = muted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text(
      "Version ${VibeUpdater.versionLabel}  ·  SimpleX",
      Modifier.clip(RoundedCornerShape(14.dp)).background(VibeColors.high()).padding(horizontal = 12.dp, vertical = 5.dp),
      fontSize = 13.sp, color = muted
    )
    Spacer(Modifier.height(14.dp))
    Text(status, Modifier.height(20.dp), fontSize = 14.sp, color = muted)
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
      if (vibeCanSelfUpdate) {
        Spacer(Modifier.height(10.dp))
        BlurpleButton(vt("Check for updates"), null) { VibeHaptics.light(); check() }
      }
      Spacer(Modifier.height(10.dp))
      OutlineButton(vt("Updates")) { ModalManager.start.showModal(settings = true) { VibeUpdatesView() } }
      Spacer(Modifier.height(10.dp))
      OutlineButton(vt("Source code on GitHub")) { uri.openUriCatching(SOURCE) }
      Spacer(Modifier.height(10.dp))
      OutlineButton(vt("Credits")) { VibeCredits.show() }
      Spacer(Modifier.height(10.dp))
      OutlineButton("❤  Support Vibe X") { VibeSupport.open() }
      Spacer(Modifier.height(10.dp))
      OutlineButton(vt("How Vibe X keeps you safe")) {
        ModalManager.fullscreen.showCustomModal { close -> VibeWelcome(chatModel, onDone = close) }
      }
      Spacer(Modifier.height(16.dp))
      Text(
        "Free software based on SimpleX Chat (GNU AGPL v3).\nYour messages are end-to-end encrypted and the keys stay on your devices.",
        Modifier.fillMaxWidth(), fontSize = 12.sp, lineHeight = 17.sp, color = muted, textAlign = TextAlign.Center
      )
      Spacer(Modifier.height(24.dp))
    }
  }
}

/* -------------------------------------------------------------- Credits --- */

/** Credits: who made Vibe X and what it is built on. */
object VibeCredits {
  private class Card(val name: String, val what: String, val note: String?, val link: String?)

  private val madeBy = listOf(
    Card("blackshadowde", "Creator of Vibe, Vibgram and Vibe X", "Design, features and builds", GITHUB),
  )
  private val team = listOf(
    Card("A.Gowthami", "Support team", null, null),
  )
  private val builtOn = listOf(
    Card("SimpleX Chat", "The app and the network Vibe X is built on: messaging, encryption, calls", "GNU AGPL v3 · SimpleX Chat Ltd", "https://github.com/simplex-chat/simplex-chat"),
    Card("Vibe", "Vibe X's look, motion and features", "GNU AGPL v3 · blackshadowde", "https://github.com/blackshadowde/Vibe-flutter"),
    Card("WebRTC", "Audio and video calls", "BSD License · The WebRTC project authors", "https://webrtc.org"),
    Card("Compose Multiplatform", "The UI toolkit", "Apache License 2.0 · JetBrains and Google", "https://www.jetbrains.com/compose-multiplatform/"),
    Card("Noto Animated Emoji", "Big animated emoji and reactions", "CC BY 4.0 · Google", "https://googlefonts.github.io/noto-emoji-animation/"),
  )

  fun show() {
    VibeSheets.show(maxHeight = 0.9f) { _ -> Content() }
  }

  @Composable
  private fun Content() {
    val muted = VibeColors.onVariant()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 20.dp).navigationBarsPadding()) {
      Text(vt("Credits"), Modifier.padding(horizontal = 4.dp), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface())
      Text("The people and projects behind Vibe X", Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp), fontSize = 15.sp, color = muted)
      Section("MADE BY", madeBy)
      Section("TEAM", team)
      Section("BUILT ON", builtOn)
      Text(
        "Vibe X is free software under the GNU AGPL v3, like SimpleX Chat. It is not made by or affiliated with SimpleX Chat Ltd or Discord. Tap a card to open its page.",
        Modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp), fontSize = 12.sp, color = muted
      )
    }
  }

  @Composable
  private fun Section(label: String, cards: List<Card>) {
    val uri = LocalUriHandler.current
    Text(label, Modifier.padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = VibeColors.onVariant())
    for (c in cards) {
      Column(
        Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, VibeColors.divider(), RoundedCornerShape(20.dp))
          .clickable(enabled = c.link != null) { c.link?.let { uri.openUriCatching(it) } }
          .padding(horizontal = 16.dp, vertical = 12.dp)
      ) {
        Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (c.link != null) VibeColors.blurple else VibeColors.onSurface())
        Spacer(Modifier.height(2.dp))
        Text(c.what, fontSize = 14.sp, color = VibeColors.onSurface())
        if (c.note != null) {
          Spacer(Modifier.height(2.dp))
          Text(c.note, fontSize = 13.sp, color = VibeColors.onVariant())
        }
      }
    }
  }
}

/** Shown once after an update. */
@Composable
fun VibeWhatsNewSheet(label: String, notes: List<String>, close: () -> Unit) {
  LaunchedEffect(Unit) { VibeHaptics.success() }
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp).navigationBarsPadding()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Image(painterResource(MR.images.vibe_logo), null, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
      Spacer(Modifier.width(14.dp))
      Column {
        Text(vt("What's new in Vibe X"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
        Text("You're now on $label", color = VibeColors.onVariant())
      }
    }
    Spacer(Modifier.height(16.dp))
    for ((i, line) in notes.withIndex()) {
      Row(Modifier.padding(vertical = 5.dp).vibePopIn(i)) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(VibeColors.blurple))
        Spacer(Modifier.width(10.dp))
        Text(line, fontSize = 15.sp, lineHeight = 20.sp, color = VibeColors.onSurface())
      }
    }
    Spacer(Modifier.height(18.dp))
    BlurpleButton(vt("Got it"), null) { close() }
  }
}
