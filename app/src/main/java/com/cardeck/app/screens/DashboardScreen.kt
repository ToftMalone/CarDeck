package com.cardeck.app.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.data.DashGauges
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.T
import com.cardeck.app.ui.rise
import com.cardeck.app.ui.TextBtn

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val c = Cd.c
    val v = rememberActiveVehicle(vm) ?: return
    val (_, connKind) = connStatus(vm, v)
    val connected = connKind == ConnKind.Ok
    val supported by vm.obd.supported.collectAsState()
    val values by vm.obd.live.collectAsState()
    val sport = vm.dashSport

    // Toutes les données sont lues en permanence par le service : l'écran affiche les dernières valeurs.
    LaunchedEffect(Unit) { vm.obd.keepReading() }

    Column(Modifier.fillMaxSize()) {
        com.cardeck.app.ui.PageBar(if (sport) "Tableau Sport" else "Tableau de bord", v.nickname, { vm.back() }) {
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
                    val gauges = DashGauges.gauges(
                        sport, values, sup, v.template.pids.map { it.id }.toSet(),
                        if (sport) v.template.pids.filter { it.id in v.template.dashSport } else emptyList(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        gauges.chunked(2).forEachIndexed { i, row ->
                            Row(Modifier.rise(i), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { g -> GaugeCard(g.def, g.value, Modifier.weight(1f), g.note) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            com.cardeck.app.DockSpace()
        }
    }
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
            val bg by animateColorAsState(if (sel) acc.accent else c.sf3, tween(250, easing = com.cardeck.app.ui.Motion.Emphasized), label = "mode")
            val fg by animateColorAsState(if (sel) acc.onAccent else c.onv, tween(250, easing = com.cardeck.app.ui.Motion.Emphasized), label = "modeFg")
            Box(
                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable { if (!sel) { haptics.segment(); onChange(isSport) } }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { T(label, 13, fg, 600) }
        }
    }
}

