package chat.simplex.common.views.vibe

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import chat.simplex.common.platform.androidAppContext

actual fun vibeLinksOpenHere(): Boolean? {
  if (Build.VERSION.SDK_INT < 31) return null
  return runCatching {
    val dvm = androidAppContext.getSystemService(android.content.pm.verify.domain.DomainVerificationManager::class.java) ?: return null
    val state = dvm.getDomainVerificationUserState(androidAppContext.packageName) ?: return null
    if (!state.isLinkHandlingAllowed) return false
    val map = state.hostToStateMap
    val host = map["simplex.chat"] ?: return null
    host != android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_NONE
  }.getOrNull()
}

actual fun vibeOpenLinkSettings() {
  runCatching {
    val intent = if (Build.VERSION.SDK_INT >= 31)
      Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:" + androidAppContext.packageName))
    else
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + androidAppContext.packageName))
    androidAppContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}
