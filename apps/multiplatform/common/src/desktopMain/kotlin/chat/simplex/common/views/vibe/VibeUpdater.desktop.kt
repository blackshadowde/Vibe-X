package chat.simplex.common.views.vibe

import chat.simplex.common.platform.tmpDir
import java.io.File

actual val vibeCanSelfUpdate: Boolean = false

actual fun vibeDeviceAbi(): String = System.getProperty("os.arch") ?: ""

actual fun vibeUpdateDir(): File = File(tmpDir, "vibe-update")

actual fun vibeCanInstallApk(): Boolean = false

actual fun vibeAllowInstallApk() {}

actual fun vibeInstallApk(file: File): Boolean = false
