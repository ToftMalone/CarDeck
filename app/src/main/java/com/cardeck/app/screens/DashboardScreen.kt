package com.cardeck.app.screens

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.data.PidDef
import com.cardeck.app.data.StdPids
import com.cardeck.app.data.fr
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt

/** Données lues pour chaque tableau de bord (identifiants des PIDs standard). */
private val STANDARD_IDS = listOf("std0D", "std0C", "std05", "std5C", "std2F", "std42", "std10", "std5E")
private val SPORT_IDS = listOf("std0F", "std0B", "std33", "std0E", "std0D")

/** Lus moins souvent (varient peu). */
private val SLOW_IDS = setOf("std2F", "std33")

private const val BARO_DEFAULT_KPA = 101.3
private const val G = 9.81

/** Essence : rapport air/carburant stœchiométrique et masse volumique (g/L). */
private const val AFR = 14.7
private const val FUEL_DENSITY = 745.0

private fun pseudo(id: String, label: String, unit: String, icon: androidx.compose.ui.graphics.vector.ImageVector, decimals: Int, min: Double, max: Double) =
    PidDef(id, label, unit, icon, "", decimals = decimals, min = min, max = max) { 0.0 }

private val MAP_BAR = pseudo("dash_map", "Pression collecteur (MAP)", "bar", Icons.Rounded.Air, 2, 0.0, 2.5)
private val BOOST_BAR = pseudo("dash_boost", "Pression de suralimentation", "bar", Icons.Rounded.Speed, 2, -1.0, 1.5)
private val VOLT = pseudo("dash_volt", "Tension batterie", "V", Icons.Rounded.BatteryChargingFull, 1, 11.0, 15.0)
private val CONSO_100 = pseudo("dash_conso", "Consommation", "L/100 km", Icons.Rounded.LocalGasStation, 1, 0.0, 20.0)
private val CONSO_H = pseudo("dash_conso_h", "Consommation", "L/h", Icons.Rounded.LocalGasStation, 1, 0.0, 5.0)
private val G_LONG = pseudo("dash_glong", "Accélération longitudinale", "g", Icons.Rounded.Speed, 2, -1.0, 1.0)
private val G_LAT = pseudo("dash_glat", "Accélération latérale", "g", Icons.Rounded.Sync, 2, 0.0, 1.0)

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val c = Cd.c
    val ctx = LocalContext.current
    val v = rememberActiveVehicle(vm) ?: return
    val (_, connKind) = connStatus(vm, v)
    val connected = connKind == ConnKind.Ok
    val supported by vm.obd.supported.collectAsState()
    val values = remember { mutableStateMapOf<String, Double>() }
    val sport = vm.dashSport
    val ids = if (sport) SPORT_IDS else STANDARD_IDS
    // Accélération horizontale totale mesurée par le téléphone (g) et pics de la session.
    var horizontalG by remember { mutableFloatStateOf(0f) }
    val peaks = remember { mutableStateMapOf<String, Double>() }

    // Lecture en continu des seules données du tableau de bord affiché (rafraîchissement rapide).
    LaunchedEffect(connected, sport) {
        if (!connected) return@LaunchedEffect
        var cycle = 0
        var lastSpeed: Double? = null
        var lastT = 0L
        var gLong = 0.0
        while (true) {
            var sup = vm.obd.supported.value
            if (sup == null) {
                sup = withContext(Dispatchers.IO) { vm.obd.use { it.supportedPids() } }
                if (sup.isNullOrEmpty()) { delay(5000); continue }
                vm.obd.supported.value = sup
            }
            val defs = ids.mapNotNull { id -> StdPids.all.find { it.id == id } }
                .filter { StdPids.pidNumber(it) in sup && (it.id !in SLOW_IDS || cycle % 15 == 0) }
            val readVolt = !sport && 0x42 !in sup && cycle % 10 == 0
            val out = withContext(Dispatchers.IO) {
                vm.obd.use { e -> e.readAll(defs) to (if (readVolt) e.voltage() else null) }
            }
            out?.first?.forEach { (id, x) -> values[id] = x }
            out?.second?.let { values["elm_volt"] = it }
            // Accélération longitudinale : dérivée de la vitesse lue sur le véhicule.
            val now = System.currentTimeMillis()
            val speed = out?.first?.firstOrNull { it.first == "std0D" }?.second
            if (sport && speed != null) {
                val s0 = lastSpeed
                if (s0 != null && now > lastT) {
                    val a = (speed - s0) / 3.6 / ((now - lastT) / 1000.0) / G
                    gLong = gLong * 0.5 + a * 0.5
                    values[G_LONG.id] = gLong
                    if (gLong > (peaks["accel"] ?: 0.0)) peaks["accel"] = gLong
                    if (gLong < (peaks["brake"] ?: 0.0)) peaks["brake"] = gLong
                }
                lastSpeed = speed
                lastT = now
            }
            cycle++
            delay(80)
        }
    }

    // Capteurs du téléphone : accélération horizontale (sans la gravité), pour l'accélération latérale.
    DisposableEffect(sport) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val lin = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val grav = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (!sport || sm == null || lin == null || grav == null) return@DisposableEffect onDispose {}
        val gv = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.sensor.type == Sensor.TYPE_GRAVITY) { e.values.copyInto(gv); return }
                val gn = sqrt(gv[0] * gv[0] + gv[1] * gv[1] + gv[2] * gv[2])
                if (gn < 1f) return
                val ux = gv[0] / gn; val uy = gv[1] / gn; val uz = gv[2] / gn
                val ax = e.values[0]; val ay = e.values[1]; val az = e.values[2]
                val vert = ax * ux + ay * uy + az * uz
                val hx = ax - vert * ux; val hy = ay - vert * uy; val hz = az - vert * uz
                val h = sqrt(hx * hx + hy * hy + hz * hz) / G.toFloat()
                horizontalG = horizontalG * 0.85f + h * 0.15f
                val gl = values[G_LONG.id] ?: 0.0
                val lat = sqrt(max(0.0, horizontalG * horizontalG - gl * gl))
                if (lat > (peaks["lat"] ?: 0.0)) peaks["lat"] = lat
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, grav, SensorManager.SENSOR_DELAY_GAME)
        sm.registerListener(listener, lin, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
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
                    fun gauge(id: String) = Triple(std(id), values[id], if (has(id)) null else missing)
                    val gauges: List<Triple<PidDef, Double?, String?>> = if (!sport) {
                        // Consommation : débit de carburant (PID 5E) ou calcul à partir du débit d'air (MAF).
                        val speed = values["std0D"]
                        val lph = values["std5E"] ?: values["std10"]?.let { it / AFR * 3600 / FUEL_DENSITY }
                        val conso = when {
                            lph == null -> Triple(CONSO_100, null, if (has("std5E") || has("std10")) null else missing)
                            speed != null && speed >= 5 -> Triple(CONSO_100, lph / speed * 100, null)
                            else -> Triple(CONSO_H, lph, "À l'arrêt")
                        }
                        val volt = if (has("std42")) Triple(VOLT, values["std42"], "Calculateur") else Triple(VOLT, values["elm_volt"], "Prise OBD")
                        STANDARD_IDS.take(5).map { gauge(it) } + listOf(conso, volt)
                    } else {
                        val map = values["std0B"]
                        val baro = values["std33"] ?: BARO_DEFAULT_KPA
                        val lat = values[G_LONG.id]?.let { gl -> sqrt(max(0.0, horizontalG * horizontalG - gl * gl)) } ?: horizontalG.toDouble()
                        listOf(
                            gauge("std0F"),
                            Triple(MAP_BAR, map?.let { it / 100 }, if (has("std0B")) "Absolue" else missing),
                            Triple(BOOST_BAR, map?.let { (it - baro) / 100 }, if (has("std0B")) "Relative (MAP − pression atmosphérique)" else missing),
                            gauge("std0E"),
                            Triple(G_LONG, values[G_LONG.id], if (has("std0D")) "Pics : +${fr(peaks["accel"] ?: 0.0, 2)} / ${fr(peaks["brake"] ?: 0.0, 2)} g" else missing),
                            Triple(G_LAT, lat, "Pic : ${fr(peaks["lat"] ?: 0.0, 2)} g"),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        gauges.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { (p, x, note) -> GaugeCard(p, x?.let { if (it.isNaN()) null else it }, Modifier.weight(1f), note) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (sport) {
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                            TextBtn("Remettre les pics à zéro", { peaks.clear() })
                        }
                        T(
                            "Accélération longitudinale calculée à partir de la vitesse du véhicule ; latérale mesurée par le téléphone (fixé dans la voiture).",
                            12, c.onv, lineHeight = 16, modifier = Modifier.padding(top = 4.dp),
                        )
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

