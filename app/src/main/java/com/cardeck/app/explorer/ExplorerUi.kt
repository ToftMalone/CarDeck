package com.cardeck.app.explorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** État de l'explorateur affiché à l'écran (mis à jour depuis le travail en arrière-plan). */
class ExplorerUi : ExplorerListener {
    var running by mutableStateOf(false)
    var mode by mutableStateOf<String?>(null)
    var stepName by mutableStateOf("")
    var stepIndex by mutableIntStateOf(0)
    var stepTotal by mutableIntStateOf(0)
    var detailText by mutableStateOf<String?>(null)
    var progress by mutableStateOf<Float?>(null)
    /** Message de fin (null pendant le travail). */
    var done by mutableStateOf<String?>(null)
    /** Change à chaque fin d'exploration (relecture des résultats). */
    var refresh by mutableIntStateOf(0)
    val lines = mutableStateListOf<String>()

    fun reset() {
        mode = null; stepName = ""; stepIndex = 0; stepTotal = 0; detailText = null; progress = null; done = null
        lines.clear()
    }

    override fun step(index: Int, total: Int, name: String) { stepIndex = index; stepTotal = total; stepName = name }

    override fun detail(text: String?, progress: Float?) { detailText = text; this.progress = progress }

    override fun line(text: String) {
        lines.add(0, text)
        while (lines.size > 200) lines.removeAt(lines.lastIndex)
    }
}
