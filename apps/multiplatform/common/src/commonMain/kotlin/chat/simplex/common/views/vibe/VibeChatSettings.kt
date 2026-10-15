package chat.simplex.common.views.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatItem
import chat.simplex.common.platform.*
import chat.simplex.common.views.helpers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Copies a received photo or video to the phone's gallery (Pictures / Movies › Vibe X). */
expect fun vibeSaveToGallery(item: ChatItem)
/** Folders that only hold copies Vibe X can make again (emoji animations, previews, updates). */
expect fun vibeCacheDirs(): List<File>
/** Starts keeping a crash report for the next start. */
expect fun vibeWatchCrashes()
/** What crashed (or froze) last time, once; null if nothing did. */
expect fun vibeTakeCrashReport(): String?

/** Settings › Chat: low data mode, saving media, storage. */
@Composable
fun VibeChatSettingsView() {
  val low = appPreferences.vibeLowData.state.value
  val save = appPreferences.vibeSaveMedia.state.value
  ColumnWithScrollBar {
    Text("Chat", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 16.dp), fontSize = 28.sp, color = VibeColors.onSurface())
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(vibeCard())) {
      SwitchRow(VibeIcons.DataSaver, "Low data mode", "Photos and videos load only when you tap them; no animated emoji", low) {
        appPreferences.vibeLowData.set(it)
      }
      Divider(color = VibeColors.divider())
      SwitchRow(VibeIcons.Download, "Save media to gallery", "Photos and videos you receive go to Pictures › Vibe X", save) {
        appPreferences.vibeSaveMedia.set(it)
      }
      Divider(color = VibeColors.divider())
      Row(
        Modifier.fillMaxWidth().clickable { ModalManager.start.showModal(settings = true) { VibeStorageView() } }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(VibeIcons.Storage, null, Modifier.size(24.dp), tint = VibeColors.onSurface())
        Spacer(Modifier.width(16.dp))
        Text("Storage & cache", Modifier.weight(1f), fontSize = 16.sp, color = VibeColors.onSurface())
      }
    }
    Spacer(Modifier.height(24.dp))
  }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth().clickable { VibeHaptics.medium(); set(!on) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, Modifier.size(24.dp), tint = VibeColors.onSurface())
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
      Text(title, fontSize = 16.sp, color = VibeColors.onSurface())
      Text(sub, fontSize = 13.sp, color = VibeColors.onVariant())
    }
    Switch(
      checked = on, onCheckedChange = { VibeHaptics.medium(); set(it) },
      colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VibeColors.blurple, checkedTrackAlpha = 1f)
    )
  }
}

private fun dirSize(dir: File): Long = runCatching { dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

/** Storage & cache, like Telegram's: what Vibe X keeps on this phone, and clearing it. */
@Composable
fun VibeStorageView() {
  val scope = rememberCoroutineScope()
  var files by remember { mutableStateOf<Pair<Int, Long>?>(null) }
  var cache by remember { mutableStateOf<Long?>(null) }
  var db by remember { mutableStateOf<Long?>(null) }
  fun measure() = scope.launch {
    withContext(Dispatchers.IO) {
      files = directoryFileCountAndSize(appFilesDir.absolutePath)
      cache = (vibeCacheDirs() + tmpDir).sumOf { dirSize(it) }
      db = runCatching { dataDir.listFiles()?.filter { it.isFile && it.name.endsWith(".db") }?.sumOf { it.length() } }.getOrNull() ?: 0L
    }
  }
  LaunchedEffect(Unit) { measure() }
  val total = (files?.second ?: 0) + (cache ?: 0) + (db ?: 0)
  ColumnWithScrollBar {
    Text("Storage & cache", Modifier.padding(start = 20.dp, top = 8.dp, bottom = 16.dp), fontSize = 28.sp, color = VibeColors.onSurface())
    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(vibeCard()).padding(18.dp)) {
      Text(formatBytes(total), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      Text("used by Vibe X on this phone", color = VibeColors.onVariant())
      Spacer(Modifier.height(14.dp))
      // a bar split into the three parts
      val parts = listOf((db ?: 0L) to VibeColors.blurple, (files?.second ?: 0L) to VibeColors.green, (cache ?: 0L) to Color(0xFFFAA61A))
      Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(VibeColors.divider())) {
        for ((size, color) in parts) if (size > 0 && total > 0) Box(Modifier.weight(size.toFloat() / total).fillMaxHeight().background(color))
      }
      Spacer(Modifier.height(14.dp))
      Legend(VibeColors.blurple, "Messages (database)", db)
      Legend(VibeColors.green, "Photos, videos, files" + (files?.first?.let { " · $it" } ?: ""), files?.second)
      Legend(Color(0xFFFAA61A), "Cache", cache)
    }
    Spacer(Modifier.height(16.dp))
    Column(Modifier.padding(horizontal = 16.dp)) {
      Button("Clear cache", "Emoji animations, previews and downloaded updates. They come back when needed.") {
        scope.launch {
          withContext(Dispatchers.IO) {
            vibeCacheDirs().forEach { runCatching { it.listFiles()?.forEach { f -> f.deleteRecursively() } } }
          }
          VibeHaptics.success()
          showToast("Cache cleared")
          measure()
        }
      }
      Spacer(Modifier.height(10.dp))
      Button("Delete all photos, videos and files", "From every chat, on this phone only. Messages stay.", red = true) {
        AlertManager.shared.showAlertDialog(
          title = "Delete all files?",
          text = "Every photo, video, voice note and file in your chats will be removed from this phone. This can't be undone.",
          confirmText = "Delete",
          destructive = true,
          onConfirm = {
            deleteAppFiles()
            measure()
          }
        )
      }
    }
    Spacer(Modifier.height(24.dp))
  }
}

@Composable
private fun Legend(color: Color, label: String, size: Long?) {
  Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
    Spacer(Modifier.width(10.dp))
    Text(label, Modifier.weight(1f), color = VibeColors.onSurface())
    Text(size?.let { formatBytes(it) } ?: "…", fontWeight = FontWeight.Bold, color = VibeColors.onSurface())
  }
}

@Composable
private fun Button(title: String, sub: String, red: Boolean = false, onClick: () -> Unit) {
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, VibeColors.divider(), RoundedCornerShape(20.dp))
      .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (red) VibeColors.red else VibeColors.onSurface())
    Text(sub, fontSize = 13.sp, color = VibeColors.onVariant())
  }
}

/* ------------------------------------------------------------ crashes --- */

/** If Vibe X crashed or froze last time, say so once and offer the report to copy. */
object VibeCrash {
  fun checkOnStart() {
    runCatching { vibeWatchCrashes() }
    val report = runCatching { vibeTakeCrashReport() }.getOrNull() ?: return
    VibeSheets.show(background = { vibePage() }) { close -> Sheet(report, close) }
  }

  @Composable
  private fun Sheet(report: String, close: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
      Text("😵", fontSize = 36.sp)
      Spacer(Modifier.height(6.dp))
      Text("Vibe X stopped last time", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = VibeColors.onSurface())
      Text(
        "Sorry about that. If you send this report to the developer, the problem can be found and fixed. It has no messages or contacts in it.",
        Modifier.padding(top = 6.dp, bottom = 12.dp), color = VibeColors.onVariant(), lineHeight = 20.sp
      )
      Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(16.dp)).background(vibeCard()).verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = VibeColors.onSurface())
      }
      Spacer(Modifier.height(14.dp))
      Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(VibeColors.blurple)
          .clickable {
            clipboard.setText(AnnotatedString(report))
            showToast("Report copied")
            close()
          },
        contentAlignment = Alignment.Center
      ) { Text("Copy report", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
          "Report on GitHub", Modifier.clip(RoundedCornerShape(12.dp)).clickable {
            clipboard.setText(AnnotatedString(report))
            runCatching { uri.openUri("https://github.com/${VibeUpdater.REPO}/issues/new") }
            close()
          }.padding(12.dp), color = VibeColors.blurple, fontWeight = FontWeight.SemiBold
        )
        Text("Dismiss", Modifier.clip(RoundedCornerShape(12.dp)).clickable { close() }.padding(12.dp), color = VibeColors.onVariant(), fontWeight = FontWeight.SemiBold)
      }
    }
  }
}
