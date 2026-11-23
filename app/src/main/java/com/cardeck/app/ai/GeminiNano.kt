package com.cardeck.app.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Disponibilité de Gemini Nano sur ce téléphone. */
sealed interface AiStatus {
    data object Checking : AiStatus
    /** Téléphone non compatible (ou AICore absent) : l'assistant est caché. */
    data object Unavailable : AiStatus
    /** Compatible, modèle à télécharger (par Android, en arrière-plan). */
    data object Downloadable : AiStatus
    data class Downloading(val bytes: Long) : AiStatus
    data object Ready : AiStatus
}

/** Événement du téléchargement du modèle. */
sealed interface AiDownload {
    data class Progress(val bytes: Long) : AiDownload
    data object Done : AiDownload
    data class Failed(val message: String) : AiDownload
}

/** Gemini Nano via AICore (API Prompt de ML Kit) : tout reste sur le téléphone. */
class GeminiNano {
    private val model: GenerativeModel by lazy { Generation.getClient() }

    suspend fun status(): AiStatus = try {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> AiStatus.Ready
            FeatureStatus.DOWNLOADABLE -> AiStatus.Downloadable
            FeatureStatus.DOWNLOADING -> AiStatus.Downloading(0)
            else -> AiStatus.Unavailable
        }
    } catch (e: Exception) {
        // AICore absent, téléphone non pris en charge, Play Services trop anciens…
        AiStatus.Unavailable
    }

    fun download(): Flow<AiDownload> = model.download().map { d ->
        when (d) {
            is DownloadStatus.DownloadProgress -> AiDownload.Progress(d.totalBytesDownloaded)
            is DownloadStatus.DownloadCompleted -> AiDownload.Done
            is DownloadStatus.DownloadFailed -> AiDownload.Failed(d.e.message ?: "code ${d.e.errorCode}")
            else -> AiDownload.Progress(0)
        }
    }

    /** Réponse au fil de l'eau : chaque élément est un nouveau morceau de texte. */
    fun ask(prompt: String): Flow<String> {
        val request = generateContentRequest(TextPart(prompt)) {
            temperature = 0.2f
            topK = 16
            maxOutputTokens = 400
        }
        return model.generateContentStream(request).map { r -> r.candidates.firstOrNull()?.text.orEmpty() }
    }

    /** Message lisible pour une erreur de Gemini. */
    fun describe(e: Throwable): String = (e as? GenAiException)?.let { "Gemini n'a pas pu répondre (code ${it.errorCode})" }
        ?: (e.message ?: "Gemini n'a pas pu répondre")
}
