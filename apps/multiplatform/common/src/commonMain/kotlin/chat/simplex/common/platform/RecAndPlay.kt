package chat.simplex.common.platform

import androidx.compose.runtime.MutableState
import chat.simplex.common.model.*
import kotlinx.coroutines.CoroutineScope

interface RecorderInterface {
  companion object {
    // Allows to stop the recorder from outside without having the recorder in a variable
    var stopRecording: (() -> Unit)? = null
    val extension: String = "m4a"
  }
  fun start(onProgressUpdate: (position: Int?, finished: Boolean) -> Unit): String
  fun stop(): Int
  /** Loudness since the last call, 0..1 (Vibe X's recording bars). */
  fun amplitude(): Float = 0f
  /** Pause / resume a recording; false where it isn't supported. */
  fun pause(): Boolean = false
  fun resume(): Boolean = false
}

expect class RecorderNative(): RecorderInterface

enum class TrackState {
  PLAYING, PAUSED, REPLACED
}

data class CurrentlyPlayingState(
  val fileSource: CryptoFile,
  val onProgressUpdate: (position: Int?, state: TrackState) -> Unit,
  val smallView: Boolean,
)

interface AudioPlayerInterface {
  val currentlyPlaying: MutableState<CurrentlyPlayingState?>
  fun play(
    fileSource: CryptoFile,
    audioPlaying: MutableState<Boolean>,
    progress: MutableState<Int>,
    duration: MutableState<Int>,
    resetOnEnd: Boolean,
    smallView: Boolean,
  )
  fun stop()
  fun stop(item: ChatItem)
  fun stop(fileName: String?)
  fun pause(audioPlaying: MutableState<Boolean>, pro: MutableState<Int>)
  fun seekTo(ms: Int, pro: MutableState<Int>, filePath: String?)
  fun duration(unencryptedFilePath: String): Int?
  /** Playback speed of the track that is playing now (Vibe X's 1x / 1.5x / 2x). */
  fun setSpeed(speed: Float) {}
}

expect object AudioPlayer: AudioPlayerInterface

interface SoundPlayerInterface {
  fun start(scope: CoroutineScope, sound: Boolean)
  fun stop()
}

interface CallSoundsPlayerInterface {
  fun startConnectingCallSound(scope: CoroutineScope)
  fun startInCallSound(scope: CoroutineScope)
  fun stop()
  fun vibrate(times: Int = 1)
}

expect object SoundPlayer: SoundPlayerInterface

expect object CallSoundsPlayer: CallSoundsPlayerInterface
