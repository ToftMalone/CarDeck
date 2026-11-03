package com.cardeck.app.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.cardeck.app.AppViewModel
import com.cardeck.app.Perms
import com.cardeck.app.R
import com.cardeck.app.Screen
import com.cardeck.app.data.DashGauges
import com.cardeck.app.data.DriveEvent
import com.cardeck.app.data.DriveLog
import com.cardeck.app.data.PidDef
import com.cardeck.app.data.StdPids
import com.cardeck.app.data.Transport
import com.cardeck.app.ui.Accent
import com.cardeck.app.ui.ButtonStyle
import com.cardeck.app.ui.EmptyStateCard
import com.cardeck.app.ui.ErrorBanner
import com.cardeck.app.ui.FilterChips
import com.cardeck.app.ui.Gauge
import com.cardeck.app.ui.HeroCard
import com.cardeck.app.ui.KeyValueList
import com.cardeck.app.ui.Level
import com.cardeck.app.ui.LineChart
import com.cardeck.app.ui.LiveNumber
import com.cardeck.app.ui.LocalHaptics
import com.cardeck.app.ui.LogEntry
import com.cardeck.app.ui.LogList
import com.cardeck.app.ui.Metric
import com.cardeck.app.ui.MetricStyle
import com.cardeck.app.ui.MetricTile
import com.cardeck.app.ui.ScreenHeader
import com.cardeck.app.ui.SecondaryButton
import com.cardeck.app.ui.SectionCard
import com.cardeck.app.ui.SegmentedGroup
import com.cardeck.app.ui.Sparkline
import com.cardeck.app.ui.StartStopButton
import com.cardeck.app.ui.StatePanel
import com.cardeck.app.ui.Stepper
import com.cardeck.app.ui.Sym
import com.cardeck.app.ui.BarChart
import com.cardeck.app.ui.cs
import com.cardeck.app.ui.fadeUp
import com.cardeck.app.ui.fmt
import com.cardeck.app.ui.rise
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private const val KMH_PER_MPH = 1.609344
private const val SHIFT_MIN = 3000
private const val SHIFT_MAX = 8000
private const val SHIFT_STEP = 250

/** Conversion des unités d'affichage (vitesse, température) selon les réglages. */
private class Units(val mph: Boolean, val fahrenheit: Boolean) {
    fun speed(kmh: Double?) = kmh?.let { if (mph) it / KMH_PER_MPH else it }
    fun temp(c: Double?) = c?.let { if (fahrenheit) it * 9 / 5 + 32 else it }

    /** Valeur, bornes et unité d'une donnée, converties si besoin. */
    fun of(def: PidDef, v: Double?, speedUnit: String, tempUnit: String): Triple<Double?, ClosedFloatingPointRange<Double>, String> = when (def.unit) {
        "km/h" -> Triple(speed(v), speed(def.min)!!..speed(def.max)!!, speedUnit)
        "°C" -> Triple(temp(v), temp(def.min)!!..temp(def.max)!!, tempUnit)
        else -> Triple(v, def.min..def.max, def.unit)
    }
}

/** Série de courbe proposée dans « Tendances ». */
private data class Series(val id: String, val label: Int, val unit: String, val decimals: Int = 0)

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val v = rememberActiveVehicle(vm) ?: return
    val conn = rememberConnection(vm, v)
    val supported by vm.obd.supported.collectAsState()
    val values by vm.obd.live.collectAsState()
    val hap = LocalHaptics.current

    // Toutes les données sont lues en permanence par le service : l'écran affiche les dernières valeurs.
    LaunchedEffect(Unit) { vm.obd.keepReading() }

    // Blocages : aucun boîtier, ou autorisation Bluetooth manquante.
    var permTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permTick++ }
    val needsBt = v.adapter != null && v.adapter.transport != Transport.Wifi
    val btOk = remember(permTick) { Perms.bluetoothOk(ctx) }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permTick++
        if (Perms.bluetoothOk(ctx)) { hap.confirm(); vm.connectNow() }
    }
    if (v.adapter == null) {
        StatePanel(
            Sym.Bluetooth, stringResource(R.string.state_no_adapter_title), stringResource(R.string.state_no_adapter_body),
            stringResource(R.string.state_pair), { vm.home(); vm.pairActive() },
        )
        return
    }
    if (needsBt && !btOk) {
        StatePanel(
            Sym.BluetoothDisabled, stringResource(R.string.state_bt_title), stringResource(R.string.state_bt_body),
            stringResource(R.string.state_grant), { btLauncher.launch(Perms.bluetooth) },
        )
        return
    }

    val units = Units(vm.mph, vm.fahrenheit)
    val speedUnit = stringResource(if (vm.mph) R.string.unit_mph else R.string.unit_kmh)
    val tempUnit = stringResource(if (vm.fahrenheit) R.string.unit_f else R.string.unit_c)
    val rpmUnit = stringResource(R.string.unit_rpm)
    val live = conn == Conn.Live
    fun value(id: String) = if (live) values[id]?.takeUnless { it.isNaN() } else null
    val rpm = value("std0C")
    val rpmMax = StdPids.byId("std0C")?.max ?: 8000.0
    val shift = vm.shiftRpm

    // Seuil d'alerte franchi : retour haptique (le journal est tenu par le ViewModel).
    val above = rpm != null && rpm >= shift
    LaunchedEffect(above) { if (above) hap.threshold() }

    val drive = vm.drive
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(drive.running) { while (drive.running) { now = System.currentTimeMillis(); delay(1000) } }

    val series = listOf(
        Series("std0D", R.string.dash_speed, speedUnit),
        Series("std0C", R.string.dash_rpm, rpmUnit),
        Series("std04", R.string.dash_load, "%"),
        Series("std0B", R.string.dash_map, stringResource(R.string.unit_kpa)),
        Series("std05", R.string.dash_coolant, tempUnit),
    )
    var shown by rememberSaveable { mutableStateOf(listOf(0, 1)) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.dash_title), v.nickname)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. Carte héros : état, vitesse en direct, tuiles secondaires.
            HeroCard(
                title = stringResource(R.string.dash_speed),
                value = units.speed(value("std0D")),
                decimals = 0,
                unit = speedUnit,
                chip = { ConnectionChip(conn, onHero = true) },
                badge = Sym.Speed,
                tiles = listOf(
                    Metric(stringResource(R.string.dash_rpm), rpm, rpmUnit),
                    Metric(stringResource(R.string.dash_load), value("std04"), "%"),
                    Metric(stringResource(R.string.dash_coolant), units.temp(value("std05")), tempUnit),
                ),
            )

            when (conn) {
                Conn.Offline -> ErrorBanner(
                    stringResource(R.string.err_offline_title), stringResource(R.string.err_offline_body),
                    stringResource(R.string.retry), { hap.tick(); vm.connectNow() },
                )
                Conn.Connected, Conn.Connecting -> EmptyStateCard(
                    Sym.Sensors, stringResource(R.string.state_reading_title), stringResource(R.string.state_reading_body), busy = true,
                )
                Conn.Live -> if (supported == null) EmptyStateCard(
                    Sym.HourglassEmpty, stringResource(R.string.state_reading_title), stringResource(R.string.state_reading_body), busy = true,
                )
                Conn.NoAdapter -> Unit
            }

            // 2. Sections : moteur (tuiles), compte-tours (jauge + courbe), tendances, admission, session, journal.
            val gauges = DashGauges.gauges(
                vm.dashSport, if (live) values else emptyMap(), supported, v.template.pids.map { it.id }.toSet(),
                if (vm.dashSport) v.template.pids.filter { it.id in v.template.dashSport } else emptyList(),
            )
            val missing = stringResource(R.string.dash_not_provided)
            SectionCard(stringResource(R.string.dash_engine), Modifier.rise(0), Sym.Bolt) {
                gauges.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { g ->
                            val (gv, range, unit) = units.of(g.def, g.value, speedUnit, tempUnit)
                            val span = (range.endInclusive - range.start).takeIf { it > 0 } ?: 1.0
                            MetricTile(
                                Metric(
                                    g.def.label, gv, unit, g.def.decimals,
                                    level = gv?.let { ((it - range.start) / span).toFloat() } ?: 0f,
                                    note = if (g.note == DashGauges.MISSING) missing else g.note,
                                ),
                                Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            SectionCard(
                stringResource(R.string.dash_rev), Modifier.rise(1), Sym.Speed,
                subtitle = stringResource(R.string.dash_rev_sub, fmt(shift.toDouble())),
            ) {
                val all = Accent.all
                val gaugeColor = if (above) all.poor.accent else if (rpm != null && rpm >= shift * 0.85) all.fair.accent else Accent.current.accent
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Gauge(((rpm ?: 0.0) / rpmMax).toFloat(), Modifier.size(180.dp), gaugeColor, mark = (shift / rpmMax).toFloat())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LiveNumber(rpm, 0, MetricStyle.medium, cs.onSurface)
                        Text(rpmUnit, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
                LineChart(drive.history["std0C"] ?: emptyList(), 0f, rpmMax.toFloat(), threshold = shift.toFloat(), color = gaugeColor)
            }

            SectionCard(stringResource(R.string.dash_trends), Modifier.rise(2), Sym.ShowChart, subtitle = stringResource(R.string.dash_trends_sub)) {
                if (shown.isEmpty()) {
                    Text(stringResource(R.string.dash_trends_none), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                shown.sorted().forEach { i ->
                    val s = series[i]
                    val raw = drive.history[s.id] ?: emptyList()
                    val pts = when (s.id) {
                        "std0D" -> raw.map { units.speed(it.toDouble())!!.toFloat() }
                        "std05" -> raw.map { units.temp(it.toDouble())!!.toFloat() }
                        else -> raw
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(stringResource(s.label), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                            LiveNumber(if (live) pts.lastOrNull()?.toDouble() else null, s.decimals, MetricStyle.small, cs.onSurface)
                            Text(s.unit, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
                        }
                        Sparkline(pts)
                    }
                }
            }

            SectionCard(stringResource(R.string.dash_intake), Modifier.rise(3), Sym.Air) {
                val map = value("std0B")
                val baro = value("std33")
                val boost = if (map != null) (map - (baro ?: 101.3)) / 100 else null
                val kpa = stringResource(R.string.unit_kpa)
                KeyValueList(
                    listOf(
                        stringResource(R.string.dash_map) to "${fmt(map)} $kpa",
                        stringResource(R.string.dash_boost) to "${fmt(boost, 2)} ${stringResource(R.string.unit_bar)}",
                        stringResource(R.string.dash_baro) to "${fmt(baro)} $kpa",
                        stringResource(R.string.dash_iat) to "${fmt(units.temp(value("std0F")))} $tempUnit",
                        stringResource(R.string.dash_maf) to "${fmt(value("std10"), 1)} ${stringResource(R.string.unit_gs)}",
                        stringResource(R.string.dash_throttle) to "${fmt(value("std11"))} %",
                        stringResource(R.string.dash_timing) to "${fmt(value("std0E"), 1)} °",
                    ),
                )
            }

            val sessionText = when {
                drive.running -> stringResource(R.string.dash_session_running, duration(now - drive.startedAt))
                drive.stoppedAt > 0 -> stringResource(R.string.dash_session_done, duration(drive.stoppedAt - drive.startedAt))
                else -> stringResource(R.string.dash_session_idle)
            }
            SectionCard(stringResource(R.string.dash_session), Modifier.rise(4), Sym.Timer, subtitle = sessionText) {
                val p = drive.peaks
                val peaks = listOf(
                    Metric(stringResource(R.string.dash_peak_speed), units.speed(p["std0D"]), speedUnit, sym = Sym.Speed),
                    Metric(stringResource(R.string.dash_peak_rpm), p["std0C"], rpmUnit, sym = Sym.AvgPace),
                    Metric(stringResource(R.string.dash_peak_load), p["std04"], "%", sym = Sym.Bolt),
                    Metric(stringResource(R.string.dash_peak_map), p["std0B"], stringResource(R.string.unit_kpa), sym = Sym.Compress),
                )
                peaks.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { MetricTile(it, Modifier.weight(1f)) } }
                }
                // Pointes rapportées à l'échelle de chaque donnée.
                val ranges = listOf("std0D", "std0C", "std04", "std0B").map { id -> StdPids.byId(id)?.max ?: 100.0 }
                BarChart(
                    listOf("std0D", "std0C", "std04", "std0B").mapIndexed { i, id -> ((p[id] ?: 0.0) / ranges[i]).toFloat() },
                    peaks.map { it.label },
                )
            }

            SectionCard(stringResource(R.string.dash_log), Modifier.rise(5), Sym.ReceiptLong) {
                LogList(drive.log.take(12).map { logEntry(it) }, stringResource(R.string.dash_log_empty))
            }

            // 3. Paramètres : jauges Standard / Sport, seuil d'alerte, courbes affichées.
            SectionCard(stringResource(R.string.dash_display), Modifier.rise(6), Sym.Tune) {
                Text(stringResource(R.string.dash_mode), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                SegmentedGroup(
                    listOf(stringResource(R.string.dash_standard), stringResource(R.string.dash_sport)),
                    if (vm.dashSport) 1 else 0, { vm.updateDashSport(it == 1) },
                    syms = listOf(Sym.Dashboard, Sym.Speed),
                )
                Stepper(
                    stringResource(R.string.dash_shift), fmt(shift.toDouble()),
                    onMinus = { vm.updateShiftRpm((shift - SHIFT_STEP).coerceAtLeast(SHIFT_MIN)) },
                    onPlus = { vm.updateShiftRpm((shift + SHIFT_STEP).coerceAtMost(SHIFT_MAX)) },
                    minusEnabled = shift > SHIFT_MIN, plusEnabled = shift < SHIFT_MAX,
                    sub = stringResource(R.string.dash_shift_sub),
                )
                Text(stringResource(R.string.dash_curves), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                FilterChips(series.map { stringResource(it.label) }, shown.toSet()) { i -> shown = if (i in shown) shown - i else shown + i }
            }

            // 4. Actions : session (principal), remise à zéro, reconnexion, toutes les données.
            Column(Modifier.fadeUp(200), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StartStopButton(
                    drive.running, stringResource(R.string.dash_start), stringResource(R.string.dash_stop),
                    { val t = System.currentTimeMillis(); if (drive.running) drive.stop(t) else drive.start(t) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        stringResource(R.string.dash_reset), { hap.tick(); drive.clearPeaks() }, Modifier.weight(1f),
                        sym = Sym.Refresh, style = ButtonStyle.Outlined, enabled = drive.peaks.isNotEmpty(),
                    )
                    SecondaryButton(
                        stringResource(R.string.dash_reconnect), { hap.tick(); vm.connectNow() }, Modifier.weight(1f),
                        sym = Sym.Sync, style = ButtonStyle.Filled, enabled = conn == Conn.Offline,
                    )
                }
                SecondaryButton(
                    stringResource(R.string.dash_all_data), { hap.tick(); vm.go(Screen.ObdData) }, Modifier.fillMaxWidth(),
                    sym = Sym.Memory, large = true,
                )
            }
        }
    }
}

private fun duration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun logEntry(l: DriveLog): LogEntry {
    val time = remember(l.t) { DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(l.t)) }
    return when (l.event) {
        DriveEvent.Connected -> LogEntry(time, stringResource(R.string.log_connected), Level.Good)
        DriveEvent.Disconnected -> LogEntry(time, stringResource(R.string.log_disconnected), Level.Fair)
        DriveEvent.ShiftAlert -> LogEntry(time, stringResource(R.string.log_shift, fmt(l.value)), Level.Poor)
        DriveEvent.SessionStart -> LogEntry(time, stringResource(R.string.log_session_start))
        DriveEvent.SessionStop -> LogEntry(time, stringResource(R.string.log_session_stop))
    }
}
