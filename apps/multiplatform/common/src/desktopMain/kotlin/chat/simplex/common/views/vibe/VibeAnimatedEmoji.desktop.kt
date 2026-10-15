package chat.simplex.common.views.vibe

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
actual fun VibeAnimatedEmoji(emoji: String, size: Dp, modifier: Modifier) = VibePlainEmoji(emoji, size, modifier)
