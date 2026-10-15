package chat.simplex.common.views.vibe

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun rememberVibeGallery(): VibeGallery = VibeGallery(emptyList(), granted = true, request = {})

@Composable
actual fun VibeMediaThumb(item: VibeMediaItem, modifier: Modifier) {}
