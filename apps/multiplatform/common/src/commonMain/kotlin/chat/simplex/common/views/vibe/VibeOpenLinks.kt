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

  fun show() = VibeSheets.show(background = { vibePage() }) { close -> Sheet(close) }

  @Composable
  private fun Sheet(close: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
      Text(vt("Open invitation links in Vibe X"), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      Spacer(Modifier.height(8.dp))
      Text(
        vt("Right now, invitation links someone sends you open in the browser. Let Android open them in Vibe X instead:"),
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
          Text(vt("Not now"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface())
        }
        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(VibeColors.blurple).clickable { close(); vibeOpenLinkSettings() }, contentAlignment = Alignment.Center) {
          Text(vt("Open settings"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
      }
    }
  }
}
