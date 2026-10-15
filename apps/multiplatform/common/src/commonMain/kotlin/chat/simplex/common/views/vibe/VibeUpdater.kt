package chat.simplex.common.views.vibe

import androidx.compose.runtime.mutableStateOf
import chat.simplex.common.BuildConfigCommon
import chat.simplex.common.model.json
import chat.simplex.common.platform.Log
import chat.simplex.common.platform.TAG
import chat.simplex.common.platform.appPreferences
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/** A Vibe X release on GitHub. */
@Serializable
data class VibeRelease(
  val tag: String,
  val version: String, // e.g. 7.0.4
  val build: Int, // the build workflow's run number
  val apkUrl: String,
  val apkName: String,
  val size: Long, // bytes, 0 if unknown
  val notes: List<String>,
  val pageUrl: String,
) {
  val label: String get() = "$version (build $build)"
}

/** Whether this platform can download and install updates itself (Android). */
expect val vibeCanSelfUpdate: Boolean
/** The phone's main ABI, to pick the right APK (arm64-v8a / armeabi-v7a). */
expect fun vibeDeviceAbi(): String
/** Where the downloaded update is kept. */
expect fun vibeUpdateDir(): File
/** True when Android lets Vibe X install updates. */
expect fun vibeCanInstallApk(): Boolean
/** Opens the setting that lets Vibe X install updates. */
expect fun vibeAllowInstallApk()
/** Hands the APK to Android's installer; false if it couldn't be opened. */
expect fun vibeInstallApk(file: File): Boolean

/**
 * In-app updater, as in Vibe: asks GitHub for the latest release of Vibe X,
 * shows what's new, downloads the APK for this phone and opens Android's
 * installer. Goes through your SOCKS proxy (Tor) when you use one.
 */
object VibeUpdater {
  const val REPO = "blackshadowde/Vibe-X"
  const val RELEASES_URL = "https://github.com/$REPO/releases"
  private const val API = "https://api.github.com/repos/$REPO/releases/latest"
  private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

  /** This app's version and build. */
  val version: String get() = BuildConfigCommon.ANDROID_VERSION_NAME
  val build: Int get() = BuildConfigCommon.VIBE_BUILD
  val versionLabel: String get() = if (build > 0) "$version (build $build)" else version

  /** A newer release waiting to be installed (drives the red dots). */
  val pending = mutableStateOf<VibeRelease?>(null)
  /** The latest release, newer or not (its notes show in Updates). */
  val latest = mutableStateOf<VibeRelease?>(null)
  val checking = mutableStateOf(false)
  val lastChecked = mutableStateOf<Long?>(null)

  enum class Stage { Info, Downloading, NeedsPermission, Installing, Failed }
  val stage = mutableStateOf(Stage.Info)
  /** 0..1 while downloading, null before the size is known. */
  val progress = mutableStateOf<Float?>(null)
  val error = mutableStateOf<String?>(null)

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private var job: Job? = null
  private var downloaded: File? = null
  private var loaded = false

  /** Restores the last known pending update, so the red dot survives a restart. */
  fun load() {
    if (loaded) return
    loaded = true
    runCatching {
      val at = appPreferences.vibeUpdateCheckedAt.get()
      if (at > 0) lastChecked.value = at
      val raw = appPreferences.vibeUpdatePending.get() ?: return
      val r = json.decodeFromString(VibeRelease.serializer(), raw)
      if (isNewer(r)) pending.value = r else appPreferences.vibeUpdatePending.set(null)
    }
  }

  fun isNewer(r: VibeRelease): Boolean = r.build > build

  /** Asks GitHub now. Throws when it can't be reached. */
  suspend fun refresh(): VibeRelease? {
    load()
    checking.value = true
    try {
      val r = withContext(Dispatchers.IO) { fetchLatest() }
      val now = System.currentTimeMillis()
      lastChecked.value = now
      appPreferences.vibeUpdateCheckedAt.set(now)
      latest.value = r
      val newer = r?.takeIf { isNewer(it) }
      pending.value = newer
      appPreferences.vibeUpdatePending.set(newer?.let { json.encodeToString(VibeRelease.serializer(), it) })
      if (newer == null) withContext(Dispatchers.IO) { runCatching { vibeUpdateDir().listFiles()?.forEach { it.delete() } } }
      return newer
    } finally {
      checking.value = false
    }
  }

  /** Quiet check when the app opens: at most every 6 hours; shows the update unless you said Later. */
  fun maybeCheck() {
    if (!vibeCanSelfUpdate) return
    load()
    val now = System.currentTimeMillis()
    if (now - appPreferences.vibeUpdateCheckedAt.get() < CHECK_EVERY_MS) return
    scope.launch {
      try {
        val r = refresh() ?: return@launch
        val laterTag = appPreferences.vibeUpdateLaterTag.get()
        if (laterTag == r.tag && System.currentTimeMillis() < appPreferences.vibeUpdateLaterUntil.get()) return@launch
        withContext(Dispatchers.Main) { showSheet(r) }
      } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Log.w(TAG, "Vibe update check failed: ${e.message}")
      }
    }
  }

  /** Not now: don't bring this update up by itself for a day. */
  fun later(r: VibeRelease) {
    appPreferences.vibeUpdateLaterTag.set(r.tag)
    appPreferences.vibeUpdateLaterUntil.set(System.currentTimeMillis() + 24 * 60 * 60 * 1000L)
  }

  /** The download / install pane for [r]. */
  fun showSheet(r: VibeRelease) {
    if (stage.value != Stage.Downloading && stage.value != Stage.Installing) {
      stage.value = Stage.Info
      error.value = null
    }
    VibeSheets.show { close -> VibeUpdateSheet(r, close) }
  }

  fun startDownload(r: VibeRelease) {
    job?.cancel()
    stage.value = Stage.Downloading
    progress.value = null
    error.value = null
    job = scope.launch {
      try {
        val dir = vibeUpdateDir().apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, r.apkName)
        val part = File(dir, r.apkName + ".part")
        val conn = open(r.apkUrl, readTimeout = 30_000)
        try {
          if (conn.responseCode !in 200..299) throw Exception("GitHub answered ${conn.responseCode}")
          val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.size
          var done = 0L
          var shown = -1
          conn.inputStream.use { input ->
            part.outputStream().use { out ->
              val buf = ByteArray(64 * 1024)
              while (true) {
                ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                done += n
                if (total > 0) {
                  val pct = (done * 100 / total).toInt()
                  if (pct != shown) {
                    shown = pct
                    progress.value = (done.toFloat() / total).coerceIn(0f, 1f)
                  }
                }
              }
            }
          }
          if (total > 0 && done != total) throw Exception("Download incomplete")
        } finally {
          conn.disconnect()
        }
        if (!part.renameTo(file)) throw Exception("Could not save the update")
        downloaded = file
        progress.value = 1f
        withContext(Dispatchers.Main) { install() }
      } catch (e: Throwable) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Vibe update download failed: ${e.message}")
        stage.value = Stage.Failed
        error.value = "The download failed. Check your connection and try again."
      }
    }
  }

  fun install() {
    val f = downloaded ?: return
    if (!vibeCanInstallApk()) {
      stage.value = Stage.NeedsPermission
      return
    }
    stage.value = Stage.Installing
    if (!vibeInstallApk(f)) {
      stage.value = Stage.Failed
      error.value = "Could not open the installer."
    }
  }

  fun cancel() {
    job?.cancel()
    job = null
    stage.value = Stage.Info
    progress.value = null
  }

  /** After an update, once: "What's new in Vibe X" with this build's release notes. */
  fun maybeShowWhatsNew() {
    if (build <= 0) return
    val seen = appPreferences.vibeSeenBuild.get()
    if (seen == 0) { appPreferences.vibeSeenBuild.set(build); return } // a fresh install: nothing to tell yet
    if (seen >= build) return
    scope.launch {
      val notes = try {
        fetchNotes("vibe-x-$version-$build")
      } catch (e: Throwable) {
        if (e is CancellationException) throw e
        return@launch // offline: try again next time
      }
      appPreferences.vibeSeenBuild.set(build)
      if (notes.isNullOrEmpty()) return@launch
      withContext(Dispatchers.Main) {
        VibeSheets.show(background = { vibePage() }) { close -> VibeWhatsNewSheet(versionLabel, notes, close) }
      }
    }
  }

  private fun fetchNotes(tag: String): List<String>? {
    val c = open("https://api.github.com/repos/$REPO/releases/tags/$tag")
    c.setRequestProperty("Accept", "application/vnd.github+json")
    try {
      if (c.responseCode == 404) return null
      if (c.responseCode != 200) throw Exception("GitHub answered ${c.responseCode}")
      val body = c.inputStream.bufferedReader().use { it.readText() }
      val j = Json.parseToJsonElement(body).jsonObject
      return notesFrom(j["body"]?.jsonPrimitive?.contentOrNull ?: "")
    } finally {
      c.disconnect()
    }
  }

  /* ------------------------------------------------------------ GitHub --- */

  private fun open(url: String, readTimeout: Int = 15_000): HttpURLConnection {
    val proxy = runCatching {
      if (appPreferences.networkUseSocksProxy.get()) {
        val p = appPreferences.networkProxy.get()
        Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(p.host.ifBlank { "localhost" }, p.port))
      } else null
    }.getOrNull()
    val c = (if (proxy != null) URL(url).openConnection(proxy) else URL(url).openConnection()) as HttpURLConnection
    c.connectTimeout = 15_000
    c.readTimeout = readTimeout
    c.instanceFollowRedirects = true
    c.setRequestProperty("User-Agent", "Vibe-X")
    return c
  }

  private fun fetchLatest(): VibeRelease? {
    val c = open(API)
    c.setRequestProperty("Accept", "application/vnd.github+json")
    val body = try {
      if (c.responseCode == 404) return null // no release yet
      if (c.responseCode != 200) throw Exception("GitHub answered ${c.responseCode}")
      c.inputStream.bufferedReader().use { it.readText() }
    } finally {
      c.disconnect()
    }
    val j = Json.parseToJsonElement(body).jsonObject
    val tag = j["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
    // vibe-x-7.0.4-57
    val m = Regex("""^vibe-x-(.+)-(\d+)$""").find(tag) ?: return null
    val version = m.groupValues[1]
    val build = m.groupValues[2].toIntOrNull() ?: return null
    val apks = (j["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
      .filter { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
    if (apks.isEmpty()) return null
    val abi = vibeDeviceAbi()
    val apk = apks.firstOrNull { it["name"]!!.jsonPrimitive.content.contains(abi) }
      ?: apks.firstOrNull { it["name"]!!.jsonPrimitive.content.contains("arm64-v8a") }
      ?: apks.first()
    return VibeRelease(
      tag = tag,
      version = version,
      build = build,
      apkUrl = apk["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null,
      apkName = apk["name"]!!.jsonPrimitive.content,
      size = apk["size"]?.jsonPrimitive?.longOrNull ?: 0L,
      notes = notesFrom(j["body"]?.jsonPrimitive?.contentOrNull ?: ""),
      pageUrl = j["html_url"]?.jsonPrimitive?.contentOrNull ?: RELEASES_URL,
    )
  }

  /** Lines of the "What's new in this update" part of the release page. */
  private fun notesFrom(body: String): List<String> {
    val start = "### What's new in this update"
    val i = body.indexOf(start)
    if (i < 0) return emptyList()
    var part = body.substring(i + start.length)
    val end = part.indexOf("\n---")
    if (end >= 0) part = part.substring(0, end)
    return part.lines().map { it.trim() }.filter { it.isNotEmpty() }
      .map { it.replaceFirst(Regex("^[-*]\\s+"), "").replace("**", "").replace("`", "") }
  }
}
