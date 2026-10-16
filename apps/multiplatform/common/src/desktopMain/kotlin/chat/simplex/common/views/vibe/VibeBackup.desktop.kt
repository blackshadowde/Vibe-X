package chat.simplex.common.views.vibe

import androidx.compose.runtime.Composable
import java.io.File
import java.net.URI

@Composable
actual fun rememberVibeBackupPicker(onPicked: (String?) -> Unit): () -> Unit = { onPicked(null) }

@Composable
actual fun rememberVibeRestorePicker(onPicked: (URI?) -> Unit): () -> Unit = { onPicked(null) }

actual fun vibeBackupKeepAccess(uri: String) {}

actual fun vibeBackupWrite(from: File, to: String) {
  from.copyTo(File(URI(to)), overwrite = true)
}

actual fun vibeBackupLocationName(uri: String): String? = runCatching { File(URI(uri)).takeIf { it.exists() }?.name }.getOrNull()

actual fun vibeBackupSchedule(days: Int, wifiOnly: Boolean) {}
