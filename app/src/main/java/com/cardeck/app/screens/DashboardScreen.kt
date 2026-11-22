package com.cardeck.app.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.data.DashGauges
import com.cardeck.app.data.Gauge
import com.cardeck.app.data.PidDef
import com.cardeck.app.data.StdPids
import com.cardeck.app.data.fr
import com.cardeck.app.ui.BlinkDot
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.CdChip
import com.cardeck.app.ui.GaugeTile
import com.cardeck.app.ui.IconChip
import com.cardeck.app.ui.LiveChart
import com.cardeck.app.ui.Motion
import com.cardeck.app.ui.SemiGauge
import com.cardeck.app.ui.ShapeBadge
import com.cardeck.app.ui.StatePanel
import com.cardeck.app.ui.T
import com.cardeck.app.ui.cookieShape
import com.cardeck.app.ui.fadeUp
import com.cardeck.app.ui.gs
import com.cardeck.app.ui.niceStep
import com.cardeck.app.ui.rf
import com.cardeck.app.ui.rise
import kotlinx.coroutines.delay

/** Points gardés pour la courbe en direct (un toutes les 500 ms : 30 s). */
private const val HISTORY = 60

private fun std(id: String) = StdPids.all.first { it.id == id }

/** Fraction d'une valeur entre les bornes de sa donnée. */
private fun frac(def: PidDef, v: Double?) = v?.let { ((it - def.min) / (def.max - def.min)).toFloat().coerceIn(0f, 1f) }

/**
 * Tableau de bord, comme ceux d'All Radio Tools : grande carte d'en-tête à la couleur de la rubrique (état du moteur,
 * pastilles, jauge en demi-cercle), courbe en direct au choix, puis tuiles des autres données avec leur anneau.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(vm: AppViewModel) {
    val c = Cd.c
    val acc = Cd.acc
    val v = rememberActiveVehicle(vm) ?: return
    val (_, connKind) = connStatus(vm, v)
    val connected = connKind == ConnKind.Ok
    val supported by vm.obd.supported.collectAsState()
    val values by vm.obd.live.collectAsState()
    val ecu by vm.obd.ecuOnline.collectAsState()
    val sport = vm.dashSport

    // Toutes les données sont lues en permanence par le service : l'écran affiche les dernières valeurs.
    LaunchedEffect(Unit) { vm.obd.keepReading() }

    Column(Modifier.fillMaxSize()) {
        com.cardeck.app.ui.PageBar(if (sport) "Tableau Sport" else "Tableau de bord", v.nickname, { vm.back() }) {
            ModeSelector(sport) { vm.updateDashSport(it) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val sup = supported
            when {
                !connected -> StatePanel(
                    if (v.adapter == null) Icons.Rounded.SettingsInputHdmi else Icons.Rounded.LinkOff,
                    "Boîtier non connecté",
                    "Connectez le boîtier OBD2 pour afficher les données du moteur en direct.",
                    if (v.adapter == null) "Associer un boîtier" to { vm.pairActive() } else "Se connecter" to { vm.connectNow() },
                )
                sup == null -> StatePanel(
                    Icons.Rounded.HourglassEmpty, "Lecture en cours…",
                    "Mettez le contact. CarDeck interroge le véhicule pour savoir quelles données il fournit.",
                )
                else -> {
                    val gauges = DashGauges.gauges(
                        sport, values, sup, v.template.pids.map { it.id }.toSet(),
                        if (sport) v.template.pids.filter { it.id in v.template.dashSport } else emptyList(),
                    )
                    fun valueOf(def: PidDef): Double? = gauges.firstOrNull { it.def.id == def.id }?.value ?: values[def.id]

                    // En-tête : vitesse (Standard) ou suralimentation (Sport, à défaut le régime).
                    val hero: PidDef = if (!sport) std("std0D") else if (gauges.any { it.def.id == DashGauges.BOOST_BAR.id && it.note != DashGauges.MISSING }) DashGauges.BOOST_BAR else std("std0C")
                    Hero(v.nickname, engineLabel(values["std0C"], ecu), hero, valueOf(hero), heroLabel(sport, values), chips(sport, values))

                    // Courbe en direct, donnée au choix.
                    val options = if (!sport) listOf(std("std0C"), std("std0D"), std("std05")) else listOf(DashGauges.BOOST_BAR, std("std0E"), std("std0C"))
                    LiveCard(options, ::valueOf)

                    // Tuiles des autres données, deux par ligne, de même hauteur.
                    val tiles = gauges.filter { it.def.id != hero.id }
                    tiles.chunked(2).forEachIndexed { i, row ->
                        Row(Modifier.height(IntrinsicSize.Min).rise(i + 2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { g -> Tile(g, Modifier.weight(1f).fillMaxHeight()) }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            com.cardeck.app.DockSpace()
        }
    }
}

private fun engineLabel(rpm: Double?, ecu: Boolean) = when {
    rpm != null && rpm > 300 -> "Moteur tournant · ${fr(rpm)} tr/min"
    ecu -> "Contact mis, moteur coupé"
    else -> "En attente du calculateur moteur"
}

private fun heroLabel(sport: Boolean, values: Map<String, Double>): String =
    if (!sport) "km/h" + (values["std0C"]?.let { " · ${fr(it)} tr/min" } ?: "")
    else "bar · turbo" + (values["std0C"]?.let { " · ${fr(it)} tr/min" } ?: "")

/** Pastilles de l'en-tête : quelques valeurs d'un coup d'œil. */
private fun chips(sport: Boolean, values: Map<String, Double>): List<Pair<PidDef, String>> {
    val ids = if (!sport) listOf("std05", "std5C", "std2F", "std42") else listOf("std0F", "std0E", "std05")
    return ids.mapNotNull { id -> StdPids.all.firstOrNull { it.id == id }?.let { d -> values[id]?.let { d to d.withUnit(it) } } }
}

/** Carte d'en-tête : conteneur de l'accent, coins de 32 dp, entrée en fondu montant. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(title: String, subtitle: String, def: PidDef, value: Double?, label: String, chips: List<Pair<PidDef, String>>) {
    val c = Cd.c
    val acc = Cd.acc
    Column(Modifier.fadeUp().fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(acc.container).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(title, style = gs(28, 34, 500), color = acc.onContainer, maxLines = 1)
                Text(subtitle, Modifier.graphicsLayer { alpha = 0.85f }, style = rf(14, 20, tnum = true), color = acc.onContainer)
            }
            ShapeBadge(Icons.Rounded.DirectionsCar, cookieShape(), 44.dp, acc.accent, acc.onAccent, 24.dp, spinMs = 30_000)
        }
        if (chips.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { (d, text) -> IconChip(d.icon, text, acc.accent) }
            }
        }
        SemiGauge(
            frac(def, value), acc.accent, c.sf, value?.let { def.text(it) } ?: "—", label,
            fr(def.min, if (def.max - def.min < 10) 1 else 0), fr(def.max, if (def.max - def.min < 10) 1 else 0),
            Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp), labelColor = acc.onContainer,
        )
    }
}

/** Courbe en direct : un point toutes les 500 ms sur 30 s, la donnée se choisit par les puces. */
@Composable
private fun LiveCard(options: List<PidDef>, valueOf: (PidDef) -> Double?) {
    val c = Cd.c
    val acc = Cd.acc
    var chosen by remember { mutableStateOf(options.first().id) }
    if (options.none { it.id == chosen }) chosen = options.first().id
    val history = remember { mutableStateMapOf<String, List<Float>>() }
    val read by rememberUpdatedState(valueOf)
    val opts by rememberUpdatedState(options)
    LaunchedEffect(Unit) {
        while (true) {
            for (d in opts) read(d)?.let { x -> history[d.id] = (history[d.id].orEmpty() + x.toFloat()).takeLast(HISTORY) }
            delay(500)
        }
    }
    val def = options.first { it.id == chosen }
    Column(Modifier.rise(1).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.sf1).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("En direct", Modifier.weight(1f), style = rf(16, 22, 600), color = c.on)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BlinkDot(c.e, 8.dp, 1200)
                Text("2 mesures/s · 30 s", style = rf(12, 16), color = c.onv)
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { d -> CdChip(d.label.substringBefore(" (").substringBefore(" de "), d.id == chosen) { chosen = d.id } }
        }
        AnimatedContent(
            def, label = "chart",
            transitionSpec = { fadeIn(tween(300, easing = Motion.Emphasized)) togetherWith fadeOut(tween(150)) },
        ) { d ->
            val samples = history[d.id].orEmpty()
            val step = niceStep(d.min, d.max)
            Column {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(valueOf(d)?.let { d.text(it) } ?: "—", style = gs(28, 32, 500, tnum = true), color = c.on)
                    Text(d.unit, Modifier.padding(bottom = 3.dp), style = rf(12, 16), color = c.onv)
                }
                if (samples.size < 2) T("La courbe apparaît après quelques mesures…", 13, c.onv, modifier = Modifier.padding(vertical = 56.dp))
                else LiveChart(samples, d.min.toFloat(), d.max.toFloat(), step.toFloat(), acc.accent, Modifier.padding(top = 10.dp), capacity = HISTORY, decimals = if (step < 1) 1 else 0)
            }
        }
    }
}

/** Tuile d'une donnée : valeur, unité, anneau de niveau, note (absente, estimée…). */
@Composable
private fun Tile(g: Gauge, modifier: Modifier) {
    GaugeTile(g.def.label, g.value?.let { g.def.text(it) } ?: "—", g.def.unit, g.def.icon, frac(g.def, g.value) ?: 0f, g.note, modifier)
}

/** Sélecteur Standard / Sport, en haut à droite. */
@Composable
private fun ModeSelector(sport: Boolean, onChange: (Boolean) -> Unit) {
    val c = Cd.c
    val acc = Cd.acc
    val haptics = Cd.haptics
    Row(Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(c.sf3).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(false to "Standard", true to "Sport").forEach { (isSport, label) ->
            val sel = isSport == sport
            val bg by animateColorAsState(if (sel) acc.accent else c.sf3, tween(250, easing = Motion.Emphasized), label = "mode")
            val fg by animateColorAsState(if (sel) acc.onAccent else c.onv, tween(250, easing = Motion.Emphasized), label = "modeFg")
            Box(
                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable { if (!sel) { haptics.segment(); onChange(isSport) } }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { T(label, 13, fg, 600) }
        }
    }
}
