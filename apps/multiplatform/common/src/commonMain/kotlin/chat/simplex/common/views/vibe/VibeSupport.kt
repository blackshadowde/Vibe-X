package chat.simplex.common.views.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.ColumnWithScrollBar
import chat.simplex.common.platform.appPreferences
import chat.simplex.common.platform.showToast
import chat.simplex.common.views.helpers.ModalManager
import chat.simplex.common.views.newchat.QRCode
import java.net.URLEncoder

/**
 * "Support Vibe X": UPI donations, as in Vibe. A page in Settings, plus a
 * gentle pane now and then (after a few days of use, at most once a month,
 * with "Don't ask again").
 */
object VibeSupport {
  const val UPI_ID = "ashish.boddu@ybl"
  private const val PAYEE = "Vibe"
  private const val DAY = 24 * 60 * 60 * 1000L

  /** Standard UPI link: any UPI app (GPay, PhonePe, Paytm, BHIM…) opens it. */
  val upiLink: String
    get() = "upi://pay?pa=$UPI_ID&pn=${enc(PAYEE)}&tn=${enc(vt("Support Vibe X"))}&cu=INR"

  private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

  fun open() {
    ModalManager.start.showModal(settings = true) { VibeSupportView() }
  }

  /** Called when the app opens: shows the pane only when it's polite to. */
  fun maybeAsk() {
    runCatching {
      if (appPreferences.vibeSupportNever.get()) return
      val now = System.currentTimeMillis()
      val first = appPreferences.vibeSupportFirstSeen.get()
      if (first == 0L) {
        appPreferences.vibeSupportFirstSeen.set(now)
        return
      }
      if (now - first < 5 * DAY) return // let people try Vibe X first
      if (now - appPreferences.vibeSupportLastAsk.get() < 30 * DAY) return // at most once a month
      // don't stack on another pane (an update, a menu)
      if (VibeSheets.stack.isNotEmpty()) return
      appPreferences.vibeSupportLastAsk.set(now)
      VibeSheets.show { close -> AskSheet(close) }
    }
  }

  @Composable
  private fun AskSheet(close: () -> Unit) {
    Column(
      Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp).navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text("❤️", fontSize = 40.sp)
      Spacer(Modifier.height(8.dp))
      Text(vt("Enjoying Vibe X?"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      Spacer(Modifier.height(8.dp))
      Text(
        vt("Vibe X is free, with no ads and no tracking. It is built by one person. If it helps you stay in touch, a small UPI donation keeps it going."),
        textAlign = TextAlign.Center, lineHeight = 20.sp, color = VibeColors.onVariant()
      )
      Spacer(Modifier.height(20.dp))
      Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(VibeColors.blurple)
          .clickable { close(); open() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
      ) {
        Text("❤", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(vt("Support Vibe X"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
      }
      Spacer(Modifier.height(4.dp))
      Row {
        Link(vt("Maybe later")) { close() }
        Link(vt("Don't ask again")) { appPreferences.vibeSupportNever.set(true); close() }
      }
    }
  }

  @Composable
  private fun Link(text: String, onClick: () -> Unit) {
    Text(
      text, Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
      color = VibeColors.blurple, fontWeight = FontWeight.SemiBold
    )
  }
}

/** Settings → Support Vibe X: QR code, UPI ID to copy, and "Pay with a UPI app". */
@Composable
fun VibeSupportView() {
  val uri = LocalUriHandler.current
  val clipboard = LocalClipboardManager.current
  val muted = VibeColors.onVariant()
  ColumnWithScrollBar(horizontalAlignment = Alignment.CenterHorizontally) {
    Text("❤️", Modifier.padding(top = 8.dp), fontSize = 44.sp)
    Spacer(Modifier.height(8.dp))
    Text(vt("Keep Vibe X free"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
    Spacer(Modifier.height(8.dp))
    Text(
      vt("No ads, no tracking, no paid features. Donations pay for development and testing. Any amount helps. Thank you!"),
      Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center, lineHeight = 20.sp, color = muted
    )
    Spacer(Modifier.height(24.dp))
    // scan it from another phone, or show it to a friend
    Column(
      Modifier.width(240.dp).shadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      QRCode(VibeSupport.upiLink, padding = PaddingValues(0.dp), tintColor = Color(0xFF111214), withLogo = false)
      Spacer(Modifier.height(10.dp))
      Text(vt("Scan with any UPI app"), color = Color(0xFF4E5058), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(22.dp))
    Row(
      Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(vibeCard())
        .clickable {
          clipboard.setText(AnnotatedString(VibeSupport.UPI_ID))
          VibeHaptics.light()
          showToast(vt("UPI ID copied"))
        }
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(Modifier.weight(1f)) {
        Text("UPI ID", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = muted)
        Spacer(Modifier.height(2.dp))
        Text(VibeSupport.UPI_ID, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface())
      }
      Icon(VibeIcons.Copy, null, Modifier.size(20.dp), tint = VibeColors.onSurface())
      Spacer(Modifier.width(6.dp))
      Text(vt("Copy"), color = VibeColors.onSurface())
    }
    Spacer(Modifier.height(14.dp))
    Row(
      Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(VibeColors.blurple)
        .clickable {
          VibeHaptics.medium()
          val ok = runCatching { uri.openUri(VibeSupport.upiLink) }.isSuccess
          if (!ok) showToast("No UPI app found. Copy the UPI ID instead.")
        },
      horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
      Text(vt("Pay with a UPI app"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(10.dp))
    Text(
      vt("Opens GPay, PhonePe, Paytm or BHIM. If your app blocks it, copy the UPI ID and pay from inside the app."),
      Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center, fontSize = 12.5.sp, color = muted
    )
    Spacer(Modifier.height(32.dp))
  }
}
