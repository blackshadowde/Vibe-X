package chat.simplex.common.views.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.appPreferences
import chat.simplex.common.platform.chatModel
import chat.simplex.common.views.helpers.AlertManager

/**
 * Invitation links (simplex.chat/…, smp….simplex.im/…) open the browser,
 * because Android only opens web links in an app the website vouches for,
 * and SimpleX's sites vouch for SimpleX's own app. You can allow it for
 * Vibe X yourself in Android's "Open by default" settings.
 */
/** True when Android opens invitation links in Vibe X, false when it doesn't, null when it can't tell. */
expect fun vibeLinksOpenHere(): Boolean?

/** Opens Android's "Open by default" page for Vibe X. */
expect fun vibeOpenLinkSettings()

object VibeOpenLinks {
  /** Once, a little after the app opens: offer to open invitation links here. */
  fun maybeAsk() {
    if (appPreferences.vibeLinksAsked.get()) return
    if (vibeLinksOpenHere() != false) return
    appPreferences.vibeLinksAsked.set(true)
    VibeSheets.show(background = { vibePage() }) { close -> Sheet(close) }
  }

  private val simplexScheme = Regex("""simplex:/[^\s<>"]+""", RegexOption.IGNORE_CASE)

  /** The SimpleX invitation, address or group link in a text, if there is one. */
  fun linkIn(text: String?): String? {
    if (text.isNullOrBlank()) return null
    simplexScheme.find(text)?.let { return it.value }
    return VibeLinks.firstUrl(text)?.takeIf { VibeLinks.isSimplexLink(it) }
  }

  /** Opens a SimpleX link in Vibe X, like tapping it in a chat. */
  fun open(link: String) {
    chatModel.appOpenUrl.value = null to link
  }

  /** A link copied in this app (your own, to send): never offer it back. */
  fun copiedHere(text: String?, ts: Long) {
    if (ts != 0L) appPreferences.vibeClipTs.set(ts)
    linkIn(text)?.let { appPreferences.vibeClipLink.set(it) }
  }

  /** Coming back to the app with an invitation link copied somewhere else: offer to open it, once. */
  fun offerCopied(text: String?) {
    val link = linkIn(text) ?: return
    if (link == appPreferences.vibeClipLink.get()) return
    appPreferences.vibeClipLink.set(link)
    AlertManager.shared.showAlertDialog(
      title = vt("Open the copied link?"),
      text = vt("You copied a Vibe X invitation link. Open it here to connect."),
      confirmText = vt("Open"),
      onConfirm = { open(link) },
      dismissText = vt("Not now")
    )
  }

  fun show() = VibeSheets.show(background = { vibePage() }) { close -> Sheet(close) }

  @Composable
  private fun Sheet(close: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
      Text(vt("Open invitation links in Vibe X"), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      Spacer(Modifier.height(8.dp))
      Text(
        vt("Easiest: copy the link (or share it to Vibe X), then open Vibe X. It offers to open the link for you. To have links open here right away when tapped, you can also allow it once in Android:"),
        fontSize = 15.sp, color = VibeColors.onVariant()
      )
      Spacer(Modifier.height(10.dp))
      val steps = listOf(vt("Tap Open settings below"), vt("Turn on \"Open supported links\""), vt("Tap \"Add link\" and tick all the links"))
      steps.forEachIndexed { i, step ->
        val n = i + 1
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("$n", Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(VibeColors.blurple).padding(top = 2.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
          Spacer(Modifier.width(10.dp))
          Text(step, fontSize = 15.sp, color = VibeColors.onSurface())
        }
      }
      Spacer(Modifier.height(16.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(vibeCard()).clickable { close() }, contentAlignment = Alignment.Center) {
          Text(vt("Done"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface())
        }
        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(VibeColors.blurple).clickable { close(); vibeOpenLinkSettings() }, contentAlignment = Alignment.Center) {
          Text(vt("Open settings"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
      }
    }
  }
}
