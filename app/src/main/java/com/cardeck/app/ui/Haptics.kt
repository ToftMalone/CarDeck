package com.cardeck.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

/** Retours haptiques de l'application (désactivables dans les réglages). */
class Haptics(private val view: View?, private val enabled: Boolean) {
    private fun perform(constant: Int) {
        if (enabled) view?.performHapticFeedback(constant)
    }

    /** Sélection d'un élément. */
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** Changement d'onglet ou de segment. */
    fun segment() = perform(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)

    /** Action réussie. */
    fun confirm() = perform(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)

    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    /** Seuil franchi. */
    fun threshold() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CONTEXT_CLICK,
    )
}

val LocalHaptics = staticCompositionLocalOf { Haptics(null, false) }
