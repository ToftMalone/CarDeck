package com.cardeck.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.R
import com.cardeck.app.ai.AiStatus
import com.cardeck.app.ai.Assistant
import com.cardeck.app.data.tr
import com.cardeck.app.obd.ObdState
import com.cardeck.app.ui.BackBar
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.FilledBtn
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.T
import com.cardeck.app.ui.rf

/** Logo de Gemini (étoile en dégradé). */
@Composable
fun GeminiLogo(size: Int) {
    Image(painterResource(R.drawable.ic_gemini), contentDescription = "Gemini", modifier = Modifier.size(size.dp))
}

/** Réponse de Gemini, débarrassée de la mise en forme Markdown que le texte brut afficherait. */
internal fun plainAnswer(md: String): String = md.lines().joinToString("\n") { l ->
    val t = l.trimEnd()
    val lead = t.length - t.trimStart().length
    val body = t.trimStart()
    val line = when {
        body.startsWith("#") -> body.trimStart('#').trim()
        body.startsWith("* ") || body.startsWith("- ") -> "• " + body.drop(2)
        else -> body
    }
    " ".repeat(minOf(lead, 4)) + line.replace("**", "").replace("__", "")
}.trim()

/** Assistant Gemini Nano : questions sur la voiture, réponses à partir de ce que CarDeck a lu. */
@Composable
fun AssistantScreen(vm: AppViewModel) {
    val c = Cd.c
    val scroll = rememberScrollState()
    val st by vm.obd.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val status = vm.aiStatus
    // Toujours à la fin de la conversation quand la réponse s'allonge.
    val last = vm.aiChat.lastOrNull()
    LaunchedEffect(vm.aiChat.size, last?.answer?.length) { scroll.animateScrollTo(scroll.maxValue) }
    LaunchedEffect(Unit) { if (status is AiStatus.Checking || status is AiStatus.Unavailable) vm.checkAi() }

    Column(Modifier.fillMaxSize().imePadding()) {
        BackBar("Gemini", scroll.value > 48, { vm.back() }) {
            Spacer(Modifier.weight(1f))
            if (vm.aiChat.isNotEmpty() && !vm.aiBusy) IconBtn(Icons.Rounded.DeleteSweep, { vm.clearAiChat() }, c.onv)
        }
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (status) {
                AiStatus.Checking -> Note("Vérification de Gemini Nano…")
                AiStatus.Unavailable -> com.cardeck.app.ui.EmptyStateCard(
                    Icons.Rounded.PhonelinkErase, "Gemini Nano n'est pas disponible sur ce téléphone",
                    "L'assistant utilise Gemini Nano, l'IA de Google intégrée à certains téléphones récents (Pixel 9 et plus, Galaxy S25 et plus…). " +
                        "Elle peut aussi arriver avec une mise à jour d'Android ou d'AICore.",
                )
                AiStatus.Downloadable, is AiStatus.Downloading -> DownloadCard(vm, status)
                AiStatus.Ready -> {
                    Intro()
                    if (st !is ObdState.Connected) Note("Boîtier non connecté : Gemini se base sur le dernier diagnostic et les trajets enregistrés.")
                    if (vm.aiChat.isEmpty()) Suggestions(vm)
                    vm.aiChat.forEach { m -> Exchange(m) }
                    if (vm.aiChat.isNotEmpty() && !vm.aiBusy) Suggestions(vm)
                }
            }
        }
        if (status == AiStatus.Ready) InputBar(vm, input, { input = it }) { vm.askAi(input); input = "" }
    }
}

@Composable
private fun Note(text: String) {
    T(text, 13, Cd.c.onv, lineHeight = 18, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun Intro() {
    val c = Cd.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.sf1).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.sf3), contentAlignment = Alignment.Center) { GeminiLogo(28) }
        Column(Modifier.weight(1f)) {
            T("Posez une question sur votre voiture", 15, weight = 600)
            T(
                "Gemini Nano, sur votre téléphone : rien ne part sur Internet. Réponses indicatives, à partir de ce que CarDeck a lu ; pas un diagnostic de garage.",
                12, c.onv, lineHeight = 16, modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(vm: AppViewModel) {
    val c = Cd.c
    val haptics = Cd.haptics
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Assistant.SUGGESTIONS.forEach { q ->
            Box(
                Modifier.clip(RoundedCornerShape(18.dp)).background(c.sf2).clickable(enabled = !vm.aiBusy) { haptics.tick(); vm.askAi(tr(q)) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) { T(q, 13, c.on, 500) }
        }
    }
}

@Composable
private fun Exchange(m: AppViewModel.AiMessage) {
    val c = Cd.c
    val acc = Cd.acc
    // Question, à droite.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).background(acc.container).padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Texte saisi par l'utilisateur : jamais traduit.
            androidx.compose.material3.Text(m.question, style = rf(14, 20), color = acc.onContainer)
        }
    }
    // Réponse, à gauche, avec le logo.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 4.dp)) { GeminiLogo(22) }
        Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)).background(c.sf1).padding(horizontal = 14.dp, vertical = 10.dp)) {
            when {
                m.error != null -> T(m.error!!, 14, c.e, lineHeight = 20)
                m.answer.isBlank() && !m.done -> com.cardeck.app.ui.Skeleton(Modifier.fillMaxWidth().height(16.dp))
                // Réponse déjà dans la langue du téléphone : affichée telle quelle.
                else -> androidx.compose.material3.Text(plainAnswer(m.answer), style = rf(14, 21), color = c.on)
            }
        }
    }
}

@Composable
private fun DownloadCard(vm: AppViewModel, status: AiStatus) {
    val c = Cd.c
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sf1).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(c.sf3), contentAlignment = Alignment.Center) { GeminiLogo(40) }
        T("Gemini Nano est disponible", 16, weight = 600, modifier = Modifier.padding(top = 14.dp))
        T(
            "Ce téléphone peut faire tourner Gemini Nano, l'IA de Google, sans Internet. Android doit d'abord télécharger le modèle (une seule fois, de préférence en Wi-Fi).",
            13, c.onv, align = TextAlign.Center, lineHeight = 18, modifier = Modifier.padding(top = 4.dp),
        )
        Box(Modifier.padding(top = 16.dp)) {
            if (status is AiStatus.Downloading) {
                val mb = status.bytes / 1_000_000
                T(if (mb > 0) "Téléchargement… $mb Mo" else "Téléchargement…", 14, c.p, 600)
            } else FilledBtn("Télécharger Gemini Nano", { vm.downloadAi() })
        }
        if (status !is AiStatus.Downloading) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ico(Icons.Rounded.CloudDownload, 16, c.onv)
            T("Plusieurs centaines de Mo", 12, c.onv)
        }
    }
}

@Composable
private fun InputBar(vm: AppViewModel, value: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    val c = Cd.c
    val acc = Cd.acc
    Row(
        Modifier.fillMaxWidth().background(c.sf).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.weight(1f),
            placeholder = { com.cardeck.app.ui.Text("Votre question…") },
            shape = RoundedCornerShape(24.dp), maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank() && !vm.aiBusy) onSend() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = acc.accent, unfocusedBorderColor = c.olv, cursorColor = acc.accent),
        )
        if (vm.aiBusy) IconBtn(Icons.Rounded.Stop, { vm.stopAi() }, c.oe, c.e)
        else IconBtn(Icons.AutoMirrored.Rounded.Send, { if (value.isNotBlank()) onSend() }, if (value.isNotBlank()) acc.onAccent else c.onv, if (value.isNotBlank()) acc.accent else c.sf3)
    }
}
