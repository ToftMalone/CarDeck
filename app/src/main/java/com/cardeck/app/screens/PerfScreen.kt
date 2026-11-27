package com.cardeck.app.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.data.PerfConditions
import com.cardeck.app.data.PerfKind
import com.cardeck.app.data.PerfRun
import com.cardeck.app.data.PerfTimer
import com.cardeck.app.data.fr
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.LevelBar
import com.cardeck.app.ui.Segmented
import com.cardeck.app.ui.T
import com.cardeck.app.ui.Text
import com.cardeck.app.ui.gs
import com.cardeck.app.ui.rise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Temps en secondes, deux décimales : « 7,84 s ». */
private fun secs(ms: Long) = "${fr(ms / 1000.0, 2)} s"

/** Nom court de l'épreuve (sélecteur). */
private fun short(k: PerfKind) = when (k) {
    PerfKind.ZeroTo100 -> "0–100"
    PerfKind.Quarter400 -> "400 m"
    PerfKind.From50To90 -> "50–90"
    PerfKind.From80To120 -> "80–120"
    PerfKind.Brake100To0 -> "100–0"
}

/** Résultat principal : distance d'arrêt pour un freinage, temps sinon. */
private fun main(kind: PerfKind, timeMs: Long, distanceM: Double?) =
    if (kind.braking && distanceM != null) "${fr(distanceM, 1)} m" else secs(timeMs)

/** Résultat secondaire : temps d'arrêt d'un freinage, ou vitesse au passage des 400 m. */
private fun detail(kind: PerfKind, timeMs: Long, trapKmh: Double?): String? = when {
    kind.braking -> "en ${secs(timeMs)}"
    trapKmh != null -> "${fr(trapKmh)} km/h"
    else -> null
}

/** Conditions lisibles : « 12 °C dehors · admission 25 °C · 101 kPa · SP98 ». */
private fun conditions(p: PerfConditions): String? = listOfNotNull(
    p.ambientC?.let { "${fr(it)} °C dehors" },
    p.intakeC?.let { "admission ${fr(it)} °C" },
    p.baroKpa?.let { "${fr(it)} kPa" },
    p.fuel?.label,
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/**
 * Performances : 0 à 100 km/h, reprise de 80 à 120 km/h et 400 m départ arrêté, chronométrés avec la vitesse lue sur la voiture.
 * Page ouverte : le service ne lit plus que la vitesse, au plus vite ([com.cardeck.app.obd.ObdManager.perfMode]). Chaque essai
 * réussi est enregistré pour le véhicule, avec le meilleur temps de chaque épreuve.
 */
@Composable
fun PerfScreen(vm: AppViewModel) {
    val c = Cd.c
    val acc = Cd.acc
    val haptics = Cd.haptics
    val v = rememberActiveVehicle(vm) ?: return
    val (connLabel, connKind) = connStatus(vm, v)
    val connected = connKind == ConnKind.Ok
    val supported by vm.obd.supported.collectAsState()
    val changes by vm.repo.changes.collectAsState()
    var kind by rememberSaveable { mutableStateOf(PerfKind.ZeroTo100) }
    var st by remember { mutableStateOf(PerfTimer.State()) }
    var runs by remember(v.id) { mutableStateOf<List<PerfRun>>(emptyList()) }
    var toDelete by remember { mutableStateOf<PerfRun?>(null) }
    var record by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) { vm.obd.keepReading() }
    DisposableEffect(Unit) {
        vm.obd.perfMode = true
        onDispose { vm.obd.perfMode = false; vm.obd.perfBusy = false }
    }
    LaunchedEffect(v.id, changes) { runs = withContext(Dispatchers.IO) { vm.repo.perfRuns(v.id) } }

    // Chronométrage : une minuterie par épreuve, nourrie par chaque vitesse lue. Un essai réussi est enregistré aussitôt.
    var lastConditions by remember { mutableStateOf(PerfConditions()) }
    LaunchedEffect(kind, v.id) {
        val timer = PerfTimer(kind)
        st = timer.state
        record = false
        var cond = PerfConditions()
        vm.obd.speedSamples.collect { (t, speed) ->
            val before = timer.state.last
            val now = timer.feed(t, speed)
            val started = now.phase == PerfTimer.Phase.Running && st.phase != PerfTimer.Phase.Running
            vm.obd.perfBusy = now.phase == PerfTimer.Phase.Running
            if (started) {
                haptics.confirm()
                // Conditions au départ : dernières valeurs lues (relues toutes les ~6 s hors chrono) et carburant choisi.
                val live = vm.obd.live.value
                cond = PerfConditions(live["std46"], live["std0F"], live["std33"], vm.repo.fuelFor(v.id))
            }
            st = now
            val out = now.last
            if (out !== before && out is PerfTimer.Outcome.Done) {
                haptics.confirm()
                val score = if (kind.braking) out.distanceM ?: Double.MAX_VALUE else out.timeMs.toDouble()
                val best = runs.filter { it.kind == kind }.minOfOrNull { it.score }
                record = best == null || score < best
                lastConditions = cond
                withContext(Dispatchers.IO) {
                    vm.repo.addPerfRun(v.id, kind, System.currentTimeMillis(), out.timeMs, out.trapKmh, out.hz, out.distanceM, cond)
                }
            } else if (out !== before && out is PerfTimer.Outcome.Failed) {
                haptics.threshold()
                record = false
            }
        }
    }
    // Temps qui défile pendant l'essai (même horloge que les mesures).
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(st.phase, st.startT) {
        val s0 = st.startT ?: return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            elapsed = (android.os.SystemClock.elapsedRealtime() - s0).coerceAtLeast(0)
        }
    }

    Column(Modifier.fillMaxSize()) {
        com.cardeck.app.ui.PageBar("Performances", v.nickname, { vm.back() }, scrolled = scroll.value > 48) {
            com.cardeck.app.ui.StatusChip(connLabel, connDot(connKind), blink = connKind != ConnKind.Off)
        }
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.wc).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Ico(Icons.Rounded.Warning, 22, c.owc)
                T("Uniquement sur circuit ou sur une route fermée à la circulation, jamais sur la voie publique.", 13, c.owc, lineHeight = 18, modifier = Modifier.weight(1f))
            }
            Segmented(
                PerfKind.entries.map { k -> Triple(short(k), null, k == kind) },
            ) { kind = PerfKind.entries[it] }

            val sup = supported
            when {
                !connected -> com.cardeck.app.ui.EmptyStateCard(
                    if (v.adapter == null) Icons.Rounded.SettingsInputHdmi else Icons.Rounded.LinkOff,
                    "Boîtier non connecté", "Connectez le boîtier OBD2 : les chronos utilisent la vitesse lue sur la voiture.",
                ) {
                    if (v.adapter == null) com.cardeck.app.ui.TonalBtn("Associer un boîtier", { vm.pairActive() })
                    else com.cardeck.app.ui.TonalBtn("Se connecter", { vm.connectNow() })
                }
                sup != null && 0x0D !in sup -> com.cardeck.app.ui.EmptyStateCard(
                    Icons.Rounded.Speed, "Vitesse non fournie", "Ce véhicule ne donne pas sa vitesse par la prise OBD2 : impossible de chronométrer.",
                )
                else -> LiveCard(kind, st, elapsed, record, lastConditions)
            }

            Records(kind, runs.filter { it.kind == kind }) { toDelete = it }
        }
    }

    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv,
            title = { T("Supprimer ce chrono ?", 22, lineHeight = 28) },
            text = { T("${r.kind.label} : ${main(r.kind, r.timeMs, r.distanceM)}, ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(r.at))}.", 14, c.onv, lineHeight = 20) },
            confirmButton = { TextButton({ toDelete = null; vm.repo.deletePerfRun(r.id) }) { T("Supprimer", 14, c.e, 600) } },
            dismissButton = { TextButton({ toDelete = null }) { T("Annuler", 14, acc.accent, 500) } },
        )
    }
}

/** Carte en direct : consigne, grand chiffre (temps pendant l'essai, sinon vitesse), progression et dernier résultat. */
@Composable
private fun LiveCard(kind: PerfKind, st: PerfTimer.State, elapsed: Long, record: Boolean, cond: PerfConditions) {
    val c = Cd.c
    val acc = Cd.acc
    val running = st.phase == PerfTimer.Phase.Running
    val bg by animateColorAsState(if (running) acc.container else c.sf1, tween(250), label = "live")
    val fg = if (running) acc.onContainer else c.on
    Column(
        Modifier.fillMaxWidth().rise(0).clip(RoundedCornerShape(28.dp)).background(bg).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val from = fr(kind.fromKmh)
        val hint = when (st.phase) {
            PerfTimer.Phase.Running -> "Chrono en cours…"
            PerfTimer.Phase.Ready -> when {
                kind.standing -> "Prêt : accélérez à fond, le chrono part tout seul"
                kind.braking -> "Prêt : freinez à fond, le chrono part sous $from km/h"
                else -> "Prêt : accélérez à fond, le chrono part à $from km/h"
            }
            PerfTimer.Phase.Waiting -> when {
                kind.standing -> "Arrêtez-vous complètement pour lancer un essai"
                kind.braking -> "Dépassez $from km/h pour lancer un essai"
                else -> "Passez sous $from km/h pour lancer un essai"
            }
        }
        T(hint, 14, if (running) fg else c.onv, 500, align = TextAlign.Center)
        Text(
            if (running) secs(elapsed) else "${st.speed?.let { fr(it) } ?: "—"} km/h",
            Modifier.padding(top = 8.dp), style = gs(56, 64, 500, -1f, tnum = true), color = fg, maxLines = 1,
        )
        if (running) T("${st.speed?.let { fr(it) } ?: "—"} km/h", 16, fg, 500)
        if (running && kind.braking) T("${fr(st.distanceM)} m parcourus", 13, fg, modifier = Modifier.padding(top = 4.dp))
        if (running && kind.meters != null) {
            LevelBar((st.distanceM / kind.meters).toFloat(), acc.accent, Modifier.padding(top = 12.dp))
            T("${fr(st.distanceM.coerceAtMost(kind.meters))} / ${fr(kind.meters)} m", 13, fg, modifier = Modifier.padding(top = 4.dp))
        }
        when (val last = st.last) {
            is PerfTimer.Outcome.Done -> Column(Modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                T(if (record) "🏆 Nouveau record !" else "Dernier essai", 14, c.onv, 600)
                T(main(kind, last.timeMs, last.distanceM), 32, c.on, 600)
                if (kind.braking) T("Arrêt en ${secs(last.timeMs)}", 13, c.onv)
                last.trapKmh?.let { T("${fr(it)} km/h au passage des ${fr(kind.meters ?: 0.0)} m", 13, c.onv) }
                conditions(cond)?.let { T(it, 12, c.onv, align = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)) }
            }
            is PerfTimer.Outcome.Failed -> T("Essai abandonné : ${last.reason}", 13, c.e, 500, align = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
            null -> {}
        }
        // Précision : elle dépend du nombre de vitesses lues par seconde (boîtier, voiture).
        if (st.hz > 0) {
            val low = st.hz < 5
            T(
                if (low) "Seulement ${fr(st.hz, 1)} mesures par seconde : chrono peu précis" else "${fr(st.hz, 1)} mesures de vitesse par seconde",
                12, if (low) c.e else c.onv, align = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Meilleur temps et essais enregistrés pour l'épreuve choisie. */
@Composable
private fun Records(kind: PerfKind, runs: List<PerfRun>, onDelete: (PerfRun) -> Unit) {
    val c = Cd.c
    val acc = Cd.acc
    val best = runs.minByOrNull { it.score }
    Column(Modifier.fillMaxWidth().rise(1).clip(RoundedCornerShape(24.dp)).background(c.sf1).padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ico(Icons.Rounded.EmojiEvents, 20, acc.accent)
            T(kind.label, 15, acc.accent, 600)
        }
        if (best == null) {
            T("Aucun chrono pour l'instant.", 13, c.onv, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            return@Column
        }
        Row(Modifier.padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(main(kind, best.timeMs, best.distanceM), style = gs(32, 40, 600, tnum = true), color = c.on)
            T(if (kind.braking) "plus court freinage" else "meilleur temps", 13, c.onv, modifier = Modifier.padding(bottom = 6.dp))
        }
        val fmt = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        runs.take(30).forEach { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ico(if (r.id == best.id) Icons.Rounded.EmojiEvents else Icons.Rounded.Timer, 18, if (r.id == best.id) acc.accent else c.onv)
                Column(Modifier.weight(1f)) {
                    T(listOfNotNull(main(kind, r.timeMs, r.distanceM), detail(kind, r.timeMs, r.trapKmh)).joinToString(" · "), 15, weight = 500)
                    T(fmt.format(java.util.Date(r.at)) + " · ${fr(r.hz, 1)} mesures/s", 12, c.onv)
                    conditions(r.conditions)?.let { T(it, 12, c.onv) }
                }
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { onDelete(r) }, contentAlignment = Alignment.Center) { Ico(Icons.Rounded.DeleteOutline, 20, c.onv) }
            }
        }
    }
}
