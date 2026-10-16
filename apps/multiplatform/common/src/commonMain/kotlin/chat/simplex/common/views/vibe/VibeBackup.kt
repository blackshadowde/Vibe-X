package chat.simplex.common.views.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.platform.*
import chat.simplex.common.views.database.*
import chat.simplex.common.views.helpers.*
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.*
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*

/* ---------------------------------------------------------------------------
 * Chat backup, WhatsApp style but without a Google sign-in: you pick where
 * the backup lives once (Google Drive, OneDrive or the phone) with Android's
 * own file picker, and Vibe X replaces that one file nightly or weekly.
 * ------------------------------------------------------------------------ */

/** Opens Android's "save to" picker for the backup file; gives back its address, or null. */
@Composable
expect fun rememberVibeBackupPicker(onPicked: (String?) -> Unit): () -> Unit

/** Opens Android's file picker to choose a backup to restore. */
@Composable
expect fun rememberVibeRestorePicker(onPicked: (URI?) -> Unit): () -> Unit

/** Keeps access to the picked file after restarts. */
expect fun vibeBackupKeepAccess(uri: String)

/** Writes [from] over the backup file at [to]. */
expect fun vibeBackupWrite(from: File, to: String)

/** "Google Drive · vibe-x-backup.zip", or null if the file is gone. */
expect fun vibeBackupLocationName(uri: String): String?

/** Runs the automatic backup every [days] days at night (0 = off). */
expect fun vibeBackupSchedule(days: Int, wifiOnly: Boolean)

object VibeBackup {
  val running = mutableStateOf(false)
  private val lock = kotlinx.coroutines.sync.Mutex()

  fun canBackUp(): Boolean = !appPrefs.initialRandomDBPassphrase.get()

  /**
   * Pauses the chat for a moment, exports the database, writes it over the
   * backup file and starts the chat again. Returns an error, or null.
   */
  suspend fun backUpNow(): String? {
    val to = appPreferences.vibeBackupUri.get() ?: return "Choose where to keep the backup first"
    if (!canBackUp()) return "Set a database passphrase first"
    if (!lock.tryLock()) return null
    running.value = true
    val m = chatModel
    val wasRunning = m.chatRunning.value == true
    var archive: String? = null
    val error = try {
      if (wasRunning) {
        stopChatAsync(m)
        // if the app is killed mid-backup, the chat still starts next time
        appPrefs.chatStopped.set(false)
      }
      val (path, _) = exportChatArchive(m, databaseExportDir, mutableStateOf(null))
      archive = path
      withContext(Dispatchers.IO) { vibeBackupWrite(File(path), to) }
      appPreferences.vibeBackupLast.set(System.currentTimeMillis())
      appPreferences.vibeBackupError.set(null)
      null
    } catch (e: Throwable) {
      Log.e(TAG, "Vibe backup failed: ${e.stackTraceToString()}")
      val msg = e.message ?: e.toString()
      appPreferences.vibeBackupError.set(msg)
      msg
    } finally {
      archive?.let { runCatching { File(it).delete() } }
      if (wasRunning) {
        runCatching {
          val user = m.currentUser.value
          if (user != null) m.controller.startChat(user)
          platform.androidChatStartedAfterBeingOff()
        }.onFailure { Log.e(TAG, "Vibe backup: restart failed ${it.stackTraceToString()}") }
      }
      running.value = false
      lock.unlock()
    }
    return error
  }

  /** The nightly run: waits for the chat to be ready (it may have just started for this), then backs up. */
  suspend fun runScheduled(): Boolean {
    if (appPreferences.vibeBackupUri.get() == null || appPreferences.vibeBackupEvery.get() == 0) return true
    var waited = 0
    while (!(m().controller.hasChatCtrl() && m().currentUser.value != null && m().chatRunning.value != null) && waited < 180) {
      delay(1000); waited++
    }
    if (!m().controller.hasChatCtrl() || m().currentUser.value == null) {
      appPreferences.vibeBackupError.set("Vibe X couldn't open your chats in the background. Open the app to back up.")
      return true
    }
    // not while you're in a call
    if (m().activeCall.value != null) return false
    return backUpNow() == null
  }

  private fun m() = chatModel

  fun reschedule() {
    vibeBackupSchedule(if (appPreferences.vibeBackupUri.get() == null) 0 else appPreferences.vibeBackupEvery.get(), appPreferences.vibeBackupWifi.get())
  }

  /** Replaces your chats with the ones in a backup, then restarts the app. */
  fun restore(uri: URI) {
    AlertManager.shared.showAlertDialog(
      title = vt("Restore this backup?"),
      text = vt("Your chats on this phone will be replaced by the ones in the backup. Use your most recent backup: an older one can break some chats until they're reconnected."),
      confirmText = vt("Restore"),
      destructive = true,
      onConfirm = {
        val stopped = chatModel.chatRunning.value != true
        val progress = mutableStateOf(false)
        stopChatRunBlockStartChat(stopped, mutableStateOf(appPrefs.chatLastStart.get()), progress) {
          val ok = importArchive(uri, mutableStateOf(0 to 0L), progress, false)
          if (ok) {
            appPreferences.onboardingStage.set(chat.simplex.common.views.onboarding.OnboardingStage.OnboardingComplete)
            delay(1500)
            vibeRestartApp()
          }
          ok
        }
      }
    )
  }
}

private fun whenText(ms: Long): String {
  if (ms <= 0L) return vt("Never")
  val now = Calendar.getInstance()
  val then = Calendar.getInstance().apply { timeInMillis = ms }
  val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))
  return when {
    now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) -> "${vt("Today")} $time"
    now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) == 1 -> "${vt("Yesterday")} $time"
    else -> SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(ms))
  }
}

/** Settings › Chat backup. */
@Composable
fun VibeBackupView() {
  val scope = rememberCoroutineScope()
  var uri by remember { mutableStateOf(appPreferences.vibeBackupUri.get()) }
  var every by remember { mutableStateOf(appPreferences.vibeBackupEvery.get()) }
  var wifi by remember { mutableStateOf(appPreferences.vibeBackupWifi.get()) }
  var last by remember { mutableStateOf(appPreferences.vibeBackupLast.get()) }
  var error by remember { mutableStateOf(appPreferences.vibeBackupError.get()) }
  val running = VibeBackup.running.value
  val location = remember(uri) { uri?.let { runCatching { vibeBackupLocationName(it) }.getOrNull() } }
  val canBackUp = VibeBackup.canBackUp()

  fun backUp() {
    scope.launch {
      VibeHaptics.light()
      val e = VibeBackup.backUpNow()
      last = appPreferences.vibeBackupLast.get()
      error = e
      if (e == null) { VibeHaptics.success(); showToast(vt("Backed up")) } else VibeHaptics.heavy()
    }
  }
  val pick = rememberVibeBackupPicker { picked ->
    if (picked != null) {
      runCatching { vibeBackupKeepAccess(picked) }
      appPreferences.vibeBackupUri.set(picked)
      uri = picked
      if (appPreferences.vibeBackupEvery.get() == 0) { appPreferences.vibeBackupEvery.set(1); every = 1 }
      VibeBackup.reschedule()
      backUp()
    }
  }
  val restorePick = rememberVibeRestorePicker { picked -> if (picked != null) VibeBackup.restore(picked) }

  ColumnWithScrollBar {
    Text(vt("Chat backup"), Modifier.padding(start = 20.dp, top = 8.dp, bottom = 16.dp), fontSize = 28.sp, color = VibeColors.onSurface())
    // the status card
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(vibeCard()).padding(18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(MR.images.ic_backup), null, Modifier.size(28.dp), tint = VibeColors.blurple)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(vt("Last backup"), fontSize = 13.sp, color = VibeColors.onVariant())
          Text(if (running) vt("Backing up…") else whenText(last), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = VibeColors.onSurface())
        }
      }
      Text(
        location ?: if (uri != null) vt("The backup file can't be reached. Choose it again.") else vt("Not set up yet"),
        Modifier.padding(top = 10.dp), fontSize = 14.sp, color = if (uri != null && location == null) VibeColors.red else VibeColors.onVariant()
      )
      if (error != null && !running) Text(error!!, Modifier.padding(top = 6.dp), fontSize = 13.sp, color = VibeColors.red)
      Spacer(Modifier.height(14.dp))
      if (!canBackUp) {
        Text(
          vt("Set a database passphrase first. It encrypts your backup, so no one with access to your Drive can read it."),
          fontSize = 13.5.sp, color = VibeColors.onVariant()
        )
        Spacer(Modifier.height(10.dp))
        BigButton(vt("Set passphrase"), VibeColors.blurple) {
          ModalManager.start.showModal(cardScreen = true) { DatabaseEncryptionView(chatModel, false) }
        }
      } else if (uri == null) {
        BigButton(vt("Back up to…"), VibeColors.blurple) { pick() }
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Box(Modifier.weight(1f)) { BigButton(if (running) vt("Backing up…") else vt("Back up now"), VibeColors.blurple, enabled = !running) { backUp() } }
          Box(Modifier.weight(1f)) { BigButton(vt("Change place"), null, enabled = !running) { pick() } }
        }
      }
    }
    if (uri != null && canBackUp) {
      Text(vt("AUTOMATIC BACKUP"), Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = VibeColors.onVariant())
      Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard()).padding(14.dp)) {
        Row {
          for ((label, d) in listOf(vt("Off") to 0, vt("Daily") to 1, vt("Weekly") to 7)) {
            val on = every == d
            Text(
              label,
              Modifier.padding(end = 8.dp).clip(RoundedCornerShape(16.dp))
                .then(if (on) Modifier.background(VibeColors.blurple) else Modifier.border(1.dp, VibeColors.divider(), RoundedCornerShape(16.dp)))
                .clickable { VibeHaptics.selection(); every = d; appPreferences.vibeBackupEvery.set(d); VibeBackup.reschedule() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
              color = if (on) Color.White else VibeColors.onSurface(), fontSize = 14.sp
            )
          }
        }
        Text(vt("Runs at night. The chat pauses for a few seconds while it saves."), Modifier.padding(top = 10.dp), fontSize = 13.sp, color = VibeColors.onVariant())
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).clickable { wifi = !wifi; appPreferences.vibeBackupWifi.set(wifi); VibeBackup.reschedule() }, verticalAlignment = Alignment.CenterVertically) {
          Text(vt("Only on Wi-Fi"), Modifier.weight(1f), fontSize = 16.sp, color = VibeColors.onSurface())
          androidx.compose.material.Switch(
            wifi, { wifi = it; appPreferences.vibeBackupWifi.set(it); VibeBackup.reschedule() },
            colors = androidx.compose.material.SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VibeColors.blurple, checkedTrackAlpha = 1f)
          )
        }
      }
    }
    Text(vt("RESTORE"), Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = VibeColors.onVariant())
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard()).clickable { restorePick() }.padding(16.dp)) {
      Text(vt("Restore from backup"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VibeColors.red)
      Text(vt("Pick your vibe-x-backup.zip from Drive or the phone. It replaces the chats on this phone."), Modifier.padding(top = 2.dp), fontSize = 13.sp, color = VibeColors.onVariant())
    }
    Text(
      vt("SimpleX keeps your keys only on your phone and they change as you chat, so keep backups frequent and restore only your latest one. You'll need your database passphrase to restore."),
      Modifier.padding(horizontal = 24.dp, vertical = 16.dp), fontSize = 13.sp, color = VibeColors.onVariant()
    )
    Spacer(Modifier.navigationBarsPadding().height(24.dp))
  }
}

@Composable
private fun BigButton(label: String, color: Color?, enabled: Boolean = true, onClick: () -> Unit) {
  Box(
    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
      .then(if (color != null) Modifier.background(if (enabled) color else color.copy(alpha = 0.5f)) else Modifier.border(1.dp, VibeColors.divider(), RoundedCornerShape(24.dp)))
      .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (color != null) Color.White else VibeColors.onSurface())
  }
}
