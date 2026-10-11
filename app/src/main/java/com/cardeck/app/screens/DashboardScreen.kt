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
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Speed
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
import com.cardeck.app.data.PidDef
import com.cardeck.app.data.StdPids
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Données lues pour chaque tableau de bord (identifiants des PIDs standard). */
private val STANDARD_IDS = listOf("std0D", "std0C", "std05", "std5C")
private val SPORT_IDS = listOf("std0F", "std0B", "std33")

private const val BARO_DEFAULT_KPA = 101.3

private val MAP_BAR = PidDef("dash_map", "Pression collecteur (MAP)", "bar", Icons.Rounded.Air, "010B", decimals = 2, min = 0.0, max = 2.5) { it[0] / 100.0 }
private val BOOST_BAR = PidDef("dash_boost", "Pression de suralimentation", "bar", Icons.Rounded.Speed, "010B", decimals = 2, min = -1.0, max = 1.5) { 0.0 }

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val c = Cd.c
    val v = rememberActiveVehicle(vm) ?: return
    val (_, connKind) = connStatus(vm, v)
    val connected = connKind == ConnKind.Ok
    val supported by vm.obd.supported.collectAsState()
    val values = remember { mutableStateMapOf<String, Double>() }
    val sport = vm.dashSport
    val ids = if (sport) SPORT_IDS else STANDARD_IDS

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
                .filter { StdPids.pidNumber(it) in sup && (it.id != "std33" || cycle % 20 == 0) }
            withContext(Dispatchers.IO) { vm.obd.use { it.readAll(defs) } }?.forEach { (id, x) -> values[id] = x }
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
                    fun std(id: String) = StdPids.all.first { it.id == id }
                    fun has(id: String) = StdPids.pidNumber(std(id)) in sup
                    val missing = "Non fourni par le véhicule"
                    val gauges: List<Triple<PidDef, Double?, String?>> = if (!sport) {
                        STANDARD_IDS.map { id -> Triple(std(id), values[id], if (has(id)) null else missing) }
                    } else {
                        val map = values["std0B"]
                        val baro = values["std33"] ?: BARO_DEFAULT_KPA
                        listOf(
                            Triple(std("std0F"), values["std0F"], if (has("std0F")) null else missing),
                            Triple(MAP_BAR, map?.let { it / 100 }, if (has("std0B")) "Absolue" else missing),
                            Triple(BOOST_BAR, map?.let { (it - baro) / 100 }, if (has("std0B")) "Relative (MAP − pression atmosphérique)" else missing),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        gauges.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { (p, x, note) -> GaugeCard(p, x, Modifier.weight(1f), note) }
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

