package com.cardeck.app.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.cardeck.app.ui.ArcGauge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardeck.app.AppViewModel
import com.cardeck.app.data.PidDef
import com.cardeck.app.data.StdPids
import com.cardeck.app.obd.ObdState
import com.cardeck.app.ui.Cd
import com.cardeck.app.ui.Ico
import com.cardeck.app.ui.IconBtn
import com.cardeck.app.ui.T
import com.cardeck.app.ui.rise
import com.cardeck.app.ui.rf
import com.cardeck.app.ui.spinning
import com.cardeck.app.ui.rememberSpin
import com.cardeck.app.ui.cloverShape
import androidx.compose.foundation.layout.fillMaxHeight
import com.cardeck.app.ui.TextBtn

/** Liaison OBD2 + toutes les valeurs envoyées par le véhicule. */
@Composable
fun ObdDataScreen(vm: AppViewModel) {
    val c = Cd.c
    val v = rememberActiveVehicle(vm) ?: return
    val tpl = v.template
    val (connLabel, connKind) = connStatus(vm, v)
    val st by vm.obd.state.collectAsState()
    val ecu by vm.obd.ecuOnline.collectAsState()
    val protocol by vm.obd.protocol.collectAsState()
    val connected = connKind == ConnKind.Ok
    // Toutes les données sont lues en permanence par le service de suivi : l'écran affiche ses dernières mesures.
    val values by vm.obd.live.collectAsState()
    val supported by vm.obd.supported.collectAsState()

    Column(Modifier.fillMaxSize()) {
        com.cardeck.app.ui.PageBar("Données OBD2", v.nickname + " · " + v.plate.ifBlank { tpl.code }, { vm.back() }) {
            com.cardeck.app.ui.StatusChip(connLabel, connDot(connKind), blink = connKind != ConnKind.Off)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            // Explorateur : découverte de tous les calculateurs et de leurs données (carte à sa couleur).
            val ex = com.cardeck.app.ui.accentRoles(com.cardeck.app.ui.Section.Explorer, c.dark)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).rise(0).clip(RoundedCornerShape(28.dp)).background(ex.container)
                    .clickable { vm.go(com.cardeck.app.Screen.Explorer) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    com.cardeck.app.ui.PulseRing(ex.accent, 40.dp, periodMs = 1600)
                    Box(Modifier.size(44.dp).spinning(rememberSpin(20_000)).clip(cloverShape()).background(ex.accent))
                    Ico(Icons.Rounded.TravelExplore, 24, ex.onAccent)
                }
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text("Explorateur", style = rf(16, 22, 600), color = ex.onContainer)
                    androidx.compose.material3.Text(
                        if (vm.explorer.running) "En cours · ${vm.explorer.mode ?: "préparation"}" else "Tous les calculateurs et leurs données · à l'arrêt ou en roulant",
                        style = rf(13, 18), color = ex.onContainer.copy(alpha = .85f),
                    )
                }
                Ico(Icons.AutoMirrored.Rounded.KeyboardArrowRight, 24, ex.onContainer)
            }
            // Liaison
            val a = v.adapter
            com.cardeck.app.ui.InfoList("Liaison OBD2", Modifier.rise(1)) {
                if (a == null) {
                    T("Aucun boîtier n'est associé à ce véhicule.", 14, c.onv, modifier = Modifier.padding(vertical = 8.dp))
                    TextBtn("Associer un boîtier", { vm.pairActive() })
                } else {
                    com.cardeck.app.ui.InfoLine("Boîtier", "${a.name} · ${a.transport.label}")
                    com.cardeck.app.ui.InfoLine("Interface", (st as? ObdState.Connected)?.version ?: "—")
                    com.cardeck.app.ui.InfoLine("Protocole", if (connected) protocol ?: "—" else "—")
                    com.cardeck.app.ui.InfoLine("Véhicule", if (!connected) "—" else if (ecu) "Calculateur moteur joignable" else "Contact coupé")
                    com.cardeck.app.ui.InfoLine("VIN", v.vin ?: "Non lu")
                    (st as? ObdState.Failed)?.takeIf { it.adapter.address == a.address }?.let {
                        T(it.message, 13, c.e, modifier = Modifier.padding(top = 8.dp), lineHeight = 18)
                    }
                    if (connKind == ConnKind.Off) Box(Modifier.padding(top = 4.dp, bottom = 8.dp)) { com.cardeck.app.ui.TonalBtn("Se connecter", { vm.connectNow() }) }
                }
            }
            // Toujours affichées, même boîtier déconnecté : données déclarées par le véhicule (dernière connexion),
            // ou toute la liste lue par CarDeck tant que le véhicule n'a jamais été interrogé.
            val cached = remember(v.id, supported) { vm.repo.supportedPids(v.id) }
            val known = supported ?: cached
            val std = known?.let { StdPids.forSupported(it) } ?: StdPids.all
            androidx.compose.material3.Text("Toutes les valeurs du véhicule", Modifier.padding(start = 4.dp, top = 24.dp, bottom = 4.dp), style = com.cardeck.app.ui.gs(22, 28, 500), color = c.on)
            when {
                !connected -> T("Boîtier non connecté : les valeurs s'afficheront dès la connexion.", 13, c.onv, lineHeight = 18, modifier = Modifier.padding(bottom = 4.dp))
                supported == null -> T("Mets le contact : CarDeck interroge le véhicule pour savoir quelles données il fournit.", 13, c.onv, lineHeight = 18, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (std.isEmpty()) EmptyNote(Icons.Rounded.HourglassEmpty, "Aucune donnée standard", "Ce véhicule ne déclare aucun PID OBD2 standard.")
            StdPids.CATEGORIES.forEach { cat ->
                val inCat = std.filter { it.category == cat }
                if (inCat.isNotEmpty()) PidSection(cat, inCat, values)
            }
            tpl.pids.groupBy { it.category }.forEach { (cat, list) -> PidSection("$cat · ${tpl.model}", list, values) }
            T(
                if (known != null) "${std.size + tpl.pids.size} valeurs fournies par le véhicule" else "${std.size + tpl.pids.size} valeurs lues par CarDeck (le véhicule n'a pas encore été interrogé)",
                12, c.onv, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            com.cardeck.app.DockSpace()
        }
    }
}

/** Titre de catégorie (couleur de l'accent) + tuiles des données correspondantes, entrée en cascade. */
@Composable
private fun PidSection(title: String, pids: List<PidDef>, values: Map<String, Double>) {
    com.cardeck.app.ui.SectionLabel(title)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pids.chunked(2).forEachIndexed { i, row ->
            Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min).rise(i), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p -> PidCard(p, values[p.id], Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** État vide (biscuit neutre qui tourne lentement), comme dans All Radio Tools. */
@Composable
internal fun EmptyNote(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    com.cardeck.app.ui.EmptyStateCard(icon, title, text)
}

/** Tuile de mesure d'All Radio Tools : libellé, grande valeur alignée, unité, barre de niveau à la couleur de la rubrique. */
@Composable
private fun PidCard(p: PidDef, value: Double?, modifier: Modifier) {
    com.cardeck.app.ui.MetricTile(
        p.label, value?.let { p.text(it) } ?: "—", p.unit, p.icon,
        level = value?.let { ((it - p.min) / (p.max - p.min)).toFloat().coerceIn(0f, 1f) } ?: 0f,
        modifier = modifier,
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = Cd.c
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        T(label, 14, c.onv, modifier = Modifier.weight(1f))
        T(value, 14, weight = 500, align = TextAlign.End, modifier = Modifier.weight(1.6f))
    }
}
