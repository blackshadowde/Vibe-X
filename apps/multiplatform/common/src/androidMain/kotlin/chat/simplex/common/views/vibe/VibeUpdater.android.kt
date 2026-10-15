package chat.simplex.common.views.vibe

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import chat.simplex.common.helpers.APPLICATION_ID
import chat.simplex.common.platform.Log
import chat.simplex.common.platform.TAG
import chat.simplex.common.platform.androidAppContext
import java.io.File

private const val APK_MIME = "application/vnd.android.package-archive"

actual val vibeCanSelfUpdate: Boolean = true

actual fun vibeDeviceAbi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

// inside the app's files, which its file provider shares (with the installer)
actual fun vibeUpdateDir(): File = File(androidAppContext.filesDir, "vibe-update")

actual fun vibeCanInstallApk(): Boolean =
  runCatching { androidAppContext.packageManager.canRequestPackageInstalls() }.getOrDefault(true)

actual fun vibeAllowInstallApk() {
  runCatching {
    androidAppContext.startActivity(
      Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + androidAppContext.packageName))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
  }.onFailure { Log.e(TAG, "Vibe updater: ${it.message}") }
}

actual fun vibeInstallApk(file: File): Boolean = runCatching {
  val uri = FileProvider.getUriForFile(androidAppContext, "$APPLICATION_ID.provider", file)
  androidAppContext.startActivity(
    Intent(Intent.ACTION_VIEW)
      .setDataAndType(uri, APK_MIME)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
  )
}.onFailure { Log.e(TAG, "Vibe updater: ${it.message}") }.isSuccess
