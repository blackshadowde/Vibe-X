package chat.simplex.common.views.vibe

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatModel
import chat.simplex.common.platform.appPreferences
import chat.simplex.common.views.helpers.ModalManager
import chat.simplex.common.views.migration.MigrateToDeviceView
import chat.simplex.common.views.onboarding.OnboardingStage
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.launch
import kotlin.math.sin

private val lavender = Color(0xFFB9BFFF)

private class Fact(val icon: ImageVector, val title: String, val text: String)
private class Page(val kicker: String, val title: String, val body: String, val facts: List<Fact>)

private val pages = listOf(
  Page(
    "Welcome to Vibe X",
    "Chat that stays between you and them",
    "Vibe X is a private messenger on an open network that has no user IDs of any kind, not even random numbers. Here is how it keeps you safer, in 30 seconds.",
    listOf(
      Fact(VibeIcons.Lock, "Private by default", "Every message, file and call is end-to-end encrypted."),
      Fact(VibeIcons.QrCode, "No phone number, no username", "Nothing identifies you. You connect with a one-time link or QR code."),
    )
  ),
  Page(
    "How it works",
    "Locked before it leaves your phone",
    "Each message is encrypted on your device and passed through relays that can't read it. Every conversation goes through different relays, so no server sees your whole picture.",
    listOf(
      Fact(VibeIcons.Block, "Servers can't read it", "Relays only ever carry encrypted packets, and delete them once delivered."),
      Fact(VibeIcons.Groups, "No one sees who talks to whom", "There is no contact list on any server. With private routing, not even your IP address."),
    )
  ),
  Page(
    "Why it is safer",
    "Ready for quantum computers",
    "Messages use post-quantum key exchange together with the double ratchet, so traffic someone records today stays locked even against future quantum computers.",
    listOf(
      Fact(VibeIcons.Lock, "Keys that keep changing", "Every message gets a new key. A leaked key can't open past messages, and the chat heals itself after a break-in."),
      Fact(VibeIcons.Bookmark, "Your data stays with you", "Chats live only on your phone, in an encrypted database. Move phones with Migrate to another device."),
    )
  ),
  Page(
    "The little things",
    "Safer in the details too",
    "Privacy is not just encryption. Vibe X keeps the small stuff quiet as well.",
    listOf(
      Fact(VibeIcons.Bolt, "Live typing", "They see your words as you type. Tap ⚡ in the message box to turn it off."),
      Fact(VibeIcons.Delete, "Disappearing messages", "Long-press send to make a message vanish after a while."),
      Fact(VibeIcons.Check, "Open source", "Built on an open, audited protocol anyone can inspect."),
    )
  ),
)

/** Vibe's welcome (logo, four short pages), with SimpleX's facts. Then "Get started". */
@Composable
fun VibeWelcome(chatModel: ChatModel, onDone: (() -> Unit)? = null) {
  val pager = rememberPagerState { pages.size }
  val scope = rememberCoroutineScope()
  val last = pager.currentPage == pages.size - 1
  fun finish() {
    if (onDone != null) { onDone(); return }
    val user = chatModel.currentUser.value
    appPreferences.onboardingStage.set(if (user == null) OnboardingStage.Step2_CreateProfile else OnboardingStage.OnboardingComplete)
  }
  Box(Modifier.fillMaxSize().background(VibeColors.surface())) {
    // soft blurple glow at the top
    Box(
      Modifier.fillMaxWidth().height(360.dp).offset(y = (-60).dp).drawBehind {
        drawCircle(
          Brush.radialGradient(listOf(VibeColors.blurple.copy(alpha = 0.28f), Color.Transparent), center = Offset(size.width / 2, size.height / 2), radius = size.width * 0.62f),
          radius = size.width * 0.62f, center = Offset(size.width / 2, size.height / 2)
        )
      }
    )
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
      Row(Modifier.padding(start = 20.dp, top = 12.dp, end = 12.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(MR.images.vibe_logo), null, Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(8.dp))
        Text("Vibe X", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VibeColors.onSurface())
        Spacer(Modifier.weight(1f))
        // already have Vibe X: log in with your backup
        if (onDone == null && chatModel.currentUser.value == null) VibeAccount.LogInButton()
        if (!last) {
          Text("Skip", Modifier.clip(RoundedCornerShape(12.dp)).clickable { finish() }.padding(horizontal = 12.dp, vertical = 8.dp), color = lavender, fontWeight = FontWeight.SemiBold)
        }
      }
      HorizontalPager(pager, Modifier.weight(1f)) { i ->
        PageView(pages[i], i, active = pager.currentPage == i)
      }
      Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in pages.indices) {
          val w by animateDpAsState(if (i == pager.currentPage) 28.dp else 8.dp, tween(350, easing = FastOutSlowInEasing), label = "dot")
          Box(
            Modifier.padding(end = 8.dp).size(width = w, height = 8.dp).clip(RoundedCornerShape(8.dp))
              .background(if (i == pager.currentPage) VibeColors.blurple else VibeColors.divider())
              .clickable { scope.launch { pager.animateScrollToPage(i) } }
          )
        }
        Spacer(Modifier.weight(1f))
        Row(
          Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(VibeColors.blurple)
            .clickable { if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
            .padding(horizontal = if (last) 28.dp else 24.dp).animateContentSize(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(if (last) (if (onDone != null) "Done" else "Get started") else "Next", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
          Spacer(Modifier.width(8.dp))
          Icon(VibeIcons.ArrowBack, null, Modifier.size(18.dp).graphicsLayer { rotationZ = 180f }, tint = Color.White)
        }
      }
      if (pager.currentPage == 0 && chatModel.currentUser.value == null) {
        Text(
          "Moving from another phone? Migrate here",
          Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp).clip(RoundedCornerShape(8.dp))
            .clickable { ModalManager.fullscreen.showCustomModal(animated = false) { close -> MigrateToDeviceView(close) } }
            .padding(horizontal = 10.dp, vertical = 6.dp),
          fontSize = 13.sp, color = lavender
        )
      }
    }
  }
}

@Composable
private fun PageView(d: Page, index: Int, active: Boolean) {
  val p = remember { Animatable(0f) }
  LaunchedEffect(active) { if (active) { p.snapTo(0f); p.animateTo(1f, tween(900, easing = LinearEasing)) } }
  fun Modifier.rise(begin: Float, end: Float) = graphicsLayer {
    val t = ((p.value - begin) / (end - begin)).coerceIn(0f, 1f)
    val e = 1f - (1f - t) * (1f - t) * (1f - t)
    alpha = e
    translationY = (1f - e) * 40f
  }
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.Center
  ) {
    Box(Modifier.rise(0f, 0.5f)) { Hero(index) }
    Spacer(Modifier.height(16.dp))
    Text(d.kicker.uppercase(), Modifier.rise(0.1f, 0.55f), fontSize = 12.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold, color = lavender)
    Spacer(Modifier.height(6.dp))
    Text(d.title, Modifier.rise(0.15f, 0.6f), fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface())
    Spacer(Modifier.height(10.dp))
    Text(d.body, Modifier.rise(0.2f, 0.65f), fontSize = 15.sp, lineHeight = 22.sp, color = VibeColors.onVariant())
    Spacer(Modifier.height(16.dp))
    d.facts.forEachIndexed { i, f ->
      Row(
        Modifier.rise(0.3f + 0.1f * i, 0.75f + 0.1f * i).padding(bottom = 8.dp).fillMaxWidth()
          .clip(RoundedCornerShape(20.dp)).background(VibeColors.high()).border(1.dp, VibeColors.divider(), RoundedCornerShape(20.dp)).padding(12.dp)
      ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(VibeColors.blurple.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
          Icon(f.icon, null, Modifier.size(22.dp), tint = lavender)
        }
        Spacer(Modifier.width(12.dp))
        Column {
          Text(f.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface())
          Spacer(Modifier.height(2.dp))
          Text(f.text, fontSize = 13.sp, lineHeight = 18.sp, color = VibeColors.onVariant())
        }
      }
    }
  }
}

@Composable
private fun Hero(index: Int) {
  val inf = rememberInfiniteTransition(label = "hero")
  val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "t")
  Box(
    Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp))
      .background(Brush.linearGradient(listOf(VibeColors.blurple.copy(alpha = 0.22f), VibeColors.blurple.copy(alpha = 0.06f))))
      .border(1.dp, VibeColors.blurple.copy(alpha = 0.25f), RoundedCornerShape(28.dp)),
    contentAlignment = Alignment.Center
  ) {
    // pulsing rings
    Box(Modifier.size(120.dp).drawBehind {
      for (i in 0..2) {
        val p = (t + i / 3f) % 1f
        drawCircle(lavender.copy(alpha = 0.5f * (1 - p)), radius = size.minDimension / 2 * (0.6f + 1.6f * p), style = Stroke(2.dp.toPx()))
      }
    })
    when (index) {
      0 -> {
        // the Vibe logo, popping in
        val pop = remember { Animatable(0f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 200f)) }
        Image(
          painterResource(MR.images.vibe_logo), null,
          Modifier.size(96.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(RoundedCornerShape(26.dp))
        )
      }
      1 -> Row(verticalAlignment = Alignment.CenterVertically) {
        Node("You")
        Box(Modifier.width(110.dp).height(30.dp)) {
          val x = (t * 1.4f).coerceAtMost(1f)
          Text(
            "🔒", Modifier.align(Alignment.CenterStart).graphicsLayer { translationX = x * 82.dp.toPx() },
            fontSize = 20.sp
          )
        }
        Node("Friend")
      }
      2 -> {
        val s = 1f + 0.08f * sin(t * 2f * Math.PI.toFloat())
        Box(
          Modifier.size(84.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape).background(VibeColors.blurple),
          contentAlignment = Alignment.Center
        ) {
          Text("PQ", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = Color.White)
        }
      }
      else -> Row(
        Modifier.clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomEnd = 30.dp, bottomStart = 8.dp)).background(Color(0xFFF4F5FB))
          .padding(start = 20.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("vibe", fontSize = 32.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = Color(0xFF141826))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).background(VibeColors.blurple), contentAlignment = Alignment.Center) {
          Icon(VibeIcons.Bolt, null, Modifier.size(22.dp), tint = Color.White)
        }
      }
    }
  }
}

@Composable
private fun Node(label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
      Text("📱", fontSize = 22.sp)
    }
    Spacer(Modifier.height(4.dp))
    Text(label, fontSize = 12.sp, color = VibeColors.onSurface())
  }
}
