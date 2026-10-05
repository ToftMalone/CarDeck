package com.cardeck.app.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cyclone
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.Screen
import com.cardeck.app.ScanPhase
import com.cardeck.app.data.WIDGETS
import com.cardeck.app.data.fr
import com.cardeck.app.ui.ArcGauge
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.CdSwitch
import com.cardeck.app.ui.ConnDot
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.Skeleton
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class GaugeSpec(val label: String, val icon: ImageVector, val value: String, val unit: String, val progress: Float, val color: Color, val sub: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: AppViewModel) {
    val c = Cd.c
    val L = vm.live
    var sheet by remember { mutableStateOf(false) }

    // Simulation des PID moteur (≈ 1 Hz) tant que l'écran est affiché.
    LaunchedEffect(Unit) { while (true) { vm.tick(); delay(1100) } }

    val gauges = listOf(
        GaugeSpec("Régime", Icons.Rounded.Cyclone, fr((L.rpm / 10).roundToInt() * 10), "tr/min", (L.rpm / 7000).toFloat(), if (L.rpm > 5000) c.e else c.p, "Max 7 000"),
        GaugeSpec("Vitesse", Icons.Rounded.Speed, fr(L.speed.roundToInt()), "km/h", (L.speed / 220).toFloat(), c.t, "Moy. 42 km/h"),
        GaugeSpec("Temp. moteur", Icons.Rounded.DeviceThermostat, fr(L.temp.roundToInt()), "°C", ((L.temp - 40) / 90).toFloat(), if (L.temp > 105) c.e else c.w, if (L.temp > 105) "Surchauffe" else "Normale"),
        GaugeSpec("Carburant", Icons.Rounded.LocalGasStation, fr(L.fuel.roundToInt()), "%", (L.fuel / 100).toFloat(), if (L.fuel < 15) c.e else c.g, "≈ " + fr((L.fuel * 7.2 / 5).roundToInt() * 5) + " km"),
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barre véhicule
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, { vm.back() })
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.pc), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.DirectionsCar, 22, c.opc) }
                Column(Modifier.weight(1f)) {
                    T(vm.vehicle.name, 16, weight = 500, lineHeight = 22, maxLines = 1)
                    T(vm.vehicle.plate, 12, c.onv, mono = true, lineHeight = 16)
                }
                Row(
                    Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(c.gc).padding(start = 10.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) { ConnDot(); T(if (vm.refreshing) "Synchro…" else "Connecté", 13, c.ogc, 500) }
                IconBtn(Icons.Rounded.Tune, { sheet = true }, c.onv)
            }
            PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp)) {
                    if (vm.phase == ScanPhase.Results) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.ec).clickable { vm.go(Screen.Diag) }
                                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Ico(Icons.Rounded.Error, 24, c.oec)
                            Column(Modifier.weight(1f)) {
                                T("2 défauts critiques détectés", 14, c.oec, 600)
                                T("Dernier scan à 09:38 · Voir le diagnostic", 12, c.oec.copy(alpha = .85f))
                            }
                            Ico(Icons.AutoMirrored.Rounded.KeyboardArrowRight, 24, c.oec)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        gauges.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { GaugeCard(it, Modifier.weight(1f)) } }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        T("Données moteur", 16, weight = 500)
                        TextBtn("Personnaliser", { sheet = true }, Icons.Rounded.Edit)
                    }
                    val active = WIDGETS.filter { it.id in vm.widgets }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        active.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { w ->
                                    Column(
                                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).border(1.dp, c.olv, RoundedCornerShape(20.dp)).padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        if (vm.refreshing) {
                                            Skeleton(Modifier.size(32.dp), 10.dp); Skeleton(Modifier.fillMaxWidth(.7f).height(22.dp)); Skeleton(Modifier.fillMaxWidth().height(4.dp), 2.dp)
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.sc), contentAlignment = Alignment.Center) { Ico(w.icon, 18, c.osc) }
                                                T(w.label, 12, c.onv, lineHeight = 16)
                                            }
                                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                T(w.value(L), 24, weight = 500, lineHeight = 32)
                                                T(w.unit, 12, c.onv, modifier = Modifier.padding(bottom = 4.dp))
                                            }
                                            val pct by animateFloatAsState(w.pct(L).toFloat().coerceIn(0f, 1f), tween(900), label = "w")
                                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.sf4)) {
                                                Box(Modifier.fillMaxWidth(pct).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(c.p))
                                            }
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    T("Tirez vers le bas pour actualiser · 12 PID/s", 12, c.onv, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { vm.hud = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = c.pc, contentColor = c.opc,
            icon = { Ico(Icons.Rounded.Flip, 24, c.opc) },
            text = { T("Mode HUD", 14, c.opc, 500) },
        )
        if (vm.hud) HudOverlay(vm)
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = rememberModalBottomSheetState(), containerColor = c.sf1, contentColor = c.on) {
            Column(Modifier.padding(bottom = 16.dp)) {
                T("Widgets affichés", 22, lineHeight = 28, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 4.dp))
                T("Choisissez les données moteur visibles sur le tableau de bord.", 14, c.onv, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp))
                WIDGETS.forEach { w -> WidgetToggleRow(w.icon, w.label, w.id in vm.widgets) { vm.toggleWidget(w.id) } }
            }
        }
    }
}

@Composable
fun WidgetToggleRow(icon: ImageVector, label: String, on: Boolean, horizontal: Int = 24, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = horizontal.dp, vertical = 10.dp).height(36.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Ico(icon, 24, Cd.c.onv)
        T(label, 16, modifier = Modifier.weight(1f))
        CdSwitch(on, onToggle)
    }
}

@Composable
private fun GaugeCard(g: GaugeSpec, modifier: Modifier) {
    val c = Cd.c
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(c.sf2).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ico(g.icon, 18, g.color)
            T(g.label, 12, c.onv, 500)
        }
        Box(Modifier.padding(top = 4.dp).size(138.dp), contentAlignment = Alignment.Center) {
            ArcGauge(g.progress, g.color, 8f, Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                T(g.value, 30, weight = 500, lineHeight = 36, spacing = -.5f)
                T(g.unit, 12, c.onv)
            }
        }
        T(g.sub, 12, c.onv, modifier = Modifier.padding(top = 0.dp))
    }
}

// ---- Mode HUD ----

private val HudGreen = Color(0xFF58E086)
private val HudGreenDim = Color(0xFF3FBF6A)

@Composable
fun HudOverlay(vm: AppViewModel) {
    val L = vm.live
    val view = LocalView.current
    DisposableEffect(Unit) {
        val prev = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = prev }
    }
    val segN = (L.rpm / 7000 * 24).roundToInt()
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(Icons.Rounded.Close, { vm.hud = false }, Color(0xFF9AA49C), Color(0xFF141614))
            T("MODE HUD", 14, Color(0xFF6C766E), modifier = Modifier.weight(1f), spacing = 1f)
            Row(
                Modifier.height(36.dp).clip(RoundedCornerShape(8.dp)).background(if (vm.mirror) Color(0xFF1F3324) else Color.Transparent)
                    .border(1.dp, if (vm.mirror) Color(0xFF1F3324) else Color(0xFF3A423C), RoundedCornerShape(8.dp))
                    .clickable { vm.toggleMirror() }.padding(start = 10.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) { Ico(Icons.Rounded.Flip, 18, Color(0xFFCFE9D4)); T("Miroir", 14, Color(0xFFCFE9D4), 500) }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().graphicsLayer { scaleX = if (vm.mirror) -1f else 1f },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            T(fr(L.speed.roundToInt()), 176, HudGreen, 300, lineHeight = 170, spacing = -6f)
            T("KM/H", 26, HudGreenDim, spacing = 3f)
            Row(Modifier.fillMaxWidth().padding(top = 44.dp).height(30.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(24) { i ->
                    val col = when {
                        i >= segN -> Color(0xFF121512)
                        i < 14 -> Color(0xFF58E086)
                        i < 19 -> Color(0xFFE6C84A)
                        else -> Color(0xFFE5543F)
                    }
                    Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(col))
                }
            }
            T(fr((L.rpm / 10).roundToInt() * 10) + " tr/min", 22, HudGreen, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().padding(top = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HudStat(Icons.Rounded.DeviceThermostat, fr(L.temp.roundToInt()) + "°", Modifier.weight(1f))
                HudStat(Icons.Rounded.LocalGasStation, if (L.conso > 0) fr(L.conso, 1) else "—", Modifier.weight(1f))
                HudStat(Icons.Rounded.BatteryChargingFull, fr(L.volt, 1) + "V", Modifier.weight(1f))
            }
        }
        T("Posez le téléphone à plat sous le pare-brise", 13, Color(0xFF59625B), align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun HudStat(icon: ImageVector, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Ico(icon, 28, HudGreen)
        T(value, 28, HudGreen, modifier = Modifier.padding(top = 4.dp))
    }
}
