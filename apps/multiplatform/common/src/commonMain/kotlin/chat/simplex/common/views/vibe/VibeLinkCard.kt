package chat.simplex.common.views.vibe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.model.ChatItem
import chat.simplex.common.model.LinkPreview
import chat.simplex.common.platform.base64ToBitmap
import chat.simplex.common.views.helpers.VibeLinkInfo
import chat.simplex.common.views.helpers.openUriCatching
import chat.simplex.common.views.helpers.vibeFetchLinkInfo

/**
 * Link cards like Vibe's (Discord's): the site's name, the title in link
 * blue, a few lines of description, and the picture big underneath.
 *
 * SimpleX puts a small preview inside the message (made by the sender). Vibe
 * X also looks the link up on this phone, like Vibe and Discord do, which
 * gives the site's name, the full description and a sharp picture, and gives
 * a card to links that were sent without a preview.
 */
object VibeLinks {
  private val urlRegex = Regex("""https?://[^\s<>"]+[^\s<>".,;:!?)\]}']""", RegexOption.IGNORE_CASE)
  // one look-up per link per app run; null = nothing to show
  private val cache = HashMap<String, VibeLinkInfo?>()

  fun firstUrl(text: String): String? = urlRegex.find(text)?.value

  fun cached(url: String): Pair<Boolean, VibeLinkInfo?> = synchronized(cache) { cache.containsKey(url) to cache[url] }

  suspend fun info(url: String): VibeLinkInfo? {
    cached(url).let { (has, v) -> if (has) return v }
    val v = runCatching { vibeFetchLinkInfo(url) }.getOrNull()
    synchronized(cache) {
      if (cache.size > 200) cache.clear()
      cache[url] = v
    }
    return v
  }

  /** Looking links up here is part of "link previews" (and off in low data mode). */
  fun lookUpAllowed(): Boolean = appPrefs.privacyLinkPreviews.get() && !appPrefs.vibeLowData.get()

  private val names = mapOf(
    "instagram.com" to "Instagram", "youtube.com" to "YouTube", "youtu.be" to "YouTube", "x.com" to "X", "twitter.com" to "X",
    "facebook.com" to "Facebook", "fb.watch" to "Facebook", "reddit.com" to "Reddit", "github.com" to "GitHub", "wikipedia.org" to "Wikipedia",
    "spotify.com" to "Spotify", "linkedin.com" to "LinkedIn", "tiktok.com" to "TikTok", "t.me" to "Telegram", "discord.com" to "Discord",
    "amazon.in" to "Amazon", "amazon.com" to "Amazon", "flipkart.com" to "Flipkart", "medium.com" to "Medium", "threads.net" to "Threads",
  )

  fun siteName(url: String): String {
    val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return url
    return names.entries.firstOrNull { host == it.key || host.endsWith("." + it.key) }?.value ?: host
  }
}

/** The card under a message: what this phone found for the link, else the preview that came with it. */
@Composable
fun VibeAutoLinkCard(item: ChatItem, sent: LinkPreview?, onLongClick: () -> Unit) {
  val url = remember(item.id, item.content.text) { sent?.uri ?: VibeLinks.firstUrl(item.content.text) } ?: return
  // SimpleX invitation and group links have their own cards
  if (url.contains("simplex.chat/") || url.startsWith("simplex:")) {
    if (sent != null) LinkCardFromPreview(sent, onLongClick)
    return
  }
  val allowed = VibeLinks.lookUpAllowed()
  var info by remember(url) { mutableStateOf(VibeLinks.cached(url).second) }
  var looked by remember(url) { mutableStateOf(VibeLinks.cached(url).first) }
  if (allowed && !looked) {
    LaunchedEffect(url) {
      info = VibeLinks.info(url)
      looked = true
    }
  }
  val i = info
  when {
    i != null -> LinkCard(url, i.site ?: VibeLinks.siteName(url), i.title ?: sent?.title, i.description ?: sent?.description, i.image ?: previewImage(sent), onLongClick)
    sent != null -> LinkCardFromPreview(sent, onLongClick)
  }
}

@Composable
private fun LinkCardFromPreview(p: LinkPreview, onLongClick: () -> Unit) =
  LinkCard(p.uri, VibeLinks.siteName(p.uri), p.title, p.description, previewImage(p), onLongClick)

@Composable
private fun previewImage(p: LinkPreview?): ImageBitmap? =
  remember(p?.image) { p?.image?.let { runCatching { base64ToBitmap(it) }.getOrNull() }?.takeIf { it.width > 2 && it.height > 2 } }

/** Kept for any older caller: a card from the preview that came with a message. */
@Composable
fun VibeLinkCard(preview: LinkPreview, onLongClick: () -> Unit) = LinkCardFromPreview(preview, onLongClick)

@Composable
private fun LinkCard(url: String, site: String?, title: String?, description: String?, image: ImageBitmap?, onLongClick: () -> Unit) {
  if (title.isNullOrBlank() && description.isNullOrBlank() && image == null) return
  val uri = LocalUriHandler.current
  val dark = VibeColors.isDark()
  val bg = if (dark) Color(0xFF2B2D31) else Color(0xFFF2F3F5)
  val edge = if (dark) Color(0xFF4E5058) else Color(0xFFC4C9CE)
  Row(
    Modifier
      .padding(top = 6.dp, bottom = 2.dp)
      .widthIn(max = 420.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(bg)
      .combinedClickable(onClick = { uri.openUriCatching(url) }, onLongClick = onLongClick)
      .height(IntrinsicSize.Min)
  ) {
    Box(Modifier.width(4.dp).fillMaxHeight().background(edge))
    Column(Modifier.weight(1f, false).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
      if (!site.isNullOrBlank()) {
        Text(site, fontSize = 12.sp, color = VibeColors.onVariant(), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      if (!title.isNullOrBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00A8FC), maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
      if (!description.isNullOrBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
          description, fontSize = 13.5.sp, lineHeight = 18.sp, color = VibeColors.onSurface().copy(alpha = 0.85f),
          maxLines = 3, overflow = TextOverflow.Ellipsis
        )
      }
      if (image != null) {
        Spacer(Modifier.height(10.dp))
        // (no BoxWithConstraints: the card measures its height from its content)
        Image(
          image, null,
          Modifier.fillMaxWidth().aspectRatio(1f / 0.52f).clip(RoundedCornerShape(6.dp)),
          contentScale = ContentScale.Crop
        )
      }
    }
  }
}
