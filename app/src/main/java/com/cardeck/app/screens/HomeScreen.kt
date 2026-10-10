package com.cardeck.app.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.cardeck.app.AppViewModel
import com.cardeck.app.Perms
import com.cardeck.app.Screen
import com.cardeck.app.obd.ObdState
import com.cardeck.app.service.TripService
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.CdColors
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.T
import com.cardeck.app.ui.TextBtn

private data class Tile(val label: String, val icon: ImageVector, val kind: Char, val onClick: () -> Unit, val badge: String = "")

@Composable
fun HomeScreen(vm: AppViewModel) {
    val c = Cd.c
    val v = rememberActiveVehicle(vm)
    val scan = rememberLastScan(vm, v?.id)
    val (connLabel, connKind) = connStatus(vm, v)
    val codes = scan?.codes?.size ?: 0
    val tiles = listOf(
        Tile("Tableau de bord", Icons.Rounded.Dashboard, 'p', { vm.go(Screen.Dash) }),
        Tile("Diagnostic", Icons.Rounded.Troubleshoot, 'p', { vm.go(Screen.Diag) }, if (codes > 0) codes.toString() else ""),
        Tile("Trajets", Icons.Rounded.Route, 'p', { vm.go(Screen.Trips) }),
        Tile("Ajouter un véhicule", Icons.Rounded.AddCircle, 'p', { vm.go(Screen.AddVehicle) }),
        Tile("Boîtier OBD2", Icons.Rounded.SettingsInputHdmi, 'p', { vm.pairActive() }),
        Tile("Paramètres", Icons.Rounded.Settings, 'p', { vm.go(Screen.Settings) }),
    )
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.p), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Speed, 20, c.op) }
                T("CarDeck", 20, weight = 600, modifier = Modifier.weight(1f))
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
                T("Bonjour", 28, lineHeight = 36, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp))
                if (v != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.pc).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(c.p), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.DirectionsCar, 24, c.op) }
                        Column(Modifier.weight(1f)) {
                            T(v.nickname, 18, c.opc, 500, lineHeight = 24, maxLines = 1)
                            T(listOf(v.template.fullName, v.plate).filter { it.isNotBlank() }.joinToString(" · "), 12, c.opc.copy(alpha = .85f), maxLines = 1)
                        }
                        ConnChip(connLabel, connKind, 36) { if (v.adapter == null) vm.pairActive() else if (connKind == ConnKind.Off) vm.connectNow() }
                    }
                }
                TripPermissionCard()
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tiles.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { t -> TileCard(t, c, Modifier.weight(1f)) }
                            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        ConnectionStrip(vm)
    }
}

/** Invite à accorder localisation (« toujours ») + notifications pour l'enregistrement automatique des trajets. */
@Composable
private fun TripPermissionCard() {
    val c = Cd.c
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val ok = remember(tick) { Perms.tripsOk(ctx) }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++; TripService.start(ctx) }
    val fgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        if (Perms.locationOk(ctx) && !Perms.backgroundLocationOk(ctx) && Build.VERSION.SDK_INT >= 29) bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        TripService.start(ctx)
    }
    if (ok) return
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.tc).padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Ico(Icons.Rounded.LocationOn, 22, c.otc)
        Column(Modifier.weight(1f)) {
            T("Enregistrement automatique des trajets", 14, c.otc, 600)
            T("Autorisez la localisation « Toujours » et les notifications pour que CarDeck enregistre vos trajets dès que le moteur démarre.", 13, c.otc, lineHeight = 18, modifier = Modifier.padding(top = 2.dp))
            Box(Modifier.padding(top = 4.dp)) {
                TextBtn("Autoriser", {
                    if (!Perms.locationOk(ctx) || !Perms.notificationsOk(ctx)) {
                        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
                        fgLauncher.launch(list.toTypedArray())
                    } else if (Build.VERSION.SDK_INT >= 29) {
                        bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }, color = c.otc, weight = 600)
            }
        }
    }
}

@Composable
private fun TileCard(t: Tile, c: CdColors, modifier: Modifier) {
    val bg = c.pc
    val fg = c.opc
    Box(
        modifier.height(116.dp).clip(RoundedCornerShape(20.dp)).background(c.sf2).clickable(onClick = t.onClick).padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(bg), contentAlignment = Alignment.Center) { Ico(t.icon, 28, fg) }
            T(t.label, 13, weight = 500, align = TextAlign.Center, lineHeight = 17)
        }
        if (t.badge.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 6.dp).height(20.dp).width(20.dp).clip(CircleShape).background(c.e),
                contentAlignment = Alignment.Center,
            ) { T(t.badge, 12, c.oe, 600) }
        }
    }
}

/** Bandeau « Appli — OBD2 — ECU » reflétant l'état réel de la liaison. */
@Composable
private fun ConnectionStrip(vm: AppViewModel) {
    val c = Cd.c
    val st by vm.obd.state.collectAsState()
    val ecu by vm.obd.ecuOnline.collectAsState()
    val obdOk = st is ObdState.Connected
    Row(
        Modifier.fillMaxWidth().height(76.dp).background(c.sf2).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StripNode(Icons.Rounded.Smartphone, "Appli", true)
        StripLink(Modifier.weight(1f), obdOk)
        StripNode(Icons.Rounded.SettingsInputHdmi, "OBD2", obdOk)
        StripLink(Modifier.weight(1f), obdOk && ecu)
        StripNode(Icons.Rounded.Memory, "ECU", obdOk && ecu)
    }
}

@Composable
private fun StripNode(icon: ImageVector, label: String, on: Boolean) {
    val c = Cd.c
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (on) c.gc else c.sf3), contentAlignment = Alignment.Center) { Ico(icon, 20, if (on) c.ogc else c.onv) }
        T(label, 11, c.onv, 500)
    }
}

@Composable
private fun StripLink(modifier: Modifier, on: Boolean) {
    val c = Cd.c
    val inf = rememberInfiniteTransition(label = "link")
    val x by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400)), label = "x")
    Canvas(modifier.height(20.dp)) {
        val y = size.height / 2 - 9.dp.toPx()
        drawLine(if (on) c.g.copy(alpha = .9f) else c.olv, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        if (on) {
            val a = when { x < .2f -> x / .2f; x > .8f -> (1 - x) / .2f; else -> 1f }
            drawCircle(c.g.copy(alpha = a), 4.dp.toPx(), Offset(size.width * x, y))
        }
    }
}
