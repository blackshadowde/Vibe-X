package chat.simplex.common.views.vibe

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.*
import chat.simplex.common.platform.*
import chat.simplex.common.views.chat.item.showFileErrorAlert
import chat.simplex.common.views.helpers.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Set by the recording row's send button: send the voice note as soon as it's ready. */
object VibeVoiceSend {
  val pending = mutableStateOf(false)
}

/** Loudness of the decoded voice note, one value per short chunk; null where it can't be read. */
expect fun vibeDecodeWaveform(fileSource: CryptoFile): List<Float>?

private const val VIBE_WAVES = 40

/** Waveforms of voice notes, measured from the audio once and kept while the app runs. */
object VibeWaveforms {
  private val cache = HashMap<String, FloatArray>()
  private val failed = HashSet<String>()
  // one note at a time, so scrolling through many doesn't start many decoders
  private val lock = Mutex()

  fun cached(path: String): FloatArray? = synchronized(cache) { cache[path] }

  suspend fun load(fileSource: CryptoFile): FloatArray? {
    cached(fileSource.filePath)?.let { return it }
    if (synchronized(cache) { fileSource.filePath in failed }) return null
    return withContext(Dispatchers.IO) {
      lock.withLock {
        cached(fileSource.filePath) ?: run {
          val levels = runCatching { vibeDecodeWaveform(fileSource) }.getOrNull()
          val bars = levels?.takeIf { it.isNotEmpty() }?.let { bucket(it, VIBE_WAVES) }
          synchronized(cache) { if (bars != null) cache[fileSource.filePath] = bars else failed.add(fileSource.filePath) }
          bars
        }
      }
    }
  }

  private fun bucket(levels: List<Float>, bars: Int): FloatArray {
    val n = levels.size
    val out = FloatArray(bars) { b ->
      val from = (b.toLong() * n / bars).toInt().coerceIn(0, n - 1)
      val to = ((b + 1).toLong() * n / bars).toInt().coerceIn(from + 1, n)
      var m = 0f
      for (i in from until to) m = max(m, levels[i])
      m
    }
    val top = out.maxOrNull()?.takeIf { it > 0f } ?: 1f
    for (i in out.indices) out[i] = sqrt(out[i] / top).coerceIn(0.08f, 1f)
    return out
  }
}

private fun mmss(ms: Int): String {
  val s = max(ms, 0) / 1000
  return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
}

/**
 * A voice note in the timeline, as in Vibe: a round play button, 40 bars of
 * the note's waveform (the played part solid; tap or drag to seek), the time,
 * and a mic, or the speed (1x / 1.5x / 2x) while it's playing.
 */
@Composable
fun VibeVoiceNote(providedDurationSec: Int, file: CIFile?, longClick: () -> Unit, receiveFile: (Long) -> Unit) {
  val color = VibeColors.onSurface()
  val f = file?.fileSource?.filePath
  val fileSource = remember(f, file?.fileStatus, CIFile.cachedRemoteFileRequests.toList()) { mutableStateOf(getLoadedFileSource(file)) }
  val audioPlaying = rememberSaveable(f) { mutableStateOf(false) }
  val progress = rememberSaveable(f) { mutableStateOf(0) }
  val duration = rememberSaveable(f) { mutableStateOf(providedDurationSec * 1000) }
  var speed by rememberSaveable(f) { mutableStateOf(1f) }
  val play: () -> Unit = {
    val playIfExists = {
      val src = fileSource.value
      if (src != null) {
        AudioPlayer.play(src, audioPlaying, progress, duration, resetOnEnd = true, smallView = false)
        AudioPlayer.setSpeed(speed)
      }
    }
    if (file != null && chatModel.connectedToRemote() && fileSource.value == null) {
      withBGApi {
        file.loadRemoteFile(true)
        fileSource.value = getLoadedFileSource(file)
        playIfExists()
      }
    } else playIfExists()
  }
  val pause = { AudioPlayer.pause(audioPlaying, progress) }

  val src = fileSource.value
  var wave by remember(src?.filePath) { mutableStateOf(src?.let { VibeWaveforms.cached(it.filePath) }) }
  LaunchedEffect(src?.filePath) {
    if (src != null && wave == null) wave = VibeWaveforms.load(src)
  }

  val status = file?.fileStatus
  val playable = file != null && when (status) {
    is CIFileStatus.SndStored -> file.fileProtocol == FileProtocol.LOCAL
    is CIFileStatus.SndTransfer, is CIFileStatus.SndError, is CIFileStatus.SndWarning,
    is CIFileStatus.RcvInvitation, is CIFileStatus.RcvTransfer, is CIFileStatus.RcvAccepted,
    is CIFileStatus.RcvAborted, is CIFileStatus.RcvError, is CIFileStatus.RcvWarning -> false
    else -> file.loaded
  }
  val active = audioPlaying.value || progress.value > 0

  Row(
    Modifier.padding(top = 8.dp, bottom = 8.dp, end = 12.dp).widthIn(max = 380.dp).fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // the button
    Box(
      Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(color.copy(alpha = 0.25f))
        .combinedClickable(
          onClick = {
            when {
              playable -> if (audioPlaying.value) pause() else play()
              file == null -> {}
              status is CIFileStatus.RcvInvitation || status is CIFileStatus.RcvAborted -> file?.let { receiveFile(it.fileId) }
              status is CIFileStatus.SndError -> showFileErrorAlert(status.sndFileError)
              status is CIFileStatus.SndWarning -> showFileErrorAlert(status.sndFileError, temporary = true)
              status is CIFileStatus.RcvError -> showFileErrorAlert(status.rcvFileError)
              status is CIFileStatus.RcvWarning -> showFileErrorAlert(status.rcvFileError, temporary = true)
            }
          },
          onLongClick = longClick
        ),
      contentAlignment = Alignment.Center
    ) {
      val loading = status is CIFileStatus.SndTransfer || status is CIFileStatus.RcvTransfer || status is CIFileStatus.RcvAccepted ||
          (status is CIFileStatus.SndStored && file?.fileProtocol != FileProtocol.LOCAL)
      val icon: ImageVector? = when {
        loading -> null
        status is CIFileStatus.SndError || status is CIFileStatus.RcvError -> VibeIcons.Close
        status is CIFileStatus.SndWarning || status is CIFileStatus.RcvWarning -> VibeIcons.Error
        status is CIFileStatus.RcvAborted -> VibeIcons.Refresh
        audioPlaying.value -> VibeIcons.Pause
        else -> VibeIcons.PlayArrow
      }
      if (icon == null) {
        CircularProgressIndicator(Modifier.size(24.dp), color = color, strokeWidth = 2.dp)
      } else {
        Icon(icon, null, Modifier.size(24.dp), tint = if (playable || status is CIFileStatus.RcvInvitation) color else color.copy(alpha = 0.7f))
      }
    }
    Spacer(Modifier.width(8.dp))
    // the waveform
    val bars = wave
    val played = if (duration.value > 0) progress.value.toFloat() / duration.value * VIBE_WAVES else 0f
    Canvas(
      Modifier
        .weight(1f)
        .height(32.dp)
        .padding(horizontal = 16.dp)
        .pointerInput(playable, duration.value) {
          if (!playable) return@pointerInput
          fun seek(x: Float) {
            if (!audioPlaying.value && progress.value == 0) {
              play()
            } else {
              val ms = (x / size.width).coerceIn(0f, 1f) * duration.value
              AudioPlayer.seekTo(ms.toInt(), progress, fileSource.value?.filePath)
            }
          }
          detectTapGestures(onTap = { seek(it.x) }, onLongPress = { longClick() })
        }
        .pointerInput(playable, duration.value) {
          if (!playable) return@pointerInput
          detectHorizontalDragGestures { change, _ ->
            if (audioPlaying.value || progress.value > 0) {
              val ms = (change.position.x / size.width).coerceIn(0f, 1f) * duration.value
              AudioPlayer.seekTo(ms.toInt(), progress, fileSource.value?.filePath)
            }
          }
        }
    ) {
      val slot = size.width / VIBE_WAVES
      val gap = 1.dp.toPx()
      val w = max(slot - gap * 2, 1f)
      for (i in 0 until VIBE_WAVES) {
        val h = max(size.height * (bars?.getOrNull(i) ?: 0.12f), w)
        drawRoundRect(
          if (i < played) color else color.copy(alpha = 0.5f),
          topLeft = Offset(i * slot + gap, (size.height - h) / 2),
          size = Size(w, h),
          cornerRadius = CornerRadius(w / 2, w / 2)
        )
      }
    }
    Spacer(Modifier.width(8.dp))
    Text(mmss(if (active) progress.value else duration.value), Modifier.width(36.dp), color = color, fontSize = 12.sp, maxLines = 1, softWrap = false)
    Spacer(Modifier.width(8.dp))
    Crossfade(active, label = "voiceSpeed") { on ->
      if (!on) {
        Icon(VibeIcons.MicNone, null, Modifier.padding(end = 8.dp).size(24.dp), tint = color)
      } else {
        Box(
          Modifier
            .size(32.dp, 20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.25f))
            .clickable {
              speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; 2f -> 0.5f; else -> 1f }
              AudioPlayer.setSpeed(speed)
            },
          contentAlignment = Alignment.Center
        ) {
          val label = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
          Text(label, color = color, fontSize = 9.sp, maxLines = 1)
        }
      }
    }
  }
}

/** The voice button of the input: Vibe's wave. Hold it to start recording; a tap says so. */
@Composable
fun VibeVoiceButton(enabled: Boolean = true, onStart: () -> Unit, onClick: (() -> Unit)? = null) {
  val haptic = rememberVibeHaptic()
  Box(
    Modifier
      .size(56.dp)
      .clip(CircleShape)
      .combinedClickable(
        onClick = {
          if (onClick != null) onClick() else showToast(vt("Long press to record a voice message"))
        },
        onLongClick = {
          if (onClick != null) onClick() else {
            haptic(HapticFeedbackType.LongPress)
            onStart()
          }
        }
      ),
    contentAlignment = Alignment.Center
  ) {
    Icon(VibeIcons.GraphicEq, null, Modifier.size(28.dp), tint = if (enabled) VibeColors.sendFg else VibeColors.sendFg.copy(alpha = 0.4f))
  }
}

/**
 * While recording, the whole input row becomes Vibe's recording row:
 * delete, pause / resume, the time, live loudness bars (the newest on the
 * right) and send.
 */
@Composable
fun VibeRecordingRow(progressMs: Int, rec: RecorderInterface, onCancel: () -> Unit, onSend: () -> Unit) {
  LockToCurrentOrientationUntilDispose()
  var paused by remember { mutableStateOf(false) }
  val amps = remember { mutableStateListOf<Float>() }
  LaunchedEffect(Unit) {
    while (isActive) {
      delay(100)
      if (!paused) {
        val a = runCatching { rec.amplitude() }.getOrDefault(0f)
        // as in Vibe: decibels, -50 dB … 0 dB → 0 … full height
        val db = if (a <= 0f) -100f else 20f * log10(a)
        amps.add(((100f + db * 2f) / 100f).coerceIn(0.06f, 1f))
        while (amps.size > 400) amps.removeAt(0)
      }
    }
  }
  val onSurface = VibeColors.onSurface()
  Row(
    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp).height(56.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Spacer(Modifier.width(8.dp))
    RoundIcon(VibeIcons.DeleteOutline, VibeColors.red, onCancel)
    if (appPlatform.isAndroid) {
      RoundIcon(if (paused) VibeIcons.PlayCircleOutline else VibeIcons.PauseCircleOutline, onSurface) {
        if (paused) { if (rec.resume()) paused = false } else { if (rec.pause()) paused = true }
      }
    }
    Text(mmss(progressMs), color = onSurface, fontSize = 14.sp, maxLines = 1)
    Canvas(Modifier.weight(1f).height(36.dp).padding(start = 8.dp, end = 8.dp)) {
      val w = 4.dp.toPx()
      val step = w + 2.dp.toPx()
      val count = (size.width / step).toInt()
      val shown = amps.takeLast(count)
      var x = size.width - w
      for (i in shown.indices.reversed()) {
        val h = max(size.height * shown[i], w)
        drawRoundRect(
          VibeColors.blurple,
          topLeft = Offset(x, (size.height - h) / 2),
          size = Size(w, h),
          cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        x -= step
      }
    }
    Box(
      Modifier.size(48.dp).clip(CircleShape).background(VibeColors.blurple).clickable(onClick = onSend),
      contentAlignment = Alignment.Center
    ) {
      Icon(VibeIcons.SendOutlined, null, Modifier.size(24.dp), tint = Color.White)
    }
    Spacer(Modifier.width(14.dp))
  }
}

@Composable
private fun RoundIcon(icon: ImageVector, tint: Color, onClick: () -> Unit) {
  Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    Icon(icon, null, Modifier.size(24.dp), tint = tint)
  }
}
