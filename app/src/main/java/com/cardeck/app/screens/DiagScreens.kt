package com.cardeck.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardeck.app.AppViewModel
import com.cardeck.app.ScanPhase
import com.cardeck.app.Screen
import com.cardeck.app.data.DTC_DB
import com.cardeck.app.data.Dtc
import com.cardeck.app.data.SYSTEMS
import com.cardeck.app.data.Severity
import com.cardeck.app.ui.BackBar
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.CdChip
import com.cardeck.app.ui.CdColors
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.Radar
import com.cardeck.app.ui.ScreenTitle
import com.cardeck.app.ui.T
import kotlin.math.roundToInt

private data class SevStyle(val icon: ImageVector, val bg: Color, val fg: Color, val accent: Color)

private fun sevStyle(s: Severity, c: CdColors) = when (s) {
    Severity.Crit -> SevStyle(Icons.Rounded.Error, c.ec, c.oec, c.e)
    Severity.Mid -> SevStyle(Icons.Rounded.Warning, c.wc, c.owc, c.w)
    Severity.Low -> SevStyle(Icons.Rounded.Info, c.tc, c.otc, c.t)
}

@Composable
fun DiagScreen(vm: AppViewModel) {
    val c = Cd.c
    val scroll = rememberScrollState()
    var dialog by remember { mutableStateOf(false) }
    val phase = vm.phase
    val q = vm.query.trim().lowercase()
    val sub = when (phase) {
        ScanPhase.Results -> "6 codes détectés · scan à 09:38"
        ScanPhase.Clean -> "Aucun code · scan à 09:41"
        ScanPhase.Scanning -> "Scan en cours…"
        ScanPhase.Idle -> "Dernier scan il y a 3 jours"
    }
    Column(Modifier.fillMaxSize()) {
        BackBar("Diagnostic", scroll.value > 48, { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            ScreenTitle("Diagnostic", sub)
            // Recherche
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp).clip(RoundedCornerShape(28.dp)).background(c.sf3).padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Ico(Icons.Rounded.Search, 24, c.onv)
                Box(Modifier.weight(1f)) {
                    if (vm.query.isEmpty()) T("Rechercher un code (ex. P0420)", 16, c.onv)
                    BasicTextField(
                        value = vm.query, onValueChange = { vm.query = it }, singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = c.on), cursorBrush = SolidColor(c.p),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (vm.query.isNotEmpty()) {
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable { vm.query = "" }, contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Close, 22, c.onv) }
                }
            }

            if (q.isNotEmpty()) {
                val res = DTC_DB.filter { (it.code + " " + it.title + " " + it.sys).lowercase().contains(q) }
                T(if (res.isEmpty()) "Aucun code ne correspond" else "${res.size} résultat${if (res.size > 1) "s" else ""}", 14, c.onv, 500, Modifier.padding(start = 4.dp, top = 4.dp, bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    res.forEach { d -> DtcRow(d, if (d.found && phase == ScanPhase.Results) "Détecté" else "Base de codes", true) { vm.go(Screen.Dtc(d.code)) } }
                }
            } else when (phase) {
                ScanPhase.Idle -> Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sf1).padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(180.dp).clip(CircleShape).background(c.pc))
                        Column(
                            Modifier.size(168.dp).clip(CircleShape).background(c.p).clickable { vm.startScan() },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Ico(Icons.Rounded.Troubleshoot, 56, c.op)
                            T("Scanner", 14, c.op, 600)
                        }
                    }
                    T("Scanner les défauts", 22, lineHeight = 28, modifier = Modifier.padding(top = 32.dp))
                    T("Interroge les 8 calculateurs du véhicule. Moteur allumé ou contact mis.", 14, c.onv, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).widthIn(max = 280.dp), lineHeight = 20)
                    T("ISO 15765-4 · CAN 11 bits 500 kb/s", 12, c.onv, mono = true, modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(8.dp)).background(c.sf3).padding(horizontal = 10.dp, vertical = 6.dp))
                }

                ScanPhase.Scanning -> {
                    val si = minOf(SYSTEMS.size - 1, (vm.scanP / 100f * SYSTEMS.size).toInt())
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sf1).padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Radar(184.dp, true, label = "${vm.scanP.roundToInt()} %")
                        T("Analyse : ${SYSTEMS[si]}", 16, weight = 500, modifier = Modifier.padding(top = 16.dp))
                        Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.sf4)) {
                            Box(Modifier.fillMaxWidth((vm.scanP / 100f).coerceIn(0f, 1f)).fillMaxSize().background(c.p))
                        }
                        SYSTEMS.chunked(2).forEachIndexed { r, pair ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEachIndexed { k, name ->
                                    val i = r * 2 + k
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Ico(
                                            if (i < si) Icons.Rounded.CheckCircle else if (i == si) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, 18,
                                            if (i < si) c.g else if (i == si) c.p else c.olv,
                                        )
                                        T(name, 13, if (i <= si) c.on else c.onv)
                                    }
                                }
                            }
                        }
                    }
                }

                ScanPhase.Results -> {
                    val found = DTC_DB.filter { it.found }
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("all" to "Tous · 6", "crit" to "Critique · 2", "mid" to "Moyen · 2", "low" to "Mineur · 2").forEach { (k, l) ->
                            CdChip(l, vm.filter == k) { vm.filter = k }
                        }
                    }
                    listOf(Severity.Crit, Severity.Mid, Severity.Low).forEach { sev ->
                        val items = found.filter { it.sev == sev && (vm.filter == "all" || vm.filter == sev.name.lowercase()) }
                        if (items.isNotEmpty()) {
                            Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(sevStyle(sev, c).accent))
                                T(sev.label, 14, weight = 500)
                                T("${items.size}", 14, c.onv)
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items.forEach { d -> DtcRow(d, d.sys, false) { vm.go(Screen.Dtc(d.code)) } }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).border(1.dp, c.ol, RoundedCornerShape(24.dp)).clickable { vm.startScan() },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                        ) { Ico(Icons.Rounded.Refresh, 18, c.p); T("  Nouveau scan", 14, c.p, 500) }
                        Row(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.e).clickable { dialog = true },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                        ) { Ico(Icons.Rounded.DeleteSweep, 18, c.oe); T("  Effacer les défauts", 14, c.oe, 500) }
                    }
                }

                ScanPhase.Clean -> Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sf1).padding(horizontal = 24.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(c.gc), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Verified, 48, c.ogc) }
                    T("Aucun défaut enregistré", 22, lineHeight = 28, modifier = Modifier.padding(top = 20.dp))
                    T("Les 8 calculateurs ont répondu sans code défaut. Le voyant moteur est éteint.", 14, c.onv, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).widthIn(max = 280.dp), lineHeight = 20)
                    Row(
                        Modifier.padding(top = 24.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.sc).clickable { vm.startScan() }.padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) { Ico(Icons.Rounded.Refresh, 18, c.osc); T("Nouveau scan", 14, c.osc, 500) }
                }
            }
        }
    }

    if (dialog) {
        AlertDialog(
            onDismissRequest = { dialog = false },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv, iconContentColor = c.e,
            icon = { Ico(Icons.Rounded.DeleteSweep, 24, c.e) },
            title = { T("Effacer les défauts ?", 24, lineHeight = 32, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = { T("Les 6 codes et leurs données figées seront supprimés de la mémoire des calculateurs, et le voyant moteur s'éteindra. Un défaut non réparé réapparaîtra.", 14, c.onv, lineHeight = 20) },
            confirmButton = { TextButton({ dialog = false; vm.clearFaults() }) { T("Effacer", 14, c.e, 600) } },
            dismissButton = { TextButton({ dialog = false }) { T("Annuler", 14, c.p, 500) } },
        )
    }
}

@Composable
private fun DtcRow(d: Dtc, trailingLabel: String, tag: Boolean, onClick: () -> Unit) {
    val c = Cd.c
    val s = sevStyle(d.sev, c)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.sf1).clickable(onClick = onClick).padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(s.bg), contentAlignment = Alignment.Center) { Ico(s.icon, 22, s.fg) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T(d.code, 15, weight = 700, mono = true)
                if (tag) T(trailingLabel, 11, c.onv, 500, Modifier.clip(RoundedCornerShape(6.dp)).background(c.sf3).padding(horizontal = 6.dp, vertical = 2.dp))
                else T(trailingLabel, 12, c.onv, maxLines = 1)
            }
            T(d.title, 14, c.on, modifier = Modifier.padding(top = 2.dp), lineHeight = 20)
        }
        Ico(Icons.AutoMirrored.Rounded.KeyboardArrowRight, 24, c.onv)
    }
}

@Composable
fun DtcDetailScreen(vm: AppViewModel, code: String) {
    val c = Cd.c
    val d = DTC_DB.find { it.code == code } ?: DTC_DB[0]
    val s = sevStyle(d.sev, c)
    val scroll = rememberScrollState()
    val ffLabels = listOf("Régime", "Vitesse", "Temp. moteur", "Charge")
    Column(Modifier.fillMaxSize()) {
        BackBar(d.code, scroll.value > 48, { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    T(d.code, 44, weight = 700, mono = true, lineHeight = 52, spacing = -1f)
                    Row(
                        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(s.bg).padding(start = 8.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) { Ico(s.icon, 18, s.fg); T(d.sev.label, 14, s.fg, 500) }
                }
                T(d.title, 24, lineHeight = 32, modifier = Modifier.padding(top = 8.dp))
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf2).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.sc), contentAlignment = Alignment.Center) { Ico(d.sysIcon, 22, c.osc) }
                Column { T("Système concerné", 12, c.onv); T(d.sys, 16, weight = 500) }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf1).padding(20.dp)) {
                T("Description", 16, weight = 500, modifier = Modifier.padding(bottom = 8.dp))
                T(d.desc, 14, c.onv, lineHeight = 22)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf1).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Causes possibles", 16, weight = 500, modifier = Modifier.padding(bottom = 2.dp))
                d.causes.forEachIndexed { i, t ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(c.sf3), contentAlignment = Alignment.Center) { T("${i + 1}", 12, weight = 600) }
                        T(t, 14, modifier = Modifier.padding(top = 2.dp), lineHeight = 20)
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.pc).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Recommandations", 16, c.opc, 500, Modifier.padding(bottom = 2.dp))
                d.recos.forEach { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Ico(Icons.Rounded.Check, 20, c.opc); T(r, 14, c.opc, lineHeight = 20, modifier = Modifier.weight(1f)) }
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, c.olv, RoundedCornerShape(20.dp)).padding(20.dp)) {
                T("Données figées", 16, weight = 500)
                T("Valeurs enregistrées au moment du défaut", 12, c.onv, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
                val ff = d.ff ?: List(4) { "—" }
                ff.chunked(2).forEachIndexed { r, pair ->
                    Row(Modifier.fillMaxWidth().padding(bottom = if (r == 0) 16.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        pair.forEachIndexed { k, v ->
                            Column(Modifier.weight(1f)) { T(ffLabels[r * 2 + k], 12, c.onv); T(v, 20, weight = 500, modifier = Modifier.padding(top = 2.dp), lineHeight = 28) }
                        }
                    }
                }
            }
        }
    }
}
