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
import androidx.compose.material.icons.rounded.SettingsInputHdmi
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardeck.app.AppViewModel
import com.cardeck.app.Screen
import com.cardeck.app.data.DtcCatalog
import com.cardeck.app.data.DtcInfo
import com.cardeck.app.data.Severity
import com.cardeck.app.data.agoLabel
import com.cardeck.app.ui.BackBar
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.CdChip
import com.cardeck.app.ui.CdColors
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.Radar
import com.cardeck.app.ui.ScreenTitle
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn

private data class SevStyle(val icon: ImageVector, val bg: Color, val fg: Color, val accent: Color)

private fun sevStyle(s: Severity, c: CdColors) = when (s) {
    Severity.Crit -> SevStyle(Icons.Rounded.Error, c.ec, c.oec, c.e)
    Severity.Mid -> SevStyle(Icons.Rounded.Warning, c.wc, c.owc, c.w)
    Severity.Low -> SevStyle(Icons.Rounded.Info, c.tc, c.otc, c.t)
}

@Composable
fun DiagScreen(vm: AppViewModel) {
    val c = Cd.c
    val v = rememberActiveVehicle(vm) ?: return
    val scan = rememberLastScan(vm, v.id)
    val (_, connKind) = connStatus(vm, v)
    val connected = connKind == ConnKind.Ok
    val scroll = rememberScrollState()
    var dialog by remember { mutableStateOf(false) }
    val q = vm.query.trim().lowercase()
    val sub = when {
        vm.scanning -> "Scan en cours…"
        scan == null -> "Aucun scan pour ${v.nickname}"
        else -> "${scan.codes.size} code${if (scan.codes.size > 1) "s" else ""} · scan ${agoLabel(scan.t)}"
    }
    Column(Modifier.fillMaxSize()) {
        BackBar("Diagnostic", scroll.value > 48, { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            ScreenTitle("Diagnostic", sub)
            // Recherche dans la base de codes
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
                val res = DtcCatalog.all.filter { (it.code + " " + it.title + " " + it.sys.label).lowercase().contains(q) }
                    .ifEmpty { if (Regex("[pcbu][0-9a-f]{4}").matches(q)) listOf(DtcCatalog.info(q.uppercase())) else emptyList() }
                T(if (res.isEmpty()) "Aucun code ne correspond" else "${res.size} résultat${if (res.size > 1) "s" else ""}", 14, c.onv, 500, Modifier.padding(start = 4.dp, top = 4.dp, bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    res.take(60).forEach { d ->
                        val detected = scan?.codes?.any { it.code == d.code } == true
                        DtcRow(d, if (detected) "Détecté" else "Base de codes", null) { vm.go(Screen.Dtc(d.code)) }
                    }
                }
            } else if (vm.scanning) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sf1).padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Radar(184.dp, true, label = "${vm.scanStep * 100 / vm.scanSteps.size} %")
                    T(vm.scanSteps[vm.scanStep], 16, weight = 500, modifier = Modifier.padding(top = 16.dp))
                    Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.sf4)) {
                        Box(Modifier.fillMaxWidth(vm.scanStep / vm.scanSteps.size.toFloat()).fillMaxSize().background(c.p))
                    }
                    vm.scanSteps.forEachIndexed { i, name ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Ico(
                                if (i < vm.scanStep) Icons.Rounded.CheckCircle else if (i == vm.scanStep) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, 18,
                                if (i < vm.scanStep) c.g else if (i == vm.scanStep) c.p else c.olv,
                            )
                            T(name, 13, if (i <= vm.scanStep) c.on else c.onv)
                        }
                    }
                }
            } else if (scan == null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sf1).padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ScanButton(connected) { vm.startScan() }
                    T("Scanner les défauts", 22, lineHeight = 28, modifier = Modifier.padding(top = 32.dp))
                    T(
                        if (connected) "Interroge les calculateurs OBD2 du véhicule. Moteur allumé ou contact mis." else "Le boîtier OBD2 de ce véhicule n'est pas connecté.",
                        14, c.onv, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).widthIn(max = 280.dp), lineHeight = 20,
                    )
                    if (!connected) ConnectAction(vm, v.adapter != null)
                    else T(v.template.protocol, 12, c.onv, mono = true, modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(8.dp)).background(c.sf3).padding(horizontal = 10.dp, vertical = 6.dp))
                }
            } else if (scan.codes.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sf1).padding(horizontal = 24.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(96.dp).clip(CircleShape).background(c.gc), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Verified, 48, c.ogc) }
                    T("Aucun défaut enregistré", 22, lineHeight = 28, modifier = Modifier.padding(top = 20.dp))
                    T(
                        "${scan.ecus} calculateur${if (scan.ecus > 1) "s ont" else " a"} répondu sans code défaut.",
                        14, c.onv, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).widthIn(max = 280.dp), lineHeight = 20,
                    )
                    if (connected) {
                        Row(
                            Modifier.padding(top = 24.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).background(c.sc).clickable { vm.startScan() }.padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) { Ico(Icons.Rounded.Refresh, 18, c.osc); T("Nouveau scan", 14, c.osc, 500) }
                    } else ConnectAction(vm, v.adapter != null)
                }
            } else {
                val infos = scan.codes.map { it to DtcCatalog.info(it.code) }
                val counts = Severity.entries.associateWith { s -> infos.count { it.second.sev == s } }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CdChip("Tous · ${infos.size}", vm.filter == "all") { vm.filter = "all" }
                    Severity.entries.filter { (counts[it] ?: 0) > 0 }.forEach { s -> CdChip("${s.label} · ${counts[s]}", vm.filter == s.name) { vm.filter = s.name } }
                }
                Severity.entries.forEach { sev ->
                    val items = infos.filter { it.second.sev == sev && (vm.filter == "all" || vm.filter == sev.name) }
                    if (items.isNotEmpty()) {
                        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(sevStyle(sev, c).accent))
                            T(sev.label, 14, weight = 500)
                            T("${items.size}", 14, c.onv)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items.forEach { (fc, d) -> DtcRow(d, fc.status.label, fc.ecu) { vm.go(Screen.Dtc(d.code)) } }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.weight(1f).height(48.dp).alpha(if (connected) 1f else .38f).clip(RoundedCornerShape(24.dp)).border(1.dp, c.ol, RoundedCornerShape(24.dp)).clickable(enabled = connected) { vm.startScan() },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    ) { Ico(Icons.Rounded.Refresh, 18, c.p); T("  Nouveau scan", 14, c.p, 500) }
                    Row(
                        Modifier.weight(1f).height(48.dp).alpha(if (connected) 1f else .38f).clip(RoundedCornerShape(24.dp)).background(c.e).clickable(enabled = connected) { dialog = true },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    ) { Ico(Icons.Rounded.DeleteSweep, 18, c.oe); T("  Effacer les défauts", 14, c.oe, 500) }
                }
                if (!connected) ConnectAction(vm, v.adapter != null)
            }

        }
    }

    if (dialog) {
        val n = scan?.codes?.size ?: 0
        AlertDialog(
            onDismissRequest = { dialog = false },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv, iconContentColor = c.e,
            icon = { Ico(Icons.Rounded.DeleteSweep, 24, c.e) },
            title = { T("Effacer les défauts ?", 24, lineHeight = 32, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = { T("${if (n > 1) "Les $n codes" else "Le code"} et les données figées seront supprimés de la mémoire des calculateurs, et le voyant moteur s'éteindra. Un défaut non réparé réapparaîtra. Contact mis, moteur arrêté.", 14, c.onv, lineHeight = 20) },
            confirmButton = { TextButton({ dialog = false; vm.clearFaults() }) { T("Effacer", 14, c.e, 600) } },
            dismissButton = { TextButton({ dialog = false }) { T("Annuler", 14, c.p, 500) } },
        )
    }
}

@Composable
private fun ScanButton(enabled: Boolean, onClick: () -> Unit) {
    val c = Cd.c
    Box(Modifier.size(180.dp).alpha(if (enabled) 1f else .38f), contentAlignment = Alignment.Center) {
        Box(Modifier.size(180.dp).clip(CircleShape).background(c.pc))
        Column(
            Modifier.size(168.dp).clip(CircleShape).background(c.p).clickable(enabled = enabled, onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Ico(Icons.Rounded.Troubleshoot, 56, c.op)
            T("Scanner", 14, c.op, 600)
        }
    }
}

@Composable
private fun ConnectAction(vm: AppViewModel, hasAdapter: Boolean) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(Icons.Rounded.SettingsInputHdmi, 18, Cd.c.p)
        if (hasAdapter) TextBtn("Se connecter au boîtier", { vm.connectNow() }) else TextBtn("Associer un boîtier", { vm.pairActive() })
    }
}

@Composable
private fun DtcRow(d: DtcInfo, tag: String, ecu: String?, onClick: () -> Unit) {
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
                T(tag, 11, c.onv, 500, Modifier.clip(RoundedCornerShape(6.dp)).background(c.sf3).padding(horizontal = 6.dp, vertical = 2.dp))
                if (ecu != null) T(ecu, 12, c.onv, maxLines = 1)
            }
            T(d.title, 14, c.on, modifier = Modifier.padding(top = 2.dp), lineHeight = 20)
        }
        Ico(Icons.AutoMirrored.Rounded.KeyboardArrowRight, 24, c.onv)
    }
}

@Composable
fun DtcDetailScreen(vm: AppViewModel, code: String) {
    val c = Cd.c
    val v = rememberActiveVehicle(vm)
    val scan = rememberLastScan(vm, v?.id)
    val d = DtcCatalog.info(code)
    val found = scan?.codes?.find { it.code == code }
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
                if (found != null) T("${found.status.label} · ${found.ecu} · scan ${agoLabel(scan!!.t)}", 13, c.onv, modifier = Modifier.padding(top = 6.dp))
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf2).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.sc), contentAlignment = Alignment.Center) { Ico(d.sys.icon, 22, c.osc) }
                Column { T("Système concerné", 12, c.onv); T(d.sys.label, 16, weight = 500) }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf1).padding(20.dp)) {
                T("Description", 16, weight = 500, modifier = Modifier.padding(bottom = 8.dp))
                T(
                    d.desc ?: if (d.known) d.title + "." else "Ce code n'est pas encore décrit dans la base de CarDeck.",
                    14, c.onv, lineHeight = 22,
                )
            }
            if (d.causes.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.sf1).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("Causes possibles", 16, weight = 500, modifier = Modifier.padding(bottom = 2.dp))
                    d.causes.forEachIndexed { i, t ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(c.sf3), contentAlignment = Alignment.Center) { T("${i + 1}", 12, weight = 600) }
                            T(t, 14, modifier = Modifier.padding(top = 2.dp), lineHeight = 20)
                        }
                    }
                }
            }
            if (d.recos.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.pc).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("Recommandations", 16, c.opc, 500, Modifier.padding(bottom = 2.dp))
                    d.recos.forEach { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Ico(Icons.Rounded.Check, 20, c.opc); T(r, 14, c.opc, lineHeight = 20, modifier = Modifier.weight(1f)) }
                    }
                }
            }
            val ff = if (scan?.ffCode == code) scan.ff else null
            if (ff != null) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, c.olv, RoundedCornerShape(20.dp)).padding(20.dp)) {
                    T("Données figées", 16, weight = 500)
                    T("Valeurs enregistrées au moment du défaut", 12, c.onv, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
                    ff.chunked(2).forEachIndexed { r, pair ->
                        Row(Modifier.fillMaxWidth().padding(bottom = if (r == 0) 16.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            pair.forEachIndexed { k, value ->
                                Column(Modifier.weight(1f)) { T(ffLabels.getOrElse(r * 2 + k) { "" }, 12, c.onv); T(value, 20, weight = 500, modifier = Modifier.padding(top = 2.dp), lineHeight = 28) }
                            }
                        }
                    }
                }
            }
        }
    }
}
