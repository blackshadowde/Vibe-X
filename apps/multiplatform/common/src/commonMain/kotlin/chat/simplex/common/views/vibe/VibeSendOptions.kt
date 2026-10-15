package chat.simplex.common.views.vibe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.res.MR
import dev.icerock.moko.resources.compose.painterResource

/** Long-press send: send with an effect, or as a disappearing message. */
object VibeSendOptions {
  fun show(text: String, sendWithEffect: ((VibeEffect) -> Unit)?, sendTimed: ((Int) -> Unit)?, sendLater: ((Long) -> Unit)? = null) {
    if (text.isBlank()) return
    VibeSheets.show(background = { vibePage() }) { close ->
      Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        val t = VibeEffect.strip(text).trim()
        Text(
          "“${if (t.length > 80) t.take(80) + "…" else t}”",
          Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
          maxLines = 2, overflow = TextOverflow.Ellipsis, fontStyle = FontStyle.Italic, color = VibeColors.onVariant()
        )
        if (sendWithEffect != null) {
          Text(vt("Send with effect"), Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp), fontSize = 15.sp, color = VibeColors.onVariant())
          Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard()).padding(vertical = 10.dp, horizontal = 6.dp)
          ) {
            for ((row, effects) in VibeEffect.picker.chunked(4).withIndex()) {
              Row(Modifier.fillMaxWidth()) {
                for ((j, e) in effects.withIndex()) {
                  Column(
                    Modifier.weight(1f).vibePopIn(row * 4 + j).clip(RoundedCornerShape(16.dp)).clickable { close(); sendWithEffect(e) }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    VibeAnimatedEmoji(e.emoji, 40.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(e.label, fontSize = 12.sp, color = VibeColors.onVariant())
                  }
                }
                repeat(4 - effects.size) { Spacer(Modifier.weight(1f)) }
              }
            }
          }
        }
        if (sendLater != null) {
          Text(vt("Send later"), Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp), fontSize = 15.sp, color = VibeColors.onVariant())
          Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard())) {
            for ((label, at) in VibeScheduled.presets()) {
              Row(Modifier.fillMaxWidth().clickable { close(); sendLater(at) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(VibeIcons.Schedule, null, Modifier.size(22.dp), tint = VibeColors.onSurface())
                Spacer(Modifier.width(16.dp))
                Text(label, Modifier.weight(1f), fontSize = 16.sp, color = VibeColors.onSurface())
                Text(VibeScheduled.whenText(at).substringAfter(" at ", ""), fontSize = 13.sp, color = VibeColors.onVariant())
              }
            }
          }
        }
        if (sendTimed != null) {
          Spacer(Modifier.height(12.dp))
          Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(vibeCard())) {
            for ((label, secs) in listOf(vt("Disappears after 30 seconds") to 30, vt("Disappears after 1 minute") to 60, vt("Disappears after 5 minutes") to 300, vt("Disappears after 1 hour") to 3600)) {
              Row(Modifier.fillMaxWidth().clickable { close(); sendTimed(secs) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(MR.images.ic_timer), null, Modifier.size(22.dp), tint = VibeColors.onSurface())
                Spacer(Modifier.width(16.dp))
                Text(label, fontSize = 16.sp, color = VibeColors.onSurface())
              }
            }
          }
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }
}
