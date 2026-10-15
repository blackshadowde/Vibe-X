package chat.simplex.common.views.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.appPreferences
import chat.simplex.common.platform.chatModel
import chat.simplex.common.ui.theme.DefaultTheme
import chat.simplex.common.ui.theme.ThemeManager
import kotlinx.datetime.Clock
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

const val VIBE_TEXT_MIN = 12
const val VIBE_TEXT_MAX = 30

/** Size of message text in chats (Telegram's Text size; names and times keep theirs). */
@Composable
fun vibeMessageTextSize() = appPreferences.vibeTextSize.state.value.coerceIn(VIBE_TEXT_MIN, VIBE_TEXT_MAX)

/**
 * Settings > Appearance in Vibe X: "Change your style", as in Vibe:
 * Light / Dark / System, a live preview drawn like a chat, Text size and
 * Haptic feedback. (SimpleX's wallpapers, bubble shapes and colour
 * editors aren't used by Vibe X's look.)
 */
@Composable
fun VibeStyleView() {
  val muted = VibeColors.onVariant()
  // keeps clear of the top bar and the status bar, like every settings page
  chat.simplex.common.platform.ColumnWithScrollBar {
    Text(
      "Change your style", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 16.dp),
      fontSize = 28.sp, color = VibeColors.onSurface()
    )
    // theme
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(vibeCard()).padding(12.dp)) {
      val current = appPreferences.currentTheme.state.value
      val mode = when (current) {
        DefaultTheme.SYSTEM_THEME_NAME -> 2
        DefaultTheme.LIGHT.themeName -> 0
        else -> 1
      }
      val outline = VibeColors.onVariant().copy(alpha = 0.6f)
      Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).border(1.dp, outline, RoundedCornerShape(24.dp))) {
        Segment(VibeIcons.LightMode, "Light", mode == 0, Modifier.weight(1f)) { ThemeManager.applyTheme(DefaultTheme.LIGHT.themeName) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(outline))
        Segment(VibeIcons.DarkMode, "Dark", mode == 1, Modifier.weight(1f)) {
          ThemeManager.changeDarkTheme(DefaultTheme.DARK.themeName)
          ThemeManager.applyTheme(DefaultTheme.DARK.themeName)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(outline))
        Segment(VibeIcons.Check, "System", mode == 2, Modifier.weight(1f)) {
          ThemeManager.changeDarkTheme(DefaultTheme.DARK.themeName)
          ThemeManager.applyTheme(DefaultTheme.SYSTEM_THEME_NAME)
        }
      }
    }
    Text("Messages:", Modifier.padding(start = 24.dp, top = 22.dp, bottom = 10.dp), fontSize = 16.sp, color = muted)
    val size = vibeMessageTextSize()
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(vibeCard())) {
      Preview(size)
      Divider(color = VibeColors.divider())
      Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Text size", Modifier.weight(1f), fontSize = 17.sp, color = VibeColors.onSurface())
        Text("$size", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.onSurface())
      }
      Divider(color = VibeColors.divider())
      Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("A", fontSize = 13.sp, color = VibeColors.onSurface())
        Slider(
          value = size.toFloat(),
          onValueChange = {
            val v = it.roundToInt().coerceIn(VIBE_TEXT_MIN, VIBE_TEXT_MAX)
            if (v != appPreferences.vibeTextSize.get()) VibeHaptics.selection()
            appPreferences.vibeTextSize.set(v)
          },
          valueRange = VIBE_TEXT_MIN.toFloat()..VIBE_TEXT_MAX.toFloat(),
          steps = VIBE_TEXT_MAX - VIBE_TEXT_MIN - 1,
          modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
          colors = SliderDefaults.colors(
            thumbColor = VibeColors.blurple, activeTrackColor = VibeColors.blurple,
            inactiveTrackColor = VibeColors.onVariant().copy(alpha = 0.25f),
            activeTickColor = Color.White.copy(alpha = 0.6f), inactiveTickColor = VibeColors.onVariant().copy(alpha = 0.5f)
          )
        )
        Text("A", fontSize = 24.sp, color = VibeColors.onSurface())
      }
      Divider(color = VibeColors.divider())
      val haptics = appPreferences.vibeHaptics.state.value
      Row(
        Modifier.fillMaxWidth().clickable { appPreferences.vibeHaptics.set(!haptics) }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f)) {
          Text("Haptic feedback", fontSize = 17.sp, color = VibeColors.onSurface())
          Text("Vibrate gently on taps, menus and reactions", fontSize = 14.sp, color = muted)
        }
        Switch(
          checked = haptics,
          onCheckedChange = { appPreferences.vibeHaptics.set(it); VibeHaptics.medium() },
          colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = VibeColors.blurple, checkedTrackAlpha = 1f,
            uncheckedThumbColor = if (VibeColors.isDark()) Color(0xFFDBDEE1) else Color(0xFF80848E),
            uncheckedTrackColor = if (VibeColors.isDark()) Color(0xFF4E5058) else Color(0xFFE3E5E8), uncheckedTrackAlpha = 1f
          )
        )
      }
    }
    Spacer(Modifier.height(24.dp))
  }
}

@Composable
private fun Segment(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
  Row(
    modifier.fillMaxHeight()
      .background(if (selected) VibeColors.secondaryContainer() else Color.Transparent)
      .clickable(onClick = onClick),
    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(if (selected) VibeIcons.Check else icon, null, Modifier.size(20.dp), tint = VibeColors.onSurface())
    Spacer(Modifier.width(8.dp))
    Text(label, fontSize = 16.sp, color = VibeColors.onSurface())
  }
}

/** The preview, drawn exactly like a Vibe X chat: reply line, two messages, ticks. */
@Composable
private fun Preview(size: Int) {
  val user = chatModel.currentUser.value
  val me = user?.displayName ?: "You"
  val now = Clock.System.now()
  val line = VibeColors.onVariant().copy(alpha = 0.45f)
  Column(
    Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(VibeColors.surface())
      .padding(top = 10.dp, end = 12.dp, bottom = 14.dp)
  ) {
    Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.width(64.dp).fillMaxHeight().padding(start = 12.dp).drawBehind {
          val cx = 20.dp.toPx()
          val mid = this.size.height / 2
          val r = 8.dp.toPx()
          val p = Path().apply {
            moveTo(cx, this@drawBehind.size.height + 2.dp.toPx())
            lineTo(cx, mid + r)
            quadraticBezierTo(cx, mid, cx + r, mid)
            lineTo(this@drawBehind.size.width, mid)
          }
          drawPath(p, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
      )
      Spacer(Modifier.width(6.dp))
      VibeAvatar(user?.image, me, 16.dp)
      Spacer(Modifier.width(6.dp))
      Text("@$me", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.ownName(), maxLines = 1)
      Spacer(Modifier.width(6.dp))
      Text("Hey! Still on for tonight? 🍕", fontSize = 13.5.sp, color = VibeColors.onVariant(), maxLines = 1)
    }
    PreviewRow(null, "Gowthami", VibeColors.otherName(), vibeTime(now - 2.minutes), size, "Yes! I'll bring the board games", ticks = false)
    Spacer(Modifier.height(14.dp))
    PreviewRow(user?.image, me, VibeColors.ownName(), vibeTime(now), size, "Perfect, see you at 8 🎲", ticks = true)
  }
}

@Composable
private fun PreviewRow(image: String?, name: String, color: Color, time: String, size: Int, text: String, ticks: Boolean) {
  Row {
    Spacer(Modifier.width(12.dp))
    VibeAvatar(image, name, 40.dp)
    Spacer(Modifier.width(12.dp))
    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.width(6.dp))
        Text(time, fontSize = 12.sp, color = VibeColors.onVariant())
      }
      Spacer(Modifier.height(2.dp))
      Row(verticalAlignment = Alignment.Bottom) {
        Text(text, Modifier.weight(1f, false), fontSize = size.sp, lineHeight = (size * 1.3f).sp, color = VibeColors.onSurface())
        if (ticks) Icon(VibeIcons.DoneAll, null, Modifier.padding(start = 6.dp, bottom = 3.dp).size(18.dp), tint = VibeColors.blurple)
      }
    }
  }
}
