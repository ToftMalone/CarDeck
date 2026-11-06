package com.cardeck.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.explorer.Bus
import com.cardeck.app.explorer.Explore
import com.cardeck.app.explorer.ExplorerData
import com.cardeck.app.ui.BackBar
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.FilledBtn
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.Radar
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Résumé des découvertes enregistrées pour un véhicule. */
private class Summary(val ecus: Int, val can11: Int, val can29: Int, val k: Int, val answers: Int, val blocks: Int, val bus: Int, val snaps: Int, val drives: Int, val uds: List<Pair<String, Int>>, val names: Map<String, String>)

private fun summarize(d: ExplorerData) = Summary(
    d.ecus.size, d.ecus.values.count { it.bus == Bus.Can11 }, d.ecus.values.count { it.bus == Bus.Can29 }, d.ecus.values.count { it.bus == Bus.K },
    d.answers.size, d.dataBlocks().size, d.bus.size, d.history.size, d.drives.size,
    d.ecus.values.filter { it.proto == "uds" && it.bus != Bus.K && it.key !in ExplorerData.UDS_SKIPPED }.map { it.key to (d.progress["22:${it.key}"] ?: 0) },
    d.names(),
)

/** Explorateur : balayage à l'arrêt ou enregistrement en roulant, choisi selon l'état de la voiture au lancement. */
@Composable
fun ExplorerScreen(vm: AppViewModel) {
    val c = Cd.c
    val v = rememberActiveVehicle(vm) ?: return
    val ex = vm.explorer
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val (_, connKind) = connStatus(vm, v)
    val connected = connKind == ConnKind.Ok
    val scroll = rememberScrollState()
    var summary by remember { mutableStateOf<Summary?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(v.id, ex.refresh) {
        summary = withContext(Dispatchers.IO) { summarize(ExplorerData.load(java.io.File(vm.explorerDir(v.id), "decouvertes.json"))) }
    }
    // Écran allumé pendant l'exploration : elle peut durer longtemps.
    val view = LocalView.current
    DisposableEffect(ex.running) {
        view.keepScreenOn = ex.running
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar("Explorateur", scroll.value > 48, { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Explorateur", 32, lineHeight = 40, modifier = Modifier.padding(top = 4.dp))
            T(
                "Découvre tous les calculateurs de ${v.nickname} et tout ce qu'ils acceptent de donner. Un seul bouton : " +
                    "voiture à l'arrêt, c'est le balayage complet ; voiture qui roule, c'est l'enregistrement en roulant.",
                14, c.onv, lineHeight = 20,
            )
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.sf2).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Ico(Icons.Rounded.Lock, 20, c.onv)
                T(
                    "Lecture seule garantie : seuls les services de lecture partent vers la voiture, jamais d'effacement, d'écriture, de pilotage ni de déverrouillage. " +
                        "Les adresses 7F1 / 7F9 (airbags, ISO 26021) ne sont jamais interrogées.",
                    13, c.onv, lineHeight = 18,
                )
            }

            if (ex.running) Running(vm)
            else {
                ex.done?.let { msg ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.sc).padding(14.dp)) { T(msg, 14, c.osc, lineHeight = 20) }
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sf1).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(88.dp).clip(CircleShape).background(c.pc), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.TravelExplore, 44, c.opc) }
                    T("🅿️ À l'arrêt : balayage complet", 15, weight = 600, modifier = Modifier.padding(top = 16.dp))
                    T(
                        "Contact mis, moteur au ralenti de préférence. Recherche des calculateurs (CAN 11 et 29 bits, ligne K), identification, " +
                            "blocs constructeur, identifiants UDS par tranches de 10 000, lecture mémoire, carte du bus. Interrompu, il reprend où il s'était arrêté.",
                        13, c.onv, align = TextAlign.Center, lineHeight = 18, modifier = Modifier.padding(top = 4.dp),
                    )
                    T("🚗 En roulant : enregistrement", 15, weight = 600, modifier = Modifier.padding(top = 14.dp))
                    T(
                        "Lancez une fois parti : mesures OBD, blocs constructeur et bus enregistrés ensemble, puis pistes de décodage et cartes moteur. " +
                            "Il s'arrête quand vous le demandez ou au contact coupé.",
                        13, c.onv, align = TextAlign.Center, lineHeight = 18, modifier = Modifier.padding(top = 4.dp),
                    )
                    Box(Modifier.padding(top = 20.dp)) {
                        FilledBtn("Lancer l'exploration", { vm.startExplore() }, enabled = connected)
                    }
                    if (!connected) T("Connectez d'abord le boîtier OBD2.", 12, c.onv, modifier = Modifier.padding(top = 6.dp))
                }
                summary?.let { s -> Results(s) }
                if ((summary?.answers ?: 0) > 0 || (summary?.ecus ?: 0) > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextBtn("Partager les résultats", {
                            scope.launch {
                                val zip = withContext(Dispatchers.IO) { runCatching { vm.explorerZip(v.id) }.getOrNull() }
                                if (zip != null) shareFile(ctx, zip, "application/zip", "Résultats de l'exploration") else vm.showSnack("Rien à partager pour l'instant")
                            }
                        }, Icons.Rounded.Share)
                        TextBtn("Tout effacer", { confirmClear = true }, Icons.Rounded.DeleteOutline, color = c.e)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv,
            title = { T("Effacer les découvertes ?", 22, lineHeight = 28) },
            text = { T("Calculateurs trouvés, réponses, enregistrements en roulant et rapport de ${v.nickname} seront supprimés. Le prochain balayage repartira de zéro.", 14, c.onv, lineHeight = 20) },
            confirmButton = { TextButton({ confirmClear = false; vm.clearExplorer(v.id) }) { T("Effacer", 14, c.e, 600) } },
            dismissButton = { TextButton({ confirmClear = false }) { T("Annuler", 14, c.p, 500) } },
        )
    }
}

@Composable
private fun Running(vm: AppViewModel) {
    val c = Cd.c
    val ex = vm.explorer
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sf1).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Radar(160.dp, true, if (ex.mode == "Enregistrement en roulant") Icons.Rounded.DirectionsCar else Icons.Rounded.Memory)
        T(ex.mode ?: "Préparation…", 18, weight = 600, modifier = Modifier.padding(top = 12.dp))
        if (ex.stepTotal > 0) T("Étape ${ex.stepIndex}/${ex.stepTotal} · ${ex.stepName}", 14, c.onv, align = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
        else T(ex.stepName, 14, c.onv)
        ex.detailText?.let { T(it, 12, c.onv, mono = true, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp), lineHeight = 16) }
        ex.progress?.let { p ->
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.sf4)) {
                Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).fillMaxSize().background(c.p))
            }
        }
        Row(
            Modifier.padding(top = 16.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.e).clickable { vm.stopExplore() }.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { Ico(Icons.Rounded.Stop, 20, c.oe); T("Arrêter", 14, c.oe, 600) }
        T("Tout ce qui est trouvé est enregistré au fur et à mesure.", 12, c.onv, modifier = Modifier.padding(top = 8.dp))
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf2).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T("Journal", 14, weight = 600)
        if (ex.lines.isEmpty()) T("…", 12, c.onv)
        ex.lines.take(60).forEachIndexed { i, l -> T(l, 12, c.on, mono = true, lineHeight = 16, modifier = Modifier.alpha(if (i < 8) 1f else .7f)) }
    }
}

@Composable
private fun Results(s: Summary) {
    val c = Cd.c
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.sf2).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T("Découvertes", 16, weight = 600, modifier = Modifier.padding(bottom = 4.dp))
        if (s.ecus == 0 && s.answers == 0) { T("Rien pour l'instant : lancez un balayage à l'arrêt.", 13, c.onv); return@Column }
        Line("Calculateurs", "${s.ecus}" + listOfNotNull(
            s.can11.takeIf { it > 0 }?.let { "$it CAN 11 bits" }, s.can29.takeIf { it > 0 }?.let { "$it CAN 29 bits" }, s.k.takeIf { it > 0 }?.let { "$it ligne K" },
        ).joinToString(", ").let { if (it.isEmpty()) "" else " ($it)" })
        Line("Réponses positives", "${s.answers}")
        Line("Blocs constructeur", "${s.blocks}")
        Line("Identifiants actifs sur le bus", "${s.bus}")
        Line("Instantanés pour la corrélation", "${s.snaps}")
        Line("Enregistrements en roulant", "${s.drives}")
        s.uds.forEach { (k, n) -> Line("UDS $k", "$n / ${Explore.UDS_TOTAL}" + if (n >= Explore.UDS_TOTAL) " ✅" else "") }
        if (s.names.isNotEmpty()) {
            T("Identifiés", 13, weight = 600, modifier = Modifier.padding(top = 6.dp))
            s.names.forEach { (k, n) -> T("$k → $n", 12, c.onv, mono = true) }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        T(label, 13, Cd.c.onv, modifier = Modifier.weight(1f))
        T(value, 13, weight = 500)
    }
}
