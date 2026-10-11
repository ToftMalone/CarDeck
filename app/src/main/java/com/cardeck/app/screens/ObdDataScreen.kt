package com.cardeck.app.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.cardeck.app.ui.ArcGauge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.data.PidDef
import com.cardeck.app.data.StdPids
import com.cardeck.app.data.fr
import com.cardeck.app.obd.ObdState
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Liaison OBD2 + toutes les valeurs envoyées par le véhicule (et commandes du simulateur). */
@Composable
fun ObdDataScreen(vm: AppViewModel) {
    val c = Cd.c
    val v = rememberActiveVehicle(vm) ?: return
    val tpl = v.template
    val (connLabel, connKind) = connStatus(vm, v)
    val st by vm.obd.state.collectAsState()
    val ecu by vm.obd.ecuOnline.collectAsState()
    val volt by vm.obd.voltage.collectAsState()
    val protocol by vm.obd.protocol.collectAsState()
    val connected = connKind == ConnKind.Ok
    val values = remember { mutableStateMapOf<String, Double>() }

    // Tension batterie lue sur le boîtier tant que l'écran est affiché.
    LaunchedEffect(connected) {
        while (connected) {
            withContext(Dispatchers.IO) { vm.obd.use { it.voltage() } }?.let { vm.obd.voltage.value = it }
            delay(5000)
        }
    }
    val supported by vm.obd.supported.collectAsState()
    // Lecture en continu : PIDs standard déclarés par le véhicule + PIDs propres au modèle.
    LaunchedEffect(connected, tpl.id) {
        if (!connected) return@LaunchedEffect
        var cycle = 0
        while (true) {
            var sup = vm.obd.supported.value
            if (sup == null) {
                sup = withContext(Dispatchers.IO) { vm.obd.use { it.supportedPids() } }
                if (sup.isNullOrEmpty()) { delay(5000); continue }
                vm.obd.supported.value = sup
            }
            val list = (StdPids.forSupported(sup) + tpl.pids).filter { !it.slow || cycle % 6 == 0 }
            // Par petits paquets pour rafraîchir l'écran au fil de la lecture.
            for (chunk in list.groupBy { it.request to it.header }.values.chunked(4)) {
                withContext(Dispatchers.IO) { vm.obd.use { it.readAll(chunk.flatten()) } }?.forEach { (id, v) -> values[id] = v }
            }
            cycle++
            delay(100)
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
                T("Données OBD2 · " + v.plate.ifBlank { tpl.code }, 12, c.onv, lineHeight = 16, maxLines = 1)
            }
            ConnChip(connLabel, connKind)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            // Liaison
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.sf2).padding(16.dp)) {
                T("Liaison OBD2", 16, weight = 500, modifier = Modifier.padding(bottom = 8.dp))
                val a = v.adapter
                if (a == null) {
                    T("Aucun boîtier n'est associé à ce véhicule.", 14, c.onv)
                    TextBtn("Associer un boîtier", { vm.pairActive() })
                } else {
                    InfoRow("Boîtier", "${a.name} · ${a.transport.label}")
                    InfoRow("Interface", (st as? ObdState.Connected)?.version ?: "—")
                    InfoRow("Protocole", if (connected) protocol ?: "—" else "—")
                    InfoRow("Véhicule", if (!connected) "—" else if (ecu) "Calculateur moteur joignable" else "Contact coupé")
                    InfoRow("Tension batterie", if (connected) volt?.let { fr(it, 1) + " V" } ?: "—" else "—")
                    InfoRow("VIN", v.vin ?: "Non lu")
                    (st as? ObdState.Failed)?.takeIf { it.adapter.address == a.address }?.let {
                        T(it.message, 13, c.e, modifier = Modifier.padding(top = 8.dp), lineHeight = 18)
                    }
                    if (connKind == ConnKind.Off) TextBtn("Se connecter", { vm.connectNow() })
                }
            }
            val sup = supported
            val std = if (sup == null) emptyList() else StdPids.forSupported(sup)
            if (v.adapter?.transport == com.cardeck.app.data.Transport.Sim) SimPanel(vm)
            T("Toutes les valeurs du véhicule", 16, weight = 500, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            when {
                !connected -> EmptyNote(Icons.Rounded.HourglassEmpty, "Boîtier non connecté", "Connectez le boîtier OBD2 pour lire les données du moteur.")
                sup == null -> EmptyNote(Icons.Rounded.HourglassEmpty, "Lecture des données disponibles…", "Mettez le contact. CarDeck interroge le véhicule pour savoir quelles données il fournit.")
                std.isEmpty() -> EmptyNote(Icons.Rounded.HourglassEmpty, "Aucune donnée standard", "Ce véhicule ne déclare aucun PID OBD2 standard.")
                else -> {
                    StdPids.CATEGORIES.forEach { cat ->
                        val inCat = std.filter { it.category == cat }
                        if (inCat.isNotEmpty()) PidSection(cat, inCat, values)
                    }
                    T("${std.size} valeurs fournies par le véhicule", 12, c.onv, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            }
            tpl.pids.groupBy { it.category }.forEach { (cat, list) -> PidSection("$cat · ${tpl.model}", list, values) }
        }
    }
}

/** Titre de catégorie + cartes des données correspondantes. */
@Composable
private fun PidSection(title: String, pids: List<PidDef>, values: Map<String, Double>) {
    T(title, 14, Cd.c.p, 500, Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        pids.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { p -> PidCard(p, values[p.id], Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun EmptyNote(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    val c = Cd.c
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, c.olv, RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(c.sf3), contentAlignment = Alignment.Center) { Ico(icon, 28, c.onv) }
        T(title, 16, weight = 500, modifier = Modifier.padding(top = 14.dp))
        T(text, 14, c.onv, align = TextAlign.Center, lineHeight = 20, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun PidCard(p: PidDef, value: Double?, modifier: Modifier) {
    val c = Cd.c
    Column(modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, c.olv, RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.sc), contentAlignment = Alignment.Center) { Ico(p.icon, 18, c.osc) }
            T(p.label, 12, c.onv, lineHeight = 16, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            T(value?.let { fr(it, p.decimals) } ?: "—", 24, weight = 500, lineHeight = 32)
            T(p.unit, 12, c.onv, modifier = Modifier.padding(bottom = 4.dp))
        }
        val pct by animateFloatAsState(value?.let { ((it - p.min) / (p.max - p.min)).toFloat().coerceIn(0f, 1f) } ?: 0f, tween(500), label = "pid")
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.sf4)) {
            Box(Modifier.fillMaxWidth(pct).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(c.p))
        }
    }
}

@Composable
internal fun GaugeCard(p: PidDef, value: Double?, modifier: Modifier, note: String? = null) {
    val c = Cd.c
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(c.sf2).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ico(p.icon, 18, c.p)
            T(p.label, 12, c.onv, 500)
        }
        Box(Modifier.padding(top = 4.dp).size(138.dp), contentAlignment = Alignment.Center) {
            ArcGauge(value?.let { ((it - p.min) / (p.max - p.min)).toFloat() } ?: 0f, c.p, 8f, Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(value?.let { fr(it, p.decimals) } ?: "—", 30, weight = 500, lineHeight = 36, spacing = -.5f)
                T(p.unit, 12, c.onv)
            }
        }
        if (note != null) T(note, 12, c.onv, align = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = Cd.c
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        T(label, 14, c.onv, modifier = Modifier.weight(1f))
        T(value, 14, weight = 500, align = TextAlign.End, modifier = Modifier.weight(1.6f))
    }
}

/** Commandes de la voiture virtuelle (profil « Simulateur »). */
@Composable
private fun SimPanel(vm: AppViewModel) {
    val c = Cd.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var on by remember { mutableStateOf(com.cardeck.app.sim.SimCar.engineOn) }
    var faults by remember { mutableStateOf(com.cardeck.app.sim.SimCar.dtcs.size) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(24.dp)).background(c.tc).padding(16.dp)) {
        T("Voiture virtuelle", 16, c.otc, 500)
        T(
            if (on) "Moteur en marche : la voiture roule sur un circuit, le trajet est enregistré automatiquement."
            else "Démarrez le moteur pour simuler un trajet. Coupez-le pour terminer le trajet (environ 20 s après).",
            13, c.otc, lineHeight = 18, modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextBtn(if (on) "Couper le moteur" else "Démarrer le moteur", {
                val car = com.cardeck.app.sim.SimCar
                if (car.engineOn) car.stopEngine() else {
                    // Le circuit est placé autour de la dernière position connue du téléphone.
                    lastKnownLocation(ctx)?.let { car.setCenter(it.latitude, it.longitude) }
                    car.startEngine()
                }
                on = car.engineOn
                vm.connectNow()
            }, color = c.otc, weight = 600)
            TextBtn("Simuler un défaut", {
                val code = com.cardeck.app.sim.SimCar.injectFault()
                faults = com.cardeck.app.sim.SimCar.dtcs.size
                vm.showSnack("Défaut $code ajouté à la voiture virtuelle")
            }, color = c.otc, weight = 600)
        }
        if (faults > 0) T("$faults défaut${if (faults > 1) "s" else ""} en mémoire (visible${if (faults > 1) "s" else ""} dans Diagnostic)", 12, c.otc)
        val live by com.cardeck.app.service.TripService.live.collectAsState()
        val note by com.cardeck.app.service.TripService.lastNote.collectAsState()
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.otc.copy(alpha = .12f)).padding(10.dp)) {
            Column {
                T("Suivi des trajets : " + (live ?: "arrêté"), 12, c.otc, 500, lineHeight = 16)
                note?.let { T(it, 12, c.otc, lineHeight = 16, modifier = Modifier.padding(top = 2.dp)) }
                if (live == null) TextBtn("Démarrer le suivi", { com.cardeck.app.service.TripService.start(ctx) }, color = c.otc, weight = 600)
            }
        }
    }
}

/** Dernière position connue du téléphone (null sans autorisation de localisation). */
@android.annotation.SuppressLint("MissingPermission")
private fun lastKnownLocation(ctx: android.content.Context): android.location.Location? {
    if (!com.cardeck.app.Perms.locationOk(ctx)) return null
    val lm = ctx.getSystemService(android.location.LocationManager::class.java) ?: return null
    return runCatching {
        lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
    }.getOrNull()
}
