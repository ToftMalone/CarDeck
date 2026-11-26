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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import com.cardeck.app.ui.Text
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

/** Fraction d'une valeur entre les bornes de sa donnée. */
private fun frac(def: PidDef, v: Double?) = v?.let { ((it - def.min) / (def.max - def.min)).toFloat().coerceIn(0f, 1f) }

/**
 * Tableau de bord (Standard ou Sport) : grille de jauges circulaires, deux par ligne, au design d'All Radio Tools
 * (cartes, couleur de la rubrique, polices, entrées en cascade, arcs à ressort).
 */
@Composable
fun DashboardScreen(vm: AppViewModel) {
    val c = Cd.c
    val acc = Cd.acc
    val v = rememberActiveVehicle(vm) ?: return
    val (_, connKind) = connStatus(vm, v)
    val connected = connKind == ConnKind.Ok
    val supported by vm.obd.supported.collectAsState()
    val values by vm.obd.live.collectAsState()
    val sport = vm.dashSport

    // Toutes les données sont lues en permanence par le service : l'écran affiche les dernières valeurs.
    LaunchedEffect(Unit) { vm.obd.keepReading() }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        com.cardeck.app.ui.PageBar(if (sport) "Tableau Sport" else "Tableau de bord", v.nickname, { vm.back() }, scrolled = scroll.value > 48) {
            ModeSelector(sport) { vm.updateDashSport(it) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val sup = supported
            // Non connecté → lecture des données disponibles → jauges : passage en fondu, pas d'un coup.
            val phase = when { !connected -> 0; sup == null -> 1; else -> 2 }
            com.cardeck.app.ui.StateSwap(phase) { ph -> when {
                ph == 0 -> {
                    com.cardeck.app.ui.EmptyStateCard(
                        if (v.adapter == null) Icons.Rounded.SettingsInputHdmi else Icons.Rounded.LinkOff,
                        "Boîtier non connecté", "Connectez le boîtier OBD2 pour afficher les données du moteur.",
                    ) {
                        if (v.adapter == null) com.cardeck.app.ui.TonalBtn("Associer un boîtier", { vm.pairActive() })
                        else com.cardeck.app.ui.TonalBtn("Se connecter", { vm.connectNow() })
                    }
                }
                ph == 1 || sup == null -> com.cardeck.app.ui.EmptyStateCard(
                    Icons.Rounded.HourglassEmpty, "Lecture des données disponibles…",
                    "Mettez le contact. CarDeck interroge le véhicule pour savoir quelles données il fournit.",
                )
                else -> {
                    val gauges = DashGauges.gauges(
                        sport, values, sup, v.template.pids.map { it.id }.toSet(),
                        if (sport) v.template.pids.filter { it.id in v.template.dashSport } else emptyList(),
                    )
                    // Changement Standard / Sport : les cartes se renouvellent en fondu enchaîné.
                    AnimatedContent(
                        sport, label = "dash",
                        transitionSpec = { (fadeIn(tween(400, 90, Motion.Emphasized)) + androidx.compose.animation.slideInVertically(tween(400, 90, Motion.Emphasized)) { it / 30 }) togetherWith fadeOut(tween(90)) },
                    ) { _ ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            gauges.chunked(2).forEachIndexed { i, row ->
                                Row(Modifier.height(IntrinsicSize.Min).rise(i), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { g -> GaugeCard(g, Modifier.weight(1f).fillMaxHeight()) }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } }
        }
    }
}

/**
 * Carte de jauge (même disposition qu'avant) au design d'All Radio Tools : icône dans un carré arrondi à la couleur de la
 * rubrique, arc de 270° qui se remplit depuis zéro à l'apparition puis suit la valeur avec un ressort, point au bout de
 * l'arc, grande valeur en chiffres alignés, bornes sous les extrémités.
 */
@Composable
private fun GaugeCard(g: Gauge, modifier: Modifier) {
    val c = Cd.c
    val acc = Cd.acc
    val p = g.def
    val value = g.value
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(c.sf1).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.cardeck.app.ui.LeadingIcon(p.icon, acc.container, acc.onContainer, size = 28.dp, iconSize = 16, shape = RoundedCornerShape(10.dp))
            Text(p.label, Modifier.weight(1f), style = rf(12, 16, 500), color = c.onv, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Box(Modifier.padding(top = 6.dp).size(138.dp), contentAlignment = Alignment.Center) {
            com.cardeck.app.ui.ArcGauge(frac(p, value) ?: 0f, acc.accent, 10f, Modifier.fillMaxSize(), track = c.sf3, knob = if (value != null) acc.onAccent else null)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value?.let { p.text(it) } ?: "—", Modifier.padding(horizontal = 16.dp), style = gs(30, 36, 500, -0.5f, tnum = true), color = c.on, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(p.unit, style = rf(12, 16), color = c.onv, maxLines = 1)
            }
            val dec = if (p.max - p.min < 10) 1 else 0
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fr(p.min, dec), style = rf(10, 12, tnum = true), color = c.onv)
                Text(fr(p.max, dec), style = rf(10, 12, tnum = true), color = c.onv)
            }
        }
        if (g.note != null) Text(g.note, Modifier.padding(top = 4.dp), style = rf(11, 14), color = c.onv, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
    }
}

/** Sélecteur Standard / Sport, en haut à droite. */
@Composable
private fun ModeSelector(sport: Boolean, onChange: (Boolean) -> Unit) {
    val c = Cd.c
    val acc = Cd.acc
    val haptics = Cd.haptics
    Row(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(c.sf3).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(false to "Standard", true to "Sport").forEach { (isSport, label) ->
            val sel = isSport == sport
            val bg by animateColorAsState(if (sel) acc.accent else c.sf3, tween(250, easing = Motion.Emphasized), label = "mode")
            val fg by animateColorAsState(if (sel) acc.onAccent else c.onv, tween(250, easing = Motion.Emphasized), label = "modeFg")
            Box(
                Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable { if (!sel) { haptics.segment(); onChange(isSport) } }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { T(label, 13, fg, 600, maxLines = 1) }
        }
    }
}
