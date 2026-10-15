package chat.simplex.common.views.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.platform.*
import chat.simplex.common.views.database.*
import chat.simplex.common.views.helpers.*
import chat.simplex.common.views.onboarding.OnboardingStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.net.URI

/** Restarts the app (to open with a different database). */
expect fun vibeRestartApp()

/**
 * Log out / log in, the SimpleX way. There is no account on a server: your
 * chats live only in this phone's database. Log out saves it as one
 * encrypted file (your "account") and clears the app; Log in loads that
 * file back. The passphrase you set is the password.
 */
object VibeAccount {
  fun showLogOut() {
    VibeSheets.show { close -> LogOutSheet(close) }
  }

  private fun copy(from: File, to: URI): Boolean = try {
    to.outputStream().use { stream -> BufferedOutputStream(stream).use { out -> from.inputStream().use { it.copyTo(out) } } }
    true
  } catch (e: Throwable) {
    Log.e(TAG, "Vibe log out: saving failed: ${e.message}")
    false
  }

  @Composable
  private fun LogOutSheet(close: () -> Unit) {
    val m = chatModel
    val prefs = m.controller.appPrefs
    val random = remember { prefs.initialRandomDBPassphrase.state }
    var busy by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf<String?>(null) }
    val wasRunning = remember { m.chatRunning.value == true }
    val muted = VibeColors.onVariant()
    fun restartChat() {
      if (wasRunning) startChat(m, mutableStateOf(prefs.chatLastStart.get()), m.chatDbChanged)
    }
    val save = rememberFileChooserLauncher(false) { to: URI? ->
      val file = archive?.let { File(it) }
      archive = null
      if (file == null) return@rememberFileChooserLauncher
      if (to == null) {
        // cancelled: nothing is cleared, chats go on
        file.delete()
        busy = false
        restartChat()
        return@rememberFileChooserLauncher
      }
      withLongRunningApi {
        val ok = withContext(Dispatchers.IO) { copy(file, to) }
        file.delete()
        if (!ok) {
          busy = false
          AlertManager.shared.showAlertMsg("Couldn't save your backup", "Nothing was cleared. Try saving it somewhere else.")
          restartChat()
          return@withLongRunningApi
        }
        try {
          deleteChatAsync(m)
          prefs.chatStopped.set(false)
          prefs.onboardingStage.set(OnboardingStage.Step1_SimpleXInfo)
          VibeHaptics.success()
          showToast(vt("Logged out. Your backup is saved."))
          vibeRestartApp()
        } catch (e: Throwable) {
          busy = false
          AlertManager.shared.showAlertMsg("Couldn't log out", e.message ?: e.toString())
        }
      }
    }
    Column(
      Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp).navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text("👋", fontSize = 40.sp)
      Spacer(Modifier.height(8.dp))
      Text(vt("Log out of Vibe X"), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      Spacer(Modifier.height(8.dp))
      Text(
        "Your chats live only on this phone. Logging out saves them as one encrypted backup file, then clears Vibe X. " +
            "To log back in, open Vibe X, tap \"Log in\" and pick the file. Your passphrase is your password.",
        textAlign = TextAlign.Center, lineHeight = 20.sp, color = muted
      )
      Spacer(Modifier.height(12.dp))
      Text(
        "Keep the file and passphrase safe: without them your chats can't be recovered. " +
            vt("Only log in with your newest backup, and don't use it on two phones at once."),
        textAlign = TextAlign.Center, fontSize = 13.sp, lineHeight = 18.sp, color = VibeColors.red
      )
      Spacer(Modifier.height(20.dp))
      when {
        busy -> CircularProgressIndicator(Modifier.size(32.dp), color = VibeColors.blurple, strokeWidth = 3.dp)
        random.value -> {
          Text(vt("First, set a passphrase: it locks the backup and is what you log in with."), textAlign = TextAlign.Center, color = VibeColors.onSurface())
          Spacer(Modifier.height(12.dp))
          Pill(vt("Set passphrase"), filled = true) {
            close()
            ModalManager.start.showModal { DatabaseEncryptionView(chatModel, false) }
          }
        }
        else -> {
          Pill(vt("Save backup & log out"), filled = true) {
            busy = true
            withLongRunningApi {
              try {
                if (m.chatRunning.value == true) stopChatAsync(m)
                val (path, errors) = exportChatArchive(m, null, mutableStateOf(null))
                if (errors.isNotEmpty()) Log.w(TAG, "Vibe log out: archive warnings: $errors")
                archive = path
                save.launch(path.substringAfterLast(File.separator))
              } catch (e: Throwable) {
                busy = false
                AlertManager.shared.showAlertMsg("Couldn't make the backup", e.message ?: e.toString())
                restartChat()
              }
            }
          }
        }
      }
      Spacer(Modifier.height(4.dp))
      if (!busy) Pill(vt("Cancel"), filled = false) { close() }
    }
  }

  /** "Log in" on the welcome screen: pick your backup file and Vibe X opens with your chats. */
  @Composable
  fun LogInButton(modifier: Modifier = Modifier) {
    var busy by remember { mutableStateOf(false) }
    val pick = rememberFileChooserLauncher(true) { from: URI? ->
      if (from == null) return@rememberFileChooserLauncher
      AlertManager.shared.showAlertDialog(
        title = vt("Log in with this backup?"),
        text = "Vibe X opens with the chats in this file. Use your newest backup: an older one can break your conversations. " +
            "You'll be asked for its passphrase.",
        confirmText = vt("Log in"),
        onConfirm = {
          busy = true
          withLongRunningApi {
            val m = chatModel
            runCatching { if (m.chatRunning.value == true) stopChatAsync(m) }
            val progress = mutableStateOf(false)
            val ok = importArchive(from, mutableStateOf(0 to 0L), progress, false)
            busy = false
            if (ok) {
              m.controller.appPrefs.chatStopped.set(false)
              m.controller.appPrefs.onboardingStage.set(OnboardingStage.OnboardingComplete)
              AlertManager.shared.hideAllAlerts()
              showToast(vt("Logged in. Opening your chats…"))
              vibeRestartApp()
            }
          }
        }
      )
    }
    Box(
      modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = !busy) { withLongRunningApi { pick.launch("application/zip") } }
        .padding(horizontal = 12.dp, vertical = 8.dp),
      contentAlignment = Alignment.Center
    ) {
      if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = VibeColors.blurple, strokeWidth = 2.dp)
      else Text(vt("Log in"), color = Color(0xFFB9BFFF), fontWeight = FontWeight.SemiBold)
    }
  }

  @Composable
  private fun Pill(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
      Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp))
        .then(if (filled) Modifier.background(VibeColors.blurple) else Modifier.border(1.dp, VibeColors.divider(), RoundedCornerShape(25.dp)))
        .clickable(onClick = onClick),
      contentAlignment = Alignment.Center
    ) {
      Text(text, color = if (filled) Color.White else VibeColors.onSurface(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
  }
}
