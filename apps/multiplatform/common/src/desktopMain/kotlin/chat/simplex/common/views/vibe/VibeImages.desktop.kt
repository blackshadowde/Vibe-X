package chat.simplex.common.views.vibe

import androidx.compose.ui.graphics.ImageBitmap
import chat.simplex.common.views.helpers.getBitmapFromUri
import java.net.URI

actual fun vibePreviewBitmap(uri: URI, maxPx: Int): ImageBitmap? = runCatching { getBitmapFromUri(uri, false) }.getOrNull()

actual fun vibeEncodeAvatar(image: ImageBitmap, maxChars: Int): String = chat.simplex.common.platform.resizeImageToStrSize(image, maxChars.toLong())
