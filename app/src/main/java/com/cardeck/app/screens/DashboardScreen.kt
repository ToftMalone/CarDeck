package com.cardeck.app.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.data.DashGauges
import com.cardeck.app.data.StdPids
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val c = Cd.c
    val v = rememberActiveVehicle(vm) ?: return
    val (_, connKind) = connStatus(vm, v)
    val connected = connKind == ConnKind.Ok
    val supported by vm.obd.supported.collectAsState()
    val values = remember { mutableStateMapOf<String, Double>() }
    val sport = vm.dashSport
    val ids = if (sport) DashGauges.SPORT_IDS else DashGauges.STANDARD_IDS

    // Lecture en continu des seules données du tableau de bord affiché (rafraîchissement rapide).
    LaunchedEffect(connected, sport) {
        if (!connected) return@LaunchedEffect
        var cycle = 0
        while (true) {
            var sup = vm.obd.supported.value
            if (sup == null) {
                sup = withContext(Dispatchers.IO) { vm.obd.use { it.supportedPids() } }
                if (sup.isNullOrEmpty()) { delay(5000); continue }
                vm.obd.supported.value = sup
            }
            val defs = ids.mapNotNull { id -> StdPids.all.find { it.id == id } }
                .filter { StdPids.pidNumber(it) in sup && (it.id !in DashGauges.SLOW_IDS || cycle % 15 == 0) }
            val readVolt = !sport && 0x42 !in sup && cycle % 10 == 0
            val out = withContext(Dispatchers.IO) {
                vm.obd.use { e -> e.readAll(defs) to (if (readVolt) e.voltage() else null) }
            }
            out?.first?.forEach { (id, x) -> values[id] = x }
            out?.second?.let { values["elm_volt"] = it }
            out?.let { (read, volt) -> vm.obd.publish(read + listOfNotNull(volt?.let { "elm_volt" to it })) }
            cycle++
            delay(80)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, { vm.back() })
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.pc), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.DirectionsCar, 22, c.opc) }
            Column(Modifier.weight(1f)) {
                T(v.nickname, 16, weight = 500, lineHeight = 22, maxLines = 1)
                T(if (sport) "Tableau de bord Sport" else "Tableau de bord", 12, c.onv, lineHeight = 16, maxLines = 1)
            }
            ModeSelector(sport) { vm.updateDashSport(it) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            val sup = supported
            when {
                !connected -> {
                    EmptyNote(Icons.Rounded.HourglassEmpty, "Boîtier non connecté", "Connectez le boîtier OBD2 pour afficher les données du moteur.")
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (v.adapter == null) TextBtn("Associer un boîtier", { vm.pairActive() }) else TextBtn("Se connecter", { vm.connectNow() })
                    }
                }
                sup == null -> EmptyNote(Icons.Rounded.HourglassEmpty, "Lecture des données disponibles…", "Mettez le contact. CarDeck interroge le véhicule pour savoir quelles données il fournit.")
                else -> {
                    val gauges = DashGauges.gauges(sport, values, sup)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        gauges.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { g -> GaugeCard(g.def, g.value, Modifier.weight(1f), g.note) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Sélecteur Standard / Sport, en haut à droite. */
@Composable
private fun ModeSelector(sport: Boolean, onChange: (Boolean) -> Unit) {
    val c = Cd.c
    Row(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.sf3).padding(3.dp)) {
        listOf(false to "Standard", true to "Sport").forEach { (isSport, label) ->
            val sel = isSport == sport
            val bg by animateColorAsState(if (sel) c.p else c.sf3, label = "mode")
            Box(
                Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(bg).clickable { onChange(isSport) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { T(label, 13, if (sel) c.op else c.onv, 600) }
        }
    }
}

