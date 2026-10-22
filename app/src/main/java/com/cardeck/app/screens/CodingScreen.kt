package com.cardeck.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.data.CodingBackup
import com.cardeck.app.data.CodingDef
import com.cardeck.app.data.dayLabel
import com.cardeck.app.data.timeHm
import com.cardeck.app.ui.BackBar
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.CdChip
import com.cardeck.app.ui.FilledBtn
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.ScreenTitle
import com.cardeck.app.ui.SectionLabel
import com.cardeck.app.ui.Spinner
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CodingScreen(vm: AppViewModel) {
    val c = Cd.c
    val v = rememberActiveVehicle(vm) ?: return
    val tpl = v.template
    val (_, connKind) = connStatus(vm, v)
    val connected = connKind == ConnKind.Ok
    val scroll = rememberScrollState()
    val changes by vm.repo.changes.collectAsState()
    var backups by remember(v.id) { mutableStateOf<List<CodingBackup>>(emptyList()) }
    var confirmApply by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    LaunchedEffect(v.id) { vm.codingFor(v.id) }
    LaunchedEffect(v.id, changes) { backups = withContext(Dispatchers.IO) { vm.repo.codingBackups(v.id) } }

    Column(Modifier.fillMaxSize()) {
        BackBar("Codage", scroll.value > 48, { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            ScreenTitle("Codage", "${v.nickname} · ${tpl.fullName}")

            if (tpl.codings.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, c.olv, RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(c.sf3), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Build, 30, c.onv) }
                    val why = tpl.codingImpossible
                    T(if (why != null) "Le codage n'est pas possible" else "Fonctions de codage à venir", 18, weight = 500, modifier = Modifier.padding(top = 16.dp))
                    T(
                        why ?: "Les fonctions de codage de la ${tpl.fullName} seront ajoutées avec les données du modèle (calculateurs, identifiants et valeurs).",
                        14, c.onv, align = TextAlign.Center, lineHeight = 20, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else {
                // Lecture
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.sf2).padding(16.dp)) {
                    val busy = vm.codingBusy
                    if (busy != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Spinner(); T(busy, 14, weight = 500)
                        }
                        T("Ne coupez pas le contact et ne débranchez pas le boîtier.", 13, c.onv, modifier = Modifier.padding(top = 8.dp), lineHeight = 18)
                    } else {
                        T(
                            when {
                                !connected -> "Connectez le boîtier OBD2 pour lire le codage."
                                vm.codingData.isEmpty() -> "Mettez le contact (moteur arrêté) puis lisez le codage actuel de la voiture."
                                else -> "Codage lu. Choisissez les nouvelles valeurs puis appliquez."
                            },
                            14, c.onv, lineHeight = 20,
                        )
                        if (connected) TextBtn(if (vm.codingData.isEmpty()) "Lire le codage" else "Relire le codage", { vm.readCoding() }, Icons.Rounded.Memory)
                        else TextBtn("Se connecter au boîtier", { vm.connectNow() })
                    }
                }

                vm.codingReport?.let { (ok, msg) ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(if (ok) c.gc else c.ec).padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Ico(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, 22, if (ok) c.ogc else c.oec)
                        T(msg, 13, if (ok) c.ogc else c.oec, lineHeight = 18)
                    }
                }

                tpl.codings.groupBy { it.category }.forEach { (cat, defs) ->
                    SectionLabel(cat)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        defs.forEach { d -> CodingRow(vm, d) }
                    }
                }

                if (backups.isNotEmpty()) {
                    SectionLabel("Sauvegardes")
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf1).padding(16.dp)) {
                        T("Codage d'origine sauvegardé avant la première modification :", 13, c.onv, lineHeight = 18)
                        backups.forEach { b -> T("• ${b.module} · ${dayLabel(b.t)} ${timeHm(b.t)}", 13, modifier = Modifier.padding(top = 4.dp)) }
                        if (connected && vm.codingBusy == null) TextBtn("Restaurer le codage d'origine", { confirmRestore = true }, Icons.Rounded.Restore)
                    }
                }
            }
        }
        if (tpl.codings.isNotEmpty() && vm.codingPending.isNotEmpty()) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
                val n = vm.codingPending.size
                FilledBtn("Appliquer $n modification${if (n > 1) "s" else ""}", { confirmApply = true }, enabled = connected && vm.codingBusy == null)
            }
        }
    }

    if (confirmApply) {
        val modules = tpl.codings.filter { it.id in vm.codingPending }.map { it.module }.distinct().joinToString(", ")
        AlertDialog(
            onDismissRequest = { confirmApply = false },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv, iconContentColor = c.w,
            icon = { Ico(Icons.Rounded.Warning, 24, c.w) },
            title = { T("Écrire le codage ?", 22, lineHeight = 28, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                T(
                    "Module${if (modules.contains(',')) "s" else ""} concerné${if (modules.contains(',')) "s" else ""} : $modules.\n\n" +
                        "Le codage d'origine est sauvegardé automatiquement et pourra être restauré. Contact mis, moteur arrêté, batterie chargée ; " +
                        "ne coupez pas le contact pendant l'écriture.",
                    14, c.onv, lineHeight = 20,
                )
            },
            confirmButton = { TextButton({ confirmApply = false; vm.applyCoding() }) { T("Écrire", 14, c.p, 600) } },
            dismissButton = { TextButton({ confirmApply = false }) { T("Annuler", 14, c.p, 500) } },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv,
            title = { T("Restaurer le codage d'origine ?", 22, lineHeight = 28) },
            text = { T("Toutes les modifications de codage faites avec CarDeck sur ce véhicule seront annulées.", 14, c.onv, lineHeight = 20) },
            confirmButton = { TextButton({ confirmRestore = false; vm.restoreCoding() }) { T("Restaurer", 14, c.p, 600) } },
            dismissButton = { TextButton({ confirmRestore = false }) { T("Annuler", 14, c.p, 500) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodingRow(vm: AppViewModel, d: CodingDef) {
    val c = Cd.c
    val data = vm.codingData[d.blockKey]
    val error = vm.codingErrors[d.blockKey]
    val current = data?.let { d.valueIn(it) }
    val chosen = vm.codingPending[d.id] ?: current
    Column(Modifier.fillMaxWidth().background(c.sf1).padding(16.dp)) {
        T(d.label, 16, weight = 500)
        T(d.description, 13, c.onv, lineHeight = 18, modifier = Modifier.padding(top = 2.dp))
        T(d.module, 12, c.onv, modifier = Modifier.padding(top = 4.dp))
        when {
            error != null -> T(error, 13, c.e, modifier = Modifier.padding(top = 8.dp), lineHeight = 18)
            data == null -> T("Valeur actuelle : non lue", 13, c.onv, modifier = Modifier.padding(top = 8.dp))
            else -> {
                val known = d.options.any { it.value == current }
                if (!known) T("Valeur actuelle : 0x%02X (non répertoriée)".format(current ?: 0), 13, c.onv, modifier = Modifier.padding(top = 8.dp))
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.options.forEach { o ->
                        CdChip(o.label + if (o.value == current) " (actuel)" else "", chosen == o.value) {
                            if (vm.codingBusy == null) vm.choose(d.id, o.value, current)
                        }
                    }
                }
            }
        }
    }
}
