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
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
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
import com.cardeck.app.data.ReadinessVerdict
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
import androidx.compose.foundation.layout.Spacer
import com.cardeck.app.ui.rememberSpin
import com.cardeck.app.ui.spinning
import com.cardeck.app.ui.groupShape
import com.cardeck.app.ui.pop
import com.cardeck.app.ui.rise

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

            // Recherche → scan en cours → aucun scan → aucun défaut → codes : passage en fondu d'un état à l'autre.
            val phase = when {
                q.isNotEmpty() -> "search"
                vm.scanning -> "scan"
                scan == null -> "none"
                scan.codes.isEmpty() -> "clean"
                else -> "codes"
            }
            com.cardeck.app.ui.StateSwap(phase) { ph -> Column {
            if (ph == "search") {
                // Filtre recalculé seulement quand le texte change (pas à chaque rafraîchissement de la page) : saisie fluide.
                val res = remember(q) {
                    DtcCatalog.all.filter { (it.code + " " + it.title + " " + it.sys.label).lowercase().contains(q) }
                        .ifEmpty { if (Regex("[pcbu][0-9a-f]{4}").matches(q)) listOf(DtcCatalog.info(q.uppercase())) else emptyList() }
                }
                T(if (res.isEmpty()) "Aucun code ne correspond" else "${res.size} résultat${if (res.size > 1) "s" else ""}", 14, c.onv, 500, Modifier.padding(start = 4.dp, top = 4.dp, bottom = 10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val shown = res.take(60)
                    shown.forEachIndexed { i, d ->
                        val detected = scan?.codes?.any { it.code == d.code } == true
                        DtcRow(d, if (detected) "Détecté" else "Base de codes", null, groupShape(i, shown.size)) { vm.go(Screen.Dtc(d.code)) }
                    }
                }
            } else if (ph == "scan") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sf1).padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Radar(184.dp, true, label = "${vm.scanStep * 100 / vm.scanSteps.size} %")
                    T(vm.scanSteps.getOrElse(vm.scanStep) { "" }, 16, weight = 500, modifier = Modifier.padding(top = 16.dp))
                    vm.scanDetail?.let { T(it, 13, c.onv) }
                    com.cardeck.app.ui.LevelBar(vm.scanStep / vm.scanSteps.size.toFloat(), c.p, Modifier.padding(top = 16.dp, bottom = 20.dp))
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
            } else if (ph == "none" || scan == null) {
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
                    else {
                        Box(Modifier.padding(top = 20.dp)) { LevelPicker(vm) }
                        T(v.template.protocol, 12, c.onv, mono = true, modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(8.dp)).background(c.sf3).padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            } else if (ph == "clean" || scan.codes.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.sf1).padding(horizontal = 24.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    com.cardeck.app.ui.ShapeBadge(Icons.Rounded.Verified, com.cardeck.app.ui.cookieShape(), 104.dp, c.gc, c.ogc, 48.dp, Modifier.pop(), spinMs = 40_000)
                    T("Aucun défaut enregistré", 22, lineHeight = 28, modifier = Modifier.padding(top = 20.dp))
                    T(
                        "${scan.ecus} calculateur${if (scan.ecus > 1) "s ont" else " a"} répondu sans code défaut.",
                        14, c.onv, align = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).widthIn(max = 280.dp), lineHeight = 20,
                    )
                }
                DiagActions(vm, v, connected) { dialog = true }
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
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items.forEachIndexed { i, (fc, d) -> Box(Modifier.rise(i)) { DtcRow(d, fc.status.label, fc.ecu, groupShape(i, items.size)) { vm.go(Screen.Dtc(d.code)) } } }
                        }
                    }
                }
                DiagActions(vm, v, connected) { dialog = true }
            }
            } }

            if (!vm.scanning && q.isEmpty()) ReadinessCard(vm, v.id, connected)

            // Journal du dernier scan : tous les échanges avec le boîtier, pour comprendre un défaut non trouvé.
            if (!vm.scanning && q.isEmpty()) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val log = remember(v.id, scan?.id) { vm.scanLog(v.id) }
                if (log != null) Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TextBtn("Partager le journal du scan", { shareFile(ctx, log, "text/plain", "Journal du diagnostic") }, Icons.Rounded.Share)
                    T("Toutes les questions posées à la voiture et ses réponses, à partager pour analyser un défaut qui ne serait pas trouvé.", 12, c.onv, align = TextAlign.Center, lineHeight = 16)
                }
            }
        }
    }

    if (dialog) {
        val n = scan?.codes?.size ?: 0
        val ecus = scan?.codes?.map { it.ecu }?.distinct().orEmpty()
        AlertDialog(
            onDismissRequest = { dialog = false },
            containerColor = c.sf3, titleContentColor = c.on, textContentColor = c.onv, iconContentColor = c.e,
            icon = { Ico(Icons.Rounded.DeleteSweep, 24, c.e) },
            title = { T("Effacer les défauts ?", 24, lineHeight = 32, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (n > 0) T("${if (n > 1) "$n codes" else "1 code"} au dernier scan" + (if (ecus.isNotEmpty()) " (${ecus.joinToString(", ")})" else "") + ".", 14, c.onv, lineHeight = 20)
                    T("• Simple : codes OBD classiques du moteur, voyant moteur et données figées.", 14, c.onv, lineHeight = 20)
                    T("• Complet : en plus, les codes de chaque calculateur (ABS, airbag, carrosserie…).", 14, c.onv, lineHeight = 20)
                    T("Contact mis, moteur arrêté. Un défaut non réparé réapparaîtra.", 13, c.onv, lineHeight = 18)
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton({ dialog = false; vm.clearFaults(full = true) }) { T("Effacement complet", 14, c.e, 600) }
                    TextButton({ dialog = false; vm.clearFaults(full = false) }) { T("Effacement simple (OBD)", 14, c.e, 600) }
                }
            },
            dismissButton = { TextButton({ dialog = false }) { T("Annuler", 14, c.p, 500) } },
        )
    }
}

/**
 * « Prêt pour le contrôle technique ? » : voyant moteur et état des tests antipollution embarqués.
 * Après un effacement des défauts ou un débranchement de la batterie, ces tests repartent de zéro.
 */
@Composable
private fun ReadinessCard(vm: AppViewModel, vehicleId: Long, connected: Boolean) {
    val c = Cd.c
    val r = vm.readiness?.takeIf { it.first == vehicleId }?.second
    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(28.dp)).background(c.sf1).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Ico(Icons.AutoMirrored.Rounded.FactCheck, 22, c.p)
            T("Prêt pour le contrôle technique ?", 18, weight = 500, modifier = Modifier.weight(1f))
        }
        if (r == null) {
            T(
                "Ce contrôle vérifie le voyant moteur et les tests antipollution que la voiture fait elle-même. Après un effacement des défauts " +
                    "ou un débranchement de la batterie, ils ne sont plus prêts, et le contrôle de la pollution peut échouer.",
                13, c.onv, lineHeight = 18,
            )
        } else {
            val (icon, bg, fg, title) = when (r.verdict) {
                ReadinessVerdict.Ready -> Quad(Icons.Rounded.CheckCircle, c.gc, c.ogc, "Prête pour le contrôle technique")
                ReadinessVerdict.NotReady -> Quad(Icons.Rounded.Warning, c.wc, c.owc, "Pas encore prête : ${r.notReady.size} test${if (r.notReady.size > 1) "s" else ""} non terminé${if (r.notReady.size > 1) "s" else ""}")
                ReadinessVerdict.MilOn -> Quad(Icons.Rounded.Error, c.ec, c.oec, "Voyant moteur allumé")
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Ico(icon, 22, fg)
                T(title, 15, fg, 500, lineHeight = 20, modifier = Modifier.weight(1f))
            }
            T(
                when (r.verdict) {
                    ReadinessVerdict.Ready -> "Voyant éteint et tous les tests antipollution terminés."
                    ReadinessVerdict.NotReady -> "Roulez 2 à 3 jours en conditions variées (ville, route, voie rapide, moteur bien chaud), " +
                        "sans effacer les défauts ni débrancher la batterie, puis vérifiez à nouveau."
                    ReadinessVerdict.MilOn -> "Le voyant moteur est vérifié au contrôle technique : faites réparer le défaut " +
                        "(${r.dtcCount} code${if (r.dtcCount > 1) "s" else ""} signalé${if (r.dtcCount > 1) "s" else ""}) avant d'y aller."
                },
                13, c.onv, lineHeight = 18,
            )
            listOfNotNull(
                r.kmSinceClear?.let { "$it km depuis l'effacement des défauts" },
                r.warmupsSinceClear?.let { "$it démarrage${if (it > 1) "s" else ""} moteur chaud depuis l'effacement" },
            ).forEach { T(it, 12, c.onv) }
            if (r.monitors.isEmpty()) T("La voiture ne déclare aucun test antipollution.", 13, c.onv)
            r.monitors.sortedBy { it.ready }.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico(if (m.ready) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, 18, if (m.ready) c.g else c.w)
                    T(m.label, 14, modifier = Modifier.weight(1f))
                    T(if (m.ready) "Prêt" else "Non terminé", 13, if (m.ready) c.onv else c.w, 500)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            when {
                vm.readinessBusy -> Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.cardeck.app.ui.Spinner(18.dp, 2.dp, c.p); T("  Lecture…", 14, c.p, 500)
                }
                connected -> TextBtn(if (r == null) "Vérifier" else "Vérifier à nouveau", { vm.checkReadiness() }, Icons.Rounded.Refresh)
                else -> T("Connectez le boîtier OBD2 pour vérifier.", 12, c.onv)
            }
        }
    }
}

private data class Quad(val icon: androidx.compose.ui.graphics.vector.ImageVector, val bg: androidx.compose.ui.graphics.Color, val fg: androidx.compose.ui.graphics.Color, val title: String)

/** Choix du niveau de diagnostic : simple (codes OBD classiques) ou complet (tous les calculateurs). */
@Composable
private fun LevelPicker(vm: AppViewModel) {
    val c = Cd.c
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        com.cardeck.app.ui.Segmented(
            listOf(Triple("Simple · OBD", null, !vm.diagFull), Triple("Complet", null, vm.diagFull)),
        ) { vm.updateDiagFull(it == 1) }
        T(
            if (vm.diagFull) "Tous les calculateurs (moteur, ABS, airbag, carrosserie…) · 1 à 2 minutes"
            else "Codes OBD classiques (moteur, voyant, données figées) · quelques secondes",
            12, c.onv, align = TextAlign.Center, lineHeight = 16, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Après un scan : niveau, nouveau scan et effacement des défauts (pour toutes les voitures). */
@Composable
private fun DiagActions(vm: AppViewModel, v: com.cardeck.app.data.VehicleProfile, connected: Boolean, onClear: () -> Unit) {
    val c = Cd.c
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (connected) LevelPicker(vm)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1f).height(48.dp).alpha(if (connected) 1f else .38f).clip(RoundedCornerShape(24.dp)).border(1.dp, c.ol, RoundedCornerShape(24.dp)).clickable(enabled = connected) { vm.startScan() },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) { Ico(Icons.Rounded.Refresh, 18, c.p); T("  Nouveau scan", 14, c.p, 500) }
            Row(
                Modifier.weight(1f).height(48.dp).alpha(if (connected && !vm.clearing) 1f else .38f).clip(RoundedCornerShape(24.dp)).background(c.e).clickable(enabled = connected && !vm.clearing, onClick = onClear),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                if (vm.clearing) com.cardeck.app.ui.Spinner(18.dp, 2.dp, c.oe) else Ico(Icons.Rounded.DeleteSweep, 18, c.oe)
                T(if (vm.clearing) "  Effacement…" else "  Effacer les défauts", 14, c.oe, 500)
            }
        }
        // Modèle en lecture seule : rien n'est écrit dans la voiture, à part l'effacement des codes demandé ici.
        if (v.template.readOnly) T("${v.template.model} : lecture seule, sauf l'effacement des codes défaut quand vous le demandez.", 12, c.onv, lineHeight = 16)
        if (!connected) ConnectAction(vm, v.adapter != null)
    }
}

@Composable
private fun ScanButton(enabled: Boolean, onClick: () -> Unit) {
    val c = Cd.c
    val haptics = Cd.haptics
    Box(Modifier.size(196.dp).alpha(if (enabled) 1f else .38f), contentAlignment = Alignment.Center) {
        if (enabled) com.cardeck.app.ui.PulseRing(c.p, 140.dp, periodMs = 2400)
        Box(Modifier.size(184.dp).spinning(rememberSpin(40_000)).clip(com.cardeck.app.ui.cookieShape()).background(c.pc))
        Column(
            Modifier.size(160.dp).clip(CircleShape).background(c.p).clickable(enabled = enabled) { haptics.confirm(); onClick() },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Ico(Icons.Rounded.Troubleshoot, 56, c.op)
            com.cardeck.app.ui.Text("Scanner", style = com.cardeck.app.ui.rf(15, 20, 600), color = c.op)
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
private fun DtcRow(d: DtcInfo, tag: String, ecu: String?, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp), onClick: () -> Unit) {
    val c = Cd.c
    val s = sevStyle(d.sev, c)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.sf1).clickable(onClick = onClick).padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        com.cardeck.app.ui.LeadingIcon(s.icon, s.bg, s.fg)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T(d.code, 15, weight = 700, mono = true)
                Box(
                    Modifier.height(24.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, c.olv, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { com.cardeck.app.ui.Text(tag, style = com.cardeck.app.ui.rf(11, 14, 600), color = c.onv, maxLines = 1) }
                if (ecu != null) T(ecu, 12, c.onv, maxLines = 1)
            }
            com.cardeck.app.ui.Text(d.title, Modifier.padding(top = 2.dp), style = com.cardeck.app.ui.rf(14, 20), color = c.on)
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
            Row(Modifier.fillMaxWidth().rise(0).clip(RoundedCornerShape(24.dp)).background(c.sf1).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                com.cardeck.app.ui.LeadingIcon(d.sys.icon, c.pc, c.opc)
                Column { T("Système concerné", 12, c.onv); T(d.sys.label, 16, weight = 600) }
            }
            Column(Modifier.fillMaxWidth().rise(1).clip(RoundedCornerShape(24.dp)).background(c.sf1).padding(20.dp)) {
                com.cardeck.app.ui.Text("Description", Modifier.padding(bottom = 8.dp), style = com.cardeck.app.ui.rf(14, 20, 600), color = c.p)
                T(
                    d.desc ?: if (d.known) d.title + "." else "Ce code n'est pas encore décrit dans la base de CarDeck.",
                    14, c.onv, lineHeight = 22,
                )
            }
            if (d.causes.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().rise(2).clip(RoundedCornerShape(24.dp)).background(c.sf1).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.cardeck.app.ui.Text("Causes possibles", Modifier.padding(bottom = 2.dp), style = com.cardeck.app.ui.rf(14, 20, 600), color = c.p)
                    d.causes.forEachIndexed { i, t ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(c.pc), contentAlignment = Alignment.Center) { T("${i + 1}", 12, c.opc, 700) }
                            T(t, 14, modifier = Modifier.padding(top = 2.dp), lineHeight = 20)
                        }
                    }
                }
            }
            if (d.recos.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().rise(3).clip(RoundedCornerShape(24.dp)).background(c.pc).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.cardeck.app.ui.Text("Recommandations", Modifier.padding(bottom = 2.dp), style = com.cardeck.app.ui.rf(14, 20, 600), color = c.opc)
                    d.recos.forEach { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Ico(Icons.Rounded.Check, 20, c.opc); T(r, 14, c.opc, lineHeight = 20, modifier = Modifier.weight(1f)) }
                    }
                }
            }
            val ff = if (scan?.ffCode == code) scan.ff else null
            if (ff != null) {
                Column(Modifier.fillMaxWidth().rise(4), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.cardeck.app.ui.SectionLabel("Données figées")
                    T("Valeurs enregistrées au moment du défaut", 12, c.onv, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    ff.chunked(2).forEachIndexed { r, pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEachIndexed { k, value -> com.cardeck.app.ui.ValueTile(ffLabels.getOrElse(r * 2 + k) { "" }, value, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
