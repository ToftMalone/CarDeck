package com.cardeck.app.screens

import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.Screen
import com.cardeck.app.ScanPhase
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.CdColors
import com.cardeck.app.ui.ConnDot
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.T

private data class Tile(val label: String, val icon: ImageVector, val kind: Char, val onClick: () -> Unit, val badge: String = "")

@Composable
fun HomeScreen(vm: AppViewModel) {
    val c = Cd.c
    val v = vm.vehicle
    val phase = vm.phase
    val tiles = listOf(
        Tile("Tableau de bord", Icons.Rounded.Dashboard, 'p', { vm.goDash() }),
        Tile("Diagnostic", Icons.Rounded.Troubleshoot, 'p', { vm.go(Screen.Diag) }, if (phase == ScanPhase.Results) "2" else ""),
        Tile("Mode HUD", Icons.Rounded.Flip, 'p', { vm.goDash(true) }),
        Tile("Trajets", Icons.Rounded.Route, 't', { vm.go(Screen.Trips); vm.loadTrips() }),
        Tile("Ajouter un véhicule", Icons.Rounded.AddCircle, 's', { vm.go(Screen.AddVehicle) }),
        Tile("Boîtier OBD2", Icons.Rounded.SettingsInputHdmi, 's', { vm.go(Screen.Pair) }),
        Tile("Paramètres", Icons.Rounded.Settings, 's', { vm.go(Screen.Settings) }),
    )
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.p), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.Speed, 20, c.op) }
                T("CarDeck", 20, weight = 600, modifier = Modifier.weight(1f))
                Box(Modifier.size(48.dp).clip(CircleShape).clickable { vm.go(Screen.Settings) }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(c.tc), contentAlignment = Alignment.Center) { T("CL", 13, c.otc, 600) }
                }
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
                T("Bonjour Camille", 28, lineHeight = 36, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.pc).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(c.p), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.DirectionsCar, 24, c.op) }
                        Column(Modifier.weight(1f)) {
                            T(v.name, 18, c.opc, 500, lineHeight = 24)
                            T(v.plate, 12, c.opc, mono = true)
                        }
                        Row(
                            Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(c.gc).clickable { vm.go(Screen.Pair) }.padding(start = 12.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) { ConnDot(); T("Connecté", 13, c.ogc, 600) }
                    }
                    Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(1.dp).background(c.opc.copy(alpha = .22f)))
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.go(Screen.Trips); vm.loadTrips() }.padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Stat("Km moyens / jour", "46,3", "km", c.opc, Modifier.weight(1f))
                        Stat("Conduite / jour", "01:25", "h:min", c.opc, Modifier.weight(1f))
                        val (dv, dd, dc) = when (phase) {
                            ScanPhase.Results -> Triple("Alerte", "6 codes · 09:38", c.e)
                            ScanPhase.Clean -> Triple("OK", "Aucun code · 09:41", c.g)
                            else -> Triple("OK", "Il y a 3 jours", c.opc)
                        }
                        Column(Modifier.weight(1f)) {
                            T("Dernier diagnostic", 12, c.opc.copy(alpha = .85f), lineHeight = 16)
                            T(dv, 20, dc, 600, Modifier.padding(top = 4.dp), lineHeight = 28)
                            T(dd, 11, c.opc.copy(alpha = .85f))
                        }
                    }
                }
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
        ConnectionStrip()
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        T(label, 12, color.copy(alpha = .85f), lineHeight = 16)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            T(value, 24, color, 500, lineHeight = 30)
            T(unit, 12, color.copy(alpha = .85f), modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun TileCard(t: Tile, c: CdColors, modifier: Modifier) {
    val (bg, fg) = when (t.kind) { 'p' -> c.pc to c.opc; 't' -> c.tc to c.otc; else -> c.sc to c.osc }
    Box(
        modifier.height(116.dp).clip(RoundedCornerShape(20.dp)).background(c.sf2).clickable(onClick = t.onClick).padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(bg), contentAlignment = Alignment.Center) { Ico(t.icon, 28, fg) }
            T(t.label, 13, weight = 500, align = TextAlign.Center, lineHeight = 17)
        }
        if (t.badge.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 0.dp, end = 6.dp).height(20.dp).width(20.dp).clip(CircleShape).background(c.e),
                contentAlignment = Alignment.Center,
            ) { T(t.badge, 12, c.oe, 600) }
        }
    }
}

/** Bandeau « Appli — OBD2 — ECU » avec liaisons animées. */
@Composable
private fun ConnectionStrip() {
    val c = Cd.c
    Row(
        Modifier.fillMaxWidth().height(76.dp).background(c.sf2).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StripNode(Icons.Rounded.Smartphone, "Appli")
        StripLink(Modifier.weight(1f))
        StripNode(Icons.Rounded.SettingsInputHdmi, "OBD2")
        StripLink(Modifier.weight(1f))
        StripNode(Icons.Rounded.Memory, "ECU")
    }
}

@Composable
private fun StripNode(icon: ImageVector, label: String) {
    val c = Cd.c
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.gc), contentAlignment = Alignment.Center) { Ico(icon, 20, c.ogc) }
        T(label, 11, c.onv, 500)
    }
}

@Composable
private fun StripLink(modifier: Modifier) {
    val c = Cd.c
    val inf = rememberInfiniteTransition(label = "link")
    val x by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400)), label = "x")
    Canvas(modifier.height(20.dp).padding(bottom = 0.dp)) {
        val y = size.height / 2 - 9.dp.toPx()
        drawLine(c.g.copy(alpha = .9f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        val a = when { x < .2f -> x / .2f; x > .8f -> (1 - x) / .2f; else -> 1f }
        drawCircle(c.g.copy(alpha = a), 4.dp.toPx(), Offset(size.width * x, y))
    }
}
