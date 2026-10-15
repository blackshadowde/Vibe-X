package chat.simplex.common.views.vibe

import androidx.compose.runtime.mutableStateOf
import chat.simplex.common.model.*
import chat.simplex.common.platform.appPreferences
import chat.simplex.common.platform.showToast
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Bookmarked ("starred") messages, kept on this device only (SimpleX has
 * no starring, and nothing about it needs to leave your phone). A short
 * copy of the message is kept so the list shows even before the chat loads.
 */
object VibeStarred {
  @Serializable
  data class Star(
    val chatId: String,
    val chatType: String,
    val apiId: Long,
    val itemId: Long,
    val sender: String,
    val text: String,
    val ts: Long,
  )

  private val json = Json { ignoreUnknownKeys = true }
  private val ser = ListSerializer(Star.serializer())
  val all = mutableStateOf(load())

  private fun load(): List<Star> = try {
    appPreferences.vibeStarred.get()?.let { json.decodeFromString(ser, it) } ?: emptyList()
  } catch (e: Exception) {
    emptyList()
  }

  private fun save(list: List<Star>) {
    all.value = list
    appPreferences.vibeStarred.set(json.encodeToString(ser, list))
  }

  fun isStarred(chatId: String, itemId: Long) = all.value.any { it.chatId == chatId && it.itemId == itemId }

  fun forChat(chatId: String) = all.value.filter { it.chatId == chatId }.sortedByDescending { it.ts }

  fun toggle(chatInfo: ChatInfo, item: ChatItem) {
    if (isStarred(chatInfo.id, item.id)) {
      save(all.value.filterNot { it.chatId == chatInfo.id && it.itemId == item.id })
      showToast("Removed bookmark")
    } else {
      val (name, _) = vibeSender(chatInfo, item)
      save(
        all.value + Star(
          chatInfo.id, chatInfo.chatType.type, chatInfo.apiId, item.id, name,
          VibeEffect.strip(item.text).take(300), item.meta.itemTs.toEpochMilliseconds()
        )
      )
      showToast("Bookmarked")
    }
  }
}
