package chat.simplex.common.views.helpers

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layoutId
import dev.icerock.moko.resources.compose.painterResource
import dev.icerock.moko.resources.compose.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.model.LinkPreview
import chat.simplex.common.model.NetworkProxyAuth
import chat.simplex.common.platform.*
import chat.simplex.common.ui.theme.*
import chat.simplex.common.views.chat.chatViewScrollState
import chat.simplex.common.views.chat.item.CHAT_IMAGE_LAYOUT_ID
import chat.simplex.common.views.chat.item.imageViewFullWidth
import chat.simplex.res.MR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URL
import java.util.UUID

private const val OG_SELECT_QUERY = "meta[property^=og:]"
private const val ICON_SELECT_QUERY = "link[rel^=icon],link[rel^=apple-touch-icon],link[rel^=shortcut icon]"
private val IMAGE_SUFFIXES = listOf(".jpg", ".png", ".ico", ".webp", ".gif")
private const val PREVIEW_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
private const val CRAWLER_UA = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
// Apple sends request with special user-agent which handled differently by X.com (gives the video poster)
private const val X_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_1) AppleWebKit/601.2.4 (KHTML, like Gecko) Version/9.0.1 Safari/601.2.4 facebookexternalhit/1.1 Facebot Twitterbot/1.0"
/** A see-through 1x1 picture, for links without one (the card then shows no picture). */
const val NO_PREVIEW_IMAGE = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="

// Authenticator.setDefault is process-global. The mutex serializes preview fetches
// so concurrent calls cannot clobber each other's authenticator, and so the
// snapshot/restore in getLinkPreview is race-free.
private val previewMutex = Mutex()

/** Sets up the SOCKS proxy (and its credentials) for a preview fetch; call under [previewMutex]. */
private fun previewProxy(): Proxy? {
  val useSocksProxy = appPrefs.networkUseSocksProxy.get()
  if (!useSocksProxy) {
    Authenticator.setDefault(null)
    return null
  }
  val networkProxy = appPrefs.networkProxy.get()
  val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(networkProxy.host, networkProxy.port))
  val (authUser, authPass) = when (networkProxy.auth) {
    NetworkProxyAuth.USERNAME ->
      if (networkProxy.username.isNotEmpty() && networkProxy.password.isNotEmpty())
        networkProxy.username to networkProxy.password
      else
        null to null
    // Per-call random credentials drive Tor-style stream isolation: each
    // preview gets its own circuit, and previews don't share a circuit
    // with other unauthenticated traffic on the proxy.
    NetworkProxyAuth.ISOLATE ->
      UUID.randomUUID().toString() to UUID.randomUUID().toString()
  }
  if (authUser != null && authPass != null) {
    Authenticator.setDefault(object : Authenticator() {
      override fun getPasswordAuthentication(): PasswordAuthentication? =
        // Only respond when the SOCKS proxy itself challenges. A destination
        // server returning 401 also triggers RequestorType.SERVER; without
        // this gate, the JDK's auto-retry would post our SOCKS credentials
        // in an Authorization header to the destination.
        if (requestingHost == networkProxy.host && requestingPort == networkProxy.port)
          PasswordAuthentication(authUser, authPass.toCharArray())
        else null
    })
  } else {
    Authenticator.setDefault(null)
  }
  return proxy
}

/**
 * The page, asked for the way link-preview crawlers ask (sites like
 * Instagram and Facebook only give their titles, descriptions and pictures
 * to those), then as a phone browser if that doesn't work.
 */
private fun fetchPreviewDoc(url: String, proxy: Proxy?): org.jsoup.nodes.Document? {
  val agents = if (url.lowercase().startsWith("https://x.com/")) listOf(X_UA, CRAWLER_UA, PREVIEW_UA) else listOf(CRAWLER_UA, PREVIEW_UA)
  var fallback: org.jsoup.nodes.Document? = null
  for (ua in agents) {
    val doc = runCatching {
      Jsoup.connect(url)
        .ignoreContentType(true)
        .timeout(10000)
        .followRedirects(true)
        .proxy(proxy)
        .header("Accept-Language", "en-US,en;q=0.9")
        .userAgent(ua)
        .execute()
        .parse()
    }.getOrNull() ?: continue
    if (doc.selectFirst("meta[property=\"og:title\"], meta[name=\"og:title\"], meta[name=\"twitter:title\"], meta[property=\"twitter:title\"]") != null) return doc
    if (fallback == null) fallback = doc
  }
  return fallback
}

private fun org.jsoup.nodes.Document.meta(key: String): String? =
  selectFirst("meta[property=\"$key\"], meta[name=\"$key\"]")?.attr("content")?.trim()?.ifEmpty { null }

private fun org.jsoup.nodes.Document.previewImageUri(): String? =
  meta("og:image") ?: meta("og:image:url") ?: meta("og:image:secure_url") ?: meta("twitter:image") ?: meta("twitter:image:src")

private fun downloadPreviewImage(imageUri: String, proxy: Proxy?): androidx.compose.ui.graphics.ImageBitmap? = try {
  val conn = URL(imageUri).openConnection(proxy ?: Proxy.NO_PROXY)
  conn.connectTimeout = 15_000
  conn.readTimeout = 15_000
  conn.setRequestProperty("User-Agent", CRAWLER_UA)
  conn.getInputStream().use(::loadImageBitmap)
} catch (e: Exception) {
  Log.d(TAG, "preview image failed: ${e.message}")
  null
}

suspend fun getLinkPreview(url: String): LinkPreview? {
  return withContext(Dispatchers.IO) {
    previewMutex.withLock {
      try {
        try {
          val title: String?
          val u = kotlin.runCatching { URL(url) }.getOrNull() ?: return@withLock null
          val proxy = previewProxy()
          var description = ""
          var imageUri = when {
            IMAGE_SUFFIXES.any { u.path.lowercase().endsWith(it) } -> {
              title = u.path.substringAfterLast("/")
              url
            }
            else -> {
              val doc = fetchPreviewDoc(url, proxy) ?: return@withLock null
              title = doc.meta("og:title") ?: doc.meta("twitter:title") ?: doc.title().trim().ifEmpty { null }
              description = (doc.meta("og:description") ?: doc.meta("twitter:description") ?: doc.meta("description") ?: "").take(300)
              doc.previewImageUri()
                ?: doc.select(ICON_SELECT_QUERY).firstOrNull { it.attr("rel").contains("icon") }?.attr("href")
                ?: "/favicon.ico"
            }
          }
          var image: String? = null
          if (imageUri != null) {
            imageUri = normalizeImageUri(u, imageUri)
            image = downloadPreviewImage(imageUri, proxy)?.let { runCatching { resizeImageToStrSize(it, maxDataSize = 14000) }.getOrNull() }
          }
          // Vibe X: a card even when the site has no picture (or it won't load)
          return@withLock LinkPreview(url, title?.ifBlank { null } ?: u.host.removePrefix("www."), description, image ?: NO_PREVIEW_IMAGE)
        } catch (e: Exception) {
          e.printStackTrace()
        }
        return@withLock null
      } finally {
        Authenticator.setDefault(null)
      }
    }
  }
}

/** What Vibe X shows in a link card: fetched on this phone, with a sharper picture than fits in a message. */
class VibeLinkInfo(
  val url: String,
  val title: String?,
  val description: String?,
  val site: String?,
  val image: androidx.compose.ui.graphics.ImageBitmap?,
)

suspend fun vibeFetchLinkInfo(url: String): VibeLinkInfo? {
  return withContext(Dispatchers.IO) {
    previewMutex.withLock {
      try {
        val u = kotlin.runCatching { URL(url) }.getOrNull() ?: return@withLock null
        val proxy = previewProxy()
        if (IMAGE_SUFFIXES.any { u.path.lowercase().endsWith(it) }) {
          val img = downloadPreviewImage(url, proxy) ?: return@withLock null
          return@withLock VibeLinkInfo(url, null, null, u.host.removePrefix("www."), shrinkPreview(img))
        }
        val doc = fetchPreviewDoc(url, proxy) ?: return@withLock null
        val title = doc.meta("og:title") ?: doc.meta("twitter:title") ?: doc.title().trim().ifEmpty { null }
        val description = doc.meta("og:description") ?: doc.meta("twitter:description") ?: doc.meta("description")
        val site = doc.meta("og:site_name")
        val image = doc.previewImageUri()?.let { downloadPreviewImage(normalizeImageUri(u, it), proxy) }?.let(::shrinkPreview)
        if (title == null && description == null && image == null) return@withLock null
        VibeLinkInfo(url, title, description?.take(400), site, image)
      } catch (e: Exception) {
        Log.d(TAG, "vibeFetchLinkInfo failed: ${e.message}")
        null
      } finally {
        Authenticator.setDefault(null)
      }
    }
  }
}

private fun shrinkPreview(b: androidx.compose.ui.graphics.ImageBitmap): androidx.compose.ui.graphics.ImageBitmap {
  val max = 900
  if (b.width <= max && b.height <= max) return b
  val s = max.toFloat() / maxOf(b.width, b.height)
  return runCatching { b.scale(maxOf(1, (b.width * s).toInt()), maxOf(1, (b.height * s).toInt())) }.getOrDefault(b)
}

@Composable
fun ComposeLinkView(linkPreview: LinkPreview?, cancelPreview: () -> Unit, cancelEnabled: Boolean) {
  val sentColor = MaterialTheme.appColors.sentMessage
  Row(
    Modifier.fillMaxWidth().padding(top = 8.dp).background(sentColor),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (linkPreview == null) {
      Box(
        Modifier.fillMaxWidth().weight(1f).height(60.dp).padding(start = 16.dp),
        contentAlignment = Alignment.CenterStart
      ) {
        CircularProgressIndicator(
          Modifier.size(16.dp),
          color = MaterialTheme.colors.secondary,
          strokeWidth = 2.dp
        )
      }
    } else {
      val imageBitmap = base64ToBitmap(linkPreview.image)
      Image(
        imageBitmap,
        stringResource(MR.strings.image_descr_link_preview),
        modifier = Modifier.width(80.dp).height(60.dp).padding(end = 8.dp)
      )
      Column(Modifier.fillMaxWidth().weight(1F)) {
        Text(linkPreview.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          linkPreview.uri, maxLines = 1, overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.body2
        )
      }
    }
    if (cancelEnabled) {
      IconButton(onClick = cancelPreview, modifier = Modifier.padding(0.dp)) {
        Icon(
          painterResource(MR.images.ic_close),
          contentDescription = stringResource(MR.strings.icon_descr_cancel_link_preview),
          tint = MaterialTheme.colors.primary,
          modifier = Modifier.padding(10.dp)
        )
      }
    }
  }
}

@Composable
fun ChatItemLinkView(linkPreview: LinkPreview, showMenu: State<Boolean>, onLongClick: () -> Unit) {
  val image = remember(linkPreview.image) { base64ToBitmap(linkPreview.image) }
  Column(
    Modifier
    .layoutId(CHAT_IMAGE_LAYOUT_ID)
    .width(if (image.width * 0.97 <= image.height) imageViewFullWidth() * 0.75f else DEFAULT_MAX_IMAGE_WIDTH)
  ) {
    val blurred = remember { mutableStateOf(appPrefs.privacyMediaBlurRadius.get() > 0) }
    Image(
      image,
      stringResource(MR.strings.image_descr_link_preview),
      modifier = Modifier
        .fillMaxWidth()
        .desktopModifyBlurredState(blurred, showMenu)
        .privacyBlur(true, image, blurred, chatViewScrollState.collectAsState(), onLongClick = onLongClick),
      contentScale = ContentScale.FillWidth,
    )
    Column(Modifier.padding(top = 6.dp).padding(horizontal = 12.dp)) {
      Text(linkPreview.title, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp, modifier = Modifier.padding(bottom = 4.dp))
      if (linkPreview.description != "") {
        Text(linkPreview.description, maxLines = 12, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, lineHeight = 20.sp)
      }
      Text(linkPreview.uri, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colors.secondary)
    }
  }
}

private fun normalizeImageUri(u: URL, imageUri: String) = when {
  !imageUri.lowercase().startsWith("http") -> {
    "${u.protocol}://${u.host}" +
        if (imageUri.startsWith("/"))
          imageUri
        else
        // When an icon is used as an image with relative href path like: site=site.com, <link rel="icon" href="icon.png">
          if (u.path.endsWith("/")) u.path + imageUri
          else u.path.substringBeforeLast("/") + "/$imageUri"
  }
  else -> imageUri
}

/*fun normalizeImageUriTest() {
  val expect = mapOf<Pair<URL, String>, String>(
    URL("https://example.com") to "icon.png" to "https://example.com/icon.png",
    URL("https://example.com/") to "icon.png" to "https://example.com/icon.png",
    URL("https://example.com/") to "/icon.png" to "https://example.com/icon.png",
    URL("https://example.com") to "assets/images/favicon.png" to "https://example.com/assets/images/favicon.png",
    URL("https://example.com/") to "assets/images/favicon.png" to "https://example.com/assets/images/favicon.png",
    URL("https://example.com/dir") to "/favicon.png" to "https://example.com/favicon.png",
    URL("https://example.com/dir/") to "favicon.png" to "https://example.com/dir/favicon.png",
    URL("https://example.com/dir/") to "/favicon.png" to "https://example.com/favicon.png",
    URL("https://example.com/dir/page") to "favicon.png" to "https://example.com/dir/favicon.png",
    URL("https://example.com/abcde.gif") to "https://example.com/abcde.gif" to "https://example.com/abcde.gif",
    URL("https://example.com/abcde.gif?a=b") to "https://example.com/abcde.gif?a=b" to "https://example.com/abcde.gif?a=b",
  )
  expect.forEach {
    Log.d(TAG, "Image URI ${normalizeImageUri(it.key.first, it.key.second)} == ${normalizeImageUri(it.key.first, it.key.second) == it.value}")
  }
}*/

@Preview/*(
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  showBackground = true,
  name = "ChatItemLinkView (Dark Mode)"
)*/
@Composable
fun PreviewChatItemLinkView() {
  SimpleXTheme {
    ChatItemLinkView(LinkPreview.sampleData, remember { mutableStateOf(false) }) {}
  }
}

@Preview/*(
  uiMode = Configuration.UI_MODE_NIGHT_YES,
  showBackground = true,
  name = "ComposeLinkView (Dark Mode)"
)*/
@Composable
fun PreviewComposeLinkView() {
  SimpleXTheme {
    ComposeLinkView(LinkPreview.sampleData, cancelPreview = { -> }, true)
  }
}

@Preview
@Composable
fun PreviewComposeLinkViewLoading() {
  SimpleXTheme {
    ComposeLinkView(null, cancelPreview = { -> }, true)
  }
}
