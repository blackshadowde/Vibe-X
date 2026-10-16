package chat.simplex.common.views.vibe

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.work.*
import chat.simplex.common.platform.androidAppContext
import java.io.File
import java.net.URI
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Composable
actual fun rememberVibeBackupPicker(onPicked: (String?) -> Unit): () -> Unit {
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { onPicked(it?.toString()) }
  return { runCatching { launcher.launch("vibe-x-backup.zip") } }
}

@Composable
actual fun rememberVibeRestorePicker(onPicked: (URI?) -> Unit): () -> Unit {
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { onPicked(it?.let { u -> URI(u.toString()) }) }
  return { runCatching { launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) } }
}

actual fun vibeBackupKeepAccess(uri: String) {
  androidAppContext.contentResolver.takePersistableUriPermission(
    Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
  )
}

actual fun vibeBackupWrite(from: File, to: String) {
  val cr = androidAppContext.contentResolver
  val u = Uri.parse(to)
  // "wt" empties the old backup first; some providers only know "w"
  val out = runCatching { cr.openOutputStream(u, "wt") }.getOrNull() ?: cr.openOutputStream(u, "w")
    ?: throw IllegalStateException("The backup file can't be opened. Choose where to keep it again.")
  out.use { o -> from.inputStream().use { it.copyTo(o, 64 * 1024) } }
}

actual fun vibeBackupLocationName(uri: String): String? {
  val u = Uri.parse(uri)
  val name = androidAppContext.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
    if (c.moveToFirst()) c.getString(0) else null
  } ?: return null
  val where = when {
    u.authority?.contains("com.google.android.apps.docs") == true -> "Google Drive"
    u.authority?.contains("onedrive") == true || u.authority?.contains("skydrive") == true -> "OneDrive"
    u.authority?.contains("dropbox") == true -> "Dropbox"
    u.authority?.contains("externalstorage") == true || u.authority?.contains("media") == true || u.authority?.contains("downloads") == true -> "This phone"
    else -> null
  }
  return if (where != null) "$where · $name" else name
}

private const val WORK = "vibe_backup"

actual fun vibeBackupSchedule(days: Int, wifiOnly: Boolean) {
  val wm = WorkManager.getInstance(androidAppContext)
  if (days <= 0) {
    wm.cancelUniqueWork(WORK)
    return
  }
  // first run at the next 2 AM
  val now = Calendar.getInstance()
  val next = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 2); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
  }
  val constraints = Constraints.Builder()
    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
    .setRequiresBatteryNotLow(true)
    .build()
  val req = PeriodicWorkRequestBuilder<VibeBackupWorker>(days.toLong(), TimeUnit.DAYS)
    .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS)
    .setConstraints(constraints)
    .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
    .build()
  wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
}

class VibeBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result =
    try {
      if (VibeBackup.runScheduled()) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.success()
    } catch (e: Throwable) {
      if (runAttemptCount < 3) Result.retry() else Result.success()
    }
}
