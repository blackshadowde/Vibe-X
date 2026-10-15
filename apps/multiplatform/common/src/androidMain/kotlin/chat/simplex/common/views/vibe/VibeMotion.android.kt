package chat.simplex.common.views.vibe

import android.os.Build
import android.view.HapticFeedbackConstants
import chat.simplex.common.platform.mainActivity

/** Same feedback Vibe-flutter's HapticFeedback calls give (they go through the window's view too). */
actual fun vibePerformHaptic(kind: VibeHaptics.Kind) {
  val view = mainActivity.get()?.window?.decorView ?: return
  val constant = when (kind) {
    VibeHaptics.Kind.Selection -> HapticFeedbackConstants.CLOCK_TICK
    VibeHaptics.Kind.Light -> HapticFeedbackConstants.VIRTUAL_KEY
    VibeHaptics.Kind.Medium -> HapticFeedbackConstants.KEYBOARD_TAP
    VibeHaptics.Kind.Heavy -> HapticFeedbackConstants.LONG_PRESS
    VibeHaptics.Kind.Success ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
  }
  view.performHapticFeedback(constant)
}
