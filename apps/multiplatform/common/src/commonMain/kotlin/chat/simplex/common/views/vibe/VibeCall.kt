package chat.simplex.common.views.vibe

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import chat.simplex.common.views.call.CallMediaType
import chat.simplex.common.views.call.RcvCallInvitation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The middle of a Vibe voice call: their big picture with rings rippling
 * out while it rings, and a soft green glow that breathes once you're
 * connected (like Discord's speaking ring), then their name.
 */
@Composable
fun VibeCallHero(image: String?, name: String, connected: Boolean) {
  val inf = rememberInfiniteTransition(label = "callHero")
  val ripple by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing)), label = "ripple")
  val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
  val size = 168.dp
  Box(Modifier.size(size + 80.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
      val r0 = size.toPx() / 2
      val c = center
      if (connected) {
        drawCircle(VibeColors.green.copy(alpha = 0.18f + 0.18f * breathe), r0 + 10.dp.toPx() + 8.dp.toPx() * breathe, c)
        drawCircle(VibeColors.green, r0 + 5.dp.toPx(), c, style = Stroke(3.dp.toPx()))
      } else {
        for (k in 0..2) {
          val t = (ripple + k / 3f) % 1f
          drawCircle(Color.White.copy(alpha = 0.22f * (1f - t)), r0 + 40.dp.toPx() * t, c, style = Stroke(2.dp.toPx()))
        }
      }
    }
    VibeAvatar(image, name, size)
  }
  Spacer(Modifier.height(8.dp))
  Text(name, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
  Spacer(Modifier.height(4.dp))
}

private fun RcvCallInvitation.vibeKind() = if (callType.media == CallMediaType.Video) "Incoming video call" else "Incoming voice call"

/** A call coming in while you use the app: a card that drops in from the top, with Decline and Accept. */
@Composable
fun VibeIncomingCallBanner(invitation: RcvCallInvitation, reject: () -> Unit, ignore: () -> Unit, accept: () -> Unit) {
  val p = remember { Animatable(0f) }
  LaunchedEffect(Unit) { VibeHaptics.heavy(); p.animateTo(1f, VibeSprings.pop()) }
  val c = invitation.contact
  val video = invitation.callType.media == CallMediaType.Video
  val inf = rememberInfiniteTransition(label = "ring")
  val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearOutSlowInEasing)), label = "pulse")
  Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
    Row(
      Modifier
        .graphicsLayer {
          translationY = -(1f - p.value) * 120.dp.toPx()
          alpha = p.value.coerceIn(0f, 1f)
        }
        .fillMaxWidth()
        .shadow(16.dp, RoundedCornerShape(28.dp))
        .clip(RoundedCornerShape(28.dp))
        .background(Color(0xFF1E1F22))
        .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(60.dp)) {
          drawCircle(VibeColors.green.copy(alpha = 0.35f * (1f - pulse)), size.minDimension / 2 * (0.8f + 0.2f * pulse))
        }
        VibeAvatar(c.image, c.displayName, 48.dp)
      }
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(c.chatViewName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(if (video) VibeIcons.Videocam else VibeIcons.Call, null, Modifier.size(14.dp), tint = Color(0xFFB5BAC1))
          Spacer(Modifier.width(4.dp))
          Text(invitation.vibeKind(), fontSize = 13.sp, color = Color(0xFFB5BAC1), maxLines = 1)
        }
      }
      Box(Modifier.size(36.dp).clip(CircleShape).clickable { ignore() }, contentAlignment = Alignment.Center) {
        Icon(VibeIcons.Close, "Ignore", Modifier.size(20.dp), tint = Color(0xFFB5BAC1))
      }
      Spacer(Modifier.width(6.dp))
      RoundCallButton(VibeIcons.CallEnd, VibeColors.red, 48.dp, "Decline") { VibeHaptics.medium(); reject() }
      Spacer(Modifier.width(10.dp))
      RoundCallButton(if (video) VibeIcons.Videocam else VibeIcons.Call, VibeColors.green, 48.dp, "Accept") { VibeHaptics.success(); accept() }
    }
  }
}

/** The full-screen ringing screen (lock screen): their picture with rings, then big Decline and Accept. */
@Composable
fun VibeIncomingCallFull(invitation: RcvCallInvitation, reject: () -> Unit, ignore: () -> Unit, accept: () -> Unit) {
  val c = invitation.contact
  val video = invitation.callType.media == CallMediaType.Video
  val inf = rememberInfiniteTransition(label = "accept")
  val bob by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
  Column(
    Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2B2F7A), Color(0xFF15162E), Color(0xFF0B0B10))))
      .systemBarsPadding().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(Modifier.height(24.dp))
    Text(invitation.vibeKind(), fontSize = 16.sp, color = Color(0xFFB9BFFF), fontWeight = FontWeight.SemiBold)
    Text("Vibe X · end-to-end encrypted", fontSize = 13.sp, color = Color(0xFF8E93B8))
    Spacer(Modifier.weight(1f))
    VibeCallHero(c.image, c.chatViewName, connected = false)
    Spacer(Modifier.weight(1.3f))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundCallButton(VibeIcons.CallEnd, VibeColors.red, 72.dp, "Decline") { VibeHaptics.medium(); reject() }
        Spacer(Modifier.height(8.dp))
        Text("Decline", color = Color.White, fontSize = 14.sp)
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.graphicsLayer { translationY = -bob * 8.dp.toPx() }) {
          RoundCallButton(if (video) VibeIcons.Videocam else VibeIcons.Call, VibeColors.green, 72.dp, "Accept") { VibeHaptics.success(); accept() }
        }
        Spacer(Modifier.height(8.dp))
        Text("Accept", color = Color.White, fontSize = 14.sp)
      }
    }
    Spacer(Modifier.height(16.dp))
    Text("Ignore", Modifier.clip(RoundedCornerShape(16.dp)).clickable { ignore() }.padding(horizontal = 16.dp, vertical = 8.dp), color = Color(0xFFB5BAC1))
  }
}

@Composable
private fun RoundCallButton(icon: ImageVector, color: Color, size: androidx.compose.ui.unit.Dp, label: String, onClick: () -> Unit) {
  Box(Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    Icon(icon, label, Modifier.size(size * 0.46f), tint = Color.White)
  }
}
