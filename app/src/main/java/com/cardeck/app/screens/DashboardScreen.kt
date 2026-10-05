package com.cardeck.app.screens

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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
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

@Composable
fun DashboardScreen(vm: AppViewModel) {
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
    // PIDs du modèle (liste propre à chaque modèle de véhicule).
    LaunchedEffect(connected, tpl.id) {
        if (!connected || tpl.pids.isEmpty()) return@LaunchedEffect
        while (true) {
            for (p in tpl.pids) {
                withContext(Dispatchers.IO) { vm.obd.use { it.query(p.request, p.header) } }?.let { values[p.id] = p.decode(it) }
            }
            delay(250)
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
                T(v.plate.ifBlank { tpl.code }, 12, c.onv, mono = true, lineHeight = 16)
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
            T("Données moteur", 16, weight = 500, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            if (tpl.pids.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, c.olv, RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(c.sf3), contentAlignment = Alignment.Center) { Ico(Icons.Rounded.HourglassEmpty, 30, c.onv) }
                    T("Données moteur à venir", 18, weight = 500, modifier = Modifier.padding(top = 16.dp))
                    T(
                        "La liste des PIDs propres à la ${tpl.fullName} n'est pas encore intégrée. Le tableau de bord affichera ici les valeurs exactes de votre voiture.",
                        14, c.onv, align = TextAlign.Center, lineHeight = 20, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    tpl.pids.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { p ->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).border(1.dp, c.olv, RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.sc), contentAlignment = Alignment.Center) { Ico(p.icon, 18, c.osc) }
                                        T(p.label, 12, c.onv, lineHeight = 16)
                                    }
                                    val value = values[p.id]
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        T(value?.let { fr(it, p.decimals) } ?: "—", 24, weight = 500, lineHeight = 32)
                                        T(p.unit, 12, c.onv, modifier = Modifier.padding(bottom = 4.dp))
                                    }
                                    val pct = value?.let { ((it - p.min) / (p.max - p.min)).toFloat().coerceIn(0f, 1f) } ?: 0f
                                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.sf4)) {
                                        Box(Modifier.fillMaxWidth(pct).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(c.p))
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
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
