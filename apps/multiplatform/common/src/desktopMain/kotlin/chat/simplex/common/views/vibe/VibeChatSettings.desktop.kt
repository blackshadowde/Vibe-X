package chat.simplex.common.views.vibe

import chat.simplex.common.model.ChatItem
import java.io.File

actual fun vibeSaveToGallery(item: ChatItem) {}

actual fun vibeCacheDirs(): List<File> = emptyList()

actual fun vibeWatchCrashes() {}

actual fun vibeTakeCrashReport(): String? = null
