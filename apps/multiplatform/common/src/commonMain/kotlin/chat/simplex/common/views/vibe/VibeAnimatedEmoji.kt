package chat.simplex.common.views.vibe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import chat.simplex.common.ui.theme.EmojiFont

/**
 * Google's Noto Animated Emoji (CC BY 4.0): every emoji has its own
 * animation (a laughing face rocks, fire flickers, a heart beats…), like
 * Vibe. Downloaded once and kept; shows the normal emoji while loading,
 * when an emoji has no animation, offline, or when a SOCKS proxy (Tor) is
 * on, so nothing goes around your proxy.
 */
@Composable
expect fun VibeAnimatedEmoji(emoji: String, size: Dp, modifier: Modifier = Modifier)

@Composable
fun VibePlainEmoji(emoji: String, size: Dp, modifier: Modifier = Modifier) {
  val fs = with(LocalDensity.current) { (size * 0.78f).toSp() }
  Box(modifier.size(size), contentAlignment = Alignment.Center) {
    Text(emoji, style = TextStyle(fontFamily = EmojiFont, fontSize = fs, lineHeight = fs))
  }
}

/** Noto's file names: code points in lowercase hex joined by "_" (with and without FE0F). */
fun notoCodes(emoji: String): List<String> {
  val full = emoji.codePoints().toArray().joinToString("_") { Integer.toHexString(it) }
  val bare = full.split("_").filter { it != "fe0f" }.joinToString("_")
  return listOf(full, bare).distinct()
}
